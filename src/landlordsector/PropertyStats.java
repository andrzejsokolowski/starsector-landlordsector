package landlordsector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import landlordsector.data.ConditionTable;
import landlordsector.data.HouseTraits;
import landlordsector.data.TenantTraits;

/** The stat sheet of one home, owned or for sale, worked out from its market right now. */
public final class PropertyStats {

    /** A line explaining part of a tenant's happiness. good: green, false: red, null: plain. */
    public static final class Note {
        public final String text;
        public final Boolean good;

        Note(String text, Boolean good) {
            this.text = text;
            this.good = good;
        }
    }

    public float comfort;
    public float decor;
    /** The tenant's happiness, or the happiness an average tenant would have when there is none. */
    public float happiness;
    public float security;
    public float marketMult;
    /** S+, S, A, B, C, D, E or F. */
    public String grade = "F";
    /** Why the location got its grade, for example "size 6, stability 7, habitable, mild climate". */
    public String gradeReason = "";
    /**
     * What makes up the grade, for its tooltip: {text, "+" / "-" / null}. Every price factor, then every market
     * condition with its effects (Andrzej, 0.3.3: the conditions with details in a tooltip on the grade letter).
     */
    public final List<String[]> gradeLines = new ArrayList<>();
    /** The world, for example "Arid world" or "Station". */
    public String world = "";
    /** Likes, dislikes and rent the tenant cannot afford. Empty without a tenant. */
    public final List<Note> notes = new ArrayList<>();

    private PropertyStats() {
    }

    /** The home with its own tenant (or none). */
    public static PropertyStats of(Property p) {
        return of(p.tier(), p.market(), climate(p.climateId), p.condition, p.setting(), p.traits(), p.decor(),
                p.tenant);
    }

    /** The home as it would be with this tenant moving in. */
    public static PropertyStats withTenant(Property p, Tenant t) {
        return of(p.tier(), p.market(), climate(p.climateId), p.condition, p.setting(), p.traits(), p.decor(), t);
    }

    /** A home for sale: new, at Normal rent, no tenant. */
    public static PropertyStats of(Listing l, MarketAPI market) {
        Tier t = TierTable.get(l.tierId);
        if (t == null) t = TierTable.all().get(0);
        return of(t, market, climate(l.climateId), 1f, RentSetting.NORMAL,
                l.traits == null ? new ArrayList<>() : l.traits, t.decor, null);
    }

