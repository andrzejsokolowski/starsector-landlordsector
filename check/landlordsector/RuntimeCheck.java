package landlordsector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.MutableValue;

/**
 * Drives the money loop on stand-in markets without the game: buying, rent, tariff, wear, condemning,
 * repairs, sales, reputation, hostility, lost colonies, NPC buyers on a player colony, the homeowner
 * bonuses through the OddiszCommons ledger, and a save round trip through XStream.
 */
public final class RuntimeCheck {

    private static final List<String> problems = new ArrayList<>();
    private static final Map<String, Object> persistent = new HashMap<>();
    private static final Map<String, MarketAPI> markets = new HashMap<>();
    private static final Map<String, Float> repChanges = new HashMap<>();
    private static final Set<String> hostileFactions = new java.util.HashSet<>();
    private static final MutableValue credits = new MutableValue(50_000_000f);
    /** Credits are a float: at tens of millions one step is several credits. */
    private static final float CREDIT_EPS = 16f;
    private static long now = 1_000_000L;
    private static final long DAY = 1000L;

    /** Fields of one stand-in market. */
    static final class FakeMarket {
        String id, name, factionId;
        int size;
        float stability, hazard;
        boolean habitable, mild, own;
        final MutableStat tariff = new MutableStat(0.3f);
        final com.fs.starfarer.api.combat.MutableStatWithTempMods stabilityStat = new com.fs.starfarer.api.combat.MutableStatWithTempMods(0f);
        final List<MarketImmigrationModifier> growth = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\n")) {
            int hash = line.indexOf('#');
            text.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        installGlobals();
        TierTable.load(new JSONObject(text.toString()));

        FakeMarket heg = new FakeMarket();
        heg.id = "fake_heg"; heg.name = "Fakeholm"; heg.factionId = "hegemony"; heg.size = 6;
        heg.stability = 7f; heg.hazard = 1f; heg.habitable = true;
        MarketAPI a = market(heg);
        FakeMarket col = new FakeMarket();
        col.id = "fake_colony"; col.name = "Homestead"; col.factionId = "player"; col.size = 5;
        col.stability = 5f; col.hazard = 1.25f; col.own = true;
        MarketAPI b = market(col);
        FakeMarket pir = new FakeMarket();
        pir.id = "fake_pirate"; pir.name = "Cutthroat"; pir.factionId = "pirates"; pir.size = 5;
        MarketAPI c = market(pir);

        expect(MarketRules.isEligible(a) && MarketRules.isEligible(b), "Hegemony and player markets sell homes");
        expect(!MarketRules.isEligible(c), "pirate markets do not sell homes");

        MarketListings la = MarketRules.listings(a);
        expect(la.list().size() == Pricing.listingCount(6), "a size 6 market lists " + Pricing.listingCount(6));
        for (Listing l : la.list()) {
            Tier t = TierTable.get(l.tierId);
            expect(t != null && t.minSize <= 6, "listing tier fits the market size: " + l.tierId);
            expect(t != null && l.basePrice >= t.min && l.basePrice <= t.max, "base price inside the tier range");
            expect("station".equals(l.climateId), "a market with no planet is a station");
        }
        expect(MarketRules.listings(a) == la, "listings stay the same until the refresh");

        // Buy the cheapest on the Hegemony world: the price includes the 30 percent tariff.
        Listing cheap = la.list().get(0);
        float before = credits.get();
        float expected = cheap.basePrice * MarketRules.mult(a) * 1.3f;
        String result = Actions.buy(a, cheap);
        expect(result.startsWith("Bought"), "buying works: " + result);
        expect(Math.abs(before - credits.get() - expected) < CREDIT_EPS, "the buy price includes the tariff");
        expect(!la.list().contains(cheap), "a bought home leaves the listings");
        expect(Actions.buy(a, cheap).startsWith("That home"), "a home cannot be bought twice");
        Property home = Portfolio.get().properties().get(0);

        // On the player's colony: half price, no tariff.
        Listing own = MarketRules.listings(b).list().get(0);
        before = credits.get();
        float ownPrice = own.basePrice * MarketRules.mult(b) * 0.5f;
        Actions.buy(b, own);
        expect(Math.abs(before - credits.get() - ownPrice) < CREDIT_EPS, "own colony homes cost half with no tariff");
        Property colonyHome = Portfolio.get().properties().get(1);

        // One month.
        MonthlyTick tick = new MonthlyTick();
        float paid = month(tick);
        float rentA = home.lastRent;
        expect(rentA > 0f, "rent is paid");
        expect(paid >= rentA + colonyHome.lastRent - 1f, "the income report carries the rent");
        expect(Math.abs(home.condition - (1f - 1f / 24f)) < 1e-3 || home.condition < 1f, "a month wears the home");
        expect(colonyHome.lastRent > 0f, "a colony home pays rent too");

