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
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\n")) {
            int hash = line.indexOf('#');
            text.append(hash >= 0 ? line.substring(0, hash) : line).append('\n');
        }
        TierTable.load(new JSONObject(text.toString()));

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

        // Andrzej's price spread: 0.25x to 4x.
        float prime = Pricing.marketMult(8, 10f, 0.5f, true, true, false);
        float worst = Pricing.marketMult(3, 0f, 2.5f, false, false, true);
        float typical = Pricing.marketMult(6, 6f, 1.25f, false, false, false);
        expect(Math.abs(prime - 4f) < 1e-4, "a prime world reaches 4x, got " + prime);
        expect(Math.abs(worst - 0.25f) < 1e-4, "the worst world drops to 0.25x, got " + worst);
        expect(typical > 0.5f && typical < 1.5f, "a typical world sits near 1x, got " + typical);
        expect(Pricing.marketMult(6, 6f, 1f, true, true, false) > Pricing.marketMult(6, 6f, 1f, false, false, true),
                "mild, habitable, clean worlds cost more than polluted ones");
        expect(Pricing.sizeMult(7) > Pricing.sizeMult(5), "bigger markets cost more");
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
        System.out.println("Landlordsector rules verified: prime " + prime + "x, worst " + worst + "x, typical "
                + typical + "x, estate rent " + estateRent);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) problems.add(what);
    }
}
