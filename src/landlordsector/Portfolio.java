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
    /** Property agents by market id. */
    public Map<String, Agents.Agent> agents = new HashMap<>();
    /** True once the first agents were placed in this save. */
    public boolean agentsSeeded;
    /** Tenants the finders brought back, waiting to be given a home. */
    public List<Tenant> tenantPool;
    /** Tenant finders by id. */
    public Map<String, Finders.State> finders;
    public boolean findersSeeded;
    /** Homes bought before 0.3.0 got a tenant of their own. */
    public boolean tenantsMigrated;
    /** 0.4.0: when each market's open market last got its furniture, by market id. */
    public Map<String, Long> shopStocked;

    /** 0.5.0: furniture the player keeps for their homes, by upgrade id, how many of each. */
    public Map<String, Integer> furniture;
    /** 0.5.0: true once the save was moved to 0.5.0 (listings rolled again with neighbours and new counts). */
    public boolean upgraded050;
    /** 0.8.0: true once every market's homes for sale were rolled again with the new room and the world table. */
    public boolean upgraded080;
    /** 0.8.1: true once homes for sale were rolled again with each world's lore traits. */
    public boolean upgraded081;
    /** 1.12.0: tenants living in your homes got their lease. */
    public boolean upgraded1120;
    /** 1.13.0: tenants on the old "No lease" got an open-ended lease. */
    public boolean upgraded1130;

    /** 1.2.0: grown kids who work in the mines or inspire a colony, kept for their pay and bonus. */
    public List<Child> adultKids;
    /** 1.2.0: kids who left with a former partner: alimony until they grow up. */
    public List<Child> exKids;
    /** 1.2.0: children born so far, for "Junior II", "Junior III". */
    public int kidsBorn;
    /** 1.2.0: last month's alimony and the pay grown kids sent home. */
    public float lastAlimony;
    public float lastKidPay;
    /** 1.8.0: what raising the kids at home cost last month. */
    public float lastKidCost;

    public List<Child> adultKids() {
        if (adultKids == null) adultKids = new ArrayList<>();
        return adultKids;
    }

    public List<Child> exKids() {
        if (exKids == null) exKids = new ArrayList<>();
        return exKids;
    }

    public Map<String, Integer> furniture() {
        if (furniture == null) furniture = new java.util.LinkedHashMap<>();
        return furniture;
    }

    public int furnitureCount(String id) {
        Integer n = furniture().get(id);
        return n == null ? 0 : n;
    }

    public void addFurniture(String id, int n) {
        int now = furnitureCount(id) + n;
        if (now <= 0) furniture().remove(id);
        else furniture().put(id, now);
    }

    public Map<String, Long> shopStocked() {
        if (shopStocked == null) shopStocked = new HashMap<>();
        return shopStocked;
    }

    public List<Tenant> tenantPool() {
        if (tenantPool == null) tenantPool = new ArrayList<>();
        return tenantPool;
    }

    public Map<String, Finders.State> finders() {
        if (finders == null) finders = new HashMap<>();
        return finders;
    }

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

    public Map<String, Agents.Agent> agents() {
        if (agents == null) agents = new HashMap<>();
        return agents;
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