    public static PropertyStats of(Tier tier, MarketAPI m, Climate climate, float condition, RentSetting setting,
            List<String> houseTraits, float decor, Tenant tenant) {
        PropertyStats s = new PropertyStats();
        s.decor = Pricing.clamp(decor, 0f, 100f);
        float traitComfort = 0f, traitHappy = 0f, traitSecurity = 0f;
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d == null) continue;
            traitComfort += d.comfort;
            traitHappy += d.happiness;
            traitSecurity += d.security;
        }
        if (m == null) {
            s.comfort = Pricing.clamp(tier.comfort * 0.5f + traitComfort, 0f, 100f);
            s.happiness = Pricing.happiness(s.comfort, s.decor, setting.happiness, condition);
            s.world = "Lost";
            s.gradeReason = "the colony is gone";
            return s;
        }
        Set<String> conds = Tenants.conditions(m);
        float condComfort = 0f, condHappy = 0f, condSecurity = 0f;
        List<String[]> condLines = new ArrayList<>();
        for (ConditionTable.Effect e : ConditionTable.matching(conds, m.getFactionId())) {
            condComfort += e.comfort;
            condHappy += e.happiness;
            condSecurity += e.security;
            MarketConditionAPI mc = m.getCondition(e.id);
            String name = mc != null && mc.getName() != null ? mc.getName() : e.id;
            condLines.add(conditionLine(name, e));
        }

        boolean habitable = conds.contains(Conditions.HABITABLE);
        boolean mild = conds.contains(Conditions.MILD_CLIMATE);
        boolean pollution = conds.contains(Conditions.POLLUTION);
        boolean pirates = conds.contains(Conditions.PIRATE_ACTIVITY);
        boolean pathers = conds.contains(Conditions.PATHER_CELLS);
        s.comfort = Pricing.clamp(Pricing.comfort(tier.comfort, climate, habitable, mild, pollution,
                m.getHazardValue(), condition) + condComfort + traitComfort, 0f, 100f);
        s.security = Pricing.clamp(Pricing.security(m.getStabilityValue(), military(m), defenses(m), pirates, pathers)
                + condSecurity + traitSecurity, 0f, 100f);
        float home = Pricing.happiness(s.comfort, s.decor, setting.happiness, condition) + condHappy + traitHappy;
        s.happiness = Pricing.clamp(home, 0f, 100f);
        if (tenant != null) s.happiness = tenantHappiness(s, home, tenant, setting, houseTraits, tier.index);

        s.marketMult = MarketRules.mult(m);
        s.grade = Pricing.grade(s.marketMult);
        float hazard = m.getHazardValue();
        float bare = Pricing.planetMult(hazard, false, false, false);
        factor(s, "Market size " + m.getSize(), Pricing.sizeMult(m.getSize()));
        factor(s, "Stability " + Math.round(m.getStabilityValue()), Pricing.stabilityMult(m.getStabilityValue()));
        factor(s, "Hazard " + Math.round(hazard * 100f) + " percent", bare);
        if (habitable) factor(s, "Breathable world", Pricing.planetMult(hazard, true, false, false) / bare);
        if (mild) factor(s, "Mild climate", Pricing.planetMult(hazard, false, true, false) / bare);
        if (pollution) factor(s, "Pollution", Pricing.planetMult(hazard, false, false, true) / bare);
        if (pirates) factor(s, "Pirate activity", Pricing.dangerMult(true, false));
        if (pathers) factor(s, "Pather cells", Pricing.dangerMult(false, true));
        s.gradeLines.addAll(condLines);
        String total = "Price x" + two(s.marketMult);
        if (s.marketMult <= Pricing.MULT_MIN + 0.001f || s.marketMult >= Pricing.MULT_MAX - 0.001f) {
            total += " (the limit)";
        }
        s.gradeLines.add(new String[] {total, null});

        PlanetAPI planet = m.getPlanetEntity();
        if (climate == Climate.STATION || planet == null) {
            s.world = "Station";
        } else {
            String name = planet.getTypeNameWithWorld();
            s.world = name == null || name.isEmpty() ? climate.label : name;
        }
        return s;
    }

    private static float tenantHappiness(PropertyStats s, float home, Tenant t, RentSetting setting,
            List<String> houseTraits, int tier) {
        Set<String> tags = HouseTraits.tags(houseTraits);
        int liked = 0, disliked = 0;
        for (String id : t.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            for (String tag : d.likes) {
                if (!tags.contains(tag)) continue;
                liked++;
                s.notes.add(new Note(d.name + ": likes the home being " + tagName(tag, houseTraits) + " (+" + Math.round(TenantRules.LIKE) + ")", true));
            }
            for (String tag : d.dislikes) {
                if (!tags.contains(tag)) continue;
                disliked++;
                s.notes.add(new Note(d.name + ": hates the home being " + tagName(tag, houseTraits) + " (-" + Math.round(TenantRules.DISLIKE) + ")", false));
            }
        }
        float price = TenantRules.pricePenalty(tier, setting, t.wealth);
        float beneath = TenantRules.beneathPenalty(tier, t.wealth);
        String letter = TenantRules.wealthLetter(t.wealth);
        if (price >= TenantRules.CANT_AFFORD) {
            s.notes.add(new Note("Wealth " + letter + " can't pay for this home at " + setting.label + " rent (-"
                    + Math.round(price) + ")", false));
        } else if (price > 0f) {
            s.notes.add(new Note("Wealth " + letter + " finds this home at " + setting.label + " rent a stretch (-"
                    + Math.round(price) + ")", false));
        }
        if (beneath > 0f) {
            s.notes.add(new Note("Wealth " + letter + " thinks this home is beneath them (-" + Math.round(beneath) + ")",
                    false));
        }
        if (t.mood >= 5f) s.notes.add(new Note("In a good mood", true));
        if (t.mood <= -5f) s.notes.add(new Note("In a bad mood", false));
        return TenantRules.tenantHappiness(home, t.traitHappiness(), liked, disliked, price + beneath, t.mood);
    }

    private static void factor(PropertyStats s, String what, float mult) {
        String sign = mult > 1.001f ? "+" : (mult < 0.999f ? "-" : null);
        s.gradeLines.add(new String[] {what + ": price x" + two(mult), sign});
    }

    /** "Rogue AI Core: price x0.8, comfort -5, security -10", green when all good, red when all bad. */
    private static String[] conditionLine(String name, ConditionTable.Effect e) {
        List<String> parts = new ArrayList<>();
        int good = 0, bad = 0;
        if (Math.abs(e.price - 1f) > 0.001f) {
            parts.add("price x" + two(e.price));
            if (e.price > 1f) good++; else bad++;
        }
        float[] v = {e.comfort, e.happiness, e.security};
        String[] n = {"comfort", "happiness", "security"};
        for (int i = 0; i < v.length; i++) {
            if (Math.abs(v[i]) < 0.5f) continue;
            parts.add(n[i] + " " + (v[i] > 0f ? "+" : "") + Math.round(v[i]));
            if (v[i] > 0f) good++; else bad++;
        }
        String sign = good > 0 && bad == 0 ? "+" : (bad > 0 && good == 0 ? "-" : null);
        return new String[] {name + ": " + (parts.isEmpty() ? "no effect" : String.join(", ", parts)), sign};
    }

    private static String two(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }

    /** The name of the house trait that carries this tag, lower case ("field of flowers"). */
    private static String tagName(String tag, List<String> houseTraits) {
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null && d.tags.contains(tag)) return d.name;
        }
        return tag;
    }

    private static float military(MarketAPI m) {
        if (m.hasIndustry(Industries.HIGHCOMMAND)) return 30f;
        if (m.hasIndustry(Industries.MILITARYBASE)) return 20f;
        if (m.hasIndustry(Industries.PATROLHQ)) return 10f;
        return 0f;
    }

    private static float defenses(MarketAPI m) {
        if (m.hasIndustry(Industries.HEAVYBATTERIES)) return 15f;
        if (m.hasIndustry(Industries.GROUNDDEFENSES)) return 10f;
        return 0f;
    }

    static Climate climate(String id) {
        for (Climate c : Climate.values()) if (c.id.equals(id)) return c;
        return Climate.HOSTILE;
    }
}
