package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_baby [home]: your partner has a baby now. */
public class LlsBaby implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        Tenant t = p.partner();
        if (t == null || !p.playerHome) {
            Console.showMessage("You need a partner living with you there. lls_marry finds one.");
            return CommandResult.ERROR;
        }
        t.conceivedAt = 0L;
        t.toldPregnant = false;
        Family.birth(p, t, Lls.random());
        Child c = p.children().get(p.children().size() - 1);
        Console.showMessage(c.name + " was born.");
        return CommandResult.SUCCESS;
    }
}
