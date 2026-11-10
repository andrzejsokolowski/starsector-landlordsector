package landlordsector;

/** A home for sale at a market. Saved in the portfolio. */
public class Listing {

    public String id;
    public String tierId;
    public String climateId;
    public String artPath;
    /** Price inside the tier's base range, before the market multiplier. */
    public float basePrice;

    public Listing() {
    }

    public Listing(String id, String tierId, String climateId, String artPath, float basePrice) {
        this.id = id;
        this.tierId = tierId;
        this.climateId = climateId;
        this.artPath = artPath;
        this.basePrice = basePrice;
    }
}