        // Greedy rent costs reputation with the Hegemony and pays more.
        Actions.setRent(home, RentSetting.GREEDY);
        repChanges.clear();
        month(tick);
        expect(home.lastRent > rentA * 1.3f, "greedy rent pays more");
        Float rep = repChanges.get("hegemony");
        expect(rep != null && rep < 0f, "greedy rent costs Hegemony reputation");
        expect(!repChanges.containsKey("player"), "own colonies never change reputation");

        // Hostility freezes rent and blocks the sale.
        hostileFactions.add("hegemony");
        month(tick);
        expect(home.lastRent == 0f && home.frozen, "rent is frozen while hostile");
        expect(Actions.cannotSell(home) != null, "no sale while hostile");
        hostileFactions.clear();

        // Run it down until condemned, then rebuild.
        for (int i = 0; i < 30 && !home.condemned; i++) month(tick);
        expect(home.condemned, "an unrepaired greedy home is condemned within 30 months");
        month(tick);
        expect(home.lastRent == 0f, "a condemned home pays nothing");
        float full = home.fullValue();
        expect(Math.abs(home.value() - full * 0.2f) < 1f, "a condemned home is worth its land");
        before = credits.get();
        expect(Actions.repair(home).startsWith("Rebuilt"), "a condemned home can be rebuilt");
        expect(home.condition == 1f && !home.condemned, "rebuilt to new");
        expect(Math.abs(before - credits.get() - full * 0.1f) < CREDIT_EPS, "a rebuild costs 10 percent of the value");
        expect(Actions.repair(home).startsWith("It needs no"), "a new home needs no repairs");

        // NPC buyers on the colony over a few years.
        for (int i = 0; i < 36; i++) month(tick);
        List<Float> npc = Portfolio.get().npcHomes().get(b.getId());
        expect(npc != null && !npc.isEmpty(), "NPC buyers buy homes on the player colony");
        expect(npc == null || npc.size() <= 5 * 3, "NPC homes stay under size x homesPerSize");
        expect(Portfolio.get().lastTax > 0f, "NPC homes pay property tax");

        // The homeowner bonuses go through the ledger.
        HomeownerScript.install();
        sectorScripts.get(0).advance(0.1f);
        MutableStat.StatMod mod = col.stabilityStat.getFlatStatMod("landlordsector_homeowners");
        expect(mod != null && mod.getValue() > 0f && mod.getValue() <= 1f, "homeowners add stability, at most 1");
        expect(col.growth.size() == 1, "homeowners add one growth modifier");
        PopulationComposition inc = new PopulationComposition();
        col.growth.get(0).modifyIncoming(b, inc);
        expect(inc.getWeight().getFlatStatMod("landlordsector_homeowners") != null, "homeowners add growth");
        HomeownerScript.beforeSave();
        expect(col.stabilityStat.getFlatStatMod("landlordsector_homeowners") == null, "nothing is left in the save");

        // Save round trip.
        com.thoughtworks.xstream.XStream x = new com.thoughtworks.xstream.XStream(new com.thoughtworks.xstream.io.xml.DomDriver());
        com.thoughtworks.xstream.XStream.setupDefaultSecurity(x);
        x.allowTypesByWildcard(new String[] {"landlordsector.**", "java.util.**", "java.lang.**"});
        String xml = x.toXML(Portfolio.get());
        Portfolio back = (Portfolio) x.fromXML(xml);
        expect(back.properties().size() == 2 && back.properties().get(0).setting() == RentSetting.GREEDY,
                "the portfolio survives a save");

        // Sell.
        before = credits.get();
        float sale = home.saleValue();
        expect(Actions.sell(home).startsWith("Sold"), "selling works");
        expect(Math.abs(credits.get() - before - sale) < CREDIT_EPS, "the sale pays the sale value");
        expect(Math.abs(sale - home.value() * (1f - 0.05f - 0.3f)) < 1f, "sale minus 5 percent fee and tariff");

        // A colony that is gone takes its homes with it.
        markets.remove(b.getId());
        month(tick);
        expect(Portfolio.get().properties().isEmpty(), "homes on a lost colony are gone");
        expect(!Portfolio.get().npcHomes().containsKey(b.getId()), "NPC homes of a lost colony are dropped");

        if (!problems.isEmpty()) {
            for (String p : problems) System.err.println("FAIL: " + p);
            System.exit(1);
        }
        System.out.println("Landlordsector runtime verified: first month rent " + Math.round(rentA)
                + ", NPC homes " + (npc == null ? 0 : npc.size()) + ", save size " + xml.length() + " chars");
    }

