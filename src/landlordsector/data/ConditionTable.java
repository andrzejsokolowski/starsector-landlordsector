package landlordsector.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/**
 * What each market condition does to homes, from data/config/landlordsector/conditions.json. A condition
 * from a mod that is not installed never appears on a market, so its entry is simply never used.
 */
public final class ConditionTable {

    public static final String PATH = "data/config/landlordsector/conditions.json";

    /** One condition's effect. */
    public static final class Effect {
        public final String id;
        public final float price, comfort, happiness, security;

        Effect(String id, float price, float comfort, float happiness, float security) {
            this.id = id;
            this.price = price;
            this.comfort = comfort;
            this.happiness = happiness;
            this.security = security;
        }
    }

    private static Map<String, Effect> effects = Collections.emptyMap();

    private ConditionTable() {
    }

    public static void load(JSONObject json) {
        Map<String, Effect> map = new HashMap<>();
        JSONObject c = json.optJSONObject("conditions");
        if (c != null) {
            Iterator<?> keys = c.keys();
            while (keys.hasNext()) {
                String id = String.valueOf(keys.next());
                JSONObject e = c.optJSONObject(id);
                if (e == null) continue;
                map.put(id, new Effect(id, (float) e.optDouble("price", 1), (float) e.optDouble("comfort", 0),
                        (float) e.optDouble("happiness", 0), (float) e.optDouble("security", 0)));
            }
        }
        effects = map;
    }

    public static Effect get(String conditionId) {
        return effects.get(conditionId);
    }

    /** The effects that apply to a market with these condition ids. */
    public static List<Effect> matching(Iterable<String> conditionIds) {
        List<Effect> out = new ArrayList<>();
        for (String id : conditionIds) {
            Effect e = effects.get(id);
            if (e != null) out.add(e);
        }
        return out;
    }

    public static int size() {
        return effects.size();
    }
}
