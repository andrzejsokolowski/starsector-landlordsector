package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_condition <home> <0-100>: sets a home's condition. */
public class LlsCondition implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 1) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w.length > 1 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        Integer n = Lls.number(w[w.length - 1], 0, 100, "Condition");
        if (n == null) return CommandResult.ERROR;
        p.condition = n / 100f;
        p.condemned = n == 0;
        Console.showMessage(p.displayName() + " is at " + n + "%" + (p.condemned ? " and condemned." : "."));
        return CommandResult.SUCCESS;
    }
}
