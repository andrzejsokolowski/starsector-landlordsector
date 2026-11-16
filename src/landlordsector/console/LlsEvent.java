package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_event <home> <event id>: makes a house or tenant event happen now. */
public class LlsEvent implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length != 2) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        TenantEvents.Def e = TenantEvents.get(w[1]);
        if (e == null) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (TenantEvents.Def x : TenantEvents.all()) ids.add(x.id);
            Lls.unknown("event", w[1], ids, "events");
            return CommandResult.ERROR;
        }
        if (p.market() == null) {
            Console.showMessage("The colony of " + p.displayName() + " is gone.");
            return CommandResult.ERROR;
        }
        TenantDaily.apply(p, p.market(), e);
        Console.showMessage("Event " + e.id + " happened at " + p.displayName() + ". See its log.");
        return CommandResult.SUCCESS;
    }
}
