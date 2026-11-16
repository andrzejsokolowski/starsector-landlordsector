package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_tenant [home] [faction]: a new screened tenant moves in at once. */
public class LlsTenant implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        MarketAPI m = p.market();
        if (m == null) {
            Console.showMessage("The colony of " + p.displayName() + " is gone.");
            return CommandResult.ERROR;
        }
        if (p.playerHome) {
            Console.showMessage("You live in " + p.displayName() + ". lls_marry finds you a partner instead.");
            return CommandResult.ERROR;
        }
        if (p.tenant != null) {
            Console.showMessage(p.tenant.name + " already lives there. lls_evict first.");
            return CommandResult.ERROR;
        }
        String faction = w.length > 1 ? w[1] : m.getFactionId();
        if (Global.getSector().getFaction(faction) == null) {
            Console.showMessage("No faction \"" + faction + "\".");
            return CommandResult.ERROR;
        }
        Tenant t = Tenants.create(faction, m, 1f, true, "advert", Lls.random(), 0,
                java.util.Collections.<String, Float>emptyMap(), p.tier().index);
        t.since = Global.getSector().getClock().getTimestamp();
        p.tenant = t;
        p.advertising = false;
        p.applicants().clear();
        if (p.condemned) {
            p.condemned = false;
            p.condition = Math.max(p.condition, 0.05f);
        }
        p.addLog(t.name + " moved in.", true, t.name + " moved in.");
        Console.showMessage(t.name + " (" + faction + ", wealth " + TenantRules.wealthLetter(t.wealth)
                + ") moved into " + p.displayName() + ". Traits: "
                + (t.traits().isEmpty() ? "none" : String.join(", ", t.traits())) + ".");
        return CommandResult.SUCCESS;
    }
}
