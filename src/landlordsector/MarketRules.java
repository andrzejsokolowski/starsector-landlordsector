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

    public static float mult(MarketAPI market) {
        return Pricing.marketMult(market.getSize(), market.getStabilityValue(), market.getHazardValue(),
                market.hasCondition(Conditions.HABITABLE), market.hasCondition(Conditions.MILD_CLIMATE),
                market.hasCondition(Conditions.POLLUTION), market.hasCondition(Conditions.PIRATE_ACTIVITY),
                market.hasCondition(Conditions.PATHER_CELLS));
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
        return listing.basePrice * mult(market) * Pricing.decorValueMult(decor);
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
            ml.list().add(new Listing(Portfolio.get().newId(), t.id, climate.id, Art.pick(t.id, climate, random),
                    base));
        }
        // Cheapest first, so the screen reads like a price list.
        ml.list().sort((a, b) -> Float.compare(a.basePrice, b.basePrice));
        return ml;
    }
}
