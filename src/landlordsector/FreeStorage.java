package landlordsector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;

/**
 * 1.4.0 (Andrzej: "owning a home on a planet opens up the storage for free, without a fee for keeping items
 * inside"). A home on a market unlocks its storage without the one-time fee, and while you own one there the monthly
 * storage fee for that market is taken back out of the month's report. The game adds that fee a little every economy
 * tick (CoreScript), so {@link #waiveFees()} runs on every tick, after the game's own listener.
 */
public final class FreeStorage {

    private static final Logger log = Global.getLogger(FreeStorage.class);

    private FreeStorage() {
    }

    /** Markets where you own a home (your own colonies included, though their storage is free anyway). */
    public static Set<String> homeMarkets() {
        Set<String> out = new HashSet<>();
        for (Property p : Portfolio.get().properties()) if (p.marketId != null) out.add(p.marketId);
        return out;
    }

    /** True when you own a home on this market. */
    public static boolean has(MarketAPI m) {
        return m != null && homeMarkets().contains(m.getId());
    }

    /** Opens the market's storage without the fee. True when it was locked before. */
    public static boolean unlock(MarketAPI m) {
        if (m == null) return false;
        try {
            SubmarketAPI sub = m.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            SubmarketPlugin plugin = sub != null ? sub.getPlugin() : null;
            if (!(plugin instanceof StoragePlugin)) return false;
            StoragePlugin storage = (StoragePlugin) plugin;
            if (storage.isPlayerPaidToUnlock()) return false;
            storage.setPlayerPaidToUnlock(true);
            return true;
        } catch (Throwable t) {
            log.warn("Landlordsector: could not open the storage on " + m.getId(), t);
            return false;
        }
    }

    /** Opens the storage on every market where you own a home (on load, for homes bought before 1.4.0). */
    public static void unlockAll() {
        for (Property p : Portfolio.get().properties()) unlock(p.market());
    }

    /**
     * A campaign listener added on every load. The game's CoreScript is an older listener on the same list, so this one
     * hears each economy tick after the game has added that tick's storage fee.
     */
    public static final class Listener extends com.fs.starfarer.api.campaign.BaseCampaignEventListener {
        public Listener() {
            super(false);
        }

        @Override
        public void reportEconomyTick(int iterIndex) {
            waiveFees();
        }
    }

    /** Takes this month's storage fee for markets with your home back out of the report. */
    public static void waiveFees() {
        try {
            MonthlyReport report = SharedData.getData().getCurrentReport();
            if (report == null) return;
            FDNode storage = report.getRoot().getChildren().get(MonthlyReport.STORAGE);
            if (storage == null || storage.getChildren() == null) return;
            Set<String> homes = homeMarkets();
            Iterator<Map.Entry<String, FDNode>> it = storage.getChildren().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, FDNode> e = it.next();
                if (!homes.contains(e.getKey())) continue;
                e.getValue().upkeep = 0f;
                it.remove();
            }
            if (storage.getChildren().isEmpty()) report.getRoot().getChildren().remove(MonthlyReport.STORAGE);
        } catch (Throwable t) {
            log.warn("Landlordsector: could not waive the storage fee", t);
        }
    }
}
