package landlordsector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import landlordsector.data.ConditionTable;
import landlordsector.data.HouseTraits;

/** The hidden rule: which markets sell homes, what they list and what it costs. */
public final class MarketRules {

    public static final int MIN_SIZE = 3;

    private MarketRules() {
    }

    /** Markets that sell homes: real, visible, size 3 or more, not pirate and not Luddic Path. */
    public static boolean isEligible(MarketAPI market) {
        if (market == null || !market.isInEconomy() || market.isHidden() || market.isPlanetConditionMarketOnly()) {
            return false;
        }
        if (market.getSize() < MIN_SIZE) return false;
        FactionAPI faction = market.getFaction();
        if (faction == null) return false;
        if (Factions.LUDDIC_PATH.equals(faction.getId()) || Factions.PIRATES.equals(faction.getId())) return false;
        return !Misc.isPirateFaction(faction);
    }

    public static boolean hostile(MarketAPI market) {
        return market.getFaction() != null && market.getFaction().isHostileTo(Factions.PLAYER);
    }

    public static boolean isOwn(MarketAPI market) {
        return market.isPlayerOwned() || Factions.PLAYER.equals(market.getFactionId());
    }

    /** The price rule times every market condition in conditions.json that this market has. */
    public static float mult(MarketAPI market) {
        float m = baseMult(market);
        for (ConditionTable.Effect e : ConditionTable.matching(Tenants.conditions(market), market.getFactionId())) m *= e.price;
        return Pricing.clamp(m, Pricing.MULT_MIN, Pricing.MULT_MAX);
    }

    private static float baseMult(MarketAPI market) {
        java.util.Set<String> c = Tenants.conditions(market);
        return Pricing.marketMult(market.getSize(), market.getStabilityValue(), market.getHazardValue(),
                c.contains(Conditions.HABITABLE), c.contains(Conditions.MILD_CLIMATE), c.contains(Conditions.POLLUTION),
                c.contains(Conditions.PIRATE_ACTIVITY), c.contains(Conditions.PATHER_CELLS));
    }

    /**
     * Price multiplier of a home's traits on this market: each trait's own price, and a trait that cancels the
     * cold or the heat (Geothermal) prices the home as if the world had that much less hazard.
     */
    public static float traitPrice(java.util.List<String> traits, MarketAPI market) {
        float m = 1f;
        if (traits == null) return m;
        for (String id : traits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null) m *= d.price;
        }
        if (market == null) return m;
        java.util.Set<String> c = Tenants.conditions(market);
        float off = HouseTraits.hazardOffset(traits, c);
        if (off > 0.001f) {
            float h = market.getHazardValue();
            m *= Pricing.planetMult(h - off, false, false, false) / Pricing.planetMult(h, false, false, false);
        }
        return m;
    }

    /** The tariff the player pays here, 0 on their own colonies. */
    public static float tariff(MarketAPI market) {
        if (isOwn(market)) return 0f;
        return Pricing.clamp(market.getTariff().getModifiedValue(), 0f, 0.9f);
    }

    /** What a listing is worth new, at today's market, with its tier's decor. */
    public static float listingValue(MarketAPI market, Listing listing) {
        Tier t = TierTable.get(listing.tierId);
        float decor = t != null ? t.decor : 0f;
        return listing.basePrice * mult(market) * Pricing.decorValueMult(decor) * traitPrice(listing.traits, market);
    }

    /** Price to buy a listing right now, tariff and own-colony discount included. */
    public static float buyPrice(MarketAPI market, Listing listing) {
        float price = listingValue(market, listing);
        if (isOwn(market)) return price * TierTable.economy().ownColonyPrice;
        return price * (1f + tariff(market));
    }

    /** Tiers this market lists, cheapest first. */
    public static List<Tier> tiersFor(MarketAPI market) {
        List<Tier> out = new ArrayList<>();
        for (Tier t : TierTable.all()) if (market.getSize() >= t.minSize) out.add(t);
        return out;
    }

    /** This market's listings, rolled again when missing or older than the refresh time. */
    public static MarketListings listings(MarketAPI market) {
        Portfolio p = Portfolio.get();
        MarketListings ml = p.listings().get(market.getId());
        long now = Global.getSector().getClock().getTimestamp();
        if (ml == null || Global.getSector().getClock().getElapsedDaysSince(ml.rolledAt)
                > TierTable.economy().listingRefreshDays) {
            ml = roll(market, new Random());
            ml.rolledAt = now;
            p.listings().put(market.getId(), ml);
        }
        return ml;
    }

    public static MarketListings roll(MarketAPI market, Random random) {
        MarketListings ml = new MarketListings();
        List<Tier> tiers = tiersFor(market);
        if (tiers.isEmpty()) return ml;
        WeightedRandomPicker<Tier> picker = new WeightedRandomPicker<>(random);
        for (Tier t : tiers) picker.add(t, Math.max(0.01f, t.weight));
        Climate climate = Climate.of(market);
        int count = Pricing.listingCount(market.getSize());
        for (int i = 0; i < count; i++) {
            Tier t = picker.pick();
            float base = t.min + (t.max - t.min) * random.nextFloat();
            Listing l = new Listing(Portfolio.get().newId(), t.id, climate.id, Art.pick(t.id, climate, random), base);
            l.traits = HouseTraits.roll(Tenants.conditions(market), climate.id, climate == Climate.STATION, t.index,
                    random);
            l.name = landlordsector.data.Names.make(t.id, l.traits, random);
            ml.list().add(l);
        }
        // Cheapest first, so the screen reads like a price list.
        ml.list().sort((a, b) -> Float.compare(a.basePrice, b.basePrice));
        return ml;
    }
}
