package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc.Token;

import landlordsector.MarketRules;

/** True when the market of the person (or place) the player is talking to sells homes to them now. */
public class LLS_CanBuy extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        try {
            MarketAPI market = marketOf(dialog);
            return MarketRules.isEligible(market) && !MarketRules.hostile(market);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The agent's own market when talking to one, otherwise the market of the place. */
    public static MarketAPI marketOf(InteractionDialogAPI dialog) {
        if (dialog == null) return null;
        PersonAPI person = dialog.getInteractionTarget() != null
                ? dialog.getInteractionTarget().getActivePerson() : null;
        if (person != null && person.getMarket() != null) return person.getMarket();
        return dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMarket() : null;
    }
}
