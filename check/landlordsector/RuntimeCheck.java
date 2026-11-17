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
    /**
     * 1.7.0: the game's own clock, cycle 206 counted in milliseconds from 1970, so a negative number. The mod once
     * took timestamps above 0 for "set", and the old stand-in clock (1,000,000) hid that.
     */
    private static long now = new java.util.GregorianCalendar(206, 0, 1).getTimeInMillis();
    private static final long DAY = 86_400_000L;

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
        /** 1.2.2: the colony's administrator. */
        com.fs.starfarer.api.characters.PersonAPI admin;
    }

    /** 0.4.0: special items in the player's cargo, by id. */
    private static final Map<String, Float> cargoSpecials = new HashMap<>();
    /** 1.2.0: the player's officers and administrators. */
    private static final List<com.fs.starfarer.api.characters.PersonAPI> officers = new ArrayList<>();
    private static final List<com.fs.starfarer.api.characters.AdminData> admins = new ArrayList<>();

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
        expect(MarketRules.isEligible(c), "pirate markets sell homes (0.7.2)");
        FakeMarket path = new FakeMarket();
        path.id = "fake_path"; path.name = "Fake Path"; path.factionId = "luddic_path"; path.size = 5;
        expect(!MarketRules.isEligible(market(path)), "Luddic Path markets do not sell homes");

        // Property agents: everyone eligible gets one when the dice always say yes, pirates too (0.7.2), the Path never.
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
        expect(pir.directory.size() == 1, "pirate markets get a property agent (0.7.2)");
        expect(path.directory.isEmpty(), "Luddic Path markets get no property agent");
        Object agentPerson = heg.directory.isEmpty() ? null : heg.directory.get(0);
        now += 24 * 30 * DAY;
        Agents.update(no, false);
        expect(heg.directory.size() == 1 && heg.directory.get(0) == agentPerson, "an agent stays for good (0.3.2)");
        expect(col.directory.size() == 1 && Agents.hasAgent(a) && Agents.hasAgent(b), "every eligible market keeps its agent");

        MarketListings la = MarketRules.listings(a);
        expect(la.list().size() >= 7 && la.list().size() <= 13, "a size 6 market lists 7 to 13 homes (0.8.0), got "
                + la.list().size());
        java.util.Set<String> sizes = new java.util.HashSet<>();
        for (Listing l : la.list()) sizes.add(l.tierId);
        expect(sizes.size() == 6, "a size 6 market lists every home size, estates too (0.8.0): " + sizes);
        for (Listing l : la.list()) {
            Tier t = TierTable.get(l.tierId);
            expect(t != null && t.minSize <= 6, "listing tier fits the market size: " + l.tierId);
            expect(t != null && l.basePrice >= t.min && l.basePrice <= t.max, "base price inside the tier range");
            expect("station".equals(l.climateId), "a market with no planet is a station");
        }
        expect(MarketRules.listings(a) == la, "listings stay the same until the refresh");
        // 0.7.2: a market that grows lists one new home per size it grew, as far as the room allows.
        while (la.list().size() > 2) la.list().remove(la.list().size() - 1);
        heg.size = 8;
        expect(MarketRules.listings(a) == la && la.list().size() == 4 && la.size == 8,
                "a market that grew from 6 to 8 lists 2 more homes, got " + la.list().size());
        heg.size = 6;
        expect(MarketRules.listings(a).list().size() == 4, "a market that shrinks keeps its homes for sale");

        // Buy the cheapest on the Hegemony world: the price includes the 30 percent tariff.
        Listing cheap = la.list().get(0);
        float before = credits.get();
        float expected = MarketRules.listingWorth(a, cheap) * 1.3f;
        String result = Actions.buy(a, cheap);
        expect(result.startsWith("Bought"), "buying works: " + result);
        expect(Math.abs(before - credits.get() - expected) < CREDIT_EPS, "the buy price includes the tariff");
        expect(!la.list().contains(cheap), "a bought home leaves the listings");
        expect(Actions.buy(a, cheap).startsWith("That home"), "a home cannot be bought twice");
        Property home = Portfolio.get().properties().get(0);

        // On the player's colony: half price, no tariff.
        Listing own = MarketRules.listings(b).list().get(0);
        before = credits.get();
        float ownPrice = MarketRules.listingWorth(b, own) * 0.5f;
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
        expect(pir.directory.contains(rook.person) && pir.directory.size() == 2,
                "the headhunter is in Kanta's Den's comm directory, next to its property agent");
        expect(Finders.hire(rook) == null && rook.hired && !pir.directory.contains(rook.person),
                "hiring takes the finder out of the directory");
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
        // 1.2.1 (Andrzej): heated floors stay on a merely hot world (a tenant who loves heat may want them); only a
        // very hot world hides them and takes them out of the value.
        landlordsector.data.Upgrades.Def floors = landlordsector.data.Upgrades.get("heated_floors");
        landlordsector.data.Upgrades.Def ac = landlordsector.data.Upgrades.get("air_conditioned");
        landlordsector.data.Upgrades.Def lead = landlordsector.data.Upgrades.get("lead_lined");
        expect(!PropertyStats.noUse(floors, home.market(), home.features(false)), "heated floors stay on a hot world");
        expect(PropertyStats.noUse(lead, home.market(), home.features(false)), "lead lining is hidden without radiation");
        heg.conditions.remove("hot");
        heg.conditions.add("very_hot");
        expect(PropertyStats.noUse(floors, home.market(), home.features(false)), "heated floors are hidden on a very hot world");
        expect(!PropertyStats.noUse(ac, home.market(), home.features(false)), "air conditioning helps a very hot home");
        expect(!PropertyStats.noUse(insulation, home.market(), home.features(false)),
                "insulation stays: it is quieter too");
        expect(Actions.cannotBuild(home, floors) != null, "heated floors can't be built on a very hot world");
        float withoutFloors = MarketRules.traitPrice(java.util.List.of(), home.market());
        expect(Math.abs(MarketRules.traitPrice(java.util.List.of("heated_floors"), home.market()) - withoutFloors) < 0.0001f,
                "heated floors add nothing to a very hot home's value");
        heg.conditions.remove("very_hot");
        heg.conditions.add("hot");
        java.util.Set<String> loverLikes = new java.util.HashSet<>(landlordsector.data.TenantTraits.get("heat_lover").likes);
        loverLikes.retainAll(floors.effects.tags);
        expect(!loverLikes.isEmpty(), "a tenant who loves heat likes heated floors");
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
        // 1.0.1: never next to a building in orbit, or one that does nothing for a home.
        expect(!landlordsector.data.Environment.pickable("orbitalworks", java.util.Set.of("heavyindustry", "industry")),
                "no home sits next to the Orbital Works");
        expect(!landlordsector.data.Environment.pickable("aotd_terminus", java.util.Set.of("waystation", "structure")),
                "no home sits next to the Terminus");
        expect(!landlordsector.data.Environment.pickable("waystation", java.util.Set.of("waystation", "structure")),
                "no home sits next to a Waystation");
        expect(!landlordsector.data.Environment.pickable("IndEvo_dryDock", java.util.Set.of("structure", "unraidable")),
                "no home sits next to a building with no effect");
        expect(landlordsector.data.Environment.pickable("mining", java.util.Set.of("mining", "industry")),
                "homes still sit next to Mining");
        home.neighbor = "none";
        PropertyStats calm = home.stats();
        home.neighbor = "mining";
        PropertyStats mine = home.stats();
        expect(mine.env.get("noise") < calm.env.get("noise") && mine.env.get("air") < calm.env.get("air"),
                "a home next to Mining is louder and dirtier");
        expect(mine.marketMult < calm.marketMult && home.fullValue() > 0f, "Mining next door lowers the location");
        expect("mining nearby".equalsIgnoreCase(mine.nextTo) && "Mining".equals(mine.area),
                "the sheet names the area: " + mine.area);
        home.neighbor = "population";
        PropertyStats center = home.stats();
        expect(center.env.get("commute") > calm.env.get("commute"), "the city center shortens the commute");
        heg.industries.remove("population");
        expect("Residential".equals(home.stats().area), "a neighbour that is gone counts as a residential area");
        // The AoTD Maglev Central Hub lifts every commute on the colony to S+ (Andrzej, 0.6.0).
        heg.industries.put("aotd_maglev", java.util.Set.of("trainstation", "structure"));
        expect(home.stats().env.get("commute") >= 85f, "a Maglev makes every commute S+");
        heg.industries.remove("aotd_maglev");
        // A station home: its own rolled spot (1.0.1, 0 to 50) plus the station's 10, once the Maglev is gone.
        landlordsector.data.Environment.Score commute = landlordsector.data.Environment.score("commute");
        float spot = landlordsector.data.Environment.start(commute, home.id, null);
        expect(spot >= 0f && spot <= 50f, "a home's spot on the colony is 0 to 50: " + spot);
        expect(Math.abs(home.stats().env.get("commute") - (spot + 10f)) < 0.01f,
                "without the Maglev the station commute is back to its spot plus 10: " + home.stats().env.get("commute"));
        expect(spot == landlordsector.data.Environment.start(commute, home.id, null), "the same home rolls the same spot");
        landlordsector.data.Environment.Neighbor cc = landlordsector.data.Environment.of("population", null);
        expect(landlordsector.data.Environment.start(commute, home.id, cc) == 50f, "next to the city center the spot is 50");
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

        // Services (0.6.0): paid monthly, their effects count while hired, the cleaners take graffiti off.
        landlordsector.data.Services.Def patrol = landlordsector.data.Services.get("security_patrol");
        float secNoPatrol = home.stats().security;
        expect(Actions.toggleService(home, "security_patrol").startsWith("Hired") && home.hasService("security_patrol"),
                "a service can be hired");
        expect(home.stats().security > secNoPatrol, "a security patrol raises security");
        expect(Actions.servicePrice(home, patrol) >= patrol.min, "a service costs at least its minimum");
        float valueWithPatrol = home.fullValue(false);
        Actions.toggleService(home, "security_patrol");
        expect(!home.hasService("security_patrol") && Math.abs(home.fullValue(false) - valueWithPatrol) < 1f,
                "cancelled, and a service never changed the value");
        home.screeningService = true;
        LandlordsectorModPlugin.upgradeTo060(Portfolio.get());
        expect(!home.screeningService && home.services().contains("screening"), "the old screening flag becomes the service");
        Actions.toggleService(home, "screening");
        // 1.0.1: the Night watchman is gone; a home that hired it loses it on load.
        expect(landlordsector.data.Services.get("night_watch") == null, "no Night watchman service");
        home.services().add("night_watch");
        LandlordsectorModPlugin.upgradeTo101(Portfolio.get());
        expect(!home.services().contains("night_watch"), "an old save's Night watchman is cancelled on load");

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

        checkPictureMemory070();
        checkFamily120();
        checkManager161();

        // 0.8.0: a world from worlds.json. Eochu Bres, size 6, has room for 23 homes, lists every size and prices
        // like the Tri-Tachyon capital; the same market under another id is the plain size 6 rule.
        FakeMarket ebf = new FakeMarket();
        ebf.id = "eochu_bres"; ebf.name = "Eochu Bres"; ebf.factionId = "tritachyon"; ebf.size = 6;
        ebf.stability = 7f; ebf.hazard = 1f; ebf.habitable = true;
        MarketAPI eb = market(ebf);
        FakeMarket plainF = new FakeMarket();
        plainF.id = "fake_plain6"; plainF.name = "Plain"; plainF.factionId = "tritachyon"; plainF.size = 6;
        plainF.stability = 7f; plainF.hazard = 1f; plainF.habitable = true;
        MarketAPI plain = market(plainF);
        expect(MarketRules.housingSlots(eb) == 23 && MarketRules.housingSlots(plain) == 13,
                "Eochu Bres has room for 23 homes, a plain size 6 world 13: " + MarketRules.housingSlots(eb));
        expect(Math.abs(MarketRules.mult(eb) / MarketRules.mult(plain) - 1.3f) < 0.01f, "Eochu Bres prices x1.3");
        MarketListings ebl = MarketRules.listings(eb);
        java.util.Set<String> ebSizes = new java.util.HashSet<>();
        for (Listing l : ebl.list()) ebSizes.add(l.tierId);
        expect(ebl.list().size() >= 12 && ebSizes.contains("estate"), "Eochu Bres lists 12+ homes with an estate: "
                + ebl.list().size() + " " + ebSizes);
        boolean named = false;
        for (String[] line : PropertyStats.of(ebl.list().get(0), eb).gradeLines) {
            named |= line[0] != null && line[0].startsWith("Tri-Tachyon capital");
        }
        expect(named, "the location tooltip names the Tri-Tachyon capital");

        if (!problems.isEmpty()) {
            for (String p : problems) System.err.println("FAIL: " + p);
            System.exit(1);
        }
        System.out.println("Landlordsector runtime verified: first month rent " + Math.round(rentA)
                + ", NPC homes " + (npc == null ? 0 : npc.size()) + ", save size " + xml.length() + " chars");
    }

    /**
     * 0.7.0: exterior pictures load when drawn, not at startup; beyond 12 the least recently drawn are unloaded,
     * but never one drawn in the last second (a screen showing many pictures keeps them all).
     */
    private static void checkPictureMemory070() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject();
        org.json.JSONObject shack = new org.json.JSONObject();
        org.json.JSONArray all = new org.json.JSONArray();
        for (int i = 0; i < 15; i++) all.put("graphics/landlordsector/exteriors/test_" + i + ".jpg");
        shack.put("default", all);
        json.put("exteriors", new org.json.JSONObject().put("shack", shack));
        textureLoads.clear();
        textureUnloads.clear();
        landlordsector.Art.load(json);
        expect(textureLoads.isEmpty(), "pictures are not loaded at startup (0.7.0)");
        expect(landlordsector.Art.isKnown("graphics/landlordsector/exteriors/test_3.jpg"),
                "a listed picture is known before it is loaded");
        for (int i = 0; i < 14; i++) landlordsector.Art.use("graphics/landlordsector/exteriors/test_" + i + ".jpg");
        landlordsector.Art.use("graphics/landlordsector/exteriors/test_0.jpg");
        expect(textureLoads.size() == 14, "each picture loads once, found " + textureLoads.size());
        expect(textureUnloads.isEmpty(), "pictures drawn in the last second stay loaded");
        landlordsector.Art.use("graphics/x.png");
        expect(textureLoads.size() == 14, "a path not in art.json is left alone");
        Thread.sleep(1100L);
        landlordsector.Art.use("graphics/landlordsector/exteriors/test_14.jpg");
        expect(textureUnloads.size() == 3 && textureUnloads.contains("graphics/landlordsector/exteriors/test_1.jpg")
                && !textureUnloads.contains("graphics/landlordsector/exteriors/test_0.jpg"),
                "after a second the least recently drawn pictures above 12 are unloaded, found " + textureUnloads);
        checkRarePictures110();
    }

    /** 1.1.0 (Andrzej: "make them rarer"): the third picture shows on about rareChance of new homes, one per list. */
    private static void checkRarePictures110() throws Exception {
        String a = "graphics/landlordsector/exteriors/shack_terran.jpg";
        String b = "graphics/landlordsector/exteriors/shack_terran_2.jpg";
        String c = "graphics/landlordsector/exteriors/shack_terran_3.jpg";
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("exteriors", new org.json.JSONObject().put("shack", new org.json.JSONObject()
                .put("default", new org.json.JSONArray().put("graphics/landlordsector/exteriors/shack.png"))
                .put("terran", new org.json.JSONArray().put(a).put(b))));
        json.put("rare", new org.json.JSONObject().put("shack", new org.json.JSONObject()
                .put("terran", new org.json.JSONArray().put(c))));
        json.put("rareChance", 0.15d);
        landlordsector.Art.load(json);
        expect(landlordsector.Art.isKnown(c), "a rare picture is known like the others");
        java.util.Random random = new java.util.Random(7);
        int rareCount = 0, n = 4000;
        for (int i = 0; i < n; i++) {
            if (c.equals(landlordsector.Art.pick("shack", landlordsector.PlanetType.TERRAN, random))) rareCount++;
        }
        float share = rareCount / (float) n;
        expect(share > 0.11f && share < 0.19f, "the rare picture shows on about 15% of homes, found " + share);
        int alone = 0;
        for (int i = 0; i < 2000; i++) {
            String p = landlordsector.Art.pick("shack", landlordsector.PlanetType.TERRAN, random,
                    java.util.Arrays.asList(c, a));
            if (c.equals(p)) alone++;
        }
        expect(alone == 0, "a list never shows the rare picture twice, found " + alone);
        String other = landlordsector.Art.pick("shack", landlordsector.PlanetType.DESERT, random,
                java.util.Collections.<String>emptyList());
        expect("graphics/landlordsector/exteriors/shack.png".equals(other), "a planet type with no pictures keeps the default");
        landlordsector.Art.load(new org.json.JSONObject());
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
    private static final List<String> textureLoads = new ArrayList<>();
    private static final List<String> textureUnloads = new ArrayList<>();

    private static void installGlobals() {
        FactionAPI player = faction("player");
        CampaignClockAPI clock = mock(CampaignClockAPI.class, Map.of(
                "getTimestamp", a -> now,
                "getElapsedDaysSince", a -> (Long) a[0] == 0L ? Float.MAX_VALUE : (now - (Long) a[0]) / (float) DAY));
        MutableValue cr = credits;
        CargoAPI cargo = specialsCargo(cargoSpecials, Map.of("getCredits", a -> cr));
        com.fs.starfarer.api.campaign.FleetDataAPI fleetData = mock(com.fs.starfarer.api.campaign.FleetDataAPI.class,
                Map.of("getOfficersCopy", a -> {
                    List<com.fs.starfarer.api.characters.OfficerDataAPI> out = new ArrayList<>();
                    for (com.fs.starfarer.api.characters.PersonAPI p : officers) {
                        out.add(mock(com.fs.starfarer.api.characters.OfficerDataAPI.class, Map.of("getPerson", b -> p)));
                    }
                    return out;
                }, "removeOfficer", a -> officers.remove(a[0]), "addOfficer", a -> {
                    if (a[0] instanceof com.fs.starfarer.api.characters.PersonAPI) {
                        officers.add((com.fs.starfarer.api.characters.PersonAPI) a[0]);
                    }
                    return null;
                }));
        CampaignFleetAPI fleet = mock(CampaignFleetAPI.class, Map.of("getCargo", a -> cargo, "getFleetData", a -> fleetData));
        com.fs.starfarer.api.characters.PersonAPI me = familyPerson("Test Pilot",
                com.fs.starfarer.api.characters.FullName.Gender.MALE, 5);
        com.fs.starfarer.api.campaign.CharacterDataAPI chars = mock(com.fs.starfarer.api.campaign.CharacterDataAPI.class,
                Map.of("getAdmins", a -> new ArrayList<>(admins), "removeAdmin", a -> admins.removeIf(d -> d.getPerson() == a[0])));
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
                "addTransientScript", a -> sectorScripts.add((com.fs.starfarer.api.EveryFrameScript) a[0]),
                "getPlayerPerson", a -> me,
                "getCharacterData", a -> chars));
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
                "loadTexture", a -> textureLoads.add((String) a[0]),
                "unloadTexture", a -> textureUnloads.add((String) a[0]),
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

    /** 1.2.0: an officer, administrator or the player: name, gender, level, a relationship and a memory. */
    private static com.fs.starfarer.api.characters.PersonAPI familyPerson(String name,
            com.fs.starfarer.api.characters.FullName.Gender gender, int level) {
        Map<String, Object> mem = new HashMap<>();
        float[] rel = new float[1];
        Object[] market = new Object[1];
        String[] parts = name.split(" ", 2);
        com.fs.starfarer.api.characters.FullName full = new com.fs.starfarer.api.characters.FullName(parts[0],
                parts.length > 1 ? parts[1] : "", gender);
        com.fs.starfarer.api.campaign.rules.MemoryAPI memory = mock(com.fs.starfarer.api.campaign.rules.MemoryAPI.class,
                Map.of("set", a -> mem.put((String) a[0], a[1]), "get", a -> mem.get(a[0]),
                        "getBoolean", a -> Boolean.TRUE.equals(mem.get(a[0])), "unset", a -> mem.remove(a[0]),
                        "contains", a -> mem.containsKey(a[0])));
        com.fs.starfarer.api.characters.RelationshipAPI relApi = mock(com.fs.starfarer.api.characters.RelationshipAPI.class,
                Map.of("getRel", a -> rel[0], "setRel", a -> rel[0] = (Float) a[0]));
        com.fs.starfarer.api.characters.MutableCharacterStatsAPI stats = mock(
                com.fs.starfarer.api.characters.MutableCharacterStatsAPI.class, Map.of("getLevel", a -> level));
        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("getMemoryWithoutUpdate", a -> memory);
        h.put("getRelToPlayer", a -> relApi);
        h.put("getStats", a -> stats);
        h.put("getGender", a -> gender);
        h.put("getName", a -> full);
        h.put("getNameString", a -> name);
        h.put("getId", a -> "Sophia Anderson".equals(name) ? "aotd_sophia" : "person_" + name.replace(' ', '_'));
        h.put("setMarket", a -> market[0] = a[0]);
        h.put("getMarket", a -> market[0]);
        return mock(com.fs.starfarer.api.characters.PersonAPI.class, h);
    }

    /**
     * 1.2.0 family (LLS-5): your own home, moving in with an officer of the opposite sex at +70, no rent, the visit
     * penalty, marriage and the spouse's tariff, a child 30 days after a lucky try, divorce for half the sale with
     * the kids and alimony, alimony ending when the kid grows up, the 2% who are cheating, and a grown worker's pay.
     */
    /** 1.7.0: the tenant manager service waits for a clean applicant, moves them in and advertises. */
    private static void checkManager161() {
        FakeMarket fm0 = new FakeMarket();
        fm0.id = "fake_manager"; fm0.name = "Agency"; fm0.factionId = "hegemony"; fm0.size = 6;
        fm0.stability = 7f; fm0.hazard = 1f; fm0.habitable = true;
        MarketAPI mm = market(fm0);
        Listing l = MarketRules.listings(mm).list().get(0);
        Actions.buy(mm, l);
        Property p = null;
        for (Property q : Portfolio.get().properties()) if ("fake_manager".equals(q.marketId)) p = q;
        expect(p != null, "a home on the manager test market");
        if (p == null) return;
        expect(Actions.toggleService(p, Manager.ID).startsWith("Hired") && Manager.manages(p), "the manager is hired");
        landlordsector.data.Services.Def sd = landlordsector.data.Services.get(Manager.ID);
        expect(sd != null && Math.abs(Actions.servicePrice(p, sd) - 5000f) < 1f, "the manager costs 5,000 a month");
        String badId = null;
        for (landlordsector.data.TenantTraits.Def d : landlordsector.data.TenantTraits.all()) {
            if (Boolean.FALSE.equals(d.good) && d.homeShift == 0 && badId == null) badId = d.id;
        }
        Random r = new Random(4);
        Tenant bad = Tenants.create("independent", mm, 1f, true, "advert", r);
        bad.traits().clear();
        bad.traits().add(badId);
        bad.wealth = p.tier().index + 1;
        bad.since = now;
        p.advertising = false;
        p.applicants().clear();
        p.applicants().add(bad);
        Manager.day(p);
        expect(p.advertising && p.tenant == null && bad.screened, "the manager advertises, checks and waits");
        Tenant good = Tenants.create("independent", mm, 1f, true, "advert", r);
        good.traits().clear();
        good.wealth = p.tier().index + 1;
        good.since = now;
        p.applicants().add(good);
        Manager.day(p);
        expect(p.tenant == good && !p.advertising, "the manager moves in the applicant with no bad traits");
        p.tenant = null;
        p.applicants().clear();
        bad.since = now - 6 * DAY;
        p.applicants().add(bad);
        Manager.day(p);
        expect(p.tenant == bad, "after 5 days the manager takes the best applicant there is");
        Actions.setRent(p, RentSetting.NORMAL);
        float h = p.stats().happiness;
        Manager.day(p);
        RentSetting want = h < Manager.UNHAPPY ? RentSetting.LOW
                : (h >= Manager.HAPPY && TenantRules.canAfford(p.tier().index, RentSetting.HIGH, bad.wealth)
                        ? RentSetting.HIGH : RentSetting.NORMAL);
        expect(p.setting() == want, "the manager sets the rent by happiness " + h + ": " + p.setting());
        Actions.setRent(p, RentSetting.CHARITY);
        Manager.day(p);
        expect(p.setting() == RentSetting.CHARITY, "the manager leaves Charity rent alone");
        Actions.sell(p);
    }

    private static void checkFamily120() {
        Random yes = new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }
        };
        Random half = new Random(5) {
            @Override
            public float nextFloat() {
                return 0.5f;
            }
        };
        FakeMarket ff = new FakeMarket();
        ff.id = "fake_family"; ff.name = "Hearth"; ff.factionId = "hegemony"; ff.size = 6;
        ff.stability = 7f; ff.hazard = 1f; ff.habitable = true;
        MarketAPI fm = market(ff);
        Listing l = MarketRules.listings(fm).list().get(MarketRules.listings(fm).list().size() - 1);
        String bought = Actions.buy(fm, l);
        Property home = null;
        for (Property p : Portfolio.get().properties()) if ("fake_family".equals(p.marketId)) home = p;
        expect(home != null, "a home on the family test market: " + bought);
        if (home == null) return;
        expect(Family.cannotDeclare(home) == null && Family.declareHome(home).startsWith("You moved"),
                "an empty home can be declared the player's home");
        expect(home.playerHome && !home.advertising && home.applicants().isEmpty(), "your home stops advertising");
        expect(Family.cannotDeclare(home) != null, "a home is declared once");

        com.fs.starfarer.api.characters.PersonAPI she = familyPerson("Mira Vance",
                com.fs.starfarer.api.characters.FullName.Gender.FEMALE, 7);
        com.fs.starfarer.api.characters.PersonAPI he = familyPerson("Tom Hale",
                com.fs.starfarer.api.characters.FullName.Gender.MALE, 7);
        com.fs.starfarer.api.characters.PersonAPI shy = familyPerson("Ana Brask",
                com.fs.starfarer.api.characters.FullName.Gender.FEMALE, 2);
        officers.add(she);
        officers.add(he);
        officers.add(shy);
        List<com.fs.starfarer.api.characters.PersonAPI> cands = Family.candidates();
        expect(cands.contains(she) && cands.contains(shy) && !cands.contains(he),
                "only officers of the opposite sex are on the list");
        expect(cands.indexOf(she) < cands.indexOf(shy), "those who would say yes come first");
        // 1.2.2 (Andrzej, 2026-11-16): officers say yes at their highest level only (5 without the game's settings).
        expect(Family.maxLevel(she) == 5 && Family.wouldRefuse(she) == null && Family.wouldRefuse(shy) != null,
                "a level 7 officer says yes, a level 2 officer says no: " + Family.wouldRefuse(shy));
        expect("Officer, level 2 of 5".equals(Family.role(shy)), "the list shows the level: " + Family.role(shy));
        expect(Family.cannotPropose(home, shy) != null, "you can't propose to a level 2 officer");

        // Administrators say yes after governing one of your colonies for a cycle.
        com.fs.starfarer.api.characters.PersonAPI gov = familyPerson("Lena Orr",
                com.fs.starfarer.api.characters.FullName.Gender.FEMALE, 1);
        admins.add(new com.fs.starfarer.api.characters.AdminData(gov));
        FakeMarket govF = new FakeMarket();
        govF.id = "fake_governed"; govF.name = "Orrstead"; govF.factionId = "player"; govF.size = 4;
        govF.stability = 5f; govF.hazard = 1.5f; govF.own = true; govF.admin = gov;
        market(govF);
        expect(Family.wouldRefuse(gov) != null && Family.role(gov).equals("Administrator, governed 0 of 12 months"),
                "a new administrator says no: " + Family.role(gov));
        for (int i = 0; i < 364; i++) Family.countGovernance();
        expect(Family.wouldRefuse(gov) != null, "364 days of governing are not enough");
        Family.countGovernance();
        expect(Family.wouldRefuse(gov) == null && Family.role(gov).equals("Administrator, governed 12 of 12 months"),
                "an administrator says yes after 365 days of governing: " + Family.role(gov));

        String said = Family.propose(home, she, new Random(3) {
            @Override
            public float nextFloat() {
                return 0.5f;
            }
        });
        Tenant wife = home.partner();
        expect(said.contains(" is now your ") && wife != null && wife.person == she && wife.isSpouse(),
                "a level 7 officer says yes and is your wife at once: " + said);
        expect(!officers.contains(she) && ff.directory.contains(she), "your wife leaves the fleet for the comm directory");
        if (wife == null) return;
        for (String t : wife.traits()) expect(!Family.PARTNER_SKIP_TRAITS.contains(t), "no " + t + " partner");
        expect(!home.isRented() && home.expectedRent() == 0f, "your wife pays no rent");
        MonthlyTick tick = new MonthlyTick();
        month(tick);
        expect(home.lastRent == 0f, "no rent from your own home at the month's end");

        Family.visit(fm);
        float fresh = home.stats().happiness;
        now += 90 * DAY;
        PropertyStats away = home.stats();
        boolean noted = false;
        for (PropertyStats.Note n : away.notes) noted |= n.text.startsWith("Last visited: 90 days ago (-30)");
        expect(noted, "the happiness tooltip says Last visited: 90 days ago (-30)");
        expect(away.happiness < fresh || fresh <= 0f, "90 days away make your partner unhappier");
        Family.visit(fm);
        expect(Family.daysSince(wife.lastVisit) == 0f && wife.lastGap >= 89f, "docking there is a visit");

        expect(Family.spouseMarkets().contains("fake_family"), "no tariff where your wife lives");
        for (int i = 0; i < 300; i++) {
            landlordsector.data.TenantEvents.Def e = TenantDaily.pickTenantEvent(home, new Random(i));
            expect(e == null || !Family.PARTNER_SKIP_EVENTS.contains(e.id), "no rent events for your wife: "
                    + (e == null ? "" : e.id));
        }

        expect(Family.tryForChild(home, yes) == 2 && wife.conceivedAt != 0L && now < 0L, "a lucky try starts a child");
        expect(Family.tryForChild(home, yes) == 0, "one try a day");
        now += 21 * DAY;
        Family.daily(half);
        expect(wife.toldPregnant && home.children().isEmpty(), "pregnant after 20 days, not born yet");
        now += 10 * DAY;
        Family.daily(half);
        expect(home.children().size() == 1 && wife.conceivedAt == 0L, "born 30 days after the try");
        Child kid = home.children().isEmpty() ? null : home.children().get(0);
        expect(kid != null && kid.name.equals("Test Pilot Junior"), "the first child is the player's name and Junior: "
                + (kid == null ? null : kid.name));
        if (kid == null) return;
        for (String t : kid.traits()) expect(!Family.CHILD_SKIP_TRAITS.contains(t), "no " + t + " child");
        Family.daily(half);
        expect(kid.happyDays >= 1 && wife.kidsAtHome == 1 && wife.hasKids(), "a child's days are counted");
        // 1.2.2 (Andrzej: "the more you have, the higher the happiness and comfort requirements").
        float[] second = Family.childNeeds(1), seventh = Family.childNeeds(6);
        expect(Family.childNeeds(0) == null && second[0] == 50f && second[1] == 40f && seventh[0] == 95f
                && seventh[1] == 90f, "another child needs more happiness and comfort with each kid");
        PropertyStats now2 = home.stats();
        boolean enough = now2.happiness >= 50f && now2.comfort >= 40f;
        expect((Family.cannotTry(home) == null) == enough, "the second child's needs follow the home: "
                + Family.cannotTry(home));
        if (!enough) expect(Family.tryForChild(home, yes) == 3, "no try for a second child without the needs");
        expect(Actions.cannotSell(home) != null, "you can't sell the home your family lives in");

        float sale = home.saleValue(true);
        float c0 = credits.get();
        String split = Family.breakUp(home);
        expect(!Portfolio.get().properties().contains(home), "a divorce sells the house: " + split);
        expect(Math.abs(credits.get() - c0 - sale * 0.5f) < CREDIT_EPS, "a divorce pays you half the sale");
        expect(Portfolio.get().exKids().contains(kid) && !ff.directory.contains(she),
                "your ex takes the kids and leaves the comm directory");
        month(tick);
        expect(Portfolio.get().lastAlimony == Settings.DEFAULT_ALIMONY, "alimony for the child: "
                + Portfolio.get().lastAlimony);
        now += (long) (Family.growUpDays() * DAY) + DAY;
        month(tick);
        expect(Portfolio.get().exKids().isEmpty() && Portfolio.get().lastAlimony == 0f, "no alimony once the kid grows up");

        Listing l2 = MarketRules.listings(fm).list().get(0);
        Actions.buy(fm, l2);
        Property home2 = null;
        for (Property p : Portfolio.get().properties()) if ("fake_family".equals(p.marketId)) home2 = p;
        if (home2 == null) {
            expect(false, "a second home on the family test market");
            return;
        }
        Family.declareHome(home2);
        Random cheat = new Random() {
            @Override
            public float nextFloat() {
                return 0.01f;
            }
        };
        String no = Family.propose(home2, gov, cheat);
        expect(no.contains("cheating on you") && home2.partner() == null && Family.isAdmin(gov)
                && "Seeing someone else.".equals(Family.wouldRefuse(gov)),
                "2% are cheating on you, stay in your service and say no for good: " + no);
        // 1.7.0: AoTD stores Sophia as a man; she is still a wife.
        com.fs.starfarer.api.characters.PersonAPI sophia = familyPerson("Sophia Anderson",
                com.fs.starfarer.api.characters.FullName.Gender.MALE, 1);
        String yes2 = Family.propose(home2, sophia, cheat);
        expect(Family.isSophia(sophia) && yes2.contains(" is now your ") && home2.partner() != null
                && home2.partner().person == sophia, "Sophia always says yes: " + yes2);
        expect("wife".equals(Family.noun(home2.partner())) && "she".equals(Family.he(home2.partner())),
                "Sophia is a wife: " + Family.noun(home2.partner()));
        for (int i = 0; i < 300; i++) {
            landlordsector.data.TenantEvents.Def e = TenantDaily.pickTenantEvent(home2, new Random(i));
            expect(e == null || !e.dies, "tenant events never kill Sophia: " + (e == null ? "" : e.id));
        }

        Child miner = new Child();
        miner.id = "kid_test";
        miner.name = "Test Miner";
        miner.path = Child.WORKER;
        miner.grown = true;
        miner.happySum = 75f;
        miner.happyDays = 1;
        home2.children().add(miner);
        expect(Family.startLife(home2, miner, half) && Portfolio.get().adultKids().contains(miner)
                && miner.pay == Family.WORKER_PAY * 2.5f && home2.children().isEmpty(),
                "a grown miner sends pay home: " + miner.pay);
        expect(home2.id.equals(miner.homeId), "a miner sends money to the home they grew up in");
        float fundBefore = home2.homeFund;
        int upgradesBefore = home2.upgrades().size();
        month(tick);
        expect(Portfolio.get().lastKidPay == miner.pay, "the miner's pay arrives at the month's end");
        expect(home2.homeFund > fundBefore || home2.upgrades().size() > upgradesBefore,
                "1.3.0: the pay goes to the home (its fund or what it bought): fund " + home2.homeFund);
        // 1.3.0 Kite drive: 2% plus 5% a D-mod; no Kite in the headless fleet, so the drive is always safe here.
        expect(Math.abs(Family.crashChance(0) - 0.02f) < 1e-6f && Math.abs(Family.crashChance(3) - 0.17f) < 1e-6f,
                "a Kite crashes 2% of the time, plus 5% per D-mod");
        expect(Family.kiteDrive(home2, miner, new Random(1)) == null, "no Kite, no crash");
        // 1.3.0: "$HeIs" in event text.
        landlordsector.Tenant woman = new landlordsector.Tenant();
        woman.gender = "F";
        landlordsector.Tenant old = new landlordsector.Tenant();
        expect("She's".equals(TenantDaily.heIs(woman)) && "They're".equals(TenantDaily.heIs(old)),
                "event text says She's, He's or They're");
        expect(Family.visitPenalty(10f) == 0f && Family.visitPenalty(130f) == Family.VISIT_PENALTY_MAX,
                "visits: free for 30 days, at most -50");
        checkKids180(home2, tick, half);
    }

    /** 1.8.0 (LLS-28, LLS-29, LLS-5 changes): kid stages, training, careers, events, nicknames, the bedroom. */
    private static void checkKids180(Property home, MonthlyTick tick, Random half) {
        Tenant wife = home.partner();
        if (wife == null) {
            expect(false, "1.8.0: a wife for the kid checks");
            return;
        }
        // LLS-5: "Your wife, Name," in her events.
        String line = TenantDaily.tenantWords("$tenant fell sick.", wife);
        expect(line.startsWith("Your wife, " + wife.name + ", fell sick."), "a wife's event starts with Your wife: "
                + line);
        expect(TenantDaily.tenantWords("Police raided the home looking for $tenant.", wife)
                .endsWith("looking for your wife " + wife.name + "."), "inside a line: your wife Name");
        Tenant renter = new Tenant();
        renter.name = "Olivia Denkel";
        expect(TenantDaily.tenantWords("$tenant fell sick.", renter).equals("Olivia Denkel fell sick."),
                "a tenant's event keeps the name");

        // LLS-29 nicknames.
        renter.setNickname("Pirate tenant");
        expect("Pirate tenant".equals(renter.name) && "Olivia Denkel".equals(renter.realName)
                && "Pirate tenant".equals(renter.firstName()), "a nickname shows instead of the name");
        renter.setNickname("  ");
        expect("Olivia Denkel".equals(renter.name) && !renter.hasNickname(), "an empty nickname brings the name back");

        // LLS-5: the Romantic bedroom, your own home only, +25% a try.
        landlordsector.data.Upgrades.Def bed = landlordsector.data.Upgrades.get("romantic_bedroom");
        expect(bed != null && bed.homeOnly && bed.childChance > 0f, "the Romantic bedroom is in upgrades.json");
        if (bed != null) {
            Property rented = null;
            for (Property p : Portfolio.get().properties()) if (!p.playerHome) rented = p;
            expect(rented == null || !landlordsector.data.Upgrades.fits(bed, rented), "no Romantic bedroom in a rental");
            expect(landlordsector.data.Upgrades.fits(bed, home), "a Romantic bedroom fits your home");
            float before = Family.childChance(home);
            home.upgrades().add(landlordsector.Installed.done(bed.id));
            expect(Math.abs(before - 0.25f) < 1e-4f && Math.abs(Family.childChance(home) - 0.5f) < 1e-4f,
                    "a try's chance goes from 25% to 50% with the bedroom: " + Family.childChance(home));
        }

        // A baby: no traits, the Baby stage, no career talk yet.
        int kidsBefore = home.children().size();
        Family.birth(home, wife, new Random(11));
        expect(home.children().size() == kidsBefore + 1, "a baby is born");
        Child kid = home.children().get(home.children().size() - 1);
        expect(kid.traits().isEmpty() && Kids.stageOf(kid) == Kids.BABY && kid.stage == Kids.BABY,
                "a baby has no traits (Andrzej: only at the Child stage): " + kid.traits());
        expect(Kids.upkeep(kid) == Kids.UPKEEP_BABY, "a baby costs " + Kids.UPKEEP_BABY + " a month");
        List<Object[]> load = Kids.burden(wife);
        expect(!load.isEmpty() && ((Float) load.get(load.size() - 1)[1]) == Kids.BURDEN_BABY,
                "a baby wears your wife out");
        boolean listed = false;
        for (PropertyStats.Note n : home.stats().notes) listed |= n.text.startsWith("Your baby, ");
        expect(listed, "the happiness tooltip names the baby");
        expect(Kids.cannotLesson(kid) != null && Kids.cannotBond(kid) != null, "no lessons or days out for a baby");
        month(tick);
        expect(Portfolio.get().lastKidCost >= Kids.UPKEEP_BABY, "raising the baby shows at the month's end: "
                + Portfolio.get().lastKidCost);

        // Old saves: a baby born with traits loses them.
        Child old = new Child();
        old.id = "kid_old";
        old.name = "Old Save Junior";
        old.bornAt = now;
        old.traits().add("tidy");
        old.traits().add("athletic");
        Kids.migrate(home, old, half);
        expect(old.traits().isEmpty() && old.stage == Kids.BABY, "1.8.0 load: a baby's traits are cleared");

        // Child stage: traits from the home and the family, then the wish.
        long stage = (long) (Kids.stageDays() * DAY);
        now += stage + DAY;
        Family.daily(half);
        expect(Kids.stageOf(kid) == Kids.TODDLER && kid.stage == Kids.TODDLER && kid.traits().isEmpty(),
                "a toddler after one stage, still no traits");
        now += stage;
        Family.daily(half);
        expect(kid.stage == Kids.CHILD && !kid.traits().isEmpty(), "a child gets traits: " + kid.traits());
        for (String t : kid.traits()) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(t);
            expect(!Family.CHILD_SKIP_TRAITS.contains(t) && d != null && !d.teenOnly, "no " + t + " child");
        }
        expect(!kid.pathLocked && kid.path == null, "a child's wish is still open");
        kid.path = Child.WORKER;

        // Teen: the wish is locked, lessons and days together.
        now += stage;
        Family.daily(half);
        expect(kid.stage == Kids.TEEN && kid.pathLocked && Child.WORKER.equals(kid.path),
                "a teen keeps the wish for good");
        float c0 = credits.get();
        expect(Kids.cannotLesson(kid) == null, "a teen can take lessons: " + Kids.cannotLesson(kid));
        Kids.lesson(home, kid);
        expect(kid.lessons == 1 && Math.abs(c0 - credits.get() - Kids.LESSON_COST) < CREDIT_EPS,
                "lessons cost " + Kids.LESSON_COST);
        expect(Kids.cannotLesson(kid) != null, "the next lessons wait a month");
        now += (long) (Kids.LESSON_DAYS * DAY);
        expect(Kids.cannotLesson(kid) == null, "new lessons after a month");
        expect(Kids.cannotBond(kid) == null, "a day together with a teen");
        Kids.bond(home, kid);
        expect(kid.bonds == 1 && Kids.cannotBond(kid) != null, "one day together a week");
        load = Kids.burden(wife);
        expect(Math.abs(Math.abs((Float) load.get(load.size() - 1)[1]) - Kids.TEEN_SWING) < 1e-4f,
                "a teen helps or burdens your wife by " + Kids.TEEN_SWING);
        expect(Kids.upkeep(kid) == Kids.UPKEEP_TEEN, "a teen costs " + Kids.UPKEEP_TEEN);

        // Adult: no cost, the Academy needs one, classes.
        now += stage;
        Family.daily(half);
        expect(kid.stage == Kids.ADULT && Kids.upkeep(kid) == 0f, "an adult costs nothing");
        expect(Kids.cannotAcademy(kid) != null, "a future miner doesn't go to the Academy");
        expect(Kids.cannotClasses(kid) == null, "an adult can take extra classes");
        Kids.takeClasses(home, kid);
        expect(kid.classes, "extra classes taken");

        // Grown: a miner's pay follows the childhood, what they learned and the bad marks.
        int learned = Kids.training(kid), bad = Kids.badMarks(kid), q = kid.quality();
        float expected = Math.round(Family.WORKER_PAY * (1f + q * 0.5f) * (1f + learned * 0.25f)
                * (float) Math.pow(0.8, bad));
        now += stage;
        Family.daily(half);
        expect(!home.children().contains(kid) && Portfolio.get().adultKids().contains(kid) && kid.pay == expected,
                "a grown miner's pay: " + kid.pay + " (learned " + learned + ", bad " + bad + ")");

        // Skills: bad traits and a hard childhood.
        Child rough = new Child();
        rough.traits().add("adhd");
        rough.traits().add("asthma");
        rough.happySum = 10f;
        rough.happyDays = 1;
        List<String> off = Kids.badSkills(rough, true), adm = Kids.badSkills(rough, false);
        expect(off.contains("lls_adhd") && off.contains("lls_weak_lungs") && off.contains("lls_timid")
                && off.contains("lls_hothead"), "a rough childhood: officer skills " + off);
        expect(adm.contains("lls_slacker") && adm.contains("lls_wasteful") && adm.contains("lls_unpopular"),
                "a rough childhood: administrator skills " + adm);
        rough.traits().add("book_reader");
        rough.lessons = 2;
        rough.bonds = 9;
        expect(Kids.training(rough) == 1 + 2 + 2, "training: book reader, 2 lessons, 9 days (2 points)");

        // Events: only for the kid's stage, deaths only where they can happen.
        Child baby = new Child();
        baby.id = "kid_events";
        baby.bornAt = now;
        baby.happiness = 50f;
        int deaths = 0;
        for (int i = 0; i < 500; i++) {
            Kids.Event e = Kids.pick(home, baby, new Random(i));
            expect(e != null && e.minStage <= Kids.BABY && Kids.BABY <= e.maxStage, "a baby's event fits a baby: "
                    + (e == null ? null : e.id));
            if (e != null && e.dies) deaths++;
        }
        expect(deaths < 25, "deadly events are rare: " + deaths + " of 500");
        Child doomed = new Child();
        doomed.id = "kid_doomed";
        doomed.name = "Doomed Junior";
        doomed.bornAt = now;
        home.children().add(doomed);
        Kids.Event fever = null;
        for (Kids.Event e : Kids.events()) if ("fever".equals(e.id)) fever = e;
        expect(fever != null && fever.dies, "kid_events.json has the fever");
        if (fever != null) {
            Kids.apply(home, doomed, fever);
            expect(!home.children().contains(doomed), "a deadly event takes the kid");
        }
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
        h.put("getAdmin", a -> f.admin);
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
