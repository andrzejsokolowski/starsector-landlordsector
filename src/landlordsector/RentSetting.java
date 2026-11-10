package landlordsector;

/** The rent control. Cheap rent earns reputation, greedy rent costs it and wears the home faster. */
public enum RentSetting {
    CHARITY("Charity", 0.5f, 0.8f, 0.3f),
    LOW("Low", 0.75f, 0.9f, 0.1f),
    NORMAL("Normal", 1f, 1f, 0f),
    HIGH("High", 1.3f, 1.25f, -0.1f),
    GREEDY("Greedy", 1.6f, 1.5f, -0.3f);

    public final String label;
    public final float rentMult;
    public final float wearMult;
    /** Reputation points per month for one property of the cheapest tier. */
    public final float repPerMonth;

    RentSetting(String label, float rentMult, float wearMult, float repPerMonth) {
        this.label = label;
        this.rentMult = rentMult;
        this.wearMult = wearMult;
        this.repPerMonth = repPerMonth;
    }

    public RentSetting lower() {
        return ordinal() == 0 ? this : values()[ordinal() - 1];
    }

    public RentSetting higher() {
        return ordinal() == values().length - 1 ? this : values()[ordinal() + 1];
    }
}
