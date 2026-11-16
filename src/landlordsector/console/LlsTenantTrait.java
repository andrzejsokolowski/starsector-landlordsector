package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_tenanttrait <home> <add|remove|reveal> [trait id]: changes or reveals the tenant's traits. */
public class LlsTenantTrait implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        if (w.length < 2) return CommandResult.BAD_SYNTAX;
        Property p = Lls.home(w[0], context);
        if (p == null) return CommandResult.ERROR;
        Tenant t = Lls.tenant(p);
        if (t == null) return CommandResult.ERROR;
        String verb = w[1].toLowerCase();
        if ("reveal".equals(verb)) {
            t.screened = true;
            t.shown = t.traits().size();
            Console.showMessage("All of " + t.name + "'s traits are known: "
                    + (t.traits().isEmpty() ? "none" : String.join(", ", t.traits())) + ".");
            return CommandResult.SUCCESS;
        }
        if (w.length != 3 || !("add".equals(verb) || "remove".equals(verb))) return CommandResult.BAD_SYNTAX;
        TenantTraits.Def d = TenantTraits.get(w[2]);
        if (d == null) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (TenantTraits.Def x : TenantTraits.all()) ids.add(x.id);
            Lls.unknown("tenant trait", w[2], ids, "tenanttraits");
            return CommandResult.ERROR;
        }
        if ("add".equals(verb)) {
            if (!t.traits().contains(d.id)) t.traits().add(d.id);
            Console.showMessage(t.name + " is now " + d.name + ".");
        } else {
            Console.showMessage(t.traits().remove(d.id) ? t.name + " is no longer " + d.name + "."
                    : t.name + " was not " + d.name + ".");
        }
        return CommandResult.SUCCESS;
    }
}
