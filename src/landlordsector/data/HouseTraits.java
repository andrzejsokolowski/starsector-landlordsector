package landlordsector.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** House traits from data/config/landlordsector/house_traits.json. */
public final class HouseTraits {

    public static final String PATH = "data/config/landlordsector/house_traits.json";

    /** One house trait. */
    public static final class Def {
        public String id;
        public String name;
        public boolean good;
        public float weight = 1f;
        public Set<String> anyCondition = Collections.emptySet();
        public Set<String> climates = Collections.emptySet();
        /** null: anywhere, true: stations only, false: planets only. */
        public Boolean station;
        public int minTier = 0;
        public int maxTier = 99;
        public float comfort, happiness, security;
        public float price = 1f;
        public float wear = 1f;
        public Set<String> tags = Collections.emptySet();
        public Set<String> conflicts = Collections.emptySet();
    }

    private static Map<String, Def> defs = Collections.emptyMap();

    private HouseTraits() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new LinkedHashMap<>();
        JSONArray arr = json.optJSONArray("traits");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t == null || t.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = t.optString("id");
                d.name = t.optString("name", d.id);
                d.good = t.optBoolean("good", true);
                d.weight = (float) t.optDouble("weight", 1);
                d.anyCondition = strings(t.optJSONArray("anyCondition"));
                d.climates = strings(t.optJSONArray("climates"));
                d.station = t.has("station") && !t.isNull("station") ? t.optBoolean("station") : null;
                d.minTier = t.optInt("minTier", 0);
                d.maxTier = t.optInt("maxTier", 99);
                d.comfort = (float) t.optDouble("comfort", 0);
                d.happiness = (float) t.optDouble("happiness", 0);
                d.security = (float) t.optDouble("security", 0);
                d.price = (float) t.optDouble("price", 1);
                d.wear = (float) t.optDouble("wear", 1);
                d.tags = strings(t.optJSONArray("tags"));
                d.conflicts = strings(t.optJSONArray("conflicts"));
                map.put(d.id, d);
            }
        }
        defs = map;
    }

    static Set<String> strings(JSONArray a) {
        if (a == null) return Collections.emptySet();
        Set<String> s = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            String v = a.optString(i, "");
            if (!v.isEmpty()) s.add(v);
        }
        return s;
    }

    public static Def get(String id) {
        return defs.get(id);
    }

    public static Collection<Def> all() {
        return defs.values();
    }

    /** True when the trait can appear on a home of this tier on a market like this. */
    public static boolean fits(Def d, Set<String> conditions, String climateId, boolean station, int tier) {
        if (tier < d.minTier || tier > d.maxTier) return false;
        if (d.station != null && d.station != station) return false;
        if (!d.climates.isEmpty() && !d.climates.contains(climateId)) return false;
        if (!d.anyCondition.isEmpty()) {
            boolean any = false;
            for (String c : d.anyCondition) if (conditions.contains(c)) any = true;
            if (!any) return false;
        }
        return true;
    }

    /** 1 to 3 traits: one more for houses and up, and a coin flip for one more. */
    public static List<String> roll(Set<String> conditions, String climateId, boolean station, int tier,
            Random random) {
        int count = 1 + (tier >= 3 ? 1 : 0) + (random.nextBoolean() ? 1 : 0);
        count = Math.min(3, count);
        List<String> out = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            float total = 0f;
            List<Def> pool = new ArrayList<>();
            for (Def d : defs.values()) {
                if (out.contains(d.id) || !fits(d, conditions, climateId, station, tier)) continue;
                boolean clash = false;
                for (String o : out) {
                    Def od = defs.get(o);
                    if (d.conflicts.contains(o) || (od != null && od.conflicts.contains(d.id))) clash = true;
                }
                if (clash) continue;
                pool.add(d);
                total += Math.max(0f, d.weight);
            }
            if (pool.isEmpty() || total <= 0f) break;
            float r = random.nextFloat() * total;
            for (Def d : pool) {
                r -= Math.max(0f, d.weight);
                if (r <= 0f) {
                    out.add(d.id);
                    break;
                }
            }
        }
        return out;
    }

    /** Every tag of these traits. */
    public static Set<String> tags(List<String> traitIds) {
        Set<String> s = new HashSet<>();
        if (traitIds == null) return s;
        for (String id : traitIds) {
            Def d = defs.get(id);
            if (d != null) s.addAll(d.tags);
        }
        return s;
    }

    public static int size() {
        return defs.size();
    }
}
