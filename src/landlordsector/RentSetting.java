package landlordsector;

/** The rent control. Cheap rent earns reputation, greedy rent costs it and wears the home faster. */
public enum RentSetting {
    /** No rent at all (Andrzej, 0.3.8). */
    FREE("Free", 0f, 0.8f, 0.5f, 40f),
    CHARITY("Charity", 0.5f, 0.8f, 0.3f, 30f),
    LOW("Low", 0.75f, 0.9f, 0.1f, 15f),
    NORMAL("Normal", 1f, 1f, 0f, 0f),
    HIGH("High", 1.3f, 1.25f, -0.1f, -15f),
    GREEDY("Greedy", 1.6f, 1.5f, -0.3f, -30f);

    public final String label;
    public final float rentMult;
    public final float wearMult;
    /** Reputation points per month for one property of the cheapest tier. */
    public final float repPerMonth;
    /** Tenant happiness this setting adds or takes away. */
    public final float happiness;

    RentSetting(String label, float rentMult, float wearMult, float repPerMonth, float happiness) {
        this.label = label;
        this.rentMult = rentMult;
        this.wearMult = wearMult;
        this.repPerMonth = repPerMonth;
        this.happiness = happiness;
    }

    public RentSetting lower() {
        return ordinal() == 0 ? this : values()[ordinal() - 1];
    }

    public RentSetting higher() {
        return ordinal() == values().length - 1 ? this : values()[ordinal() + 1];
    }
}
