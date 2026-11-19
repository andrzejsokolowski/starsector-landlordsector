package landlordsector;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;

/**
 * 1.15.1 (Andrzej: "the -10 Security impact of Luddic Path Cells as a condition remains static across Active,
 * Disrupted, and Sleeper states"): how much a market's Luddic Path cells count for homes, read from the game's own
 * cells. Active cells count fully, disrupted ones (their base was destroyed) half, sleeper cells not at all. The game
 * also reports sleeper cells on the player's colonies while the player has the Pather agreement (the Planetkiller).
 */
public final class PatherCells {

    private PatherCells() {
    }

    /** 1 active, 0.5 disrupted, 0 sleeper or no cells. Cells the game can't tell us about count as active. */
    public static float threat(MarketAPI m) {
        if (m == null) return 0f;
        try {
            if (!m.hasCondition(Conditions.PATHER_CELLS)) return 0f;
            MarketConditionAPI c = m.getCondition(Conditions.PATHER_CELLS);
            Object plugin = c != null ? c.getPlugin() : null;
            LuddicPathCellsIntel intel = plugin instanceof LuddicPathCells ? ((LuddicPathCells) plugin).getIntel() : null;
            if (intel == null) intel = LuddicPathCellsIntel.getCellsForMarket(m);
            if (intel == null) return 1f;
            return Pricing.patherThreat(intel.isSleeper(), intel.getSleeperTimeout());
        } catch (Throwable e) {
            return 1f;
        }
    }
}
