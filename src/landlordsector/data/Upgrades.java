package landlordsector.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home upgrades from data/config/landlordsector/upgrades.json (0.4.0): furniture (items kept in the furniture storage
 * and placed in a home's slots) and upgrades bought with credits (called renovations in the code and saves, Andrzej
 * 0.5.0: "call them upgrades"). Every upgrade carries the same effects as a house trait, so the stat sheet, the price
 * and the tenants treat an installed upgrade exactly like a trait.
 */
public final class Upgrades {

    public static final String PATH = "data/config/landlordsector/upgrades.json";
    public static final String ITEM_PREFIX = "lls_";
    public static final String FURNITURE_TAG = "lls_furniture";

    public enum Kind { FURNITURE, RENOVATION }

    /** One upgrade. */
    public static final class Def {
        public String id;
        public Kind kind;
        public String name;
        /** Renovations: share of the home's full value, at least minCost. Built at once since 0.5.0. */
        public float cost, minCost;
        /**
         * 0.5.0 (Andrzej: "air conditioning would only increase it by a flat value"): the value it adds as a share of
         * what it costs. Upgrades that raise the value by a percent use "price" instead.
         */
        public float valueShare;
        /** 0.5.0: upgrades it can never be in one home with. */
        public Set<String> conflicts = Collections.emptySet();
        public int minTier = 0;
        public int maxTier = 99;
        /** null: anywhere, true: stations only, false: planets only. */
        public Boolean station;
        /** House traits the home must have one of. */
        public Set<String> needs = Collections.emptySet();
        /** House traits it removes when done. */
        public Set<String> fixes = Collections.emptySet();
        /** A job, not a part of the home: no slot, gone once done. */
        public boolean oneTime;
        /** Homes for sale may already have it: weight 0 never. */
        public float rollWeight;
        public Set<String> rollAnyCondition = Collections.emptySet();
        public Set<String> rollClimates = Collections.emptySet();
        /** What it does, in the house trait format. */
        public final HouseTraits.Def effects = new HouseTraits.Def();

        public boolean furniture() {
            return kind == Kind.FURNITURE;
        }

        public String itemId() {
            return ITEM_PREFIX + id;
        }
    }

    private static Map<String, Def> defs = Collections.emptyMap();
    private static float listingChance = 0.3f;
    private static int shopMin = 1, shopMax = 3;
    private static float shopDays = 30f, lootChance = 0.12f;

