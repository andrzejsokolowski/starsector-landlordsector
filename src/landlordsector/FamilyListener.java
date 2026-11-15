package landlordsector;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 1.2.0: docking at a market is a visit home; winning a battle brings your officers closer. Transient. */
public class FamilyListener extends BaseCampaignEventListener {

    public FamilyListener() {
        super(false);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        try {
            Family.visit(market);
        } catch (Throwable ignored) {
            // A visit that fails to count is not worth a crash.
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (battle == null || !battle.isPlayerInvolved() || primaryWinner == null) return;
            if (!battle.isOnPlayerSide(primaryWinner)) return;
            Family.raiseBonds(Family.BOND_PER_BATTLE, true);
        } catch (Throwable ignored) {
        }
    }
}
