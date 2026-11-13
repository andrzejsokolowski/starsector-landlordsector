package landlordsector.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home levels and health from data/config/landlordsector/levels.json (0.4.0). A level is how much of a problem
 * heat, cold, air, noise or light is in one home, 0 (fine) to 3. The place sets it, traits and upgrades move it,
 * and every step costs the tenant happiness and health.
 */
public final class Levels {

    public static final String PATH = "data/config/landlordsector/levels.json";
    public static final int MAX = 3;

    /** One level. */
    public static final class Def {
        public String id;
        public String name;
        public String[] words = {"None", "Low", "High", "Extreme"};
        /** True when a plus is good (Air +1 is cleaner air). Written changes flip sign for these. */
        public boolean better;
        /** Cost per step. */
        public float happiness, health;
        public Map<String, Float> conditions = Collections.emptyMap();
        public Map<String, Float> industries = Collections.emptyMap();
        /** Market size to level, the highest size that fits counts. */
        public final TreeMap<Integer, Integer> marketSize = new TreeMap<>();
    }

    private static List<Def> defs = Collections.emptyList();
    private static float baseHealth = 70f, sickBelow = 60f, treatmentBelow = 20f, wornHealth = 20f;

    private Levels() {
    }

    public static void load(JSONObject json) {
        List<Def> list = new ArrayList<>();
        JSONArray arr = json.optJSONArray("levels");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = o.optString("id");
                d.name = o.optString("name", d.id);
                JSONArray w = o.optJSONArray("words");
                if (w != null && w.length() == MAX + 1) {
                    d.words = new String[MAX + 1];
                    for (int k = 0; k <= MAX; k++) d.words[k] = w.optString(k, String.valueOf(k));
                }
                d.better = o.optBoolean("better", false);
                d.happiness = (float) o.optDouble("happiness", 0);
                d.health = (float) o.optDouble("health", 0);
                d.conditions = HouseTraits.numbers(o.optJSONObject("conditions"), 0f);
                d.industries = HouseTraits.numbers(o.optJSONObject("industries"), 0f);
                JSONObject ms = o.optJSONObject("marketSize");
                if (ms != null) {
                    for (java.util.Iterator<?> it = ms.keys(); it.hasNext();) {
                        String k = String.valueOf(it.next());
                        try {
                            d.marketSize.put(Integer.parseInt(k.trim()), ms.optInt(k, 0));
                        } catch (NumberFormatException ignored) {
                            // A size that is not a number is left out.
                        }
                    }
                }
                list.add(d);
            }
        }
        defs = list;
        baseHealth = (float) json.optDouble("baseHealth", 70);
        sickBelow = (float) json.optDouble("sickBelow", 60);
        treatmentBelow = (float) json.optDouble("treatmentBelow", 20);
        wornHealth = (float) json.optDouble("wornHealth", 20);
    }

    public static List<Def> all() {
        return defs;
    }

    public static Def get(String id) {
        for (Def d : defs) if (d.id.equals(id)) return d;
        return null;
    }

    public static float baseHealth() {
        return baseHealth;
    }

    public static float sickBelow() {
        return sickBelow;
    }

    public static float treatmentBelow() {
        return treatmentBelow;
    }

    public static float wornHealth() {
        return wornHealth;
    }

    /** How a written change ({"heat":-1}, {"air":1}) moves the problem: -1 means one step better. */
    public static float severity(Def d, float written) {
        return d.better ? -written : written;
    }

    public static int clamp(float v) {
        return Math.max(0, Math.min(MAX, Math.round(v)));
    }

    public static String word(Def d, int level) {
        return d.words[Math.max(0, Math.min(MAX, level))];
    }

    /** "Heat: Hot (1)". */
    public static String label(Def d, int level) {
        return d.name + ": " + word(d, level) + " (" + level + ")";
    }

    /** A written change as players read it: "Heat -1", "Air +1". */
    public static String change(Def d, float written) {
        int r = Math.round(written);
        return d.name + " " + (r > 0 ? "+" : "") + r;
    }

    /** True when the written change helps: a cooler home, cleaner air. */
    public static boolean helps(Def d, float written) {
        return severity(d, written) < 0f;
    }

    /** The level the place gives, before traits and upgrades: its conditions, buildings and size added up, at most 3. */
    public static int fromPlace(Def d, Set<String> conditions, int marketSize, Predicate<String> hasIndustry) {
        float v = 0f;
        for (Map.Entry<String, Float> e : d.conditions.entrySet()) {
            if (conditions.contains(e.getKey())) v += e.getValue();
        }
        for (Map.Entry<String, Float> e : d.industries.entrySet()) {
            if (hasIndustry != null && hasIndustry.test(e.getKey())) v += e.getValue();
        }
        v += sizeLevel(d, marketSize);
        return clamp(v);
    }

    /** The level a market of this size gives on its own. */
    public static int sizeLevel(Def d, int marketSize) {
        Map.Entry<Integer, Integer> s = d.marketSize.floorEntry(marketSize);
        return s == null ? 0 : s.getValue();
    }
}
