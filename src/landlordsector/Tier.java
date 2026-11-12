package landlordsector;

/** One property tier from data/config/landlordsector/tiers.json. Loaded once at startup. */
public final class Tier {

    public final String id;
    public final String name;
    /** Base price range in credits, before the market multiplier. */
    public final float min;
    public final float max;
    /** Monthly rent at the Normal setting, as a share of the value. */
    public final float rentShare;
    /** Smallest market size that lists this tier. */
    public final int minSize;
    /** Furniture slots (used from 0.3.0). */
    public final int slots;
    public final float weight;
    /** Base comfort and decor, 0 to 100. */
    public final float comfort;
    public final float decor;
    /** Position in the table, 0 for the cheapest. */
    public final int index;

    public Tier(String id, String name, float min, float max, float rentShare, int minSize, int slots,
            float weight, float comfort, float decor, int index) {
        this.id = id;
        this.name = name;
        this.min = min;
        this.max = max;
        this.rentShare = rentShare;
        this.minSize = minSize;
        this.slots = slots;
        this.weight = weight;
        this.comfort = comfort;
        this.decor = decor;
        this.index = index;
    }
}
