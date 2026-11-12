package landlordsector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/** Checks the shipped tier table and the price and rent rules without the game. Run by Gradle check. */
public final class RulesCheck {

    private static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        loadAll(args[0]);

        List<Tier> tiers = TierTable.all();
        expect(tiers.size() == 6, "six tiers, found " + tiers.size());
        expect(tiers.get(0).minSize <= MarketRules.MIN_SIZE, "the cheapest tier must be listed on size 3 markets");
        for (int i = 0; i < tiers.size(); i++) {
            Tier t = tiers.get(i);
            expect(t.min > 0 && t.min < t.max, t.id + ": min must be above 0 and below max");
            expect(t.rentShare > 0 && t.rentShare < 0.2f, t.id + ": rentShare out of range");
            expect(t.slots > 0 && t.weight > 0, t.id + ": slots and weight must be positive");
            if (i > 0) {
                Tier prev = tiers.get(i - 1);
                expect(t.min > prev.max * 0.99f || t.min >= prev.min, t.id + ": tiers must get dearer");
                expect(t.rentShare < prev.rentShare, t.id + ": cheaper tiers must yield more (Andrzej, LLS-1)");
                expect(t.minSize >= prev.minSize, t.id + ": minSize must not fall");
            }
        }
        Tier estate = TierTable.get("estate");
        expect(estate != null, "an estate tier");

        // Andrzej's scale (LLS-2, 2026-11-12).
        float prime = Pricing.marketMult(9, 10f, 0.25f, true, true, false, false, false);
        float worst = Pricing.marketMult(3, 0f, 3f, false, false, true, true, true);
        float typical = Pricing.marketMult(5, 5f, 1f, false, false, false, false, false);
        float earth = Pricing.marketMult(6, 7f, 0.5f, true, true, false, false, false);
        float lunamun = Pricing.marketMult(5, 10f, 1.65f, false, false, false, false, false);
        float magma = Pricing.marketMult(5, 4f, 2.75f, false, false, false, true, false);
        expect(Math.abs(prime - 4f) < 1e-4, "the best possible world reaches 4x, got " + prime);
        expect(Math.abs(worst - 0.25f) < 1e-4, "the worst world drops to 0.25x, got " + worst);
        expect(Math.abs(typical - 0.95f) < 0.06f, "a standard world sits near 1x, got " + typical);
        expect("S".equals(Pricing.grade(earth)), "an Earth-like world is grade S (Andrzej), got " + Pricing.grade(earth));
        String lg = Pricing.grade(lunamun);
        expect("B".equals(lg) || "C".equals(lg), "Lunamun is grade B or C (Andrzej), got " + lg);
        expect("F".equals(Pricing.grade(magma)), "a 275 percent magma world with pirates is F, got "
                + Pricing.grade(magma));
        String tg = Pricing.grade(typical);
        expect("C".equals(tg), "a standard world is grade C, got " + tg);
        expect("S+".equals(Pricing.grade(prime)), "the best possible world is S+, got " + Pricing.grade(prime));
        expect(Math.abs(Pricing.planetMult(1f, false, false, false) - 1f) < 1e-6, "hazard 100 percent is standard");
        expect(Math.abs(Pricing.planetMult(0.5f, false, false, false) - 1.5f) < 1e-6, "hazard 50 percent is 1.5");
        expect(Pricing.planetMult(1.65f, false, false, false) > 0.8f, "hazard 165 percent is still fine");
        expect(Math.abs(Pricing.sizeMult(5) - 1f) < 0.06f && Math.abs(Pricing.sizeMult(6) - 1f) < 0.06f,
                "size 5 and 6 are standard");
        expect(Pricing.sizeMult(8) > Pricing.sizeMult(7) && Pricing.sizeMult(7) > Pricing.sizeMult(6) + 0.2f,
                "size 7 is big, 8 bigger");
        expect(Pricing.marketMult(6, 6f, 1f, true, true, false, false, false)
                > Pricing.marketMult(6, 6f, 1f, false, false, true, false, false),
                "mild, habitable, clean worlds cost more than polluted ones");
        expect(Pricing.dangerMult(true, false) < 1f && Pricing.dangerMult(false, true) < 1f, "pirates and Pathers cost");
        expect(Pricing.stabilityMult(9) > Pricing.stabilityMult(2), "stable markets cost more");

