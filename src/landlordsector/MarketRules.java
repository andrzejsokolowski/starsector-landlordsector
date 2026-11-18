package landlordsector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import landlordsector.data.ConditionTable;
import landlordsector.data.HouseTraits;
import landlordsector.data.Worlds;

/** The hidden rule: which markets sell homes, what they list and what it costs. */
public final class MarketRules {

    public static final int MIN_SIZE = 3;
    /** Share of homes for sale that come worn (0.6.0). */
    public static final float WORN_CHANCE = 0.35f;

    private MarketRules() {
    }

    /**
     * Markets that sell homes: real, visible, size 3 or more and not Luddic Path. Pirate markets sell homes too
     * since 0.7.2 (Andrzej: "No property agent on pirate market?").
     */
    public static boolean isEligible(MarketAPI market) {
        if (market == null || !market.isInEconomy() || market.isHidden() || market.isPlanetConditionMarketOnly()) {
            return false;
        }
        if (market.getSize() < MIN_SIZE) return false;
        FactionAPI faction = market.getFaction();
        if (faction == null) return false;
        return !Factions.LUDDIC_PATH.equals(faction.getId());
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
        for (ConditionTable.Effect e : effects(market)) m *= e.price;
        return Pricing.clamp(m, Pricing.MULT_MIN, Pricing.MULT_MAX);
    }

