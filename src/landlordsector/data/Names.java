package landlordsector.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

/** Property names from data/config/landlordsector/names.json: an adjective (from a trait if any) and a noun. */
public final class Names {

    public static final String PATH = "data/config/landlordsector/names.json";

    private static Map<String, List<String>> nouns = Collections.emptyMap();
    private static List<String> adjectives = Collections.emptyList();
    private static Map<String, List<String>> byTrait = Collections.emptyMap();

    private Names() {
    }

    public static void load(JSONObject json) {
        nouns = lists(json.optJSONObject("nouns"));
        adjectives = list(json.optJSONArray("adjectives"));
        byTrait = lists(json.optJSONObject("byTrait"));
    }

    private static Map<String, List<String>> lists(JSONObject o) {
        Map<String, List<String>> m = new HashMap<>();
        if (o == null) return m;
        Iterator<?> keys = o.keys();
        while (keys.hasNext()) {
            String k = String.valueOf(keys.next());
            m.put(k, list(o.optJSONArray(k)));
        }
        return m;
    }

    private static List<String> list(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** A name for a home of this tier with these traits, for example "Leafy Cottage". */
    public static String make(String tierId, List<String> traits, Random random) {
        List<String> adj = new ArrayList<>();
        if (traits != null) {
            for (String t : traits) {
                List<String> l = byTrait.get(t);
                if (l != null) adj.addAll(l);
            }
        }
        // A home with traits usually takes its adjective from them, sometimes a plain one.
        if (adj.isEmpty() || random.nextFloat() < 0.2f) adj = adjectives;
        List<String> noun = nouns.get(tierId);
        String a = adj.isEmpty() ? "" : adj.get(random.nextInt(adj.size()));
        String n = noun == null || noun.isEmpty() ? "Home" : noun.get(random.nextInt(noun.size()));
        return (a + " " + n).trim();
    }

    public static boolean hasWordsFor(String traitId) {
        List<String> l = byTrait.get(traitId);
        return l != null && !l.isEmpty();
    }

    public static int nounCount(String tierId) {
        List<String> l = nouns.get(tierId);
        return l == null ? 0 : l.size();
    }
}
