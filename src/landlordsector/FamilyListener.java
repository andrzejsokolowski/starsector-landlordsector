package landlordsector;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** 1.2.0: docking at a market is a visit home. Transient. */
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

}
