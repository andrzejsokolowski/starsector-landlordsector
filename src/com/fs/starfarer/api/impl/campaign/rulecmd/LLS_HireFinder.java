package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import landlordsector.Finders;
import landlordsector.data.FinderDefs;

/** Hires the tenant finder the player is talking to. */
public class LLS_HireFinder extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        Finders.State s = LLS_FinderOptions.stateOf(dialog);
        if (s == null) return false;
        FinderDefs.Def d = s.def();
        String no = Finders.hire(s);
        if (no != null) {
            dialog.getTextPanel().addPara(no);
            return false;
        }
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        dialog.getTextPanel().addPara("You paid " + Misc.getDGSCredits(d.hire) + ". " + d.name()
                + " works for you now. Send them on searches from the Manage screen.", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.getDGSCredits(d.hire));
        return true;
    }
}