        // Rent: a 4 million estate at Normal and full condition pays 1 percent a month.
        float estateRent = Pricing.rent(4_000_000f, estate.rentShare, 1f, 1f, 1f);
        expect(Math.abs(estateRent - 40_000f) < 1f, "a 4 million estate rents for 40k, got " + estateRent);
        expect(Pricing.rent(100f, 0.03f, 1f, 0f, 1f) == 1.5f, "a worn-out home pays half rent");
        expect(Pricing.rent(100f, 0.03f, 1f, 1f, 2f) == 6f, "the LunaLib multiplier scales rent");

        // Value and repairs.
        Economy eco = TierTable.economy();
        expect(Pricing.value(1000f, 0f, true, eco.landValue) == 200f, "a condemned home keeps its land value");
        expect(Pricing.value(1000f, 1f, false, eco.landValue) == 1000f, "a new home is worth its full value");
        expect(Math.abs(Pricing.repairCost(1000f, 0f, eco.repairFullCost) - 100f) < 1e-3,
                "a full rebuild costs 10 percent of the value");
        expect(Pricing.repairCost(1000f, 1f, eco.repairFullCost) == 0f, "a new home needs no repairs");
        expect(Math.abs(eco.wearMonths - 24f) < 1e-3, "about 2 cycles from new to condemned (Andrzej, LLS-1)");

        // Rent settings run from cheap and kind to greedy and hard on the home.
        RentSetting[] s = RentSetting.values();
        for (int i = 1; i < s.length; i++) {
            expect(s[i].rentMult > s[i - 1].rentMult, s[i] + ": rent must rise");
            expect(s[i].wearMult >= s[i - 1].wearMult, s[i] + ": wear must not fall");
            expect(s[i].repPerMonth < s[i - 1].repPerMonth, s[i] + ": reputation must fall");
        }
        expect(RentSetting.CHARITY.lower() == RentSetting.CHARITY && RentSetting.GREEDY.higher() == RentSetting.GREEDY,
                "rent settings stop at both ends");

        // Property stats.
        for (int i = 1; i < tiers.size(); i++) {
            expect(tiers.get(i).comfort > tiers.get(i - 1).comfort, tiers.get(i).id + ": comfort must rise with the tier");
            expect(tiers.get(i).decor >= tiers.get(i - 1).decor, tiers.get(i).id + ": decor must not fall");
        }
        float mildHome = Pricing.comfort(60f, Climate.TEMPERATE, true, true, false, 0.5f, 1f);
        float toxicHome = Pricing.comfort(60f, Climate.HOSTILE, false, false, true, 2f, 1f);
        expect(mildHome > toxicHome + 30f, "a mild habitable world is far more comfortable than a toxic rock");
        expect(Pricing.comfort(60f, Climate.TEMPERATE, false, false, false, 1f, 0f) < 60f, "a ruin is less comfortable");
        float happyKind = Pricing.happiness(50f, 10f, RentSetting.CHARITY.happiness, 0.5f);
        float happyGreedy = Pricing.happiness(50f, 10f, RentSetting.GREEDY.happiness, 0.5f);
        expect(Math.abs(happyKind - happyGreedy - 60f) < 1e-3, "Charity to Greedy moves happiness by 60");
        expect(Pricing.wearMult(100f) == 0.5f && Pricing.wearMult(0f) == 1.5f, "wear runs from half to 1.5 times");
        expect(Math.abs(Pricing.tipChance(0.01f, 50f) - 0.01f) < 1e-6, "average tenants tip at the base chance");
        expect(Pricing.security(10f, 30f, 15f, false, false) == 100f && Pricing.security(0f, 0f, 0f, false, false) == 0f,
                "security range");
        expect(Pricing.security(5f, 0f, 0f, true, true) == 5f, "pirates and Pathers cut security");
        expect(Pricing.damageMult(100f) > 0.3f && Pricing.damageMult(0f) == 1f, "security cuts damage, never to zero");
        expect("S+".equals(Pricing.grade(4f)) && "S".equals(Pricing.grade(2f)) && "A".equals(Pricing.grade(1.6f))
                && "B".equals(Pricing.grade(1.1f)) && "C".equals(Pricing.grade(1f)) && "D".equals(Pricing.grade(0.6f))
                && "E".equals(Pricing.grade(0.45f)) && "F".equals(Pricing.grade(0.25f)), "location grades S+ to F");
        expect(Math.abs(Pricing.decorValueMult(100f) - 1.25f) < 1e-6, "decor adds up to a quarter to the value");
        for (int i = 1; i < s.length; i++) {
            expect(s[i].happiness < s[i - 1].happiness, s[i] + ": happiness must fall as rent rises");
        }

