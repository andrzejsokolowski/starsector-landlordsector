package landlordsector;

/**
 * The hidden price rule, as plain numbers so it can be checked without the game.
 *
 * <p>The market multiplier runs from 0.25 to 4. Andrzej's scale (LLS-2, 2026-11-12): hazard 100 percent
 * is standard, 50 percent (Earth today) is perfection, 165 percent is still fine. Size 5 and 6 are the
 * standard market, 7 is big and 8 is huge. The worst place is a 275 percent magma world with pirates all
 * around.
 */
public final class Pricing {

    public static final float MULT_MIN = 0.25f;
    public static final float MULT_MAX = 4f;

    private Pricing() {
    }

    /**
     * @param size market size
     * @param stability 0 to 10
     * @param hazard hazard rating, 1 for 100 percent
     * @param pirates the market has the Pirate Activity condition
     * @param pathers the market has Pather Cells
     */
    public static float marketMult(int size, float stability, float hazard, boolean habitable, boolean mild,
            boolean pollution, boolean pirates, boolean pathers) {
        float m = sizeMult(size) * stabilityMult(stability) * planetMult(hazard, habitable, mild, pollution)
                * dangerMult(pirates, pathers);
        return clamp(m, MULT_MIN, MULT_MAX);
    }

    /** Size 5 and 6 are standard (about 1). Small markets cost less, 7 and 8 much more. */
    public static float sizeMult(int size) {
        if (size <= 2) return 0.5f;
        switch (size) {
            case 3: return 0.6f;
            case 4: return 0.8f;
            case 5: return 0.95f;
            case 6: return 1.05f;
            case 7: return 1.3f;
            case 8: return 1.6f;
            default: return 1.6f + 0.3f * (size - 8);
        }
    }

    /** 0.75 at stability 0, 1 at 5, 1.25 at 10. */
    public static float stabilityMult(float stability) {
        return 0.75f + 0.05f * clamp(stability, 0f, 10f);
    }

    /**
     * Hazard 100 percent is 1. Better than that rises fast (50 percent gives 1.5), worse falls slowly
     * (165 percent gives 0.84, 275 percent 0.56, never under 0.35). A breathable world and a mild climate
     * add a little on top of what they already take off the hazard. Pollution takes some.
     */
    public static float planetMult(float hazard, boolean habitable, boolean mild, boolean pollution) {
        float m = hazard <= 1f ? 1f + (1f - Math.max(0.4f, hazard)) : Math.max(0.35f, 1f - (hazard - 1f) * 0.25f);
        if (habitable) m *= 1.1f;
        if (mild) m *= 1.1f;
        if (pollution) m *= 0.85f;
        return m;
    }

    /** Pirates nearby take 30 percent off, Pather cells 15. */
    public static float dangerMult(boolean pirates, boolean pathers) {
        float m = 1f;
        if (pirates) m *= 0.7f;
        if (pathers) m *= 0.85f;
        return m;
    }

    /**
     * Number of listings a market of this size rolls: 1 at size 3 and 4, 2 at 5 and 6, 3 at 7 and 8, at most 4.
     * Every market has an agent since 0.3.2, so each lists fewer homes (Andrzej, 2026-11-12).
     */
    public static int listingCount(int size) {
        if (size < 3) return 0;
        return Math.max(1, Math.min(4, (size - 1) / 2));
    }

    /** Monthly rent before tariff. */
    public static float rent(float fullValue, float rentShare, float settingMult, float condition, float rentMult) {
        return fullValue * rentShare * settingMult * (0.5f + 0.5f * clamp(condition, 0f, 1f)) * rentMult;
    }

    /** Value of a property that is not condemned: 70 percent of the full value when worn out, all of it when new. */
    public static float value(float fullValue, float condition, boolean condemned, float landValue) {
        if (condemned) return fullValue * landValue;
        return fullValue * (0.7f + 0.3f * clamp(condition, 0f, 1f));
    }

    public static float repairCost(float fullValue, float condition, float repairFullCost) {
        return fullValue * (1f - clamp(condition, 0f, 1f)) * repairFullCost;
    }

