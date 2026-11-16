package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_repairall: every home back to 100%, free, marks cleaned off. */
public class LlsRepairAll implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        int n = 0;
        for (Property p : Portfolio.get().properties()) {
            p.condition = 1f;
            p.condemned = false;
            p.traits().removeIf(id -> {
                HouseTraits.Def d = HouseTraits.get(id);
                return d != null && d.mark;
            });
            n++;
        }
        Console.showMessage("Repaired " + n + (n == 1 ? " home." : " homes."));
        return CommandResult.SUCCESS;
    }
}
