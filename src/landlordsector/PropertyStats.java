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
        /** Parts of the text in their own color: {part, "+" / "-" / null, part, color, ...}. */
        public final String[] hl;

        Note(String text, Boolean good, String... hl) {
            this.text = text;
            this.good = good;
            this.hl = hl;
        }

        /** As a tooltip line: {text, color, part, color, ...}. */
        public String[] line() {
            String[] out = new String[2 + hl.length];
            out[0] = text;
            out[1] = good == null ? null : (good ? "+" : "-");
            System.arraycopy(hl, 0, out, 2, hl.length);
            return out;
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
    /** What makes up each stat, for the tooltips on their bars: {text, "+" / "-" / null}. */
    public final List<String[]> comfortLines = new ArrayList<>();
    public final List<String[]> decorLines = new ArrayList<>();
    public final List<String[]> happinessLines = new ArrayList<>();
    public final List<String[]> securityLines = new ArrayList<>();
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
        float tierDecor = Pricing.clamp(tier.decor, 0f, 100f);
        s.decorLines.add(new String[] {"Comes with " + article(tier.name.toLowerCase()) + ": " + Math.round(tierDecor),
                null});
        float extra = s.decor - tierDecor;
        if (Math.abs(extra) >= 0.5f) s.decorLines.add(effect("Added since", extra, null));
        s.decorLines.add(effect("Tenant happiness", Pricing.decorHappiness(s.decor), null));
        s.decorLines.add(new String[] {"Below " + Math.round(Pricing.DECOR_PLAIN) + " the home feels bare and tenants"
                + " like it less; above, more. Decor also raises the value (up to a quarter) and the reputation rent"
                + " earns or costs. Furniture will add to it.", null});
        float traitComfort = 0f, traitHappy = 0f, traitSecurity = 0f;
        List<String[]> traitComfortLines = new ArrayList<>(), traitHappyLines = new ArrayList<>(),
                traitSecurityLines = new ArrayList<>();
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d == null) continue;
            traitComfort += d.comfort;
            traitHappy += d.happiness;
            traitSecurity += d.security;
            if (d.comfort != 0f) traitComfortLines.add(effect(d.name, d.comfort, d.good));
            if (d.happiness != 0f) traitHappyLines.add(effect(d.name, d.happiness, d.good));
            if (d.security != 0f) traitSecurityLines.add(effect(d.name, d.security, d.good));
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
        List<String[]> condComfortLines = new ArrayList<>(), condHappyLines = new ArrayList<>(),
                condSecurityLines = new ArrayList<>(), condPriceLines = new ArrayList<>();
        for (ConditionTable.Effect e : ConditionTable.matching(conds, m.getFactionId())) {
            condComfort += e.comfort;
            condHappy += e.happiness;
            condSecurity += e.security;
            String name = conditionName(m, e.id);
            if (Math.abs(e.price - 1f) > 0.001f) {
                condPriceLines.add(new String[] {name + ": x" + two(e.price), null, "x" + two(e.price),
                        e.price > 1f ? "+" : "-"});
            }
            if (Math.abs(e.comfort) >= 0.5f) condComfortLines.add(effect(name, e.comfort, null));
            if (Math.abs(e.happiness) >= 0.5f) condHappyLines.add(effect(name, e.happiness, null));
            if (Math.abs(e.security) >= 0.5f) condSecurityLines.add(effect(name, e.security, null));
        }

        boolean habitable = conds.contains(Conditions.HABITABLE);
        boolean mild = conds.contains(Conditions.MILD_CLIMATE);
        boolean pollution = conds.contains(Conditions.POLLUTION);
        boolean pirates = conds.contains(Conditions.PIRATE_ACTIVITY);
        boolean pathers = conds.contains(Conditions.PATHER_CELLS);
        float hazard = m.getHazardValue();

        // Comfort. A trait like Geothermal takes the cold (or the heat) of the world off this one home.
        float offset = HouseTraits.hazardOffset(houseTraits, conds);
        float homeHazard = hazard - offset;
        float unworn = Pricing.comfort(tier.comfort, climate, habitable, mild, pollution, homeHazard, 1f);
        float worn = Pricing.comfort(tier.comfort, climate, habitable, mild, pollution, homeHazard, condition);
        s.comfort = Pricing.clamp(worn + condComfort + traitComfort, 0f, 100f);
        s.comfortLines.add(new String[] {tier.name + ": " + Math.round(tier.comfort), null});
        float hz = Pricing.clamp((1f - homeHazard) * 20f, -40f, 12f);
        if (Math.abs(hz) >= 0.5f) s.comfortLines.add(effect("Hazard " + Math.round(homeHazard * 100f) + "%", hz, null));
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d == null) continue;
            for (java.util.Map.Entry<String, Float> c : d.cancels.entrySet()) {
                if (!conds.contains(c.getKey())) continue;
                String cn = conditionName(m, c.getKey());
                s.comfortLines.add(new String[] {d.name + ": no " + cn + " in this home (hazard -"
                        + Math.round(c.getValue() * 100f) + "%)", null, d.name, d.good ? "+" : "-"});
            }
        }
        if (mild) s.comfortLines.add(effect("Mild climate", 5f, null));
        if (habitable) s.comfortLines.add(effect("Habitable world", 5f, null));
        if (pollution) s.comfortLines.add(effect("Pollution", -10f, null));
        if (climate == Climate.STATION) s.comfortLines.add(effect("Station life", -5f, null));
        if (unworn - worn >= 0.5f) {
            s.comfortLines.add(effect("Wear (condition " + Math.round(condition * 100f) + "%)", worn - unworn, null));
        }
        s.comfortLines.addAll(condComfortLines);
        s.comfortLines.addAll(traitComfortLines);
        s.comfortLines.add(new String[] {"Raises tenant happiness.", null});

        // Security.
        float stab = m.getStabilityValue();
        float mil = military(m), def = defenses(m);
        s.security = Pricing.clamp(Pricing.security(stab, mil, def, pirates, pathers) + condSecurity + traitSecurity,
                0f, 100f);
        s.securityLines.add(effect("Stability " + Math.round(stab), Pricing.clamp(stab, 0f, 10f) * 6f, null));
        if (mil > 0f) s.securityLines.add(effect(industryName(m, MILITARY), mil, null));
        if (def > 0f) s.securityLines.add(effect(industryName(m, DEFENSES), def, null));
        if (pirates) s.securityLines.add(effect(conditionName(m, Conditions.PIRATE_ACTIVITY), -15f, null));
        if (pathers) s.securityLines.add(effect(conditionName(m, Conditions.PATHER_CELLS), -10f, null));
        s.securityLines.addAll(condSecurityLines);
        s.securityLines.addAll(traitSecurityLines);
        s.securityLines.add(new String[] {"Security makes the tenant feel safe, which leads to higher happiness for"
                + " law-abiding tenants, while criminals feel threatened and are less happy. Higher security also"
                + " makes break-ins and other trouble from outside rarer, and raids on the market damage the home"
                + " less.", null});

        // Happiness of the home, then the tenant's own part.
        float safe = Pricing.securityHappiness(s.security, false);
        float home = Pricing.happiness(s.comfort, s.decor, setting.happiness, condition) + condHappy + traitHappy
                + safe;
        s.happiness = Pricing.clamp(home, 0f, 100f);
        s.happinessLines.add(new String[] {"Base: 50", null});
        s.happinessLines.add(effect("Comfort " + Math.round(s.comfort), (s.comfort - 50f) * 0.4f, null));
        s.happinessLines.add(effect("Decor " + Math.round(s.decor), Pricing.decorHappiness(s.decor), null));
        if (Math.abs(safe) >= 0.5f) {
            s.happinessLines.add(effect("Security " + Math.round(s.security) + (safe > 0f ? " (feels safe)"
                    : " (feels unsafe)"), safe, null));
        }
        if (setting.happiness != 0f) s.happinessLines.add(effect(setting.label + " rent", setting.happiness, null));
        s.happinessLines.add(effect("Condition " + Math.round(condition * 100f) + "%",
                (Pricing.clamp(condition, 0f, 1f) - 0.5f) * 40f, null));
        s.happinessLines.addAll(condHappyLines);
        s.happinessLines.addAll(traitHappyLines);
        if (tenant != null) {
            s.happiness = tenantHappiness(s, home, tenant, setting, houseTraits, tier, safe);
            for (Note n : s.notes) s.happinessLines.add(n.line());
        }
        String below = "Below " + Math.round(TenantRules.MOVE_OUT_BELOW);
        String maintain = "Above " + Math.round(TenantRules.MAINTAIN_ABOVE);
        String improve = "Above " + Math.round(TenantRules.IMPROVE_ABOVE);
        s.happinessLines.add(new String[] {below + ": the tenant may move out.", null, below, "-"});
        s.happinessLines.add(new String[] {maintain + ": they do small repairs themselves (+"
                + Math.round(TenantRules.MAINTAIN_REPAIR * 100f) + "% condition a month).", null, maintain, "+"});
        s.happinessLines.add(new String[] {improve + ": they may decorate your home on their own.", null, improve,
                "+"});
        s.happinessLines.add(new String[] {"Happier tenants also wear the home slower and cause less trouble.",
                null});

        // Location: only what sets the price (Andrzej, 0.3.6: security and the rest have their own tooltips).
        s.marketMult = MarketRules.mult(m);
        s.grade = Pricing.grade(s.marketMult);
        float bare = Pricing.planetMult(hazard, false, false, false);
        factor(s, "Market size " + m.getSize(), Pricing.sizeMult(m.getSize()));
        factor(s, "Stability " + Math.round(stab), Pricing.stabilityMult(stab));
        factor(s, "Hazard " + Math.round(hazard * 100f) + "%", bare);
        if (habitable) factor(s, "Habitable world", Pricing.planetMult(hazard, true, false, false) / bare);
        if (mild) factor(s, "Mild climate", Pricing.planetMult(hazard, false, true, false) / bare);
        if (pollution) factor(s, "Pollution", Pricing.planetMult(hazard, false, false, true) / bare);
        if (pirates) factor(s, conditionName(m, Conditions.PIRATE_ACTIVITY), Pricing.dangerMult(true, false));
        if (pathers) factor(s, conditionName(m, Conditions.PATHER_CELLS), Pricing.dangerMult(false, true));
        s.gradeLines.addAll(condPriceLines);
        String total = "Location multiplier: x" + two(s.marketMult);
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
            List<String> houseTraits, Tier tier, float safe) {
        Set<String> tags = HouseTraits.tags(houseTraits);
        int liked = 0, disliked = 0;
        for (String id : t.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d == null) continue;
            String dc = d.good == null ? null : (d.good ? "+" : "-");
            if (d.happiness != 0f) {
                String num = signed(d.happiness);
                s.notes.add(new Note(d.name + ": " + num, null, d.name, dc, num, d.happiness > 0f ? "+" : "-"));
            }
            for (String tag : d.likes) {
                if (!tags.contains(tag)) continue;
                liked++;
                HouseTraits.Def h = traitWithTag(tag, houseTraits);
                String hn = h != null ? h.name : tag;
                s.notes.add(new Note(d.name + ": likes " + hn + " homes (+" + Math.round(TenantRules.LIKE) + ")", null,
                        d.name, dc, "likes", "+", hn, h == null ? null : (h.good ? "+" : "-")));
            }
            for (String tag : d.dislikes) {
                if (!tags.contains(tag)) continue;
                disliked++;
                HouseTraits.Def h = traitWithTag(tag, houseTraits);
                String hn = h != null ? h.name : tag;
                s.notes.add(new Note(d.name + ": hates " + hn + " homes (-" + Math.round(TenantRules.DISLIKE) + ")",
                        null, d.name, dc, "hates", "-", hn, h == null ? null : (h.good ? "+" : "-")));
            }
        }
        float flip = 0f;
        if (t.criminal() && Math.abs(safe) >= 0.5f) {
            // Criminals feel the other way about security: undo the law-abiding part and turn it round.
            flip = -2f * safe;
            String num = signed(flip);
            String who = criminalTraitName(t);
            s.notes.add(new Note(who + ": " + (safe > 0f ? "feels watched here " : "feels at home here ") + "("
                    + num + ")", null, who, "-", num, flip > 0f ? "+" : "-"));
        }
        int shift = t.homeShift();
        float price = TenantRules.pricePenalty(tier.index, setting, t.wealth, shift);
        float beneath = TenantRules.beneathPenalty(tier.index, t.wealth, shift, setting);
        String first = t.firstName();
        String want = "Desired home size: " + desiredTier(t.wealth, shift) + ". ";
        String here = article(tier.name.toLowerCase());
        if (price > 0f) {
            String text;
            if (tier.index > Math.max(TenantRules.fitTier(t.wealth), TenantRules.desiredTier(t.wealth, shift))) {
                text = want + first + " thinks " + here + " is too big"
                        + (TenantRules.settingShift(setting) > 0f ? " at " + setting.label + " rent" : "")
                        + (price >= TenantRules.CANT_AFFORD ? " and can't pay for it" : "");
            } else {
                text = first + " thinks " + setting.label + " rent is too high";
            }
            s.notes.add(new Note(text + " (-" + Math.round(price) + ")", false));
        }
        if (beneath > 0f) {
            s.notes.add(new Note(want + first + " thinks " + here + " is too small (-" + Math.round(beneath) + ")",
                    false));
        }
        if (t.mood >= 5f) s.notes.add(new Note("In a good mood (+" + Math.round(t.mood) + ")", true));
        if (t.mood <= -5f) s.notes.add(new Note("In a bad mood (" + Math.round(t.mood) + ")", false));
        return TenantRules.tenantHappiness(home, t.traitHappiness() + flip, liked, disliked, price + beneath, t.mood);
    }

    private static String criminalTraitName(Tenant t) {
        for (String id : t.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null && d.criminal) return d.name;
        }
        return "Criminal";
    }

    /** The tier this tenant wants, capitalized ("Villa", "Small house"). */
    public static String desiredTier(int wealth, int shift) {
        List<Tier> tiers = TierTable.all();
        return tiers.get(Math.min(tiers.size() - 1, TenantRules.desiredTier(wealth, shift))).name;
    }

    /** "Wealth: [B]", "Location: [A]" (Andrzej, 0.3.8). */
    public static String graded(String name, String letter) {
        return name + ": [" + letter + "]";
    }

    /**
     * What makes up a home's value, for the Value row's tooltip (Andrzej, 0.3.8): the base value, the location
     * multiplier, the market trend, the traits, decor and condition, then the value.
     */
    public static List<String[]> valueLines(Tier tier, float basePrice, String grade, float locationMult,
            float drift, float traitMult, float decor, float condition, boolean condemned, float value) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"Base value of this " + tier.name.toLowerCase() + ": "
                + com.fs.starfarer.api.util.Misc.getDGSCredits(basePrice), null});
        out.add(mult(graded("Location", grade) + " multiplier", locationMult));
        if (Math.abs(drift - 1f) > 0.005f) out.add(mult("Market trend", drift));
        if (Math.abs(traitMult - 1f) > 0.005f) out.add(mult("House traits", traitMult));
        float dm = Pricing.decorValueMult(decor);
        if (Math.abs(dm - 1f) > 0.005f) out.add(mult("Decor " + Math.round(decor), dm));
        if (condemned) {
            out.add(mult("Condemned: only the land", TierTable.economy().landValue));
        } else if (condition < 0.995f) {
            out.add(mult("Condition " + Math.round(condition * 100f) + "%",
                    0.7f + 0.3f * Pricing.clamp(condition, 0f, 1f)));
        }
        out.add(new String[] {"Value: " + com.fs.starfarer.api.util.Misc.getDGSCredits(value), "g"});
        return out;
    }

    private static String[] mult(String name, float v) {
        String x = "x" + two(v);
        return new String[] {name + ": " + x, null, x, v > 1.005f ? "+" : (v < 0.995f ? "-" : null)};
    }

    /** "a shack", "an estate". */
    public static String article(String word) {
        return ("aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an " : "a ") + word;
    }

    /** "Fortified: +10", the number green or red; the name in its own color when it has one. */
    private static String[] effect(String name, float v, Boolean nameGood) {
        String num = signed(v);
        String c = v > 0.001f ? "+" : (v < -0.001f ? "-" : null);
        if (nameGood == null) return new String[] {name + ": " + num, null, num, c};
        return new String[] {name + ": " + num, null, name, nameGood ? "+" : "-", num, c};
    }

    private static String signed(float v) {
        int r = Math.round(v);
        return (r > 0 ? "+" : "") + r;
    }

    private static final String[] MILITARY = {Industries.HIGHCOMMAND, Industries.MILITARYBASE, Industries.PATROLHQ};
    private static final String[] DEFENSES = {Industries.HEAVYBATTERIES, Industries.GROUNDDEFENSES};

    private static String industryName(MarketAPI m, String[] ids) {
        for (String id : ids) {
            if (!m.hasIndustry(id)) continue;
            try {
                String n = m.getIndustry(id).getCurrentName();
                if (n != null && !n.isEmpty()) return n;
            } catch (Throwable ignored) {
                // A stand-in market without industry objects.
            }
            return id;
        }
        return "";
    }

    private static String conditionName(MarketAPI m, String id) {
        try {
            MarketConditionAPI mc = m.getCondition(id);
            if (mc != null && mc.getName() != null) return mc.getName();
        } catch (Throwable ignored) {
            // A stand-in market without condition objects.
        }
        return id;
    }

    /** The house trait that carries this tag. */
    private static HouseTraits.Def traitWithTag(String tag, List<String> houseTraits) {
        for (String id : houseTraits) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null && d.tags.contains(tag)) return d;
        }
        return null;
    }

    private static void factor(PropertyStats s, String what, float mult) {
        String sign = mult > 1.001f ? "+" : (mult < 0.999f ? "-" : null);
        String x = "x" + two(mult);
        s.gradeLines.add(new String[] {what + ": " + x, null, x, sign});
    }

    private static String two(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
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