    // ---- Property stats, all 0 to 100 ----

    /**
     * Comfort: the tier's base, plus or minus 20 for every 100 percent of hazard below or above 100 (at most
     * +12 and -40), a little more for a mild climate and a breathable world, less for pollution and for
     * station life. A worn home loses up to 40 percent of it.
     */
    public static float comfort(float tierComfort, Climate climate, boolean habitable, boolean mild,
            boolean pollution, float hazard, float condition) {
        float c = tierComfort + clamp((1f - hazard) * 20f, -40f, 12f);
        if (mild) c += 5f;
        if (habitable) c += 5f;
        if (pollution) c -= 10f;
        if (climate == Climate.STATION) c -= 5f;
        c *= 0.6f + 0.4f * clamp(condition, 0f, 1f);
        return clamp(c, 0f, 100f);
    }

    /** Tenant happiness from comfort, decor, the rent setting and the home's condition. */
    public static float happiness(float comfort, float decor, float settingDelta, float condition) {
        float h = 50f + (comfort - 50f) * 0.4f + decor * 0.1f + settingDelta + (clamp(condition, 0f, 1f) - 0.5f) * 40f;
        return clamp(h, 0f, 100f);
    }

    /** Wear speed: unhappy tenants (0) wear a home 1.5 times as fast, happy ones (100) half as fast. */
    public static float wearMult(float happiness) {
        return 1.5f - clamp(happiness, 0f, 100f) / 100f;
    }

    /** Tip chance: half the base for miserable tenants, one and a half times for delighted ones. */
    public static float tipChance(float base, float happiness) {
        return base * (0.5f + clamp(happiness, 0f, 100f) / 100f);
    }

    /**
     * Security from stability (6 a point) plus the best military building and the best ground defense,
     * minus 15 for pirate activity and 10 for Pather cells.
     */
    public static float security(float stability, float military, float defenses, boolean pirates,
            boolean pathers) {
        float s = clamp(stability, 0f, 10f) * 6f + military + defenses;
        if (pirates) s -= 15f;
        if (pathers) s -= 10f;
        return clamp(s, 0f, 100f);
    }

    /** Raids and disruption hurt a well-guarded home less: a third less at security 50, two thirds at 100. */
    public static float damageMult(float security) {
        return 1f - clamp(security, 0f, 100f) / 150f;
    }

    /** Decor adds up to a quarter to the value. */
    public static float decorValueMult(float decor) {
        return 1f + clamp(decor, 0f, 100f) / 400f;
    }

    /** Decor raises the reputation cheap rent earns (and greedy rent costs), up to double. */
    public static float decorRepMult(float decor) {
        return 1f + clamp(decor, 0f, 100f) / 100f;
    }

    /** Lower edges of the grades S+ to E (Andrzej, 2026-11-12). Anything under the last is F. */
    private static final float[] GRADE_EDGES = {3.0f, 2.0f, 1.5f, 1.05f, 0.75f, 0.55f, 0.4f};
    private static final String[] GRADES = {"S+", "S", "A", "B", "C", "D", "E", "F"};

    /**
     * The location grade S+, S, A, B, C, D, E or F from the market multiplier. A standard world (size 5
     * or 6, stability 5, hazard 100 percent) is C, an Earth-like one S.
     */
    public static String grade(float marketMult) {
        for (int i = 0; i < GRADE_EDGES.length; i++) {
            if (marketMult >= GRADE_EDGES[i]) return GRADES[i];
        }
        return GRADES[GRADES.length - 1];
    }

    /** Share of eligible markets with an agent at a time: the base share, more for big markets. */
    public static float agentShare(float base, int size) {
        return clamp(base * (0.6f + 0.2f * (size - 3)), 0.05f, 0.6f);
    }

    /**
     * Monthly arrival chance for a market without an agent, so that on average {@code share} of markets
     * have one when agents stay {@code stayMonths}.
     */
    public static float agentArrival(float share, float stayMonths) {
        float s = clamp(share, 0.01f, 0.95f);
        return clamp(s / (stayMonths * (1f - s)), 0f, 1f);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
