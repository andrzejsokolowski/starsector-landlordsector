package landlordsector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;

/** Everything Landlordsector keeps in the save: owned homes, listings and NPC homes on player colonies. */
public class Portfolio {

    public static final String KEY = "landlordsector_portfolio";

    public List<Property> properties = new ArrayList<>();
    public Map<String, MarketListings> listings = new HashMap<>();
    /** Player colony market id -> values of the homes NPC buyers bought there. */
    public Map<String, List<Float>> npcHomes = new HashMap<>();
    public long nextId = 1;

    /** Last month's totals, for the Manage page. */
    public float lastRent;
    public float lastTax;
    public float lastSales;
    /** Something happened the player has not looked at on the Manage page. */
    public boolean news;

    public static Portfolio get() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object o = data.get(KEY);
        if (o instanceof Portfolio) return (Portfolio) o;
        Portfolio p = new Portfolio();
        data.put(KEY, p);
        return p;
    }

    public List<Property> properties() {
        if (properties == null) properties = new ArrayList<>();
        return properties;
    }

    public Map<String, MarketListings> listings() {
        if (listings == null) listings = new HashMap<>();
        return listings;
    }

    public Map<String, List<Float>> npcHomes() {
        if (npcHomes == null) npcHomes = new HashMap<>();
        return npcHomes;
    }

    public List<Float> npcHomes(String marketId) {
        return npcHomes().computeIfAbsent(marketId, k -> new ArrayList<>());
    }

    public String newId() {
        return "lls_" + (nextId++);
    }

    public int countAt(String marketId) {
        int n = 0;
        for (Property p : properties()) if (marketId.equals(p.marketId)) n++;
        return n;
    }
}
