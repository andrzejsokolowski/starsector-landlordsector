package landlordsector.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/** Headhunters from data/config/landlordsector/headhunters.json. The code calls them finders. */
public final class FinderDefs {

    public static final String PATH = "data/config/landlordsector/headhunters.json";

    /** One headhunter. */
    public static final class Def {
        public String id, first, last, gender, profile, pitch, perk, portrait;
        public List<String> markets = Collections.emptyList();
        public List<String> names = Collections.emptyList();
        public float hire, search, days, badTraits = 1f;
        public int tenants = 3;
        public int wealthShift;
        public Map<String, Float> traitBoost = Collections.emptyMap();

        public String name() {
            return (first + " " + last).trim();
        }
    }

    private static Map<String, Def> defs = Collections.emptyMap();

    private FinderDefs() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new LinkedHashMap<>();
        JSONArray arr = json.optJSONArray("headhunters");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f == null || f.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = f.optString("id");
                d.first = f.optString("first", "");
                d.last = f.optString("last", "");
                d.gender = f.optString("gender", "any");
                d.profile = f.optString("profile", "independent");
                d.pitch = f.optString("pitch", "");
                d.perk = f.optString("perk", "");
                d.portrait = f.isNull("portrait") ? null : f.optString("portrait", null);
                d.markets = strings(f.optJSONArray("markets"));
                d.names = strings(f.optJSONArray("names"));
                d.hire = (float) f.optDouble("hire", 50000);
                d.search = (float) f.optDouble("search", 10000);
                d.days = (float) f.optDouble("days", 14);
                d.tenants = Math.max(1, f.optInt("tenants", 3));
                d.badTraits = (float) f.optDouble("badTraits", 1);
                d.wealthShift = f.optInt("wealthShift", 0);
                d.traitBoost = TenantTraits.floats(f.optJSONObject("traitBoost"));
                map.put(d.id, d);
            }
        }
        defs = map;
    }

    private static List<String> strings(JSONArray a) {
        if (a == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public static Def get(String id) {
        return defs.get(id);
    }

    public static Collection<Def> all() {
        return defs.values();
    }
}
