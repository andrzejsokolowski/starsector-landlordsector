package landlordsector;

/**
 * The hidden price rule, as plain numbers so it can be checked without the game.
 *
 * <p>The market multiplier runs from 0.25 to 4. A size 8, fully stable, habitable world with a mild
 * climate and no pollution reaches 4. A small, unstable, polluted rock drops to 0.25.
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
     */
    public static float marketMult(int size, float stability, float hazard, boolean habitable, boolean mild,
            boolean pollution) {
        float m = sizeMult(size) * stabilityMult(stability) * planetMult(hazard, habitable, mild, pollution);
        return clamp(m, MULT_MIN, MULT_MAX);
    }

    /** 0.55 at size 3, growing a quarter per size: about 1.07 at 6 and 1.68 at 8. */
    public static float sizeMult(int size) {
        return 0.55f * (float) Math.pow(1.25, Math.max(0, size - 3));
    }

    /** 0.6 at stability 0, 1 at 5, 1.4 at 10. */
    public static float stabilityMult(float stability) {
        return 0.6f + 0.08f * clamp(stability, 0f, 10f);
    }

    /** Hazard 100 percent gives 1, 50 percent gives 1.4, 150 percent gives 0.5. Then the three conditions. */
    public static float planetMult(float hazard, boolean habitable, boolean mild, boolean pollution) {
        float m = clamp(2f - hazard, 0.4f, 1.4f);
        if (habitable) m *= 1.2f;
        if (mild) m *= 1.25f;
        if (pollution) m *= 0.7f;
        return m;
    }

    /** Number of listings a market of this size rolls. */
    public static int listingCount(int size) {
        return Math.max(0, Math.min(8, size - 1));
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
     * Comfort: the tier's base, plus a mild climate and a habitable world, minus pollution, a station or a
     * hostile world and a hazard rating over 100 percent. A worn home loses up to 40 percent of it.
     */
    public static float comfort(float tierComfort, Climate climate, boolean habitable, boolean mild,
            boolean pollution, float hazard, float condition) {
        float c = tierComfort;
        if (mild) c += 10f;
        if (habitable) c += 5f;
        if (pollution) c -= 10f;
        if (climate == Climate.STATION) c -= 5f;
        if (climate == Climate.HOSTILE) c -= 10f;
        c -= Math.max(0f, hazard - 1f) * 20f;
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

    /** Security from stability (6 a point) plus the best military building and the best ground defense. */
    public static float security(float stability, float military, float defenses) {
        return clamp(clamp(stability, 0f, 10f) * 6f + military + defenses, 0f, 100f);
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

    /** The location grade A to F: the market multiplier in a word. */
    public static String grade(float marketMult) {
        if (marketMult >= 2.5f) return "A";
        if (marketMult >= 1.6f) return "B";
        if (marketMult >= 1.0f) return "C";
        if (marketMult >= 0.6f) return "D";
        if (marketMult >= 0.35f) return "E";
        return "F";
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
