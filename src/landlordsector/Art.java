package landlordsector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/** Exterior pictures by tier and planet type ({@link PlanetType}), from data/config/landlordsector/art.json. */
public final class Art {

    public static final String PATH = "data/config/landlordsector/art.json";
    private static final Logger log = Global.getLogger(Art.class);

    /** How many exterior pictures stay in graphics memory at once; older ones are unloaded (0.7.0). */
    static final int MAX_LOADED_EXTERIORS = 12;

    /** tier id -> climate id (or "default") -> picture paths. */
    private static final Map<String, Map<String, List<String>>> exteriors = new HashMap<>();
    /**
     * 1.1.0 (Andrzej, about the third "wild" pictures: "since they are more whacky, can we make them rarer?"):
     * tier id -> planet type id -> rare pictures, picked instead of the usual ones only {@link #rareChance} of the time.
     */
    private static final Map<String, Map<String, List<String>>> rare = new HashMap<>();
    /** Chance that a new home shows its rare picture, from art.json "rareChance" (1.1.0). */
    static float rareChance = 0.15f;
    /** Pictures that stay loaded for the whole session (furniture icons). */
    private static final Set<String> loaded = new HashSet<>();
    /**
     * Every exterior picture listed in art.json; loaded only when a screen draws it. The release build checks that
     * every listed file exists (mod code may not touch files), and one that fails to load is dropped here.
     */
    private static final Set<String> listed = new HashSet<>();
    /** Exterior pictures in graphics memory now, least recently drawn first. */
    private static final LinkedHashMap<String, Long> inMemory = new LinkedHashMap<>(16, 0.75f, true);

    private Art() {
    }

    public static void load(JSONObject json) {
        exteriors.clear();
        rare.clear();
        listed.clear();
        readLists(json.optJSONObject("exteriors"), exteriors);
        readLists(json.optJSONObject("rare"), rare);
        rareChance = (float) Math.max(0d, Math.min(1d, json.optDouble("rareChance", 0.15d)));
    }

