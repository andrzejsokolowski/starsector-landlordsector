package landlordsector;

/** The "economy" numbers of tiers.json. Defaults match the shipped file. */
public final class Economy {

    public float repairFullCost = 0.05f;
    public float wearMonths = 24f;
    public float landValue = 0.2f;
    public float brokerFee = 0.05f;
    public float ownColonyPrice = 0.5f;
    /** 1.12.2 (LLS-37): rent is multiplied by this on worlds that are not the player's colonies. */
    public float otherWorldRent = 1.5f;
    public float listingRefreshDays = 90f;
    public float npcBuyChance = 0.15f;
    public float npcSaleShare = 0.5f;
    public float propertyTax = 0.005f;
    public float homesPerSize = 3f;
    public float stabilityPerHome = 0.05f;
    public float stabilityMax = 1f;
    public float growthPerHome = 0.25f;
    public float growthMax = 5f;
    public float driftPerMonth = 0.02f;
    public float driftMin = 0.7f;
    public float driftMax = 1.4f;
    public float disruptionWear = 0.05f;
    /** 1.12.1: condition a tenant at happiness 100 repairs a month; none at {@link #happyRepairFrom} or below. */
    public float happyRepair = 0.03f;
    public float happyRepairFrom = 60f;
    /** 1.12.1: damage from events (a smashed window, a fire) is multiplied by this. */
    public float eventDamage = 0.5f;
    public float raidWear = 0.1f;
    public float tacticalBombWear = 0.3f;
    public float agentShare = 0.25f;
    public float agentMinMonths = 3f;
    public float agentMaxMonths = 6f;
}
