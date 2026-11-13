package landlordsector.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tenant events from data/config/landlordsector/tenant_events.json. */
public final class TenantEvents {

    public static final String PATH = "data/config/landlordsector/tenant_events.json";

    /** One event. */
    public static final class Def {
        /** Only tenants with one of these traits have this event (the tip: Generous, Andrzej 0.3.8). */
        public java.util.Set<String> requires = java.util.Collections.emptySet();
        public String id;
        /** What the event is about (money, damage, ...). Tooltips name this, never the event itself. */
        public String kind = "";
        public boolean good;
        public float weight = 1f;
        public boolean damage, crime, big, leave;
        public float condition, mood, decor, rep, tip;
        /** This month's rent multiplier, or -1 when the event leaves the rent alone. */
        public float rentMult = -1f;
        public String text = "";
    }

    /** Lines a tenant says, for tenants with one of {@code traits} or from one of {@code factions}, or anyone. */
    public static final class Say {
        public java.util.Set<String> traits = Collections.emptySet();
        public java.util.Set<String> factions = Collections.emptySet();
        public List<String> lines = new ArrayList<>();
    }

    private static List<Def> defs = Collections.emptyList();
    /** Event id to what tenants say about it (0.3.9: big events only). */
    private static java.util.Map<String, List<Say>> messages = new java.util.HashMap<>();
    private static java.util.Map<String, String> kinds = new java.util.HashMap<>();
    private static float minDaysBetween = 5f;
    private static float dailyChance = 0.06f;

    private TenantEvents() {
    }

    public static void load(JSONObject json) {
        java.util.Map<String, List<Say>> msgs = new java.util.HashMap<>();
        JSONObject mo = json.optJSONObject("messages");
        if (mo != null) {
            for (java.util.Iterator<?> it = mo.keys(); it.hasNext();) {
                String id = String.valueOf(it.next());
                JSONArray groups = mo.optJSONArray(id);
                if (groups == null) continue;
                List<Say> list = new ArrayList<>();
                for (int g = 0; g < groups.length(); g++) {
                    JSONObject o = groups.optJSONObject(g);
                    if (o == null) continue;
                    Say say = new Say();
                    say.traits = HouseTraits.strings(o.optJSONArray("traits"));
                    say.factions = HouseTraits.strings(o.optJSONArray("factions"));
                    JSONArray lines = o.optJSONArray("lines");
                    if (lines != null) for (int k = 0; k < lines.length(); k++) say.lines.add(lines.optString(k));
                    if (!say.lines.isEmpty()) list.add(say);
                }
                msgs.put(id, list);
            }
        }
        messages = msgs;
        List<Def> list = new ArrayList<>();
        JSONArray arr = json.optJSONArray("events");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null || e.optString("id", "").isEmpty()) continue;
                Def d = new Def();
                d.id = e.optString("id");
                d.kind = e.optString("kind", "");
                d.good = e.optBoolean("good", false);
                d.weight = (float) e.optDouble("weight", 1);
                d.damage = e.optBoolean("damage", false);
                d.crime = e.optBoolean("crime", false);
                d.big = e.optBoolean("big", false);
                org.json.JSONArray req = e.optJSONArray("requires");
                if (req != null && req.length() > 0) {
                    d.requires = new java.util.HashSet<>();
                    for (int k = 0; k < req.length(); k++) d.requires.add(req.optString(k));
                }
                d.leave = e.optBoolean("leave", false);
                d.condition = (float) e.optDouble("condition", 0);
                d.mood = (float) e.optDouble("mood", 0);
                d.decor = (float) e.optDouble("decor", 0);
                d.rep = (float) e.optDouble("rep", 0);
                d.tip = (float) e.optDouble("tip", 0);
                d.rentMult = e.has("rentMult") && !e.isNull("rentMult") ? (float) e.optDouble("rentMult", 1) : -1f;
                d.text = e.optString("text", "");
                list.add(d);
            }
        }
        defs = list;
        java.util.Map<String, String> k = new java.util.HashMap<>();
        JSONObject ko = json.optJSONObject("kinds");
        if (ko != null) {
            for (java.util.Iterator<?> it = ko.keys(); it.hasNext(); ) {
                String key = String.valueOf(it.next());
                k.put(key, ko.optString(key, key));
            }
        }
        kinds = k;
        minDaysBetween = (float) json.optDouble("minDaysBetween", 5);
        dailyChance = (float) json.optDouble("dailyChance", 0.06);
    }

    public static List<Def> all() {
        return defs;
    }

    /** The words for a kind ("damage to the home"), or the kind's id when it has none. */
    public static String kindName(String kind) {
        String name = kinds.get(kind);
        return name == null ? kind : name;
    }

    public static boolean hasKind(String kind) {
        return kinds.containsKey(kind);
    }

    public static Def get(String id) {
        for (Def d : defs) if (d.id.equals(id)) return d;
        return null;
    }

    public static float minDaysBetween() {
        return minDaysBetween;
    }

    public static float dailyChance() {
        return dailyChance;
    }

    /**
     * What the tenant says about an event, in their own voice (Andrzej, 0.3.9: "instead of informing the player
     * through the log like normal, we can send them a personal message, roleplaying as that person"). Lines for one
     * of their traits come first, then lines for their faction, then lines for anyone. Null when the event has none.
     */
    public static String say(String eventId, java.util.Collection<String> traitIds, String factionId,
            java.util.Random random) {
        List<Say> groups = messages.get(eventId);
        if (groups == null || groups.isEmpty()) return null;
        List<String> pick = null;
        for (Say g : groups) {
            if (g.traits.isEmpty()) continue;
            for (String t : traitIds) if (g.traits.contains(t)) pick = pick == null ? new ArrayList<>(g.lines) : pick;
        }
        if (pick == null) {
            for (Say g : groups) if (!g.factions.isEmpty() && g.factions.contains(factionId)) { pick = g.lines; break; }
        }
        if (pick == null) {
            for (Say g : groups) if (g.traits.isEmpty() && g.factions.isEmpty()) { pick = g.lines; break; }
        }
        if (pick == null || pick.isEmpty()) return null;
        return pick.get(random.nextInt(pick.size()));
    }

    public static boolean hasMessages(String eventId) {
        List<Say> g = messages.get(eventId);
        return g != null && !g.isEmpty();
    }
}