    /** One section of art.json: tier -> planet type (or "default") -> picture paths. */
    private static void readLists(JSONObject ext, Map<String, Map<String, List<String>>> into) {
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
                        if (!p.isEmpty()) {
                            paths.add(p);
                            listed.add(p);
                        }
                    }
                }
                map.put(climate, paths);
            }
            into.put(tierId, map);
        }
    }

    /** The rare pictures of this tier and planet type, or an empty list (1.1.0). */
    static List<String> rareFor(String tierId, PlanetType type) {
        Map<String, List<String>> map = rare.get(tierId);
        List<String> list = map == null ? null : map.get(type.id);
        return list == null ? java.util.Collections.<String>emptyList() : list;
    }

    /** Loads every furniture icon, so the Furniture tab can draw them (0.5.0). */
    /** 1.5.0 home condition icons (LLS-16), 40x40, made by tools/art-maker/icons.py. */
    public static final String ICONS = "graphics/landlordsector/icons/";

    /** Pictures the mod asked the game to load once (icons, the game's own condition and building pictures). */
    private static final Set<String> ensured = new HashSet<>();

    /** Loads a small picture the first time a screen wants it, and keeps it (icons are tiny). */
    public static void ensure(String path) {
        if (path == null || ensured.contains(path)) return;
        ensured.add(path);
        loadTexture(path);
    }

    public static void loadFurnitureIcons() {
        for (landlordsector.data.Upgrades.Def d : landlordsector.data.Upgrades.of(
                landlordsector.data.Upgrades.Kind.FURNITURE)) {
            try {
                String icon = Global.getSettings().getSpecialItemSpec(d.itemId()).getIconName();
                if (icon != null && !icon.isEmpty()) loadTexture(icon);
            } catch (Throwable t) {
                log.warn("Landlordsector: no icon for " + d.itemId(), t);
            }
        }
    }

    /**
     * Makes sure an exterior picture is in graphics memory before it is drawn, and unloads the least recently
     * drawn ones beyond {@link #MAX_LOADED_EXTERIORS}, so 100+ pictures never sit in memory at once (0.7.0).
     * Other paths (furniture icons, vanilla sprites) are left alone.
     */
    public static void use(String path) {
        if (path == null || !listed.contains(path)) return;
        long now = System.currentTimeMillis();
        if (inMemory.containsKey(path)) {
            inMemory.put(path, now); // marks it as just drawn
            return;
        }
        try {
            Global.getSettings().loadTexture(path);
        } catch (Exception e) {
            log.warn("Landlordsector: cannot load picture " + path, e);
            listed.remove(path);
            return;
        }
        inMemory.put(path, now);
        while (inMemory.size() > MAX_LOADED_EXTERIORS) {
            Iterator<Map.Entry<String, Long>> it = inMemory.entrySet().iterator();
            Map.Entry<String, Long> eldest = it.next();
            // Never unload a picture drawn in the last second: a screen showing many at once keeps them all.
            if (now - eldest.getValue() < 1000L) break;
            String oldest = eldest.getKey();
            it.remove();
            try {
                Global.getSettings().unloadTexture(oldest);
            } catch (Throwable t) {
                log.warn("Landlordsector: cannot unload picture " + oldest, t);
            }
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

    /** A picture for this tier and planet type, or the tier's default; null when there is none at all. */
    public static String pick(String tierId, PlanetType type, Random random) {
        List<String> rares = rareFor(tierId, type);
        if (!rares.isEmpty() && random.nextFloat() < rareChance) return rares.get(random.nextInt(rares.size()));
        Map<String, List<String>> map = exteriors.get(tierId);
        if (map == null) return null;
        List<String> list = map.get(type.id);
        if (list == null || list.isEmpty()) list = map.get("default");
        if (list == null || list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    /**
     * 1.0.1 (Andrzej: "alternate between sprites on a property list always ... when there are 2 shacks ... at least
     * one of them is going to use the alt sprite"): the picture used least on this list, a random one of those.
     *
     * 1.1.0: a rare picture first, {@link #rareChance} of the time, unless one is already on the list.
     *
     * @param onList pictures of the homes already on the list
     */
    public static String pick(String tierId, PlanetType type, Random random, java.util.Collection<String> onList) {
        List<String> rares = new ArrayList<>(rareFor(tierId, type));
        rares.removeAll(onList);
        if (!rares.isEmpty() && random.nextFloat() < rareChance) return rares.get(random.nextInt(rares.size()));
        Map<String, List<String>> map = exteriors.get(tierId);
        if (map == null) return null;
        List<String> list = map.get(type.id);
        if (list == null || list.isEmpty()) list = map.get("default");
        if (list == null || list.isEmpty()) return null;
        int fewest = Integer.MAX_VALUE;
        List<String> best = new java.util.ArrayList<>();
        for (String path : list) {
            int n = 0;
            for (String used : onList) if (path.equals(used)) n++;
            if (n < fewest) {
                fewest = n;
                best.clear();
            }
            if (n == fewest) best.add(path);
        }
        return best.get(random.nextInt(best.size()));
    }

    /** True when this tier has a picture drawn for this planet type (not only the default one). */
    public static boolean hasOwn(String tierId, PlanetType type) {
        Map<String, List<String>> map = exteriors.get(tierId);
        List<String> list = map == null ? null : map.get(type.id);
        return list != null && !list.isEmpty();
    }

    /** True when the path is one of the tier's default (placeholder) pictures. */
    public static boolean isDefault(String tierId, String path) {
        Map<String, List<String>> map = exteriors.get(tierId);
        List<String> list = map == null ? null : map.get("default");
        return list != null && list.contains(path);
    }

    /** True when the picture is still listed in art.json and its file exists (art.json can change between versions). */
    public static boolean isKnown(String path) {
        return path != null && (listed.contains(path) || loaded.contains(path));
    }
}
