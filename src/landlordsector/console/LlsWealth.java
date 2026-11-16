package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_wealth [home] <1-6>: sets the tenant's wealth, E (1) to S (6). */
public class LlsWealth implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 1) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w.length > 1 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        Tenant t = Lls.tenant(p);
        if (t == null) return CommandResult.ERROR;
        Integer n = Lls.number(w[w.length - 1], 1, TenantRules.MAX_WEALTH, "Wealth");
        if (n == null) return CommandResult.ERROR;
        t.wealth = n;
        Console.showMessage(t.name + "'s wealth is " + TenantRules.wealthLetter(n) + ".");
        return CommandResult.SUCCESS;
    }
}
