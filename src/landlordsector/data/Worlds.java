package landlordsector.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;

/**
 * Worlds with their own housing, from data/config/landlordsector/worlds.json (0.8.0, Andrzej: "Unique planets should
 * matter"): how many homes a world has room for, which sizes it lists, and a price, comfort, happiness and security
 * effect that works like a market condition.
 */
public final class Worlds {

    public static final String PATH = "data/config/landlordsector/worlds.json";

    /** One world. */
    public static final class Def {
        public String id, name, line;
        public float homes = 1f;
        /** The price, comfort, happiness and security part, named like a condition. */
        public ConditionTable.Effect effect;
        /** Tier id to weight multiplier; tiers not named are 1. */
        public final Map<String, Float> tiers = new HashMap<>();

        public float tierMult(String tierId) {
            Float f = tiers.get(tierId);
            return f == null ? 1f : f;
        }
    }

    private static Map<String, Def> defs = Collections.emptyMap();

    private Worlds() {
    }

    public static void load(JSONObject json) {
        Map<String, Def> map = new HashMap<>();
        JSONObject w = json.optJSONObject("worlds");
        if (w != null) {
            Iterator<?> keys = w.keys();
            while (keys.hasNext()) {
                String id = String.valueOf(keys.next());
                JSONObject e = w.optJSONObject(id);
                if (e == null) continue;
                Def d = new Def();
                d.id = id;
                d.name = e.optString("name", id);
                d.line = e.isNull("line") ? null : e.optString("line", null);
                d.homes = (float) Math.max(0.1, e.optDouble("homes", 1));
                d.effect = new ConditionTable.Effect(d.name, (float) e.optDouble("price", 1),
                        (float) e.optDouble("comfort", 0), (float) e.optDouble("happiness", 0),
                        (float) e.optDouble("security", 0));
                JSONObject t = e.optJSONObject("tiers");
                if (t != null) {
                    Iterator<?> tk = t.keys();
                    while (tk.hasNext()) {
                        String tid = String.valueOf(tk.next());
                        d.tiers.put(tid, (float) Math.max(0, t.optDouble(tid, 1)));
                    }
                }
                map.put(id, d);
            }
        }
        defs = map;
    }

    /** The world entry for this market id, or null. */
    public static Def get(String marketId) {
        return marketId == null ? null : defs.get(marketId);
    }

    public static Map<String, Def> all() {
        return Collections.unmodifiableMap(defs);
    }
}
