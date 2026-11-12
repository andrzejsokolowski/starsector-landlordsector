package landlordsector;

/** Tenant math as plain numbers, checked without the game (checkRules). */
public final class TenantRules {

    /** Happiness from each liked tag in the home, and lost to each disliked one. */
    public static final float LIKE = 8f;
    public static final float DISLIKE = 15f;
    /** Happiness lost per rent step above what the tenant can afford. */
    public static final float OVERPRICED = 15f;
    /** Below this a tenant may move out. */
    public static final float MOVE_OUT_BELOW = 15f;
    /** Applicants and pool tenants wait this many days, then look elsewhere. */
    public static final float APPLICANT_DAYS = 30f;
    public static final float POOL_DAYS = 60f;
    public static final int MAX_APPLICANTS = 3;
    /** An empty home wears this much slower than a rented one. */
    public static final float VACANT_WEAR = 0.25f;

    private static final float[] TIER_DAMAGE = {2f, 1.6f, 1.2f, 1f, 0.8f, 0.6f};

    private TenantRules() {
    }

    public static final int MAX_WEALTH = 6;
    private static final String[] WEALTH = {"E", "D", "C", "B", "A", "S"};

    /** Wealth 1 to 6 as a letter, E to S, like the location grades. */
    public static String wealthLetter(int wealth) {
        return WEALTH[Math.max(1, Math.min(MAX_WEALTH, wealth)) - 1];
    }

    /** The highest rent setting a tenant of this wealth (1 to 6) pays without grumbling. */
    public static RentSetting maxSetting(int wealth) {
        switch (Math.max(1, Math.min(MAX_WEALTH, wealth))) {
            case 1: return RentSetting.LOW;
            case 2:
            case 3: return RentSetting.NORMAL;
            case 4: return RentSetting.HIGH;
            default: return RentSetting.GREEDY;
        }
    }

    public static int overpricedSteps(RentSetting setting, int wealth) {
        return Math.max(0, setting.ordinal() - maxSetting(wealth).ordinal());
    }

    /** A tenant's happiness on top of the home's base happiness. */
    public static float tenantHappiness(float homeHappiness, float traitHappiness, int liked, int disliked,
            int overpricedSteps, float mood) {
        float h = homeHappiness + traitHappiness + LIKE * liked - DISLIKE * disliked
                - OVERPRICED * overpricedSteps + mood;
        return Pricing.clamp(h, 0f, 100f);
    }

    /** Daily chance an advertised home gets an applicant. Better places and cheaper rent draw more. */
    public static float arrivalChance(float marketMult, float comfort, RentSetting setting) {
        float c = 0.06f * Pricing.clamp(marketMult, 0.5f, 2f) * Pricing.clamp(comfort / 50f, 0.5f, 1.5f)
                * settingDraw(setting);
        return Pricing.clamp(c, 0f, 0.5f);
    }

    public static float settingDraw(RentSetting s) {
        switch (s) {
            case CHARITY: return 1.8f;
            case LOW: return 1.4f;
            case HIGH: return 0.7f;
            case GREEDY: return 0.45f;
            default: return 1f;
        }
    }

    /** Bad events get rarer on cheap rent and more common on greedy rent, beyond what happiness does. */
    public static float settingBadMult(RentSetting s) {
        switch (s) {
            case CHARITY: return 0.6f;
            case LOW: return 0.8f;
            case HIGH: return 1.25f;
            case GREEDY: return 1.5f;
            default: return 1f;
        }
    }

    /**
     * How likely one event is, before the others are weighed against it.
     *
     * @param traitMult the tenant's traits' multipliers for this event, multiplied together
     * @param tier 0 shack to 5 estate
     */
    public static float eventWeight(boolean good, boolean damage, boolean crime, float baseWeight, float traitMult,
            float happiness, RentSetting setting, int tier, float security) {
        float h = Pricing.clamp(happiness, 0f, 100f);
        float w = Math.max(0f, baseWeight) * Math.max(0f, traitMult);
        if (good) {
            w *= 0.5f + h / 50f;
        } else {
            w *= 0.5f + (100f - h) / 50f;
            w *= settingBadMult(setting);
        }
        if (damage) w *= TIER_DAMAGE[Math.max(0, Math.min(TIER_DAMAGE.length - 1, tier))];
        if (crime) w *= 1.5f - Pricing.clamp(security, 0f, 100f) / 100f;
        return w;
    }

    /** Daily chance an unhappy tenant moves out: one in 30 below the line, never for loyal tenants. */
    public static float moveOutChance(float happiness, boolean loyal) {
        if (loyal) return 0f;
        return happiness < MOVE_OUT_BELOW ? 1f / 30f : 0f;
    }

    /** What screening one applicant costs: half a month's Normal rent, at least 500. */
    public static float screeningFee(float normalRent) {
        return Math.max(500f, normalRent * 0.5f);
    }

    /** The monthly screening service: a tenth of the Normal rent. It halves bad traits in applicants. */
    public static float screeningService(float normalRent) {
        return Math.max(100f, normalRent * 0.1f);
    }

    public static final float SCREENED_BAD_MULT = 0.5f;
}
