package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_givehome [size]: a new home of that size on the market you are docked at, free. */
public class LlsGiveHome implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        MarketAPI m = Lls.docked(context);
        if (m == null) return CommandResult.ERROR;
        String[] w = Lls.words(args);
        java.util.List<Tier> tiers = TierTable.all();
        Tier t = null;
        if (w.length == 0) {
            java.util.List<Tier> fit = MarketRules.tiersFor(m);
            t = (fit.isEmpty() ? tiers : fit).get(Lls.random().nextInt((fit.isEmpty() ? tiers : fit).size()));
        } else {
            String typed = Lls.rest(w, 0).toLowerCase().replace(' ', '_');
            for (Tier x : tiers) {
                if (x.id.equalsIgnoreCase(typed) || x.name.equalsIgnoreCase(Lls.rest(w, 0))
                        || String.valueOf(x.index + 1).equals(typed)) t = x;
            }
            if (t == null) {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (Tier x : tiers) ids.add(x.id);
                Lls.unknown("size", typed, ids, "sizes");
                return CommandResult.ERROR;
            }
        }
        Listing l = MarketRules.make(m, t, MarketRules.listings(m), false, Lls.random());
        Property p = Actions.own(m, l, 0f);
        p.addLog("Given by the console.");
        Console.showMessage("You now own " + Lls.line(p) + ".");
        return CommandResult.SUCCESS;
    }
}
