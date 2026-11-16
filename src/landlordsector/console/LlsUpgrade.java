package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_upgrade <home> <add|remove> <upgrade id>: builds or removes an upgrade, free and at once. */
public class LlsUpgrade implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length != 3) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        boolean add = "add".equalsIgnoreCase(w[1]);
        if (!add && !"remove".equalsIgnoreCase(w[1])) return CommandResult.BAD_SYNTAX;
        Upgrades.Def d = Upgrades.get(w[2]);
        if (d == null || d.furniture()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Upgrades.Def x : Upgrades.of(Upgrades.Kind.RENOVATION)) ids.add(x.id);
            Lls.unknown("upgrade", w[2], ids, "upgrades");
            return CommandResult.ERROR;
        }
        if (add) {
            if (p.has(d.id)) {
                Console.showMessage(p.displayName() + " already has " + d.name + ".");
                return CommandResult.SUCCESS;
            }
            p.upgrades().add(Installed.done(d.id));
            p.addLog("Built: " + d.name + ".", true, "Built: " + d.name + ".");
            Console.showMessage("Built " + d.name + " in " + p.displayName() + ".");
        } else {
            boolean had = p.upgrades().removeIf(i -> d.id.equals(i.id) && !i.furniture);
            Console.showMessage(had ? "Removed " + d.name + " from " + p.displayName() + "."
                    : p.displayName() + " has no " + d.name + ".");
        }
        return CommandResult.SUCCESS;
    }
}
