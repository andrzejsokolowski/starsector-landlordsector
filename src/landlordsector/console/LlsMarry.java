package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_marry <home> <name>: one of your officers or administrators marries you and moves into the home. */
public class LlsMarry implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 2) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        String typed = Lls.rest(w, 1).toLowerCase();
        com.fs.starfarer.api.characters.PersonAPI who = null;
        java.util.List<com.fs.starfarer.api.characters.PersonAPI> people = new java.util.ArrayList<>();
        for (com.fs.starfarer.api.characters.OfficerDataAPI o : Global.getSector().getPlayerFleet().getFleetData()
                .getOfficersCopy()) people.add(o.getPerson());
        for (com.fs.starfarer.api.characters.AdminData a : Global.getSector().getCharacterData().getAdmins()) {
            people.add(a.getPerson());
        }
        for (com.fs.starfarer.api.characters.PersonAPI x : people) {
            if (x != null && x.getNameString().toLowerCase().contains(typed)) {
                who = x;
                break;
            }
        }
        if (who == null) {
            Console.showMessage("None of your officers or administrators is called \"" + Lls.rest(w, 1) + "\".");
            return CommandResult.ERROR;
        }
        if (p.partner() != null) {
            Console.showMessage("You are already married to " + p.partner().name + ".");
            return CommandResult.ERROR;
        }
        if (!p.playerHome) {
            String no = Family.cannotDeclare(p);
            if (no != null) {
                Console.showMessage("You can't live in " + p.displayName() + ": " + no);
                return CommandResult.ERROR;
            }
            Family.declareHome(p);
        }
        Console.showMessage(Family.propose(p, who, Lls.random()));
        return CommandResult.SUCCESS;
    }
}
