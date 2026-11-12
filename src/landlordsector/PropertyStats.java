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
        List<String> condNames = new ArrayList<>();
        for (ConditionTable.Effect e : ConditionTable.matching(conds, m.getFactionId())) {
            condComfort += e.comfort;
            condHappy += e.happiness;
            condSecurity += e.security;
            MarketConditionAPI mc = m.getCondition(e.id);
            if (mc != null && mc.getName() != null && condNames.size() < 4) condNames.add(mc.getName());
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
        if (tenant != null) s.happiness = tenantHappiness(s, home, tenant, setting, houseTraits);

        s.marketMult = MarketRules.mult(m);
        s.grade = Pricing.grade(s.marketMult);
        List<String> why = new ArrayList<>();
        why.add("size " + m.getSize());
        why.add("stability " + Math.round(m.getStabilityValue()));
        why.add("hazard " + Math.round(m.getHazardValue() * 100f) + " percent");
        if (pirates) why.add("pirate activity");
        if (pathers) why.add("Pather cells");
        for (String n : condNames) why.add(n.toLowerCase());
        s.gradeReason = String.join(", ", why);

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
            List<String> houseTraits) {
        Set<String> tags = HouseTraits.tags(houseTraits);
        int liked = 0, disliked = 0;
        for (String id : t.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            for (String tag : d.likes) {
                if (!tags.contains(tag)) continue;
                liked++;
                s.notes.add(new Note(d.name + ": likes the " + tagName(tag, houseTraits), true));
            }
            for (String tag : d.dislikes) {
                if (!tags.contains(tag)) continue;
                disliked++;
                s.notes.add(new Note(d.name + ": hates the " + tagName(tag, houseTraits), false));
            }
        }
        int over = TenantRules.overpricedSteps(setting, t.wealth);
        if (over > 0) s.notes.add(new Note("Rent above what they can afford", false));
        if (t.mood >= 5f) s.notes.add(new Note("In a good mood", true));
        if (t.mood <= -5f) s.notes.add(new Note("In a bad mood", false));
        return TenantRules.tenantHappiness(home, t.traitHappiness(), liked, disliked, over, t.mood);
    }

    /** The name of the house trait that carries this tag, lower case ("field of flowers"). */
    private static String tagName(String tag, List<String> houseTraits) {
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null && d.tags.contains(tag)) return d.name.toLowerCase();
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
