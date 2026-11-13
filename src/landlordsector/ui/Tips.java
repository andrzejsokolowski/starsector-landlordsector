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
        // Home levels first (0.4.0): "Heat -1", green when it helps.
        for (java.util.Map.Entry<String, Float> e : d.levels.entrySet()) {
            landlordsector.data.Levels.Def ld = landlordsector.data.Levels.get(e.getKey());
            if (ld == null || e.getValue() == 0f) continue;
            add(out, landlordsector.data.Levels.change(ld, e.getValue()),
                    landlordsector.data.Levels.helps(ld, e.getValue()) ? "+" : "-");
        }
        if (d.comfort != 0f) add(out, signed(d.comfort) + " comfort", d.comfort > 0 ? "+" : "-");
        if (d.decor != 0f) add(out, signed(d.decor) + " decor", d.decor > 0 ? "+" : "-");
        if (d.happiness != 0f) add(out, signed(d.happiness) + " tenant happiness", d.happiness > 0 ? "+" : "-");
        if (d.health != 0f) add(out, signed(d.health) + " tenant health", d.health > 0 ? "+" : "-");
        if (d.kidsHappiness() != 0f) {
            add(out, signed(d.kidsHappiness()) + " happiness for tenants with kids", d.kidsHappiness() > 0 ? "+" : "-");
        }
        if (d.kidsHealth() != 0f) {
            add(out, signed(d.kidsHealth()) + " health for tenants with kids", d.kidsHealth() > 0 ? "+" : "-");
        }
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
        for (java.util.Map.Entry<String, Float> e : d.cancels.entrySet()) {
            add(out, "No " + conditionName(e.getKey()).toLowerCase() + " in this home (hazard -"
                    + Math.round(e.getValue() * 100f) + "%)", "+");
        }
        for (String[] line : eventKinds(d.events)) out.add(line);
        List<Boolean> likeGood = new ArrayList<>(), hateGood = new ArrayList<>();
        for (String n : likes) likeGood.add(TenantTraits.byName(n));
        for (String n : hates) hateGood.add(TenantTraits.byName(n));
        if (!likes.isEmpty()) out.add(named("Liked by", "+", likes, likeGood, " (+" + Math.round(TenantRules.LIKE) + ")"));
        if (!hates.isEmpty()) out.add(named("Hated by", "-", hates, hateGood, " (-" + Math.round(TenantRules.DISLIKE) + ")"));
        if (out.isEmpty()) add(out, "No effect on its own.", null);
        return out;
    }

    /**
     * "Likes homes that are Tranquil, Smoggy (+8)": the plain line with only the verb and each trait name
     * colored, each name by its own good or bad, so a tenant who likes a bad trait shows a green verb and a red
     * name (Andrzej, 0.3.6).
     */
    static String[] named(String verb, String verbColor, List<String> names, List<Boolean> good, String tail) {
        String text = verb + (verb.endsWith("by") ? ": " : " homes that are ") + String.join(", ", names) + tail;
        String[] line = new String[4 + names.size() * 2];
        line[0] = text;
        line[1] = null;
        line[2] = verb;
        line[3] = verbColor;
        for (int i = 0; i < names.size(); i++) {
            Boolean g = good.get(i);
            line[4 + i * 2] = names.get(i);
            line[5 + i * 2] = g == null ? null : (g ? "+" : "-");
        }
        return line;
    }

    private static String conditionName(String id) {
        try {
            String n = com.fs.starfarer.api.Global.getSettings().getMarketConditionSpec(id).getName();
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignored) {
            // Not in this game, or no game (checks).
        }
        return id.replace('_', ' ');
    }

    /** What an upgrade does (0.4.0): its effects, then where it goes and what it costs. */
    public static List<String[]> upgrade(landlordsector.data.Upgrades.Def d) {
        List<String[]> out = houseTrait(d.effects);
        if (out.size() == 1 && "No effect on its own.".equals(out.get(0)[0]) && !d.fixes.isEmpty()) out.clear();
        for (String f : d.fixes) {
            HouseTraits.Def t = HouseTraits.get(f);
            if (t != null) out.add(new String[] {"Removes " + t.name + " for good", null, t.name, t.good ? "+" : "-"});
        }
        if (d.furniture()) {
            add(out, "Furniture: uses one furniture slot. You can take it back at any time.", null);
        } else {
            String what = d.oneTime ? "A one-time job: it takes no renovation slot." : "Renovation: uses one renovation"
                    + " slot and stays with the home.";
            add(out, what + " Costs " + Math.round(d.cost * 100f) + "% of the home's value (at least "
                    + com.fs.starfarer.api.util.Misc.getDGSCredits(d.minCost) + ") and takes " + Math.round(d.days)
                    + " days.", null);
        }
        if (d.minTier > 0 || d.station != null || !d.needs.isEmpty()) {
            StringBuilder b = new StringBuilder("Only for ");
            List<String> parts = new ArrayList<>();
            if (d.minTier > 0) {
                parts.add(landlordsector.TierTable.all().get(Math.min(d.minTier,
                        landlordsector.TierTable.all().size() - 1)).name.toLowerCase() + "s and bigger");
            }
            if (Boolean.FALSE.equals(d.station)) parts.add("planets");
            if (Boolean.TRUE.equals(d.station)) parts.add("stations");
            for (String n : d.needs) {
                HouseTraits.Def t = HouseTraits.get(n);
                if (t != null) parts.add(t.name + " homes");
            }
            b.append(String.join(", ", parts)).append(".");
            add(out, b.toString(), null);
        }
        return out;
    }

    /** "Likes homes with Aquarium, Pool (+8)": the verb in its color, every upgrade name green (0.4.0). */
    static String[] withUpgrades(String verb, String verbColor, List<String> names, String tail) {
        String[] line = new String[4 + names.size() * 2];
        line[0] = verb + " homes with " + String.join(", ", names) + tail;
        line[1] = null;
        line[2] = verb;
        line[3] = verbColor;
        for (int i = 0; i < names.size(); i++) {
            line[4 + i * 2] = names.get(i);
            line[5 + i * 2] = "+";
        }
        return line;
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
        if (d.homeShift > 0) add(out, "Wants a home " + d.homeShift + " size bigger than they can comfortably pay for", null);
        if (d.homeShift < 0) add(out, "Happy with a home " + (-d.homeShift) + " size smaller than they can pay for", null);
        if (d.criminal) add(out, "Security makes them unhappy instead of happy", "-");
        for (java.util.Map.Entry<String, Float> e : d.levels.entrySet()) {
            landlordsector.data.Levels.Def ld = landlordsector.data.Levels.get(e.getKey());
            if (ld == null) continue;
            float f = e.getValue();
            String text = f <= 0.001f ? "Not bothered by " + ld.name.toLowerCase()
                    : (f > 1f ? ld.name + " bothers them " + round2(f) + " times as much"
                            : ld.name + " bothers them less (x" + round2(f) + ")");
            add(out, text, f > 1f ? "-" : "+");
        }
        if (d.healthCost != 1f) {
            add(out, d.healthCost < 1f ? "Loses health slower (x" + round2(d.healthCost) + ")"
                    : "Loses health faster (x" + round2(d.healthCost) + ")", d.healthCost < 1f ? "+" : "-");
        }
        if (d.kids) add(out, "Has kids: upgrades for kids count for them", null);
        for (String[] line : eventKinds(d.events)) out.add(line);
        List<String> likes = new ArrayList<>();
        List<String> hates = new ArrayList<>();
        for (HouseTraits.Def h : HouseTraits.all()) {
            for (String tag : h.tags) {
                if (d.likes.contains(tag) && !likes.contains(h.name)) likes.add(h.name);
                if (d.dislikes.contains(tag) && !hates.contains(h.name)) hates.add(h.name);
            }
        }
        // Upgrades they like or hate (0.4.0): "Likes homes with: Aquarium, Pool".
        List<String> likesUp = new ArrayList<>(), hatesUp = new ArrayList<>();
        for (landlordsector.data.Upgrades.Def u : landlordsector.data.Upgrades.all()) {
            for (String tag : u.effects.tags) {
                if (d.likes.contains(tag) && !likesUp.contains(u.name)) likesUp.add(u.name);
                if (d.dislikes.contains(tag) && !hatesUp.contains(u.name)) hatesUp.add(u.name);
            }
        }
        if (!likesUp.isEmpty()) out.add(withUpgrades("Likes", "+", likesUp, " (+" + Math.round(TenantRules.LIKE) + ")"));
        if (!hatesUp.isEmpty()) out.add(withUpgrades("Hates", "-", hatesUp, " (-" + Math.round(TenantRules.DISLIKE) + ")"));
        List<Boolean> likeGood = new ArrayList<>(), hateGood = new ArrayList<>();
        for (String n : likes) likeGood.add(HouseTraits.byName(n));
        for (String n : hates) hateGood.add(HouseTraits.byName(n));
        if (!likes.isEmpty()) out.add(named("Likes", "+", likes, likeGood, " (+" + Math.round(TenantRules.LIKE) + ")"));
        if (!hates.isEmpty()) out.add(named("Hates", "-", hates, hateGood, " (-" + Math.round(TenantRules.DISLIKE) + ")"));
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
        return wealth(wealth, 0);
    }

    public static String[] wealth(int wealth, int shift) {
        String taste = shift > 0 ? " They want a bigger home than that." : (shift < 0
                ? " They are happy with a smaller home than that." : "");
        return new String[] {
            "This tenant can afford " + aTier(wealth) + "." + taste,
            "A home one tier above that is hard for them to pay for: -" + Math.round(TenantRules.STRETCH)
                    + " happiness. Two or more tiers above, they can't afford it at all.",
            "A home two tiers below that is not good enough for them: -" + Math.round(TenantRules.BENEATH)
                    + " happiness. Three or more tiers below, they refuse to live there.",
            "Low rent makes a home easier to afford, high rent harder. Charity makes it cost like a home one"
                    + " tier cheaper, Greedy like one tier more expensive. Low and High count half as much. On"
                    + " Free rent anyone can afford any home, and a rich tenant will take a home one tier further down"
                    + " than usual, but never a shack."};
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

    private static String round2(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }
}
