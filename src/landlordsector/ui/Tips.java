package landlordsector.ui;

import java.util.ArrayList;
import java.util.List;

import landlordsector.RentSetting;
import landlordsector.TenantRules;
import landlordsector.data.FinderDefs;
import landlordsector.data.HouseTraits;
import landlordsector.data.TenantTraits;

/** Tooltip lines: what a trait, a stat or a headhunter does. {text, "+"} green, {text, "-"} red, {text, null} plain. */
public final class Tips {

    private Tips() {
    }

    private static void add(List<String[]> out, String text, String color) {
        out.add(new String[] {text, color});
    }

    private static String signed(float v) {
        return (v > 0 ? "+" : "") + Math.round(v);
    }

    /** What a house trait does, and which tenant traits like or hate it. */
    public static List<String[]> houseTrait(HouseTraits.Def d) {
        List<String[]> out = new ArrayList<>();
        if (d.comfort != 0f) add(out, signed(d.comfort) + " comfort", d.comfort > 0 ? "+" : "-");
        if (d.happiness != 0f) add(out, signed(d.happiness) + " tenant happiness", d.happiness > 0 ? "+" : "-");
        if (d.security != 0f) add(out, signed(d.security) + " security", d.security > 0 ? "+" : "-");
        if (d.price != 1f) add(out, "Price x" + round2(d.price), d.price > 1f ? "+" : "-");
        if (d.wear != 1f) {
            int pct = Math.round(Math.abs(d.wear - 1f) * 100f);
            add(out, "Wears out " + pct + "% " + (d.wear < 1f ? "slower" : "faster"), d.wear < 1f ? "+" : "-");
        }
        List<String> likes = new ArrayList<>();
        List<String> hates = new ArrayList<>();
        for (TenantTraits.Def t : TenantTraits.all()) {
            for (String tag : d.tags) {
                if (t.likes.contains(tag) && !likes.contains(t.name)) likes.add(t.name);
                if (t.dislikes.contains(tag) && !hates.contains(t.name)) hates.add(t.name);
            }
        }
        if (!likes.isEmpty()) add(out, "Liked by: " + String.join(", ", likes) + " (+" + Math.round(TenantRules.LIKE) + ")", "+");
        if (!hates.isEmpty()) add(out, "Hated by: " + String.join(", ", hates) + " (-" + Math.round(TenantRules.DISLIKE) + ")", "-");
        if (out.isEmpty()) add(out, "No effect on its own.", null);
        return out;
    }

    /** What a tenant trait does. */
    public static List<String[]> tenantTrait(TenantTraits.Def d) {
        List<String[]> out = new ArrayList<>();
        if (d.happiness != 0f) add(out, signed(d.happiness) + " happiness", d.happiness > 0 ? "+" : "-");
        if (d.wealth != 0) add(out, "Wealth " + signed(d.wealth), d.wealth > 0 ? "+" : "-");
        if (d.wear != 1f) {
            int pct = Math.round(Math.abs(d.wear - 1f) * 100f);
            add(out, "Wears a home " + pct + "% " + (d.wear < 1f ? "slower" : "faster"), d.wear < 1f ? "+" : "-");
        }
        if (d.loyal) add(out, "Never moves out on their own", "+");
        for (String[] line : eventKinds(d.events)) out.add(line);
        List<String> likes = new ArrayList<>();
        List<String> hates = new ArrayList<>();
        for (HouseTraits.Def h : HouseTraits.all()) {
            for (String tag : h.tags) {
                if (d.likes.contains(tag) && !likes.contains(h.name)) likes.add(h.name);
                if (d.dislikes.contains(tag) && !hates.contains(h.name)) hates.add(h.name);
            }
        }
        if (!likes.isEmpty()) add(out, "Likes homes that are " + String.join(", ", likes) + " (+" + Math.round(TenantRules.LIKE) + ")", "+");
        if (!hates.isEmpty()) add(out, "Hates homes that are " + String.join(", ", hates) + " (-" + Math.round(TenantRules.DISLIKE) + ")", "-");
        if (out.isEmpty()) add(out, "No effect on its own.", null);
        return out;
    }

