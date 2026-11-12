package landlordsector;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

/** The stat sheet of one home, owned or for sale, worked out from its market right now. */
public final class PropertyStats {

    public float comfort;
    public float decor;
    public float happiness;
    public float security;
    public float marketMult;
    public String grade = "F";
    /** Why the location got its grade, for example "size 6, stability 7, habitable, mild climate". */
    public String gradeReason = "";
    /** The world, for example "Arid world" or "Station". */
    public String world = "";

    private PropertyStats() {
    }

    public static PropertyStats of(Property p) {
        return of(p.tier(), p.market(), climate(p.climateId), p.condition, p.setting());
    }

    /** A home for sale: new, at Normal rent. */
    public static PropertyStats of(Listing l, MarketAPI market) {
        Tier t = TierTable.get(l.tierId);
        if (t == null) t = TierTable.all().get(0);
        return of(t, market, climate(l.climateId), 1f, RentSetting.NORMAL);
    }

    public static PropertyStats of(Tier tier, MarketAPI m, Climate climate, float condition, RentSetting setting) {
        PropertyStats s = new PropertyStats();
        s.decor = Pricing.clamp(tier.decor, 0f, 100f);
        if (m == null) {
            s.comfort = Pricing.clamp(tier.comfort * 0.5f, 0f, 100f);
            s.happiness = Pricing.happiness(s.comfort, s.decor, setting.happiness, condition);
            s.world = "Lost";
            s.gradeReason = "the colony is gone";
            return s;
        }
        boolean habitable = m.hasCondition(Conditions.HABITABLE);
        boolean mild = m.hasCondition(Conditions.MILD_CLIMATE);
        boolean pollution = m.hasCondition(Conditions.POLLUTION);
        s.comfort = Pricing.comfort(tier.comfort, climate, habitable, mild, pollution, m.getHazardValue(), condition);
        s.happiness = Pricing.happiness(s.comfort, s.decor, setting.happiness, condition);
        s.security = Pricing.security(m.getStabilityValue(), military(m), defenses(m));
        s.marketMult = MarketRules.mult(m);
        s.grade = Pricing.grade(s.marketMult);

        List<String> why = new ArrayList<>();
        why.add("size " + m.getSize());
        why.add("stability " + Math.round(m.getStabilityValue()));
        if (habitable) why.add("habitable");
        if (mild) why.add("mild climate");
        if (pollution) why.add("polluted");
        why.add("hazard " + Math.round(m.getHazardValue() * 100f) + " percent");
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
