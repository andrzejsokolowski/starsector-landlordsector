package landlordsector.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tenant traits from data/config/landlordsector/tenant_traits.json. */
public final class TenantTraits {

    public static final String PATH = "data/config/landlordsector/tenant_traits.json";

    /** One tenant trait. good is null for neutral traits. */
    public static final class Def {
        public String id;
        public String name;
        public Boolean good;
        public float weight = 1f;
        public Map<String, Float> factions = Collections.emptyMap();
        public Map<String, Float> conditions = Collections.emptyMap();
        public int wealth;
        public float wear = 1f;
        public float happiness;
        public Set<String> likes = Collections.emptySet();
        public Set<String> dislikes = Collections.emptySet();
        public Map<String, Float> events = Collections.emptyMap();
        public boolean loyal;
        public Set<String> conflicts = Collections.emptySet();
    }

    private static Map<String, Def> defs = Collections.emptyMap();
    private static Map<String, Integer> baseWealth = Collections.emptyMap();

    private TenantTraits() {
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
                d.good = t.has("good") && !t.isNull("good") ? t.optBoolean("good") : null;
                d.weight = (float) t.optDouble("weight", 1);
                d.factions = floats(t.optJSONObject("factions"));
                d.conditions = floats(t.optJSONObject("conditions"));
                d.wealth = t.optInt("wealth", 0);
                d.wear = (float) t.optDouble("wear", 1);
                d.happiness = (float) t.optDouble("happiness", 0);
                d.likes = HouseTraits.strings(t.optJSONArray("likes"));
                d.dislikes = HouseTraits.strings(t.optJSONArray("dislikes"));
                d.events = floats(t.optJSONObject("events"));
                d.loyal = t.optBoolean("loyal", false);
                d.conflicts = HouseTraits.strings(t.optJSONArray("conflicts"));
                map.put(d.id, d);
            }
        }
        Map<String, Integer> w = new HashMap<>();
        JSONObject wj = json.optJSONObject("wealth");
        if (wj != null) {
            Iterator<?> keys = wj.keys();
            while (keys.hasNext()) {
                String k = String.valueOf(keys.next());
                w.put(k, wj.optInt(k, 3));
            }
        }
        defs = map;
        baseWealth = w;
    }

    static Map<String, Float> floats(JSONObject o) {
        if (o == null) return Collections.emptyMap();
        Map<String, Float> m = new HashMap<>();
        Iterator<?> keys = o.keys();
        while (keys.hasNext()) {
            String k = String.valueOf(keys.next());
            m.put(k, (float) o.optDouble(k, 1));
        }
        return m;
    }

    /** Whether the trait with this display name is good (true), bad (false) or unknown (null). */
    public static Boolean byName(String name) {
        for (Def d : defs.values()) if (d.name.equals(name)) return d.good;
        return null;
    }

    public static Def get(String id) {
        return defs.get(id);
    }

    public static Collection<Def> all() {
        return defs.values();
    }

    public static int baseWealth(String factionId) {
        Integer w = baseWealth.get(factionId);
        return w == null ? 3 : w;
    }

    /** A trait's weight for a tenant from this faction and market. Bad traits times {@code badMult}. */
    public static float weight(Def d, String factionId, Set<String> conditions, float badMult) {
        float w = Math.max(0f, d.weight);
        Float f = d.factions.get(factionId);
        if (f != null) w *= f;
        for (Map.Entry<String, Float> e : d.conditions.entrySet()) {
            if (conditions.contains(e.getKey())) w *= e.getValue();
        }
        if (Boolean.FALSE.equals(d.good)) w *= badMult;
        return w;
    }

    /** 2 or 3 traits for one tenant. */
    public static List<String> roll(String factionId, Set<String> conditions, float badMult, Random random) {
        return roll(factionId, conditions, badMult, Collections.emptyMap(), random);
    }

    /** 2 or 3 traits, with some traits made likelier (a headhunter's perk). */
    public static List<String> roll(String factionId, Set<String> conditions, float badMult, Map<String, Float> boost,
            Random random) {
        int count = 2 + (random.nextFloat() < 0.4f ? 1 : 0);
        List<String> out = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            float total = 0f;
            List<Def> pool = new ArrayList<>();
            List<Float> weights = new ArrayList<>();
            for (Def d : defs.values()) {
                if (out.contains(d.id)) continue;
                boolean clash = false;
                for (String o : out) {
                    Def od = defs.get(o);
                    if (d.conflicts.contains(o) || (od != null && od.conflicts.contains(d.id))) clash = true;
                }
                if (clash) continue;
                float w = weight(d, factionId, conditions, badMult);
                Float b = boost == null ? null : boost.get(d.id);
                if (b != null) w *= b;
                if (w <= 0f) continue;
                pool.add(d);
                weights.add(w);
                total += w;
            }
            if (pool.isEmpty()) break;
            float r = random.nextFloat() * total;
            for (int i = 0; i < pool.size(); i++) {
                r -= weights.get(i);
                if (r <= 0f || i == pool.size() - 1) {
                    out.add(pool.get(i).id);
                    break;
                }
            }
        }
        return out;
    }

    public static int size() {
        return defs.size();
    }
}