        // Agents: about a quarter of size 5 markets, more for big ones, and the arrival rate keeps that share.
        expect(Math.abs(Pricing.agentShare(eco.agentShare, 5) - 0.25f) < 1e-6, "a quarter of size 5 markets");
        expect(Pricing.agentShare(eco.agentShare, 8) > Pricing.agentShare(eco.agentShare, 4), "big markets more often");
        float share = 0.25f;
        float stay = (eco.agentMinMonths + eco.agentMaxMonths) / 2f;
        float arrive = Pricing.agentArrival(share, stay);
        float steady = arrive * stay / (1f + arrive * stay);
        expect(Math.abs(steady - share) < 0.01f, "arrivals keep the share steady, got " + steady);

        // ---- 0.3.0 data files ----
        expect(landlordsector.data.ConditionTable.size() > 100, "the condition table covers the installed mods");
        expect(landlordsector.data.ConditionTable.get("US_shrooms").happiness > 0f, "psychoactive fungus cheers tenants");
        expect(landlordsector.data.ConditionTable.get("rogue_ai_core").price < 1f, "a rogue AI core lowers prices");
        for (String id : new String[] {"US_shrooms", "rat_rampant_military_core", "yunru_gaia", "ix_killsats"}) {
            expect(landlordsector.data.ConditionTable.get(id) != null, "condition table has " + id);
        }
        java.util.Set<String> houseTags = new java.util.HashSet<>();
        for (landlordsector.data.HouseTraits.Def d : landlordsector.data.HouseTraits.all()) {
            houseTags.addAll(d.tags);
            expect(d.weight > 0f && d.price > 0.5f && d.price < 1.5f && d.wear > 0.5f && d.wear < 1.5f,
                    "house trait " + d.id + " has sane numbers");
            for (String c : d.conflicts) expect(landlordsector.data.HouseTraits.get(c) != null, d.id + " conflicts with a real trait");
        }
        expect(landlordsector.data.HouseTraits.get("flowers") != null && landlordsector.data.HouseTraits.get("lava_lake") != null,
                "Andrzej's examples: field of flowers and lava lake");
        expect(landlordsector.data.HouseTraits.get("flowers").good && !landlordsector.data.HouseTraits.get("lava_lake").good,
                "flowers are good, lava is bad");
        java.util.Set<String> eventIds = new java.util.HashSet<>();
        for (landlordsector.data.TenantEvents.Def e : landlordsector.data.TenantEvents.all()) {
            eventIds.add(e.id);
            expect(!e.text.isEmpty() && e.text.indexOf('%') < 0, "event " + e.id + " has plain text");
        }
        for (landlordsector.data.TenantTraits.Def d : landlordsector.data.TenantTraits.all()) {
            for (String t : d.likes) expect(houseTags.contains(t), d.id + " likes " + t + ", which no house trait has");
            for (String t : d.dislikes) expect(houseTags.contains(t), d.id + " dislikes " + t + ", which no house trait has");
            for (String ev : d.events.keySet()) expect(eventIds.contains(ev), d.id + " names event " + ev + ", which does not exist");
        }
        expect(landlordsector.data.TenantTraits.get("vandal") != null && landlordsector.data.TenantTraits.get("filthy") != null
                && landlordsector.data.TenantTraits.get("prosperous") != null && landlordsector.data.TenantTraits.get("pollen_allergy") != null,
                "Andrzej's examples: vandal, filthy, prosperous, pollen allergy");
        // Pirates find vandals, Tri-Tachyon finds the prosperous.
        java.util.Set<String> none = java.util.Collections.emptySet();
        landlordsector.data.TenantTraits.Def vandal = landlordsector.data.TenantTraits.get("vandal");
        landlordsector.data.TenantTraits.Def rich = landlordsector.data.TenantTraits.get("prosperous");
        expect(landlordsector.data.TenantTraits.weight(vandal, "pirates", none, 1f)
                > 3f * landlordsector.data.TenantTraits.weight(vandal, "tritachyon", none, 1f), "pirates bring vandals");
        expect(landlordsector.data.TenantTraits.weight(rich, "tritachyon", none, 1f)
                > 3f * landlordsector.data.TenantTraits.weight(rich, "pirates", none, 1f), "Tri-Tachyon brings the prosperous");
        java.util.Random rnd = new java.util.Random(1);
        int pirateBad = 0, ttBad = 0;
        for (int i = 0; i < 400; i++) {
            for (String id : landlordsector.data.TenantTraits.roll("pirates", none, 1f, rnd)) {
                if (Boolean.FALSE.equals(landlordsector.data.TenantTraits.get(id).good)) pirateBad++;
            }
            for (String id : landlordsector.data.TenantTraits.roll("tritachyon", none, 1f, rnd)) {
                if (Boolean.FALSE.equals(landlordsector.data.TenantTraits.get(id).good)) ttBad++;
            }
        }
        expect(pirateBad > 2 * ttBad, "pirate tenants roll far more bad traits than Tri-Tachyon ones (" + pirateBad + " vs " + ttBad + ")");
        for (landlordsector.data.FinderDefs.Def f : landlordsector.data.FinderDefs.all()) {
            expect(!f.first.isEmpty() && f.hire > 0f && f.search > 0f && f.days > 0f && f.tenants > 0, "finder " + f.id + " is complete");
            expect(f.pitch.indexOf('%') < 0 && f.pitch.length() < 80, "finder " + f.id + " has a short plain pitch");
        }

