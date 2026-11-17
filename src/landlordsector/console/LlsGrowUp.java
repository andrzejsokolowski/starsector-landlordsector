package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_growup [home]: every child in the home grows up now. */
public class LlsGrowUp implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        int n = 0;
        for (Child c : p.children()) {
            if (c.grown) continue;
            c.bornAt = Lls.daysAgo(Family.growUpDays() + 1f);
            n++;
        }
        if (n == 0) {
            Console.showMessage("No children are growing up in " + p.displayName() + ".");
            return CommandResult.SUCCESS;
        }
        Family.daily(Lls.random());
        Console.showMessage(n + (n == 1 ? " child" : " children") + " in " + p.displayName()
                + " grew up. Those with a wish started their life; talk to the others to choose one.");
        return CommandResult.SUCCESS;
    }
}
