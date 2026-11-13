package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

import landlordsector.MarketRules;
import landlordsector.buy.BuyScreen;

/** Opens the property screen over the conversation with a property agent. */
public class LLS_OpenBuyScreen extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = LLS_CanBuy.marketOf(dialog);
        if (!MarketRules.isEligible(market)) return false;
        BuyScreen screen = new BuyScreen(market, null);
        dialog.showCustomVisualDialog(screen.width(), screen.height(), screen);
        return true;
    }
}