        // Tenant math: Andrzej's vandal is far worse in a greedy shack than in a charity estate.
        float vandalShack = TenantRules.eventWeight(false, true, false, 1f, 4f, 30f, RentSetting.GREEDY, 0, 50f);
        float vandalEstate = TenantRules.eventWeight(false, true, false, 1f, 4f, 80f, RentSetting.CHARITY, 5, 50f);
        expect(vandalShack > 8f * vandalEstate, "a vandal wrecks a greedy shack far more than a charity estate ("
                + vandalShack + " vs " + vandalEstate + ")");
        expect(TenantRules.eventWeight(true, false, false, 1f, 1f, 90f, RentSetting.NORMAL, 3, 50f)
                > TenantRules.eventWeight(true, false, false, 1f, 1f, 10f, RentSetting.NORMAL, 3, 50f), "happy tenants bring good events");
        expect(TenantRules.eventWeight(false, false, true, 1f, 1f, 50f, RentSetting.NORMAL, 3, 0f)
                > 2f * TenantRules.eventWeight(false, false, true, 1f, 1f, 50f, RentSetting.NORMAL, 3, 100f), "security stops crime");
        expect(TenantRules.maxSetting(1) == RentSetting.LOW && TenantRules.maxSetting(5) == RentSetting.GREEDY, "wealth sets the rent ceiling");
        expect(TenantRules.overpricedSteps(RentSetting.GREEDY, 1) == 3, "a poor tenant on Greedy rent is 3 steps over");
        expect(TenantRules.tenantHappiness(60f, 0f, 0, 1, 0, 0f) == 45f, "a disliked tag costs 15 happiness");
        expect(TenantRules.tenantHappiness(60f, 0f, 1, 0, 0, 0f) == 68f, "a liked tag adds 8 happiness");
        expect(TenantRules.moveOutChance(10f, true) == 0f && TenantRules.moveOutChance(10f, false) > 0f
                && TenantRules.moveOutChance(50f, false) == 0f, "only unhappy, disloyal tenants move out");
        expect(TenantRules.arrivalChance(1f, 50f, RentSetting.CHARITY) > TenantRules.arrivalChance(1f, 50f, RentSetting.GREEDY),
                "cheap rent draws applicants faster");
        expect(landlordsector.data.TenantEvents.minDaysBetween() >= 5f, "at most one event per home every 5 days (Andrzej)");

