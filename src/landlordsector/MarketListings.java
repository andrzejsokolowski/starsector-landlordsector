package landlordsector;

import java.util.ArrayList;
import java.util.List;

/** The homes one market has for sale, and when they were rolled. Saved in the portfolio. */
public class MarketListings {

    public List<Listing> listings = new ArrayList<>();
    public long rolledAt;
    /** Market size when last looked at (0.7.2; 0 in older saves). A bigger market lists more homes at once. */
    public int size;

    public List<Listing> list() {
        if (listings == null) listings = new ArrayList<>();
        return listings;
    }
}
