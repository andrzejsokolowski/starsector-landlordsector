package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import landlordsector.Actions;
import landlordsector.Finders;
import landlordsector.data.FinderDefs;

/** Adds "Hire" to a tenant finder's conversation, greyed out when the player cannot pay. */
public class LLS_FinderOptions extends BaseCommandPlugin {

    public static final String OPTION = "lls_hire_finder";

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        Finders.State s = stateOf(dialog);
        if (s == null || s.hired) return false;
        FinderDefs.Def d = s.def();
        if (d == null) return false;
        dialog.getOptionPanel().addOption("Hire " + d.name() + " for " + Misc.getDGSCredits(d.hire), OPTION);
        if (Actions.credits() < d.hire) {
            dialog.getOptionPanel().setEnabled(OPTION, false);
            dialog.getOptionPanel().setTooltip(OPTION, "Not enough credits.");
        }
        return true;
    }

    /** The finder the player is talking to, or null. */
    public static Finders.State stateOf(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getInteractionTarget() == null) return null;
        PersonAPI p = dialog.getInteractionTarget().getActivePerson();
        if (p == null) return null;
        Object id = p.getMemoryWithoutUpdate().get(Finders.FLAG);
        return id == null ? null : Finders.byFlag(String.valueOf(id));
    }
}
