package landlordsector.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Services from data/config/landlordsector/services.json (0.6.0): hired per home, paid every month as a share of the
 * home's Normal rent, cancelled at any time. Each carries the same effects as a house trait while hired, so the stat
 * sheet and the tenants treat it like one.
 */
public final class Services {

    public static final String PATH = "data/config/landlordsector/services.json";
    /** The screening service, older saves' {@code Property.screeningService}. */
    public static final String SCREENING = "screening";

    /** One service. */
    public static final class Def {
        public String id, name;
        /** Monthly price: this share of the Normal rent, at least min. */
        public float share, min;
        public int minTier = 0, maxTier = 99;
        /** null: anywhere, true: stations only, false: planets only. */
        public Boolean station;
        public boolean screening, cleans;
        /** Condition added every month. */
        public float repairs;
        public final HouseTraits.Def effects = new HouseTraits.Def();
    }

    private static Map<String, Def> defs = Collections.emptyMap();

    private Services() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new LinkedHashMap<>();
        JSONArray arr = json.optJSONArray("services");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = o.optString("id");
                d.name = o.optString("name", d.id);
                d.share = (float) o.optDouble("share", 0.1);
                d.min = (float) o.optDouble("min", 0);
                d.minTier = o.optInt("minTier", 0);
                d.maxTier = o.optInt("maxTier", 99);
                d.station = o.has("station") && !o.isNull("station") ? o.optBoolean("station") : null;
                d.screening = o.optBoolean("screening", false);
                d.cleans = o.optBoolean("cleans", false);
                d.repairs = (float) o.optDouble("repairs", 0);
                d.effects.id = d.id;
                d.effects.name = d.name;
                d.effects.good = true;
                HouseTraits.effects(o, d.effects);
                // A service never changes what the home is worth.
                d.effects.price = 1f;
                map.put(d.id, d);
            }
        }
        defs = map;
    }

    public static Def get(String id) {
        return id == null ? null : defs.get(id);
    }

    public static HouseTraits.Def effects(String id) {
        Def d = get(id);
        return d == null ? null : d.effects;
    }

    public static Collection<Def> all() {
        return defs.values();
    }

    /** The monthly price on a home whose Normal rent (after the tariff) is {@code normalRent}. */
    public static float monthly(Def d, float normalRent) {
        return Math.max(d.min, d.share * Math.max(0f, normalRent));
    }

    public static boolean fits(Def d, int tier, boolean station) {
        if (tier < d.minTier || tier > d.maxTier) return false;
        return d.station == null || d.station == station;
    }

    public static int size() {
        return defs.size();
    }
}
