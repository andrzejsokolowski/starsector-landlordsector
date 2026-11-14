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
import java.util.Random;
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
        final List<Object> directory = new ArrayList<>();
        int agentsArrived;
        /** 0.4.0: market conditions, and the open market's special items by id. */
        final java.util.Set<String> conditions = new java.util.HashSet<>();
        final Map<String, Float> openStock = new HashMap<>();
        /** 0.5.0: buildings, id to tags. */
        final Map<String, java.util.Set<String>> industries = new java.util.LinkedHashMap<>();
    }

    /** 0.4.0: special items in the player's cargo, by id. */
    private static final Map<String, Float> cargoSpecials = new HashMap<>();

    public static void main(String[] args) throws Exception {
        installGlobals();
        RulesCheck.loadAll(args[0]);

        FakeMarket heg = new FakeMarket();
        heg.id = "fake_heg"; heg.name = "Fakeholm"; heg.factionId = "hegemony"; heg.size = 6;
        heg.stability = 7f; heg.hazard = 1f; heg.habitable = true;
        MarketAPI a = market(heg);
        FakeMarket col = new FakeMarket();
        col.id = "fake_colony"; col.name = "Homestead"; col.factionId = "player"; col.size = 5;
        col.stability = 5f; col.hazard = 1.25f; col.own = true;
        MarketAPI b = market(col);
        FakeMarket pir = new FakeMarket();
        pir.id = "kantas_den"; pir.name = "Kanta's Den"; pir.factionId = "pirates"; pir.size = 5;
        MarketAPI c = market(pir);

        expect(MarketRules.isEligible(a) && MarketRules.isEligible(b), "Hegemony and player markets sell homes");
        expect(!MarketRules.isEligible(c), "pirate markets do not sell homes");

        // Property agents: everyone eligible gets one when the dice always say yes, the pirates never.
        Random yes = new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }
        };
        Random no = new Random() {
            @Override
            public float nextFloat() {
                return 0.999f;
            }
        };
        Agents.update(yes, true);
        expect(heg.directory.size() == 1 && col.directory.size() == 1, "eligible markets get a property agent");
        expect(pir.directory.isEmpty(), "pirate markets get no property agent");
        Object agentPerson = heg.directory.isEmpty() ? null : heg.directory.get(0);
        now += 24 * 30 * DAY;
        Agents.update(no, false);
        expect(heg.directory.size() == 1 && heg.directory.get(0) == agentPerson, "an agent stays for good (0.3.2)");
        expect(col.directory.size() == 1 && Agents.hasAgent(a) && Agents.hasAgent(b), "every eligible market keeps its agent");

        MarketListings la = MarketRules.listings(a);
        expect(la.list().size() >= 3 && la.list().size() <= 6, "a size 6 market lists 3 to 6 homes (0.5.0), got "
                + la.list().size());
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
        float expected = MarketRules.listingValue(a, cheap) * 1.3f;
        String result = Actions.buy(a, cheap);
        expect(result.startsWith("Bought"), "buying works: " + result);
        expect(Math.abs(before - credits.get() - expected) < CREDIT_EPS, "the buy price includes the tariff");
        expect(!la.list().contains(cheap), "a bought home leaves the listings");
        expect(Actions.buy(a, cheap).startsWith("That home"), "a home cannot be bought twice");
        Property home = Portfolio.get().properties().get(0);

        // On the player's colony: half price, no tariff.
        Listing own = MarketRules.listings(b).list().get(0);
        before = credits.get();
        float ownPrice = MarketRules.listingValue(b, own) * 0.5f;
        Actions.buy(b, own);
        expect(Math.abs(before - credits.get() - ownPrice) < CREDIT_EPS, "own colony homes cost half with no tariff");
        Property colonyHome = Portfolio.get().properties().get(1);

        // 0.3.0: a new home starts empty and advertised, applicants turn up, screening shows every trait.
        expect(!home.isRented() && home.advertising && home.expectedRent() == 0f, "a new home starts empty and advertised");
        expect(cheap.traits != null && home.traits().equals(cheap.traits), "a home keeps the house traits of its listing");
        expect(cheap.name != null && cheap.name.equals(home.name) && home.displayName().startsWith(cheap.name),
                "a home keeps the name of its listing");
        Random days = new Random(11);
        for (int i = 0; i < 400 && home.applicants().isEmpty(); i++) {
            now += DAY;
            TenantDaily.runDay(days);
        }
        expect(!home.applicants().isEmpty(), "an advertised home gets applicants");
        Tenant applicant = home.applicants().get(0);
        for (Tenant ap : home.applicants()) {
            expect(TenantRules.fits(home.tier().index, home.setting(), ap.wealth), "applicants can pay for the home and would live in it");
        }
        expect(!applicant.screened && applicant.isShown(0) && !applicant.isShown(1), "applicants show one trait until screened");
        expect(applicant.traits().size() >= 2 && applicant.name != null && !applicant.name.isEmpty(), "applicants have a name and traits");
        expect(Actions.screen(home, applicant).startsWith("Screened") && applicant.isShown(1), "screening shows every trait");
        expect(Actions.moveIn(home, applicant).contains(" moved into ") && home.isRented() && home.applicants().isEmpty(),
                "an applicant moves in");
        Tenant poor = Tenants.create("independent", b, 1f, true, "advert", days);
        poor.wealth = 1;
        if (colonyHome.tier().index >= 2) {
            expect(Actions.moveIn(colonyHome, poor).contains("won't move into") && !colonyHome.isRented(),
                    "a tenant who can't pay for a home does not move in");
        }
        Tenant fit = Tenants.create("independent", b, 1f, true, "advert", days);
        fit.wealth = colonyHome.tier().index + 1;
        Actions.moveIn(colonyHome, fit);
        expect(colonyHome.isRented(), "a tenant moves into the colony home");
        // Events: a fixed event changes the home and lands in its log.
        float before0 = home.condition;
        TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("smashed_window"));
        expect(home.condition < before0 && !home.log().isEmpty(), "a bad event damages the home and is logged");
        TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("new_furniture"));
        expect(home.decorBonus > 0f, "a prosperous tenant's new furniture adds decor");
        TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("skipped_rent"));
        expect(home.rentCut == 1f, "a skipped payment takes this month's rent");
        home.condition = 1f;
        home.rentCut = 0f;

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

        PropertyStats greedy = home.stats();
        Actions.setRent(home, RentSetting.CHARITY);
        PropertyStats kind = home.stats();
        Actions.setRent(home, RentSetting.GREEDY);
        expect(kind.happiness > greedy.happiness, "tenants are happier on Charity than on Greedy");
        expect(greedy.comfort >= 0f && greedy.comfort <= 100f && greedy.security >= 0f && greedy.security <= 100f,
                "stats stay between 0 and 100");
        expect(greedy.grade.length() == 1 && greedy.gradeLines.size() >= 4
                && greedy.gradeLines.get(0)[0].startsWith("Market size"), "the location has a grade and its factors");
        expect("Station".equals(greedy.world), "a market with no planet reads as a station");

        // Hostility freezes rent and blocks the sale.
        hostileFactions.add("hegemony");
        month(tick);
        expect(home.lastRent == 0f && home.frozen, "rent is frozen while hostile");
        expect(Actions.cannotSell(home) != null, "no sale while hostile");
        hostileFactions.clear();

        // Run it down until condemned, then rebuild.
        for (int i = 0; i < 80 && !home.condemned; i++) month(tick);
        expect(home.condemned, "an unrepaired greedy home is condemned in time");
        month(tick);
        expect(home.lastRent == 0f, "a condemned home pays nothing");
        // A Dirty home would add the cleaning to the rebuild: take the marks off first.
        home.traits().removeIf(id -> landlordsector.data.HouseTraits.get(id) != null
                && landlordsector.data.HouseTraits.get(id).mark);
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

        // Headhunters: Rook Massey sits on Kanta's Den, hires, searches, delivers. The others' markets are not here.
        Finders.place(days);
        Finders.State rook = Portfolio.get().finders().get("hh_kantas_den");
        expect(rook != null && pir.id.equals(rook.marketId), "the Kanta's Den headhunter sits on Kanta's Den");
        expect(Portfolio.get().finders().size() == 1, "headhunters whose market is missing do not appear");
        expect(pir.directory.size() == 1, "the headhunter is in Kanta's Den's comm directory");
        expect(Finders.hire(rook) == null && rook.hired && pir.directory.isEmpty(), "hiring takes the finder out of the directory");
        expect(Finders.startSearch(rook) == null && rook.searching, "a search starts");
        int poolBefore = Portfolio.get().tenantPool().size();
        now += 11 * DAY;
        Finders.deliver(days);
        expect(Portfolio.get().tenantPool().size() == poolBefore + 6, "the Kanta's Den headhunter brings back 6 tenants");
        int hardy = 0;
        // Six tenants are too few to judge a perk: roll many with Rook's boost and without (0.5.0, more traits).
        Random hr = new Random(9);
        int boosted = 0, plainHardy = 0;
        Map<String, Float> rookBoost = landlordsector.data.FinderDefs.get("hh_kantas_den").traitBoost;
        for (int i = 0; i < 500; i++) {
            if (landlordsector.data.TenantTraits.roll("pirates", java.util.Set.of(), 1.2f, rookBoost, hr).contains("hardy")) boosted++;
            if (landlordsector.data.TenantTraits.roll("pirates", java.util.Set.of(), 1.2f, hr).contains("hardy")) plainHardy++;
        }
        expect(boosted > 3 * plainHardy && boosted > 125, "Rook Massey's tenants are far more often Hardy: " + boosted
                + " of 500, " + plainHardy + " without him");
        boolean allScreened = true;
        for (Tenant t : Portfolio.get().tenantPool()) allScreened &= t.screened && "pirates".equals(t.factionId);
        expect(allScreened, "finder tenants come screened, from the finder's faction");
        Finders.place(days);
        expect(!pir.directory.contains(rook.person), "a hired finder does not come back to the directory");

        // ---- 0.4.0 and 0.5.0: levels, environment, health, furniture, upgrades, events, old saves ----
        // Three years of Greedy rent wore the home down again: fix it up first.
        home.condition = 1f;
        home.condemned = false;
        // The home came from random listings: clear what it rolled, so these numbers are the same on every run.
        home.traits = new java.util.ArrayList<>();
        home.upgrades().clear();
        home.neighbor = landlordsector.data.Environment.NOTHING;
        heg.conditions.add("hot");
        heg.conditions.add("pollution");
        PropertyStats hot = home.stats();
        expect(hot.levels.get("heat") == 1, "a hot world gives Heat 1");
        expect(hot.env.get("air") < 60f, "pollution and station air make a poor Air grade: " + hot.env.get("air"));
        expect(hot.health < landlordsector.data.Levels.baseHealth(), "heat and bad air cost health");
        expect(hot.levelLines.get("heat").get(0)[0].endsWith("Heat +1"), "the heat tooltip names its source");
        expect(!hot.levels.containsKey("light") && !hot.levels.containsKey("noise"), "no Light or Noise levels (0.5.0)");

        // Furniture through the storage (0.5.0).
        landlordsector.data.Upgrades.Def fans = landlordsector.data.Upgrades.get("fans");
        expect(Actions.cannotPlace(home, fans) != null, "no placing furniture you do not have");
        expect(Actions.store("fans").startsWith("Fans is in your furniture storage"), "right-click stores furniture");
        Portfolio.get().addFurniture("fans", 1);
        expect(Actions.place(home, fans).startsWith("Placed") && Portfolio.get().furnitureCount("fans") == 1,
                "placing takes one piece out of the storage");
        expect(home.stats().levels.get("heat") == 0, "fans take Heat 1 down to 0");
        expect(home.stats().health > hot.health, "a cooler home is healthier");
        expect("There is already one in this home.".equals(Actions.cannotPlace(home, fans)),
                "a home holds one of each piece");
        Installed placed = home.upgrades().get(home.upgrades().size() - 1);
        expect(Actions.takeBack(home, placed).startsWith("Took back") && Portfolio.get().furnitureCount("fans") == 2,
                "taking furniture back puts it in the storage");
        expect(home.stats().levels.get("heat") == 1, "without the fans the heat is back");

        // Upgrades: at once, no slots.
        landlordsector.data.Upgrades.Def alarm = landlordsector.data.Upgrades.get("alarm");
        float secBefore = home.stats().security;
        float valueBefore = home.fullValue();
        float c0 = credits.get();
        String built = Actions.build(home, alarm);
        expect(built.startsWith("Built"), "an upgrade is built at once: " + built);
        expect(c0 - credits.get() >= alarm.minCost - CREDIT_EPS, "an upgrade costs credits");
        expect(home.stats().security > secBefore, "the alarm raises security at once");
        expect(TenantDaily.featureMult(home.features(), "burglary") == 0.5f, "the alarm halves burglaries");
        expect(home.fullValue() > valueBefore && home.fullValue() < valueBefore + (c0 - credits.get()),
                "the alarm adds a flat part of its cost to the value");
        landlordsector.data.Upgrades.Def insulation = landlordsector.data.Upgrades.get("insulation");
        expect(Actions.cannotBuild(home, insulation) == null, "no upgrade slots: a second upgrade fits (0.5.0)");
        expect(Actions.remove(home, home.upgrades().get(0)).startsWith("Removed") && !home.has("alarm"),
                "an upgrade can be removed");
        // A one-time job fixes a bad trait at once.
        home.traits().add("damp");
        expect(Actions.build(home, landlordsector.data.Upgrades.get("mold_removal")).startsWith("Built"),
                "mold removal is built in a Musty home");
        expect(!home.traits().contains("damp") && !home.has("mold_removal"), "mold removal removes Musty and leaves");
        // Saves from before 0.4.0: a renovation-trait moves to the upgrades.
        home.traits().add("pool");
        LandlordsectorModPlugin.upgradeTo040(Portfolio.get());
        expect(!home.traits().contains("pool") && home.has("pool") && home.features().contains("pool"),
                "an old Poolside trait becomes a built Pool");
        // Saves from 0.4: a renovation still being built is done, two of the same furniture leave one in storage.
        Installed building = new Installed("soundproofing", false, 15f);
        home.upgrades().add(building);
        home.upgrades().add(new Installed("sofa", true, 0f));
        home.upgrades().add(new Installed("sofa", true, 0f));
        int sofasStored = Portfolio.get().furnitureCount("sofa");
        LandlordsectorModPlugin.upgradeTo050(Portfolio.get(), new Random(3));
        expect(building.ready() && building.days == 0f, "an old renovation still being built is done on load");
        int sofas = 0;
        for (Installed i : home.upgrades()) if (i.furniture && "sofa".equals(i.id)) sofas++;
        expect(sofas == 1 && Portfolio.get().furnitureCount("sofa") == sofasStored + 1,
                "a second sofa in one home goes to the storage");
        expect(home.neighbor != null, "every home has a neighbour after loading");
        for (Installed i : new java.util.ArrayList<>(home.upgrades())) {
            if (i.furniture) Actions.takeBack(home, i);
        }

        // Environment: the building next door (0.5.0).
        heg.industries.put("population", java.util.Set.of("population"));
        heg.industries.put("mining", java.util.Set.of("mining", "industry", "industrial"));
        heg.industries.put("patrolhq", java.util.Set.of("patrol", "military"));
        heg.industries.put("orbitalstation", java.util.Set.of("station"));
        java.util.Set<String> rolled = new java.util.HashSet<>();
        Random nb = new Random(5);
        for (int i = 0; i < 400; i++) rolled.add(Surroundings.roll(a, nb));
        expect(rolled.contains("mining") && rolled.contains("population") && rolled.contains("none")
                && !rolled.contains("orbitalstation"), "homes sit next to buildings or nothing, never a station: " + rolled);
        home.neighbor = "none";
        PropertyStats calm = home.stats();
        home.neighbor = "mining";
        PropertyStats mine = home.stats();
        expect(mine.env.get("noise") < calm.env.get("noise") && mine.env.get("air") < calm.env.get("air"),
                "a home next to Mining is louder and dirtier");
        expect(mine.marketMult < calm.marketMult && home.fullValue() > 0f, "Mining next door lowers the location");
        expect("Next to mining".equals(mine.nextTo), "the sheet names the neighbour: " + mine.nextTo);
        home.neighbor = "population";
        PropertyStats center = home.stats();
        expect(center.env.get("commute") > calm.env.get("commute"), "the city center shortens the commute");
        heg.industries.remove("population");
        expect("Next to nothing".equals(home.stats().nextTo), "a neighbour that is gone counts as nothing");
        home.neighbor = "none";

        // Criminals like security, but hate the police on their colony (0.5.0).
        if (home.tenant != null) {
            Tenant tt = home.tenant;
            java.util.List<String> keep = new java.util.ArrayList<>(tt.traits());
            tt.traits().clear();
            tt.traits().add("shady");
            PropertyStats crook = home.stats();
            boolean hatesPolice = false;
            for (PropertyStats.Note n : crook.notes) hatesPolice |= n.text.contains("hates patrolhq on this colony");
            heg.industries.remove("patrolhq");
            PropertyStats free = home.stats();
            boolean still = false;
            for (PropertyStats.Note n : free.notes) still |= n.text.contains("hates");
            // Happiness can sit at 0 on this Greedy home, so the note is what is checked.
            expect(hatesPolice && crook.happiness <= free.happiness, "a criminal hates the Patrol HQ on the colony");
            expect(!still, "without police on the colony the criminal hates nothing here");
            tt.traits().clear();
            tt.traits().addAll(keep);

            // Tenant events: health steers the health ones, a death empties the home.
            landlordsector.data.TenantEvents.Def died = landlordsector.data.TenantEvents.get("died_ill");
            expect(TenantDaily.tenantWeight(died, 1f, 80f, 50f) == 0f && TenantDaily.tenantWeight(died, 1f, 10f, 50f) > 0f,
                    "only the very sick die of an illness");
            landlordsector.data.TenantEvents.Def hospital = landlordsector.data.TenantEvents.get("hospital");
            expect(TenantDaily.tenantWeight(hospital, 1f, 95f, 50f) < TenantDaily.tenantWeight(hospital, 1f, 30f, 50f),
                    "healthy tenants go to the hospital less");
            tt.traits().remove("couple");
            TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("partner_moves_in"));
            expect(tt.has("couple"), "a partner moving in makes a couple");
            TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("child_born"));
            expect(tt.has("family"), "a baby makes a family");
            tt.traits().clear();
            tt.traits().addAll(keep);
        }
        // House events: graffiti lowers the decor until repairs clean it off.
        float decor0 = home.decor();
        TenantDaily.apply(home, a, landlordsector.data.TenantEvents.get("graffiti"));
        expect(home.traits().contains("graffiti") && home.decor() < decor0, "graffiti lowers the decor");
        home.condition = 1f;
        expect(home.repairCost() > 0f && Actions.repair(home).startsWith("Repaired") && !home.traits().contains("graffiti"),
                "repairs clean off graffiti");

        // Furniture in the shops.
        FurnitureSources.stock(a, days);
        float stocked = 0f;
        for (float v : heg.openStock.values()) stocked += v;
        expect(stocked >= landlordsector.data.Upgrades.shopMin() && stocked <= landlordsector.data.Upgrades.shopMax(),
                "an open market stocks furniture: " + stocked);
        FurnitureSources.stock(a, days);
        float again = 0f;
        for (float v : heg.openStock.values()) again += v;
        expect(again == stocked, "the stock waits for the restock days");
        // Fans back in for the save and the sale.
        Actions.place(home, fans);

        // A death empties the colony home and it advertises again.
        if (colonyHome.tenant != null) {
            TenantDaily.apply(colonyHome, b, landlordsector.data.TenantEvents.get("killed"));
            expect(colonyHome.tenant == null && colonyHome.advertising, "a tenant who dies leaves the home empty");
        }

        // Save round trip.
        com.thoughtworks.xstream.XStream x = new com.thoughtworks.xstream.XStream(new com.thoughtworks.xstream.io.xml.DomDriver());
        com.thoughtworks.xstream.XStream.setupDefaultSecurity(x);
        x.allowTypesByWildcard(new String[] {"landlordsector.**", "java.util.**", "java.lang.**"});
        // The stand-in agents are proxies, which cannot be saved; real ones are game objects.
        Map<String, Agents.Agent> agentsBefore = new HashMap<>(Portfolio.get().agents());
        Map<String, Finders.State> findersBefore = new HashMap<>(Portfolio.get().finders());
        Portfolio.get().agents().clear();
        Portfolio.get().finders().clear();
        String xml = x.toXML(Portfolio.get());
        Portfolio.get().agents().putAll(agentsBefore);
        Portfolio.get().finders().putAll(findersBefore);
        Portfolio back = (Portfolio) x.fromXML(xml);
        expect(back.properties().size() == 2 && back.properties().get(0).setting() == RentSetting.GREEDY,
                "the portfolio survives a save");
        expect((back.properties().get(0).tenant != null) == (home.tenant != null)
                && back.tenantPool().size() == Portfolio.get().tenantPool().size(), "tenants and the pool survive a save");
        expect(back.furnitureCount("fans") == Portfolio.get().furnitureCount("fans")
                && "none".equals(back.properties().get(0).neighbor), "the storage and neighbours survive a save");
        expect(back.properties().get(0).upgrades().size() == home.upgrades().size()
                && back.properties().get(0).has("pool"), "upgrades survive a save");

        // Sell.
        before = credits.get();
        float sale = home.saleValue(false);
        expect(home.saleValue(true) > sale, "a furnished sale pays for the furniture");
        int fansBefore = Portfolio.get().furnitureCount("fans");
        expect(Actions.sell(home).startsWith("Sold"), "selling works");
        expect(Math.abs(credits.get() - before - sale) < CREDIT_EPS, "the sale pays the sale value");
        expect(Math.abs(sale - home.value() * (1f - 0.05f - 0.3f)) < 1f, "sale minus 5 percent fee and tariff");
        expect(Portfolio.get().furnitureCount("fans") == fansBefore + 1, "selling a home puts its furniture in storage");

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
        CargoAPI cargo = specialsCargo(cargoSpecials, Map.of("getCredits", a -> cr));
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
                "getFaction", a -> faction(String.valueOf(a[0])),
                "addTransientScript", a -> sectorScripts.add((com.fs.starfarer.api.EveryFrameScript) a[0])));
        // Made on first use: the item spec stand-in pulls in game classes that need the settings first.
        Object[] itemSpec = new Object[1];
        SettingsAPI settings = mock(SettingsAPI.class, Map.of(
                "getSpecialItemSpec", a -> {
                    if (itemSpec[0] == null) {
                        itemSpec[0] = mock(com.fs.starfarer.api.campaign.SpecialItemSpecAPI.class,
                                Map.of("getBasePrice", b -> 1000f));
                    }
                    return itemSpec[0];
                },
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
                },
                "createRandomPerson", a -> person()));
    }

    private static com.fs.starfarer.api.characters.PersonAPI person() {
        Map<String, Object> mem = new HashMap<>();
        Object[] market = new Object[1];
        com.fs.starfarer.api.campaign.rules.MemoryAPI memory = mock(com.fs.starfarer.api.campaign.rules.MemoryAPI.class,
                Map.of("set", a -> mem.put((String) a[0], a[1]), "getBoolean", a -> Boolean.TRUE.equals(mem.get(a[0]))));
        return mock(com.fs.starfarer.api.characters.PersonAPI.class, Map.of(
                "getMemoryWithoutUpdate", a -> memory,
                "setMarket", a -> market[0] = a[0],
                "getMarket", a -> market[0]));
    }

    private static MarketAPI market(FakeMarket f) {
        FactionAPI fac = faction(f.factionId);
        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("getId", a -> f.id);
        h.put("getName", a -> f.name);
        h.put("getSize", a -> f.size);
        h.put("getStabilityValue", a -> f.stability);
        h.put("getHazardValue", a -> f.hazard);
        h.put("hasCondition", a -> ("habitable".equals(a[0]) && f.habitable) || ("mild_climate".equals(a[0]) && f.mild)
                || f.conditions.contains(a[0]));
        h.put("getConditions", a -> {
            List<com.fs.starfarer.api.campaign.econ.MarketConditionAPI> out = new ArrayList<>();
            for (String c : f.conditions) {
                out.add(mock(com.fs.starfarer.api.campaign.econ.MarketConditionAPI.class, Map.of("getId", b -> c)));
            }
            return out;
        });
        CargoAPI openCargo = specialsCargo(f.openStock, Map.of());
        com.fs.starfarer.api.campaign.econ.SubmarketAPI open = mock(com.fs.starfarer.api.campaign.econ.SubmarketAPI.class,
                Map.of("getCargo", a -> openCargo));
        h.put("getSubmarket", a -> "open_market".equals(a[0]) ? open : null);
        h.put("getIndustries", a -> {
            List<com.fs.starfarer.api.campaign.econ.Industry> out = new ArrayList<>();
            for (Map.Entry<String, java.util.Set<String>> e : f.industries.entrySet()) out.add(industry(e.getKey(), e.getValue()));
            return out;
        });
        h.put("getIndustry", a -> f.industries.containsKey(a[0])
                ? industry((String) a[0], f.industries.get(a[0])) : null);
        h.put("hasIndustry", a -> f.industries.containsKey(a[0]));
        h.put("getFaction", a -> fac);
        h.put("getFactionId", a -> f.factionId);
        h.put("isInEconomy", a -> true);
        h.put("isPlayerOwned", a -> f.own);
        h.put("getTariff", a -> f.tariff);
        h.put("getStability", a -> f.stabilityStat);
        h.put("addTransientImmigrationModifier", a -> f.growth.add((MarketImmigrationModifier) a[0]));
        com.fs.starfarer.api.campaign.CommDirectoryAPI dir = mock(com.fs.starfarer.api.campaign.CommDirectoryAPI.class,
                Map.of("addPerson", a -> {
                    f.directory.add(a[0]);
                    f.agentsArrived++;
                    return "entry";
                }, "removePerson", a -> f.directory.remove(a[0])));
        h.put("getCommDirectory", a -> dir);
        MarketAPI m = mock(MarketAPI.class, h);
        markets.put(f.id, m);
        return m;
    }

    /** A building stand-in (0.5.0): its id, its tags and its name (the id). */
    private static com.fs.starfarer.api.campaign.econ.Industry industry(String id, java.util.Set<String> tags) {
        com.fs.starfarer.api.loading.IndustrySpecAPI spec = mock(com.fs.starfarer.api.loading.IndustrySpecAPI.class,
                Map.of("getTags", a -> tags));
        return mock(com.fs.starfarer.api.campaign.econ.Industry.class, Map.of(
                "getId", a -> id, "getSpec", a -> spec, "getCurrentName", a -> id));
    }

    /** A cargo that holds special items by id (0.4.0 furniture). */
    private static CargoAPI specialsCargo(Map<String, Float> items, Map<String, Function<Object[], Object>> more) {
        Map<String, Function<Object[], Object>> h = new HashMap<>(more);
        h.put("addSpecial", a -> items.merge(((com.fs.starfarer.api.campaign.SpecialItemData) a[0]).getId(),
                (Float) a[1], Float::sum));
        h.put("getQuantity", a -> a[1] instanceof com.fs.starfarer.api.campaign.SpecialItemData
                ? items.getOrDefault(((com.fs.starfarer.api.campaign.SpecialItemData) a[1]).getId(), 0f) : 0f);
        h.put("removeItems", a -> {
            if (a[1] instanceof com.fs.starfarer.api.campaign.SpecialItemData) {
                items.merge(((com.fs.starfarer.api.campaign.SpecialItemData) a[1]).getId(), -(Float) a[2], Float::sum);
            }
            return null;
        });
        return mock(CargoAPI.class, h);
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
