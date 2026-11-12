package landlordsector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** The tier table and economy numbers, read from tiers.json at startup. */
public final class TierTable {

    public static final String PATH = "data/config/landlordsector/tiers.json";

    private static List<Tier> tiers = Collections.emptyList();
    private static Economy economy = new Economy();

    private TierTable() {
    }

    /** Reads the table. Throws when the file is broken, so the game stops at start with a clear error. */
    public static void load(JSONObject json) throws Exception {
        JSONArray array = json.getJSONArray("tiers");
        List<Tier> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject t = array.getJSONObject(i);
            list.add(new Tier(t.getString("id"), t.getString("name"), (float) t.getDouble("min"),
                    (float) t.getDouble("max"), (float) t.getDouble("rentShare"), t.getInt("minSize"),
                    t.getInt("slots"), (float) t.getDouble("weight"), (float) t.optDouble("comfort", 20 + 12 * i),
                    (float) t.optDouble("decor", 5 * i), i));
        }
        if (list.isEmpty()) throw new IllegalStateException("Landlordsector: " + PATH + " has no tiers");
        Economy e = new Economy();
        JSONObject ec = json.optJSONObject("economy");
        if (ec != null) {
            e.repairFullCost = num(ec, "repairFullCost", e.repairFullCost);
            e.wearMonths = Math.max(1f, num(ec, "wearMonths", e.wearMonths));
            e.landValue = num(ec, "landValue", e.landValue);
            e.brokerFee = num(ec, "brokerFee", e.brokerFee);
            e.ownColonyPrice = num(ec, "ownColonyPrice", e.ownColonyPrice);
            e.listingRefreshDays = Math.max(1f, num(ec, "listingRefreshDays", e.listingRefreshDays));
            e.npcBuyChance = num(ec, "npcBuyChance", e.npcBuyChance);
            e.npcSaleShare = num(ec, "npcSaleShare", e.npcSaleShare);
            e.propertyTax = num(ec, "propertyTax", e.propertyTax);
            e.homesPerSize = num(ec, "homesPerSize", e.homesPerSize);
            e.stabilityPerHome = num(ec, "stabilityPerHome", e.stabilityPerHome);
            e.stabilityMax = num(ec, "stabilityMax", e.stabilityMax);
            e.growthPerHome = num(ec, "growthPerHome", e.growthPerHome);
            e.growthMax = num(ec, "growthMax", e.growthMax);
            e.tipChance = num(ec, "tipChance", e.tipChance);
            e.tipShare = num(ec, "tipShare", e.tipShare);
            e.driftPerMonth = num(ec, "driftPerMonth", e.driftPerMonth);
            e.driftMin = num(ec, "driftMin", e.driftMin);
            e.driftMax = num(ec, "driftMax", e.driftMax);
            e.disruptionWear = num(ec, "disruptionWear", e.disruptionWear);
            e.raidWear = num(ec, "raidWear", e.raidWear);
            e.tacticalBombWear = num(ec, "tacticalBombWear", e.tacticalBombWear);
            e.agentShare = num(ec, "agentShare", e.agentShare);
            e.agentMinMonths = Math.max(1f, num(ec, "agentMinMonths", e.agentMinMonths));
            e.agentMaxMonths = Math.max(e.agentMinMonths, num(ec, "agentMaxMonths", e.agentMaxMonths));
        }
        tiers = Collections.unmodifiableList(list);
        economy = e;
    }

    private static float num(JSONObject o, String key, float fallback) {
        return o.has(key) && !o.isNull(key) ? (float) o.optDouble(key, fallback) : fallback;
    }

    public static List<Tier> all() {
        return tiers;
    }

    public static Economy economy() {
        return economy;
    }

    /** The tier with this id, or null when the table no longer has it. */
    public static Tier get(String id) {
        for (Tier t : tiers) if (t.id.equals(id)) return t;
        return null;
    }
}
