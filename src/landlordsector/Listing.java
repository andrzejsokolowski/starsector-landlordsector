package landlordsector;

/** A home for sale at a market. Saved in the portfolio. */
public class Listing {

    public String id;
    public String tierId;
    public String climateId;
    public String artPath;
    /** Price inside the tier's base range, before the market multiplier. */
    public float basePrice;
    /** House trait ids. Null on listings from before 0.3.0 until they are rolled on load. */
    public java.util.List<String> traits;
    /** The home's name, for example "Leafy Cottage". Null on older listings until named on load. */
    public String name;
    /** 0.4.0: renovations the home already has (ids in upgrades.json). Null on older listings. */
    public java.util.List<String> upgrades;
    /** 0.5.0: the building the home sits next to, "none", or null on older listings until rolled on load. */
    public String neighbor;

    /** House traits and the renovations it comes with: everything that counts like a trait. */
    public java.util.List<String> features() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (traits != null) out.addAll(traits);
        if (upgrades != null) out.addAll(upgrades);
        return out;
    }

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
