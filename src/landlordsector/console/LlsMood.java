package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_mood [home] <-30 to 30>: sets the tenant's mood (adds to happiness). */
public class LlsMood implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 1) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w.length > 1 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        Tenant t = Lls.tenant(p);
        if (t == null) return CommandResult.ERROR;
        Integer n = Lls.number(w[w.length - 1], -30, 30, "Mood");
        if (n == null) return CommandResult.ERROR;
        t.mood = n;
        Console.showMessage(t.name + "'s mood is " + n + ". It drifts back to 0 by 1 a day.");
        return CommandResult.SUCCESS;
    }
}
