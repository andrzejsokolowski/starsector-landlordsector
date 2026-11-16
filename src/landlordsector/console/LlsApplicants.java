package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_applicants [home] [count]: people apply to rent an empty home. */
public class LlsApplicants implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        int count = 3;
        if (w.length > 1) {
            Integer n = Lls.number(w[1], 1, 10, "Count");
            if (n == null) return CommandResult.ERROR;
            count = n;
        }
        MarketAPI m = p.market();
        if (m == null || p.tenant != null || p.playerHome) {
            Console.showMessage("Only an empty home that you rent out takes applicants.");
            return CommandResult.ERROR;
        }
        java.util.Random r = Lls.random();
        for (int i = 0; i < count; i++) {
            p.applicants().add(Tenants.create(m.getFactionId(), m, 1f, false, "advert", r, 0,
                    java.util.Collections.<String, Float>emptyMap(), p.tier().index));
        }
        Console.showMessage(count + " people applied for " + p.displayName() + ". See them on its Tenants tab.");
        return CommandResult.SUCCESS;
    }
}
