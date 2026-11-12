package landlordsector.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tenant finders from data/config/landlordsector/finders.json. */
public final class FinderDefs {

    public static final String PATH = "data/config/landlordsector/finders.json";

    /** One finder. */
    public static final class Def {
        public String id, first, last, gender, faction, profile, pitch, portrait;
        public float hire, search, days, badTraits = 1f;
        public int tenants = 3;

        public String name() {
            return (first + " " + last).trim();
        }
    }

    private static Map<String, Def> defs = Collections.emptyMap();

    private FinderDefs() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new LinkedHashMap<>();
        JSONArray arr = json.optJSONArray("finders");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f == null || f.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = f.optString("id");
                d.first = f.optString("first", "");
                d.last = f.optString("last", "");
                d.gender = f.optString("gender", "any");
                d.faction = f.optString("faction", "independent");
                d.profile = f.optString("profile", d.faction);
                d.pitch = f.optString("pitch", "");
                d.portrait = f.isNull("portrait") ? null : f.optString("portrait", null);
                d.hire = (float) f.optDouble("hire", 50000);
                d.search = (float) f.optDouble("search", 10000);
                d.days = (float) f.optDouble("days", 14);
                d.tenants = Math.max(1, f.optInt("tenants", 3));
                d.badTraits = (float) f.optDouble("badTraits", 1);
                map.put(d.id, d);
            }
        }
        defs = map;
    }

    public static Def get(String id) {
        return defs.get(id);
    }

    public static Collection<Def> all() {
        return defs.values();
    }
}