        // Listings by size.
        expect(Pricing.listingCount(3) == 2 && Pricing.listingCount(9) == 8 && Pricing.listingCount(12) == 8,
                "listing counts by size");

        // Climates from vanilla planet types.
        expect(Climate.ofType("terran") == Climate.TEMPERATE, "terran is temperate");
        expect(Climate.ofType("terran-eccentric") == Climate.TEMPERATE, "terran-eccentric is temperate");
        expect(Climate.ofType("arid") == Climate.ARID && Climate.ofType("desert1") == Climate.ARID, "arid, desert");
        expect(Climate.ofType("tundra") == Climate.COLD && Climate.ofType("frozen2") == Climate.COLD, "cold");
        expect(Climate.ofType("cryovolcanic") == Climate.COLD, "cryovolcanic is cold");
        expect(Climate.ofType("water") == Climate.OCEAN, "water is ocean");
        expect(Climate.ofType("jungle") == Climate.JUNGLE, "jungle");
        expect(Climate.ofType("barren") == Climate.HOSTILE && Climate.ofType("toxic") == Climate.HOSTILE
                && Climate.ofType("lava") == Climate.HOSTILE && Climate.ofType("rocky_metallic") == Climate.HOSTILE,
                "barren, toxic, lava and rocky worlds are hostile");

        if (!problems.isEmpty()) {
            for (String p : problems) System.err.println("FAIL: " + p);
            System.exit(1);
        }
        System.out.println("Landlordsector rules verified: standard " + typical + "x " + tg + ", Earth-like " + earth
                + "x " + Pricing.grade(earth) + ", Lunamun " + lunamun + "x " + lg + ", magma with pirates " + magma
                + "x " + Pricing.grade(magma) + ", estate rent " + estateRent);
    }

    /** Reads a Starsector JSON file: # comments stripped. */
    static JSONObject json(java.nio.file.Path path) throws Exception {
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\n")) {
            int hash = line.indexOf('#');
            text.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        return new JSONObject(text.toString());
    }

    /** Loads every data file next to tiers.json. */
    static void loadAll(String tiersPath) throws Exception {
        java.nio.file.Path dir = Paths.get(tiersPath).getParent();
        TierTable.load(json(dir.resolve("tiers.json")));
        landlordsector.data.ConditionTable.load(json(dir.resolve("conditions.json")));
        landlordsector.data.HouseTraits.load(json(dir.resolve("house_traits.json")));
        landlordsector.data.TenantTraits.load(json(dir.resolve("tenant_traits.json")));
        landlordsector.data.TenantEvents.load(json(dir.resolve("tenant_events.json")));
        landlordsector.data.FinderDefs.load(json(dir.resolve("finders.json")));
    }

    private static void expect(boolean ok, String what) {
        if (!ok) problems.add(what);
    }
}