    /** The market's condition effects, plus its world's own effect (0.8.0, worlds.json) last. */
    public static List<ConditionTable.Effect> effects(MarketAPI market) {
        List<ConditionTable.Effect> out = ConditionTable.matching(Tenants.conditions(market), market.getFactionId());
        Worlds.Def w = Worlds.get(market.getId());
        if (w != null) out.add(w.effect);
        return out;
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
            // 1.0.1: an upgrade that does nothing in this home does not raise its value.
            if (PropertyStats.noUse(landlordsector.data.Upgrades.get(id), market, traits)) continue;
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

    /**
     * 1.12.3 (LLS-37): the tariff on buying a home here, paid once: {@code housingTariff} times the market's tariff,
     * 0 on the player's colonies. Rent and sales pay no tariff.
     */
    public static float housingTariff(MarketAPI market) {
        return tariff(market) * TierTable.economy().housingTariff;
    }

    /** The tariff the player pays here, 0 on their own colonies. */
    public static float tariff(MarketAPI market) {
        if (isOwn(market)) return 0f;
        return Pricing.clamp(market.getTariff().getModifiedValue(), 0f, 0.9f);
    }

    /** What a listing is worth new, at today's market, with its tier's decor, its neighbour and its upgrades. */
    public static float listingValue(MarketAPI market, Listing listing) {
        Tier t = TierTable.get(listing.tierId);
        float decor = t != null ? t.decor : 0f;
        for (String id : listing.features()) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null) decor += d.decor;
        }
        float v = listing.basePrice * Surroundings.homeMult(market, listing.neighbor) * Pricing.decorValueMult(decor)
                * traitPrice(listing.features(), market);
        return v + landlordsector.data.Upgrades.flatValue(listing.upgrades, v,
                id -> !PropertyStats.noUse(landlordsector.data.Upgrades.get(id), market, listing.features()));
    }

    /**
     * Homes a market has room for, owned and for sale together. 0.5.0 had one per point of size; 0.8.0 (Andrzej: "the
     * amount of properties is still too low, even on big planets such as eochu bres") has many more on big markets,
     * times the world's own "homes" (worlds.json): Eochu Bres, size 6, has room for 23.
     */
    public static int housingSlots(MarketAPI market) {
        int base = Pricing.housingRoom(market.getSize());
        if (base <= 0) return 0;
        Worlds.Def w = Worlds.get(market.getId());
        return Math.max(1, Math.round(base * (w != null ? w.homes : 1f)));
    }

    /** Homes for sale to roll: half to all of the free room, at least one while there is room. */
    public static int listingRoll(int free, Random random) {
        if (free <= 0) return 0;
        int min = Math.max(1, (free + 1) / 2);
        return min + random.nextInt(free - min + 1);
    }

    /**
     * What a listing is worth as it stands (0.6.0, house flipping): its value new, lowered by its wear like an owned
     * home ({@link Pricing#value}). Repairing it costs less than the difference, so a worn home bought, repaired and
     * sold makes money before tariffs.
     */
    public static float listingWorth(MarketAPI market, Listing listing) {
        return Pricing.value(listingValue(market, listing), listing.condition(), false, TierTable.economy().landValue);
    }

    /** Price to buy a listing right now, the housing tariff or the own-colony price share included. */
    public static float buyPrice(MarketAPI market, Listing listing) {
        float price = listingWorth(market, listing);
        if (isOwn(market)) return price * TierTable.economy().ownColonyPrice;
        return price * (1f + housingTariff(market));
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
        } else {
            grow(market, ml, new Random());
        }
        ml.size = market.getSize();
        return ml;
    }

    /**
     * 0.7.2 (Andrzej: "spawn some ... each time the market increases in size"): a market that grew since its homes
     * were listed puts one new home up for sale per size it grew, as far as the new room allows. The new homes may
     * be of the bigger sizes the market now has.
     */
    public static void grow(MarketAPI market, MarketListings ml, Random random) {
        int size = market.getSize();
        if (ml.size <= 0 || size <= ml.size) return;
        int free = housingSlots(market) - Portfolio.get().countAt(market.getId()) - ml.list().size();
        int count = Math.min(size - ml.size, free);
        if (count <= 0) return;
        add(market, ml, count, random);
    }

    public static MarketListings roll(MarketAPI market, Random random) {
        MarketListings ml = new MarketListings();
        add(market, ml, listingRoll(housingSlots(market) - Portfolio.get().countAt(market.getId()), random), random);
        ml.size = market.getSize();
        return ml;
    }

    /** The pictures of the homes on this list (1.0.1: new homes take the one used least). */
    private static List<String> pictures(MarketListings ml) {
        List<String> out = new ArrayList<>();
        for (Listing l : ml.list()) if (l.artPath != null) out.add(l.artPath);
        return out;
    }

    /** Adds {@code count} new homes for sale, then sorts the list cheapest first. */
    /**
     * One home of this tier for sale on this market, not yet on any list (1.6.0: split out for the console's
     * lls_givehome). {@code mayBeWorn} false gives a home in full condition.
     */
    public static Listing make(MarketAPI market, Tier t, MarketListings ml, boolean mayBeWorn, Random random) {
        Worlds.Def world = Worlds.get(market.getId());
        Climate climate = Climate.of(market);
        float base = t.min + (t.max - t.min) * random.nextFloat();
        Listing l = new Listing(Portfolio.get().newId(), t.id, climate.id,
                Art.pick(t.id, PlanetType.of(market), random, ml != null ? pictures(ml) : new ArrayList<>()), base);
        l.traits = HouseTraits.roll(Tenants.conditions(market), climate.id, climate == Climate.STATION, t.index,
                world != null ? world.traits : null,
                random);
        l.upgrades = landlordsector.data.Upgrades.roll(Tenants.conditions(market), climate.id,
                climate == Climate.STATION, t.index, l.traits, random);
        l.name = landlordsector.data.Names.make(t.id, l.features(), random);
        l.neighbor = Surroundings.roll(market, random);
        // House flipping (Andrzej, 0.6.0): some homes come worn, the worst ones dirty or covered in graffiti.
        if (mayBeWorn && random.nextFloat() < WORN_CHANCE) {
            l.wear = 0.2f + random.nextFloat() * 0.5f;
            if (l.wear > 0.45f && random.nextFloat() < 0.6f) {
                String mark = random.nextBoolean() ? "graffiti" : "dirty";
                if (!l.traits.contains(mark) && HouseTraits.get(mark) != null) l.traits.add(mark);
            }
        }
        return l;
    }

    private static void add(MarketAPI market, MarketListings ml, int count, Random random) {
        List<Tier> tiers = new ArrayList<>();
        Worlds.Def world = Worlds.get(market.getId());
        WeightedRandomPicker<Tier> picker = new WeightedRandomPicker<>(random);
        for (Tier t : tiersFor(market)) {
            float w = Math.max(0.01f, t.weight) * (world != null ? world.tierMult(t.id) : 1f);
            if (w <= 0f) continue;
            tiers.add(t);
            picker.add(t, w);
        }
        if (tiers.isEmpty()) return;
        // 0.8.0 (Andrzej: "filled with tons of property from all ranges"): a list with room for every size the
        // market has starts with one of each, so a big market always shows its estates; the rest are rolled.
        List<Tier> first = count >= tiers.size() ? tiers : new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Tier t = i < first.size() ? first.get(i) : picker.pick();
            ml.list().add(make(market, t, ml, true, random));
        }
        // Cheapest first, so the screen reads like a price list.
        ml.list().sort((a, b) -> Float.compare(a.basePrice, b.basePrice));
    }
}
