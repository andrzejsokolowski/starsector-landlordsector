package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_housetrait <home> <add|remove> <trait id>: adds or removes a house trait. */
public class LlsHouseTrait implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length != 3) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        boolean add = "add".equalsIgnoreCase(w[1]);
        if (!add && !"remove".equalsIgnoreCase(w[1])) return CommandResult.BAD_SYNTAX;
        String id = w[2];
        HouseTraits.Def d = HouseTraits.get(id);
        if (d == null || Upgrades.get(id) != null || Services.get(id) != null) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (HouseTraits.Def x : HouseTraits.all()) ids.add(x.id);
            Lls.unknown("house trait", id, ids, "housetraits");
            return CommandResult.ERROR;
        }
        if (add) {
            if (!p.traits().contains(id)) p.traits().add(id);
            Console.showMessage(p.displayName() + " is now " + d.name + ".");
        } else {
            boolean had = p.traits().remove(id);
            Console.showMessage(had ? p.displayName() + " is no longer " + d.name + "."
                    : p.displayName() + " was not " + d.name + ".");
        }
        return CommandResult.SUCCESS;
    }
}
