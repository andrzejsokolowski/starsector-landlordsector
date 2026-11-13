package landlordsector;

/** Tenant math as plain numbers, checked without the game (checkRules). */
public final class TenantRules {

    /** Happiness from each liked tag in the home, and lost to each disliked one. */
    public static final float LIKE = 8f;
    public static final float DISLIKE = 15f;
    /** Below this a tenant may move out. */
    public static final float MOVE_OUT_BELOW = 15f;
    /** From this happiness up a tenant does small repairs: this much condition back each month. */
    public static final float MAINTAIN_ABOVE = 60f;
    public static final float MAINTAIN_REPAIR = 0.01f;
    /** From this happiness up a tenant may decorate the home: this chance a month, this much decor. */
    public static final float IMPROVE_ABOVE = 80f;
    public static final float IMPROVE_CHANCE = 0.25f;
    public static final float IMPROVE_DECOR = 2f;
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

    /*
     * Wealth and the home (0.3.3, Andrzej: "a wealth S guy ... should want a villa or a mansion ... a pirate would
     * never be able to afford a villa, despite it being set to less rent"). Each wealth fits one tier: E a shack,
     * D a studio, C a small house, B a house, A a villa, S an estate. The rent setting moves a home's price level
     * by at most one tier (Charity -1, Low -0.5, High +0.5, Greedy +1), so it matters less than the tier.
     */

    /** Happiness lost per tier of price level above what the tenant can pay. */
    public static final float STRETCH = 20f;
    /** More than one tier above: they cannot pay it at all. They do not move in; a tenant already there leaves soon. */
    public static final float CANT_AFFORD = 40f;
    /** Two tiers below their wealth: beneath them. */
    public static final float BENEATH = 15f;
    /** Three or more tiers below: they would not live there. */
    public static final float FAR_BENEATH = 30f;

    /** The tier a tenant of this wealth fits: E (1) a shack (0) to S (6) an estate (5). */
    public static int fitTier(int wealth) {
        return Math.max(1, Math.min(MAX_WEALTH, wealth)) - 1;
    }

    /** How far the rent setting moves a home's price level, in tiers. Free rent still never puts a villa in reach of the poor (0.3.3 rule). */
    public static float settingShift(RentSetting s) {
        switch (s) {
            case FREE: return -1.5f;
            case CHARITY: return -1f;
            case LOW: return -0.5f;
            case HIGH: return 0.5f;
            case GREEDY: return 1f;
            default: return 0f;
        }
    }

    /*
     * Taste (0.3.8, Andrzej: "traits that make them prefer larger houses they can't afford, and smaller houses
     * despite being rich"). A trait's homeShift moves the home size a tenant wants: Social climber +1, Frugal -1.
     * What they can pay never changes; a home above their wealth up to the size they want costs no happiness, and a
     * home counts as too small from the size they want.
     */

    /** The tier this tenant wants: the tier their wealth fits, moved by their traits. */
    public static int desiredTier(int wealth, int shift) {
        return Math.max(0, Math.min(MAX_WEALTH - 1, fitTier(wealth) + shift));
    }

    public static float pricePenalty(int tier, RentSetting s, int wealth, int shift) {
        float o = over(tier, s, wealth);
        if (o > 1.001f) return CANT_AFFORD;
        float above = tier + settingShift(s) - Math.max(fitTier(wealth), desiredTier(wealth, shift));
        return above > 0f ? STRETCH * above : 0f;
    }

    public static float beneathPenalty(int tier, int wealth, int shift) {
        int b = desiredTier(wealth, shift) - tier;
        if (b >= 3) return FAR_BENEATH;
        return b == 2 ? BENEATH : 0f;
    }

    public static boolean wouldLiveIn(int tier, int wealth, int shift) {
        return desiredTier(wealth, shift) - tier < 3;
    }

    public static boolean fits(int tier, RentSetting s, int wealth, int shift) {
        return canAfford(tier, s, wealth) && wouldLiveIn(tier, wealth, shift);
    }

    /** Tiers of price level above what the tenant pays happily (0 or less: fine). */
    public static float over(int tier, RentSetting s, int wealth) {
        return tier + settingShift(s) - fitTier(wealth);
    }

    /** Tiers the home is below what the tenant is used to (the rent setting does not matter here). */
    public static int below(int tier, int wealth) {
        return fitTier(wealth) - tier;
    }

    public static boolean canAfford(int tier, RentSetting s, int wealth) {
        return over(tier, s, wealth) <= 1.001f;
    }

    public static boolean wouldLiveIn(int tier, int wealth) {
        return below(tier, wealth) < 3;
    }

    /** True when the tenant would move into this home at this rent. */
    public static boolean fits(int tier, RentSetting s, int wealth) {
        return canAfford(tier, s, wealth) && wouldLiveIn(tier, wealth);
    }

    /** Happiness lost to the price: a stretch, or more than they can pay. */
    public static float pricePenalty(int tier, RentSetting s, int wealth) {
        float o = over(tier, s, wealth);
        if (o > 1.001f) return CANT_AFFORD;
        return o > 0f ? STRETCH * o : 0f;
    }

    /** Happiness lost to a home beneath them. */
    public static float beneathPenalty(int tier, int wealth) {
        int b = below(tier, wealth);
        if (b >= 3) return FAR_BENEATH;
        return b == 2 ? BENEATH : 0f;
    }

    /** A tenant's happiness on top of the home's base happiness. */
    public static float tenantHappiness(float homeHappiness, float traitHappiness, int liked, int disliked,
            float wealthPenalty, float mood) {
        float h = homeHappiness + traitHappiness + LIKE * liked - DISLIKE * disliked - wealthPenalty + mood;
        return Pricing.clamp(h, 0f, 100f);
    }

    /**
     * An applicant's wealth for a home: people who answer an advert look for that kind of home, so the tier sets
     * the wealth. The faction tilts it by half its own wealth (base 3), the roll by one either way.
     */
    public static int applicantWealth(int tier, int factionWealth, int roll) {
        return tier + 1 + Math.round((factionWealth - 3) * 0.5f) + roll;
    }

    /** Daily chance an advertised home gets an applicant. Better places and cheaper rent draw more. */
    public static float arrivalChance(float marketMult, float comfort, RentSetting setting) {
        float c = 0.06f * Pricing.clamp(marketMult, 0.5f, 2f) * Pricing.clamp(comfort / 50f, 0.5f, 1.5f)
                * settingDraw(setting);
        return Pricing.clamp(c, 0f, 0.5f);
    }

    public static float settingDraw(RentSetting s) {
        switch (s) {
            case FREE: return 2.5f;
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
            case FREE: return 0.5f;
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