    /**
     * Event multipliers told by kind, never by event (Andrzej, 2026-11-12: no spoilers): "More likely to have
     * good house events about money". Events of one kind and side are averaged.
     */
    static List<String[]> eventKinds(java.util.Map<String, Float> events) {
        java.util.Map<String, float[]> sums = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Float> e : events.entrySet()) {
            landlordsector.data.TenantEvents.Def ev = landlordsector.data.TenantEvents.get(e.getKey());
            if (ev == null) continue;
            String key = (ev.good ? "good|" : "bad|") + ev.kind;
            float[] s = sums.computeIfAbsent(key, k -> new float[2]);
            s[0] += (float) Math.log(Math.max(0.01f, e.getValue()));
            s[1]++;
        }
        List<String[]> out = new ArrayList<>();
        for (java.util.Map.Entry<String, float[]> e : sums.entrySet()) {
            float mult = (float) Math.exp(e.getValue()[0] / e.getValue()[1]);
            if (Math.abs(mult - 1f) < 0.05f) continue;
            boolean good = e.getKey().startsWith("good|");
            String kind = landlordsector.data.TenantEvents.kindName(e.getKey().substring(e.getKey().indexOf('|') + 1));
            boolean more = mult > 1f;
            String text = (more ? "More" : "Less") + " likely to have " + (good ? "good" : "bad")
                    + " house events about " + kind;
            add(out, text, more == good ? "+" : "-");
        }
        return out;
    }

    public static List<String[]> hidden() {
        List<String[]> out = new ArrayList<>();
        add(out, "Hidden. Screen the tenant to see it.", null);
        return out;
    }

    /** The tier name a tenant of this wealth fits, lower case ("small house"). */
    public static String fitTierName(int wealth) {
        java.util.List<landlordsector.Tier> tiers = landlordsector.TierTable.all();
        int i = Math.min(tiers.size() - 1, TenantRules.fitTier(wealth));
        return tiers.get(i).name.toLowerCase();
    }

    /** "a shack", "an estate". */
    public static String aTier(int wealth) {
        String name = fitTierName(wealth);
        return ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
    }

    public static String[] wealth(int wealth) {
        return new String[] {
            "Wealth " + TenantRules.wealthLetter(wealth) + ": this tenant can afford " + aTier(wealth) + ".",
            "A home one tier above that is hard for them to pay for: -" + Math.round(TenantRules.STRETCH)
                    + " happiness. Two or more tiers above, they can't afford it at all.",
            "A home two tiers below that is not good enough for them: -" + Math.round(TenantRules.BENEATH)
                    + " happiness. Three or more tiers below, they refuse to live there.",
            "Low rent makes a home easier to afford, high rent harder. Charity makes it cost like a home one"
                    + " tier cheaper, Greedy like one tier more expensive. Low and High count half as much."};
    }

    public static List<String[]> headhunter(FinderDefs.Def d) {
        List<String[]> out = new ArrayList<>();
        add(out, d.perk, null);
        add(out, "Each search: " + d.tenants + " screened tenants in " + Math.round(d.days) + " days.", null);
        if (d.wealthShift != 0) add(out, "Tenant wealth " + signed(d.wealthShift), d.wealthShift > 0 ? "+" : "-");
        if (d.badTraits < 1f) add(out, "Fewer bad traits (x" + round2(d.badTraits) + ")", "+");
        if (d.badTraits > 1f) add(out, "More bad traits (x" + round2(d.badTraits) + ")", "-");
        for (java.util.Map.Entry<String, Float> e : d.traitBoost.entrySet()) {
            TenantTraits.Def t = TenantTraits.get(e.getKey());
            if (t != null) add(out, t.name + " " + round2(e.getValue()) + " times as likely", null);
        }
        return out;
    }

    public static final String[] COMFORT = {"How pleasant the home is to live in: its size, the planet's hazard, a breathable"
            + " world and a mild climate, pollution, station life, market conditions and traits. Worn homes lose some."};
    public static final String[] DECOR = {"Furnishing. Raises the value (up to a quarter) and the reputation rent earns or costs."};
    public static final String[] HAPPINESS = {"From comfort, decor, condition, the rent setting and the tenant's traits."
            + " Happy tenants wear the home slower, tip more and cause less trouble. Below 15 they may move out."};
    public static final String[] SECURITY = {"From stability, military buildings, ground defenses and market conditions."
            + " Cuts burglaries, police raids and raid damage."};


    private static String round2(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }
}
