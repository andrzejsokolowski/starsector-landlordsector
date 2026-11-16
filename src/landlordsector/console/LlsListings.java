package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_listings [reroll]: the homes for sale where you are docked; reroll lists new ones. */
public class LlsListings implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        MarketAPI m = Lls.docked(context);
        if (m == null) return CommandResult.ERROR;
        String[] w = Lls.words(args);
        if (w.length > 0 && !"reroll".equalsIgnoreCase(w[0])) return CommandResult.BAD_SYNTAX;
        if (!MarketRules.isEligible(m)) {
            Console.showMessage(m.getName() + " sells no homes (too small, hidden, or Luddic Path).");
            return CommandResult.SUCCESS;
        }
        if (w.length > 0) Portfolio.get().listings().remove(m.getId());
        MarketListings ml = MarketRules.listings(m);
        StringBuilder sb = new StringBuilder((w.length > 0 ? "New homes for sale on " : "Homes for sale on ")
                + m.getName() + " (" + ml.list().size() + "):\n");
        for (Listing l : ml.list()) {
            Tier t = TierTable.get(l.tierId);
            sb.append("  ").append(l.name).append(" (").append(t != null ? t.name : l.tierId).append("), ")
                    .append(Lls.credits(MarketRules.buyPrice(m, l)));
            if (l.traits != null && !l.traits.isEmpty()) sb.append(", ").append(String.join(", ", l.traits));
            sb.append('\n');
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }
}
