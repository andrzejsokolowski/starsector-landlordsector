package landlordsector.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The environment of one home (0.5.0) from data/config/landlordsector/environment.json: Commute, Air and Noise as
 * scores from 0 to 100 shown as letter grades, the building each home sits next to, and what tenants like or hate
 * about the colony as a whole (criminals and the police).
 */
public final class Environment {

    public static final String PATH = "data/config/landlordsector/environment.json";
    /** A home next to nothing. */
    public static final String NOTHING = "none";

    /** One score: Commute, Air or Noise. */
    public static final class Score {
        public String id, name, better, worse;
        public float base, station, neutral, happiness, health;
        /**
         * 1.0.1 (Andrzej: "roll a commute randomly, from 0 to 50"): each home's own base, from rollMin to rollMax,
         * instead of {@link #base}. Negative when the score has no roll.
         */
        public float rollMin = -1f, rollMax = -1f;
        public Map<String, Float> conditions = Collections.emptyMap();
    }

    /** What a building does to the homes next to it. */
    public static final class Neighbor {
        public String key;
        /** Shown instead of the building's own name ("the city center"), or null. */
        public String name;
        public float commute, air, noise, happiness, health, security;
        public float price = 1f;
        public float weight = 1f;
        public Set<String> tags = Collections.emptySet();
        /** 1.0.1: the city center. A home next to it gets the highest rolled base ("50 + 25 from the city center"). */
        public boolean central;

        /** True when the building changes anything for the home next to it (1.0.1: no plain neighbours). */
        public boolean hasEffect() {
            return central || commute != 0f || air != 0f || noise != 0f || happiness != 0f || health != 0f
                    || security != 0f || Math.abs(price - 1f) > 0.001f || !tags.isEmpty();
        }

        /** The change to one score. */
        public float score(String id) {
            switch (id) {
                case "commute": return commute;
                case "air": return air;
                case "noise": return noise;
                default: return 0f;
            }
        }
    }

    private static List<Score> scores = Collections.emptyList();
    private static Map<String, Neighbor> byId = Collections.emptyMap();
    private static Map<String, Neighbor> byTag = Collections.emptyMap();
    private static Set<String> skipIds = Collections.emptySet(), skipTags = Collections.emptySet();
    private static Map<String, Set<String>> colonyById = Collections.emptyMap(), colonyByTag = Collections.emptyMap();
    private static float nothingWeight = 3f;
    /** 0.6.0: building id to the score each lifts every home on the colony to (Maglev: commute 95). */
    private static Map<String, Map<String, Float>> overrides = Collections.emptyMap();
    /** A building with no entry: no effects. */
    private static final Neighbor PLAIN = new Neighbor();

    private Environment() {
    }

