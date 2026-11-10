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

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
