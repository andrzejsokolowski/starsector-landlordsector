package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_month: runs the end-of-month pass now: rent, wear, reputation, the family's money. */
public class LlsMonth implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        new MonthlyTick().runMonth();
        Console.showMessage("The month's rent pass ran. Rent shows in this month's income report, paid at the "
                + "month's end like the rest of your income.");
        return CommandResult.SUCCESS;
    }
}
