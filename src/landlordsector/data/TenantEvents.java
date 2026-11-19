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
        /** 0.4.0: only for sick tenants, more often the sicker they are. */
        public boolean sickOnly;
        /** 0.4.0: the weight at health 0, rising from 1 at the "sickBelow" health. */
        public float sickMult = 1f;
        public String text = "";

        // ---- 0.5.0 ----

        /** A tenant event (their own life) rather than a house event. */
        public boolean tenant;
        /** A health event: bad ones grow as health falls, good ones shrink. */
        public boolean health;
        /** Only below this health (0: any). */
        public float maxHealth;
        /** The tenant dies. */
        public boolean dies;
        /** House traits and tenant traits it adds or removes. */
        public String addTrait, removeTrait, addTenantTrait, removeTenantTrait;
        /** Added to the tenant's wealth. */
        public int wealth;

        // ---- 1.12.0 (LLS-32, Andrzej: "interactive events ... i cant pay rent > evict, or keep") ----

        /** The answers the player picks from; empty for an event that just happens. */
        public List<Def> choices = Collections.emptyList();
        /** The answer taken when the player does not answer in {@link #answerDays} days. */
        public int defaultChoice;
        public float answerDays = 7f;
        /** Never rolled at random: the game asks it itself (the lease end). */
        public boolean system;
        /** An answer's button text. "$cost" and "$price" are filled in. */
        public String label = "";
        /** An answer: months of the home's Normal rent the player pays, or gets ({@link #gain}). */
        public float cost, gain;
        /** An answer: the rent setting moves this many steps (-1: one step lower), lease rent included. */
        public int rentStep;
        /** An answer: the home is sold to someone at this share of its sale value. */
        public float sellMult;
        /** An answer: the lease starts again for the home's lease term. */
        public boolean renew;
        /** 1.13.0: an answer that gives a happy tenant an open-ended lease. */
        public boolean openEnded;
        /** An answer: the tenant is put out for breaking the lease (no fee). */
        public boolean evict;
        /** The tenant broke the lease: evicting them is free for a while (skipped rent, police raid, trashed). */
        public boolean breach;
        /** The tenant manager picks this answer (else the default one). */
        public boolean managerPick;
        /** An answer may go wrong: with {@link #failChance} the fail outcome happens instead. */
        public Def fail;
        public float failChance;
        /** 1.12.1: the fail outcome only while the tenant's happiness is below this (0: always). */
        public float failBelow;
        /** An answer may have an outcome later: after {@link #laterDays} days, with {@link #laterChance}. */
        public Def later;
        public float laterDays, laterChance = 1f;

        /** Only while the rent is at least this setting (a request for lower rent). */
        public String minRent;
        /** Only while a lease runs (a tenant asking to leave early). */
        public boolean lease;

        public boolean isQuestion() {
            return !choices.isEmpty();
        }
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
    private static float tenantMinDays = 7f;
    private static float tenantDailyChance = 0.05f;

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
                Def d = readDef(e);
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
        JSONObject house = json.optJSONObject("house");
        JSONObject tenant = json.optJSONObject("tenant");
        minDaysBetween = (float) (house != null ? house : json).optDouble("minDaysBetween", 5);
        dailyChance = (float) (house != null ? house : json).optDouble("dailyChance", 0.06);
        tenantMinDays = tenant != null ? (float) tenant.optDouble("minDaysBetween", 7) : 7f;
        tenantDailyChance = tenant != null ? (float) tenant.optDouble("dailyChance", 0.05) : 0.05f;
    }

    /** One event, answer or outcome. Answers and outcomes use the same fields as events. */
    private static Def readDef(JSONObject e) {
        Def d = new Def();
        d.id = e.optString("id", "");
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
        d.sickOnly = e.optBoolean("sickOnly", false);
        d.sickMult = (float) e.optDouble("sickMult", 1);
        d.tenant = "tenant".equals(e.optString("type", "house"));
        d.health = e.optBoolean("health", false);
        d.maxHealth = (float) e.optDouble("maxHealth", 0);
        d.dies = e.optBoolean("dies", false);
        d.addTrait = text(e, "addTrait");
        d.removeTrait = text(e, "removeTrait");
        d.addTenantTrait = text(e, "addTenantTrait");
        d.removeTenantTrait = text(e, "removeTenantTrait");
        d.wealth = e.optInt("wealth", 0);
        d.system = e.optBoolean("system", false);
        d.label = e.optString("label", "");
        d.cost = (float) e.optDouble("cost", 0);
        d.gain = (float) e.optDouble("gain", 0);
        d.rentStep = e.optInt("rentStep", 0);
        d.sellMult = (float) e.optDouble("sellMult", 0);
        d.renew = e.optBoolean("renew", false);
        d.openEnded = e.optBoolean("openEnded", false);
        d.evict = e.optBoolean("evict", false);
        d.breach = e.optBoolean("breach", false);
        d.managerPick = e.optBoolean("managerPick", false);
        d.defaultChoice = e.optInt("default", 0);
        d.minRent = text(e, "minRent");
        d.lease = e.optBoolean("lease", false);
        d.answerDays = (float) e.optDouble("answerDays", 7);
        JSONArray ch = e.optJSONArray("choices");
        if (ch != null && ch.length() > 0) {
            d.choices = new ArrayList<>();
            for (int k = 0; k < ch.length(); k++) {
                JSONObject o = ch.optJSONObject(k);
                if (o == null) continue;
                Def c = readDef(o);
                c.id = d.id + "#" + k;
                c.tenant = d.tenant;
                c.kind = d.kind;
                d.choices.add(c);
            }
            d.defaultChoice = Math.max(0, Math.min(d.choices.size() - 1, d.defaultChoice));
        }
        JSONObject f = e.optJSONObject("fail");
        if (f != null) {
            d.fail = readDef(f);
            d.fail.id = d.id + "!fail";
            d.fail.tenant = d.tenant;
            d.failChance = (float) f.optDouble("chance", 0.5);
            d.failBelow = (float) f.optDouble("below", 0);
        }
        JSONObject l = e.optJSONObject("later");
        if (l != null) {
            d.later = readDef(l);
            d.later.id = d.id + "!later";
            d.later.tenant = d.tenant;
            d.laterDays = (float) l.optDouble("days", 30);
            d.laterChance = (float) l.optDouble("chance", 1);
        }
        return d;
    }

    /** A string field, or null when it is missing, empty or a JSON null. */
    private static String text(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, "");
        return v.isEmpty() ? null : v;
    }

    public static float tenantMinDays() {
        return tenantMinDays;
    }

    public static float tenantDailyChance() {
        return tenantDailyChance;
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
        if (id == null) return null;
        for (Def d : defs) if (d.id.equals(id)) return d;
        return null;
    }

    /** One answer of a question, or null. */
    public static Def answer(String eventId, int index) {
        Def d = get(eventId);
        if (d == null || index < 0 || index >= d.choices.size()) return null;
        return d.choices.get(index);
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
