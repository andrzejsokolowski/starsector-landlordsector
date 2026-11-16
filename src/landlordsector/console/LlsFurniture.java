package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_furniture <furniture id|all> [count]: puts furniture in your furniture storage. */
public class LlsFurniture implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 1 || w.length > 2) return CommandResult.BAD_SYNTAX;
        int count = 1;
        if (w.length > 1) {
            Integer n = Lls.number(w[1], 1, 100, "Count");
            if (n == null) return CommandResult.ERROR;
            count = n;
        }
        java.util.List<Upgrades.Def> all = Upgrades.of(Upgrades.Kind.FURNITURE);
        if ("all".equalsIgnoreCase(w[0])) {
            for (Upgrades.Def d : all) Portfolio.get().addFurniture(d.id, count);
            Console.showMessage("Added " + count + " of each of the " + all.size()
                    + " pieces of furniture to your storage.");
            return CommandResult.SUCCESS;
        }
        Upgrades.Def d = Upgrades.get(w[0]);
        if (d == null || !d.furniture()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Upgrades.Def x : all) ids.add(x.id);
            Lls.unknown("furniture", w[0], ids, "furniture");
            return CommandResult.ERROR;
        }
        Portfolio.get().addFurniture(d.id, count);
        Console.showMessage("Added " + count + " " + d.name + " to your storage ("
                + Portfolio.get().furnitureCount(d.id) + " in all).");
        return CommandResult.SUCCESS;
    }
}
