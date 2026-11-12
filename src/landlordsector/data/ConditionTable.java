package landlordsector.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What each market condition does to homes, from data/config/landlordsector/conditions.json. A condition
 * from a mod that is not installed never appears on a market, so its entry is simply never used.
 *
 * <p>Some conditions only work for one owner (FSF's Military Zone, the Imperium's doctrine). Their entry
 * names the owners ("owners": exact faction ids, "ownersContain": parts of ids) and can say what happens
 * under anyone else ("others"). Without "others" the condition does nothing for other owners.
 */
public final class ConditionTable {

    public static final String PATH = "data/config/landlordsector/conditions.json";

    /** One condition's effect. */
    public static final class Effect {
        public final String id;
        public final float price, comfort, happiness, security;
        List<String> owners = Collections.emptyList();
        List<String> ownersContain = Collections.emptyList();
        Effect others;

        Effect(String id, float price, float comfort, float happiness, float security) {
            this.id = id;
            this.price = price;
            this.comfort = comfort;
            this.happiness = happiness;
            this.security = security;
        }

        /** True when this condition's effect depends on who owns the market. */
        public boolean ownerOnly() {
            return !owners.isEmpty() || !ownersContain.isEmpty();
        }

        boolean ownedBy(String factionId) {
            if (factionId == null) return false;
            if (owners.contains(factionId)) return true;
            for (String part : ownersContain) if (factionId.contains(part)) return true;
            return false;
        }

        /** The effect under this owner, or null when there is none. */
        public Effect forOwner(String factionId) {
            if (!ownerOnly() || ownedBy(factionId)) return this;
            return others;
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
                Effect eff = parse(id, e);
                eff.owners = strings(e.optJSONArray("owners"));
                eff.ownersContain = strings(e.optJSONArray("ownersContain"));
                JSONObject o = e.optJSONObject("others");
                if (o != null) eff.others = parse(id, o);
                map.put(id, eff);
            }
        }
        effects = map;
    }

    private static Effect parse(String id, JSONObject e) {
        return new Effect(id, (float) e.optDouble("price", 1), (float) e.optDouble("comfort", 0),
                (float) e.optDouble("happiness", 0), (float) e.optDouble("security", 0));
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

    /** The entry as written, before the owner is taken into account. */
    public static Effect get(String conditionId) {
        return effects.get(conditionId);
    }

    /** The effects that apply to a market with these conditions and this owner. */
    public static List<Effect> matching(Set<String> conditionIds, String ownerFactionId) {
        List<Effect> out = new ArrayList<>();
        for (String id : conditionIds) {
            Effect e = effects.get(id);
            if (e == null) continue;
            Effect f = e.forOwner(ownerFactionId);
            if (f != null) out.add(f);
        }
        return out;
    }

    public static int size() {
        return effects.size();
    }
}
