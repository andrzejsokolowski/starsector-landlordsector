package landlordsector;

import java.util.ArrayList;
import java.util.List;

/** The homes one market has for sale, and when they were rolled. Saved in the portfolio. */
public class MarketListings {

    public List<Listing> listings = new ArrayList<>();
    public long rolledAt;

    public List<Listing> list() {
        if (listings == null) listings = new ArrayList<>();
        return listings;
    }
}