    /** Thirty days, the last economy tick, then the month-end payment like CoreScript. */
    private static float month(MonthlyTick tick) {
        now += 30 * DAY;
        tick.reportEconomyTick(9);
        MonthlyReport report = SharedData.getData().getCurrentReport();
        report.computeTotals();
        float total = report.getRoot().totalIncome - report.getRoot().totalUpkeep;
        credits.add(total);
        SharedData.getData().setCurrentReport(new MonthlyReport());
        return total;
    }

    private static void expect(boolean ok, String what) {
        if (!ok) problems.add(what);
    }

    // ---- Stand-ins ----

    private static final List<com.fs.starfarer.api.EveryFrameScript> sectorScripts = new ArrayList<>();

    private static void installGlobals() {
        FactionAPI player = faction("player");
        CampaignClockAPI clock = mock(CampaignClockAPI.class, Map.of(
                "getTimestamp", a -> now,
                "getElapsedDaysSince", a -> (now - (Long) a[0]) / (float) DAY));
        MutableValue cr = credits;
        CargoAPI cargo = mock(CargoAPI.class, Map.of("getCredits", a -> cr));
        CampaignFleetAPI fleet = mock(CampaignFleetAPI.class, Map.of("getCargo", a -> cargo));
        EconomyAPI economy = mock(EconomyAPI.class, Map.of(
                "getMarket", a -> markets.get((String) a[0]),
                "getMarketsCopy", a -> new ArrayList<>(markets.values())));
        CampaignUIAPI ui = mock(CampaignUIAPI.class, Map.of());
        SectorAPI sector = mock(SectorAPI.class, Map.of(
                "getPersistentData", a -> persistent,
                "getClock", a -> clock,
                "getEconomy", a -> economy,
                "getPlayerFleet", a -> fleet,
                "getPlayerFaction", a -> player,
                "getCampaignUI", a -> ui,
                "addTransientScript", a -> sectorScripts.add((com.fs.starfarer.api.EveryFrameScript) a[0])));
        SettingsAPI settings = mock(SettingsAPI.class, Map.of(
                "getFloat", a -> "economyIterPerMonth".equals(a[0]) ? 10f : 0f,
                "getSpriteName", a -> "graphics/x.png",
                "getString", a -> "graphics/fonts/insignia15LTaa.fnt"));
        Global.setSettings(settings);
        Global.setSector(sector);
        Global.setSoundPlayer(mock(SoundPlayerAPI.class, Map.of()));
    }

    private static FactionAPI faction(String id) {
        return mock(FactionAPI.class, Map.of(
                "getId", a -> id,
                "getDisplayName", a -> id,
                "isHostileTo", a -> !"player".equals(id) && hostileFactions.contains(id)
                        && "player".equals(String.valueOf(a[0])),
                "adjustRelationship", a -> {
                    repChanges.merge((String) a[0], (Float) a[1], Float::sum);
                    return null;
                }));
    }

    private static MarketAPI market(FakeMarket f) {
        FactionAPI fac = faction(f.factionId);
        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("getId", a -> f.id);
        h.put("getName", a -> f.name);
        h.put("getSize", a -> f.size);
        h.put("getStabilityValue", a -> f.stability);
        h.put("getHazardValue", a -> f.hazard);
        h.put("hasCondition", a -> ("habitable".equals(a[0]) && f.habitable) || ("mild_climate".equals(a[0]) && f.mild));
        h.put("getFaction", a -> fac);
        h.put("getFactionId", a -> f.factionId);
        h.put("isInEconomy", a -> true);
        h.put("isPlayerOwned", a -> f.own);
        h.put("getTariff", a -> f.tariff);
        h.put("getStability", a -> f.stabilityStat);
        h.put("addTransientImmigrationModifier", a -> f.growth.add((MarketImmigrationModifier) a[0]));
        MarketAPI m = mock(MarketAPI.class, h);
        markets.put(f.id, m);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler ih = (proxy, method, args) -> {
            Function<Object[], Object> fn = handlers.get(method.getName());
            if (fn != null) {
                Object r = fn.apply(args == null ? new Object[0] : args);
                if (method.getReturnType() == void.class) return null;
                if (r == null || method.getReturnType().isInstance(r) || method.getReturnType().isPrimitive()) {
                    return r == null ? fallback(method.getReturnType()) : r;
                }
                return fallback(method.getReturnType());
            }
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == args[0];
            if ("toString".equals(method.getName())) return type.getSimpleName() + " stand-in";
            return fallback(method.getReturnType());
        };
        return (T) Proxy.newProxyInstance(RuntimeCheck.class.getClassLoader(), new Class<?>[] {type}, ih);
    }

    private static Object fallback(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == List.class || t == Collection.class) return new ArrayList<>();
        if (t == Map.class) return new HashMap<>();
        return null;
    }
}
