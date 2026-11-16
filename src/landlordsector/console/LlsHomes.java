package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_homes: every home you own, numbered for the other commands. */
public class LlsHomes implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        java.util.List<Property> all = Portfolio.get().properties();
        if (all.isEmpty()) {
            Console.showMessage("You own no homes. lls_givehome gives you one.");
            return CommandResult.SUCCESS;
        }
        StringBuilder sb = new StringBuilder("Your homes (" + all.size() + "):\n");
        for (Property p : all) {
            sb.append("  ").append(Lls.line(p)).append(": ").append(Math.round(p.condition * 100f)).append("%");
            if (p.condemned) sb.append(", condemned");
            if (p.playerHome) sb.append(", your home");
            else if (p.tenant != null) sb.append(", tenant ").append(p.tenant.name);
            else sb.append(", empty");
            if (p.isRented()) sb.append(", rent about ").append(Lls.credits(p.expectedRent()));
            sb.append('\n');
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }
}
