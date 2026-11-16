package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_sell [home]: sells a home at its price, as from the Real estate tab. */
public class LlsSell implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        String no = Actions.cannotSell(p);
        if (no != null) {
            Console.showMessage(no);
            return CommandResult.ERROR;
        }
        Console.showMessage(Actions.sell(p));
        return CommandResult.SUCCESS;
    }
}