    private Upgrades() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new LinkedHashMap<>();
        JSONArray arr = json.optJSONArray("upgrades");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = o.optString("id");
                String kind = o.optString("kind", "furniture");
                d.kind = "renovation".equals(kind) || "upgrade".equals(kind) ? Kind.RENOVATION : Kind.FURNITURE;
                d.name = o.optString("name", d.id);
                d.cost = (float) o.optDouble("cost", 0);
                d.minCost = (float) o.optDouble("minCost", 0);
                d.valueShare = (float) o.optDouble("valueShare", 0);
                d.conflicts = HouseTraits.strings(o.optJSONArray("conflicts"));
                d.minTier = o.optInt("minTier", 0);
                d.maxTier = o.optInt("maxTier", 99);
                d.station = o.has("station") && !o.isNull("station") ? o.optBoolean("station") : null;
                d.needs = HouseTraits.strings(o.optJSONArray("needs"));
                d.fixes = HouseTraits.strings(o.optJSONArray("fixes"));
                d.oneTime = o.optBoolean("oneTime", false);
                JSONObject roll = o.optJSONObject("roll");
                if (roll != null) {
                    d.rollWeight = (float) roll.optDouble("weight", 1);
                    d.rollAnyCondition = HouseTraits.strings(roll.optJSONArray("anyCondition"));
                    d.rollClimates = HouseTraits.strings(roll.optJSONArray("climates"));
                    d.minTier = roll.optInt("minTier", d.minTier);
                }
                d.effects.id = d.id;
                d.effects.name = d.name;
                d.effects.good = true;
                d.effects.minTier = d.minTier;
                d.effects.maxTier = d.maxTier;
                d.effects.station = d.station;
                HouseTraits.effects(o, d.effects);
                map.put(d.id, d);
            }
        }
        JSONArray stock = json.optJSONArray("shopStock");
        if (stock != null && stock.length() >= 2) {
            shopMin = stock.optInt(0, 1);
            shopMax = Math.max(shopMin, stock.optInt(1, 3));
        }
        shopDays = (float) json.optDouble("shopDays", 30);
        lootChance = (float) json.optDouble("lootChance", 0.12);
        listingChance = (float) json.optDouble("listingChance", 0.3);
        // Conflicts go both ways.
        for (Def d : map.values()) {
            for (String c : d.conflicts) {
                Def o = map.get(c);
                if (o != null && !o.conflicts.contains(d.id)) {
                    Set<String> s = new java.util.HashSet<>(o.conflicts);
                    s.add(d.id);
                    o.conflicts = s;
                }
            }
        }
        defs = map;
    }

    public static Def get(String id) {
        return id == null ? null : defs.get(id);
    }

    /** The effects of an upgrade, or null. */
    public static HouseTraits.Def effects(String id) {
        Def d = get(id);
        return d == null ? null : d.effects;
    }

    /** The upgrade a cargo item places, or null for any other item. */
    public static Def byItem(String itemId) {
        if (itemId == null || !itemId.startsWith(ITEM_PREFIX)) return null;
        Def d = defs.get(itemId.substring(ITEM_PREFIX.length()));
        return d != null && d.furniture() ? d : null;
    }

    public static Collection<Def> all() {
        return defs.values();
    }

    public static List<Def> of(Kind kind) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs.values()) if (d.kind == kind) out.add(d);
        return out;
    }

    public static int shopMin() {
        return shopMin;
    }

    public static int shopMax() {
        return shopMax;
    }

    public static float shopDays() {
        return shopDays;
    }

    public static float lootChance() {
        return lootChance;
    }

    /** What a renovation costs on a home worth {@code fullValue}. */
    public static float renovationCost(Def d, float fullValue) {
        return Math.max(d.minCost, d.cost * fullValue);
    }

    /** The flat value these upgrades add to a home worth {@code fullValue} without them (0.5.0). */
    public static float flatValue(java.util.Collection<String> ids, float fullValue) {
        float v = 0f;
        if (ids == null) return v;
        for (String id : ids) {
            Def d = defs.get(id);
            if (d == null || d.furniture() || d.valueShare <= 0f) continue;
            v += d.valueShare * renovationCost(d, fullValue);
        }
        return v;
    }

    /** The installed upgrade this one clashes with, or null. */
    public static String clash(Def d, java.util.Collection<String> installed) {
        if (installed == null) return null;
        for (String i : installed) if (d.conflicts.contains(i)) return i;
        return null;
    }

    /** True when this upgrade can go into a home of this tier, on a station or a planet, with these traits. */
    public static boolean fits(Def d, int tier, boolean station, List<String> traits) {
        if (tier < d.minTier || tier > d.maxTier) return false;
        if (d.station != null && d.station != station) return false;
        if (!d.needs.isEmpty()) {
            boolean any = false;
            for (String n : d.needs) if (traits != null && traits.contains(n)) any = true;
            if (!any) return false;
        }
        return true;
    }

    /**
     * A home for sale may come with one renovation already done (0.4.0: the seven traits that became renovations
     * still show up this way). Returns the renovation ids.
     */
    public static List<String> roll(Set<String> conditions, String climateId, boolean station, int tier,
            List<String> traits, Random random) {
        List<String> out = new ArrayList<>();
        if (random.nextFloat() >= listingChance) return out;
        List<Def> pool = new ArrayList<>();
        float total = 0f;
        for (Def d : defs.values()) {
            if (d.furniture() || d.oneTime || d.rollWeight <= 0f || !fits(d, tier, station, traits)) continue;
            if (!d.rollClimates.isEmpty() && !d.rollClimates.contains(climateId)) continue;
            if (!d.rollAnyCondition.isEmpty()) {
                boolean any = false;
                for (String c : d.rollAnyCondition) if (conditions.contains(c)) any = true;
                if (!any) continue;
            }
            if (clashes(d, traits)) continue;
            pool.add(d);
            total += d.rollWeight;
        }
        if (pool.isEmpty()) return out;
        float r = random.nextFloat() * total;
        for (Def d : pool) {
            r -= d.rollWeight;
            if (r <= 0f) {
                out.add(d.id);
                break;
            }
        }
        return out;
    }

    /** A renovation that works against a trait the home has (Air conditioning in a Scorched home is fine). */
    private static boolean clashes(Def d, List<String> traits) {
        if (traits == null) return false;
        for (String t : traits) {
            HouseTraits.Def td = HouseTraits.get(t);
            if (td != null && td.conflicts.contains(d.id)) return true;
        }
        return false;
    }

    public static int size() {
        return defs.size();
    }
}
