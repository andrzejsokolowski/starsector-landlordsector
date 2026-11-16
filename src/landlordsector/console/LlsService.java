package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_service <home> <service id>: hires or cancels a service. */
public class LlsService implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length != 2) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        if (Services.get(w[1]) == null) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Services.Def x : Services.all()) ids.add(x.id);
            Lls.unknown("service", w[1], ids, "services");
            return CommandResult.ERROR;
        }
        Console.showMessage(Actions.toggleService(p, w[1]));
        return CommandResult.SUCCESS;
    }
}