    public static void load(JSONObject json) {
        List<Score> list = new ArrayList<>();
        JSONArray arr = json.optJSONArray("scores");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("id", "").isEmpty()) continue;
                Score s = new Score();
                s.id = o.optString("id");
                s.name = o.optString("name", s.id);
                s.better = o.optString("better", s.name + " better");
                s.worse = o.optString("worse", s.name + " worse");
                s.base = (float) o.optDouble("base", 50);
                s.station = (float) o.optDouble("station", 0);
                s.neutral = (float) o.optDouble("neutral", 50);
                s.happiness = (float) o.optDouble("happiness", 0);
                s.health = (float) o.optDouble("health", 0);
                JSONArray roll = o.optJSONArray("roll");
                if (roll != null && roll.length() == 2) {
                    s.rollMin = (float) roll.optDouble(0, -1);
                    s.rollMax = (float) roll.optDouble(1, -1);
                }
                s.conditions = HouseTraits.numbers(o.optJSONObject("conditions"), 0f);
                list.add(s);
            }
        }
        scores = list;
        Map<String, Neighbor> ids = new LinkedHashMap<>();
        JSONObject bi = json.optJSONObject("byId");
        if (bi != null) {
            for (java.util.Iterator<?> it = bi.keys(); it.hasNext();) {
                String k = String.valueOf(it.next());
                JSONObject o = bi.optJSONObject(k);
                if (o != null) ids.put(k, neighbor(k, o));
            }
        }
        byId = ids;
        Map<String, Neighbor> tags = new LinkedHashMap<>();
        JSONArray bt = json.optJSONArray("byTag");
        if (bt != null) {
            for (int i = 0; i < bt.length(); i++) {
                JSONObject o = bt.optJSONObject(i);
                if (o == null || o.optString("tag", "").isEmpty()) continue;
                tags.put(o.optString("tag"), neighbor(o.optString("tag"), o));
            }
        }
        byTag = tags;
        skipIds = HouseTraits.strings(json.optJSONArray("skipIds"));
        skipTags = HouseTraits.strings(json.optJSONArray("skipTags"));
        nothingWeight = (float) json.optDouble("nothingWeight", 3);
        colonyById = stringLists(json.optJSONObject("colonyTagsById"));
        colonyByTag = stringLists(json.optJSONObject("colonyTagsByTag"));
        Map<String, Map<String, Float>> ov = new HashMap<>();
        JSONObject oo = json.optJSONObject("colonyOverrides");
        if (oo != null) {
            for (java.util.Iterator<?> it = oo.keys(); it.hasNext();) {
                String k = String.valueOf(it.next());
                ov.put(k, HouseTraits.numbers(oo.optJSONObject(k), 0f));
            }
        }
        overrides = ov;
    }

    /** The lowest score a colony building forces for this score, by building id ("aotd_maglev" -> 95). */
    public static Map<String, Float> overridesFor(String scoreId, Collection<String> buildingIds) {
        Map<String, Float> out = new LinkedHashMap<>();
        for (String b : buildingIds) {
            Map<String, Float> m = overrides.get(b);
            if (m != null && m.containsKey(scoreId)) out.put(b, m.get(scoreId));
        }
        return out;
    }

    private static Neighbor neighbor(String key, JSONObject o) {
        Neighbor n = new Neighbor();
        n.key = key;
        n.name = o.has("name") && !o.isNull("name") ? o.optString("name") : null;
        n.commute = (float) o.optDouble("commute", 0);
        n.air = (float) o.optDouble("air", 0);
        n.noise = (float) o.optDouble("noise", 0);
        n.happiness = (float) o.optDouble("happiness", 0);
        n.health = (float) o.optDouble("health", 0);
        n.security = (float) o.optDouble("security", 0);
        n.price = (float) o.optDouble("price", 1);
        n.weight = (float) o.optDouble("weight", 1);
        n.tags = HouseTraits.strings(o.optJSONArray("tags"));
        n.central = o.optBoolean("central", false);
        return n;
    }

    private static Map<String, Set<String>> stringLists(JSONObject o) {
        if (o == null) return Collections.emptyMap();
        Map<String, Set<String>> m = new HashMap<>();
        for (java.util.Iterator<?> it = o.keys(); it.hasNext();) {
            String k = String.valueOf(it.next());
            m.put(k, HouseTraits.strings(o.optJSONArray(k)));
        }
        return m;
    }

    public static List<Score> scores() {
        return scores;
    }

    public static Score score(String id) {
        for (Score s : scores) if (s.id.equals(id)) return s;
        return null;
    }

    /**
     * True when a home can sit next to this building: not in orbit (stations, orbital works, the terminus), not
     * hidden, and it changes something for the home (1.0.1, Andrzej: "remove being close to buildings that dont
     * have effects, like the indevo restoration docks").
     */
    public static boolean pickable(String industryId, Collection<String> industryTags) {
        if (industryId == null || skipIds.contains(industryId)) return false;
        if (industryTags != null) for (String t : industryTags) if (skipTags.contains(t.trim())) return false;
        return of(industryId, industryTags).hasEffect();
    }

    /**
     * The score a home starts from (1.0.1): its own roll between rollMin and rollMax, the same every time for the
     * same home, or the highest roll next to the city center. Scores without a roll, and homes without an id,
     * start from the base.
     */
    public static float start(Score sc, String homeId, Neighbor nb) {
        if (sc.rollMin < 0f || sc.rollMax < sc.rollMin) return sc.base;
        if (nb != null && nb.central) return sc.rollMax;
        if (homeId == null) return sc.base;
        int range = Math.round(sc.rollMax - sc.rollMin);
        Random r = new Random(homeId.hashCode() * 31L + sc.id.hashCode());
        return sc.rollMin + r.nextInt(range + 1);
    }

    /** What this building does to the home next to it: its own entry, else its first matching tag, else nothing. */
    public static Neighbor of(String industryId, Collection<String> industryTags) {
        if (industryId == null || NOTHING.equals(industryId)) return PLAIN;
        Neighbor n = byId.get(industryId);
        if (n != null) return n;
        if (industryTags != null) {
            Set<String> trimmed = new HashSet<>();
            for (String t : industryTags) trimmed.add(t.trim());
            for (Map.Entry<String, Neighbor> e : byTag.entrySet()) if (trimmed.contains(e.getKey())) return e.getValue();
        }
        return PLAIN;
    }

    /**
     * Picks the building a new home sits next to: each pickable building by its weight, against "nothing".
     *
     * @param buildings industry id to its tags
     * @return an industry id, or {@link #NOTHING}
     */
    public static String roll(Map<String, Collection<String>> buildings, Random random) {
        List<String> ids = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = nothingWeight;
        for (Map.Entry<String, Collection<String>> e : buildings.entrySet()) {
            if (!pickable(e.getKey(), e.getValue())) continue;
            float w = Math.max(0f, of(e.getKey(), e.getValue()).weight);
            if (w <= 0f) continue;
            ids.add(e.getKey());
            weights.add(w);
            total += w;
        }
        float r = random.nextFloat() * total;
        for (int i = 0; i < ids.size(); i++) {
            r -= weights.get(i);
            if (r <= 0f) return ids.get(i);
        }
        return NOTHING;
    }

    /** Tags the whole colony carries for tenants (police, spying), from its buildings. */
    public static Set<String> colonyTags(Map<String, Collection<String>> buildings) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Collection<String>> e : buildings.entrySet()) {
            Set<String> byIdTags = colonyById.get(e.getKey());
            if (byIdTags != null) out.addAll(byIdTags);
            if (e.getValue() != null) {
                for (String t : e.getValue()) {
                    Set<String> s = colonyByTag.get(t.trim());
                    if (s != null) out.addAll(s);
                }
            }
        }
        return out;
    }

    /** The colony tags one building gives. */
    public static Set<String> colonyTags(String industryId, Collection<String> industryTags) {
        Map<String, Collection<String>> one = new HashMap<>();
        one.put(industryId, industryTags);
        return colonyTags(one);
    }

    /** Every tag any neighbour or colony building can give, for the checks. */
    public static Set<String> allTags() {
        Set<String> out = new HashSet<>();
        for (Neighbor n : byId.values()) out.addAll(n.tags);
        for (Neighbor n : byTag.values()) out.addAll(n.tags);
        for (Set<String> s : colonyById.values()) out.addAll(s);
        for (Set<String> s : colonyByTag.values()) out.addAll(s);
        return out;
    }

    public static Collection<Neighbor> byIdEntries() {
        return byId.values();
    }

    /** S+ 85+, S 75+, A 65+, B 55+, C 45+, D 35+, E 25+, F below (0.6.0: 50, no modifiers, is C). */
    public static String letter(float score) {
        if (score >= 85f) return "S+";
        if (score >= 75f) return "S";
        if (score >= 65f) return "A";
        if (score >= 55f) return "B";
        if (score >= 45f) return "C";
        if (score >= 35f) return "D";
        if (score >= 25f) return "E";
        return "F";
    }

    /** "Quieter (+15)" or "Louder (-20)". */
    public static String change(Score s, float v) {
        int r = Math.round(v);
        return (r >= 0 ? s.better + " (+" : s.worse + " (") + r + ")";
    }

    public static float clamp(float v) {
        return Math.max(0f, Math.min(100f, v));
    }
}
