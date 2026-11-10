package landlordsector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/** Exterior pictures by tier and climate, from data/config/landlordsector/art.json. */
public final class Art {

    public static final String PATH = "data/config/landlordsector/art.json";
    private static final Logger log = Global.getLogger(Art.class);

    /** tier id -> climate id (or "default") -> picture paths. */
    private static final Map<String, Map<String, List<String>>> exteriors = new HashMap<>();
    private static final Set<String> loaded = new HashSet<>();

    private Art() {
    }

    public static void load(JSONObject json) {
        exteriors.clear();
        JSONObject ext = json.optJSONObject("exteriors");
        if (ext == null) return;
        Iterator<?> tiers = ext.keys();
        while (tiers.hasNext()) {
            String tierId = String.valueOf(tiers.next());
            JSONObject byClimate = ext.optJSONObject(tierId);
            if (byClimate == null) continue;
            Map<String, List<String>> map = new HashMap<>();
            Iterator<?> climates = byClimate.keys();
            while (climates.hasNext()) {
                String climate = String.valueOf(climates.next());
                JSONArray arr = byClimate.optJSONArray(climate);
                List<String> paths = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String p = arr.optString(i, "");
                        if (!p.isEmpty() && loadTexture(p)) paths.add(p);
                    }
                }
                map.put(climate, paths);
            }
            exteriors.put(tierId, map);
        }
    }

    private static boolean loadTexture(String path) {
        if (loaded.contains(path)) return true;
        try {
            Global.getSettings().loadTexture(path);
            loaded.add(path);
            return true;
        } catch (Exception e) {
            log.warn("Landlordsector: cannot load picture " + path + ", it is skipped", e);
            return false;
        }
    }

    /** A picture for this tier and climate, or null when there is none at all. */
    public static String pick(String tierId, Climate climate, Random random) {
        Map<String, List<String>> map = exteriors.get(tierId);
        if (map == null) return null;
        List<String> list = map.get(climate.id);
        if (list == null || list.isEmpty()) list = map.get("default");
        if (list == null || list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    /** True when the picture is still listed and loaded (art.json can change between versions). */
    public static boolean isKnown(String path) {
        return path != null && loaded.contains(path);
    }
}
