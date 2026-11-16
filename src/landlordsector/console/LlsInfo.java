package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_info [home]: everything about one home. */
public class LlsInfo implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) return CommandResult.WRONG_CONTEXT;
        String[] w = Lls.words(args);
        Property p = Lls.home(w.length > 0 ? w[0] : null, context);
        if (p == null) return CommandResult.ERROR;
        PropertyStats st = p.stats();
        StringBuilder sb = new StringBuilder(Lls.line(p) + " (id " + p.id + ")\n");
        sb.append("  Value ").append(Lls.credits(p.value())).append(", paid ").append(Lls.credits(p.paidPrice))
                .append(", rent so far ").append(Lls.credits(p.totalRent)).append('\n');
        sb.append("  Condition ").append(Math.round(p.condition * 100f)).append("%")
                .append(p.condemned ? " (condemned)" : "").append(", rent setting ").append(p.setting().label)
                .append(p.advertising ? ", advertising" : "").append('\n');
        if (st != null) {
            sb.append("  Location ").append(st.grade).append(", comfort ").append(Math.round(st.comfort))
                    .append(", decor ").append(Math.round(st.decor)).append(", security ")
                    .append(Math.round(st.security)).append('\n');
        }
        sb.append("  House traits: ").append(p.traits().isEmpty() ? "none" : String.join(", ", p.traits())).append('\n');
        sb.append("  Upgrades and furniture: ").append(p.upgrades().isEmpty() ? "none" : "");
        for (int i = 0; i < p.upgrades().size(); i++) sb.append(i > 0 ? ", " : "").append(p.upgrades().get(i).id);
        sb.append('\n');
        sb.append("  Services: ").append(p.services().isEmpty() ? "none" : String.join(", ", p.services())).append('\n');
        Tenant t = p.tenant;
        if (t != null) {
            sb.append("  ").append(t.isPartner() ? "Partner" : "Tenant").append(": ").append(t.name).append(" (")
                    .append(t.factionId).append("), wealth ").append(TenantRules.wealthLetter(t.wealth))
                    .append(", mood ").append(Math.round(t.mood));
            if (st != null) sb.append(", happiness ").append(Math.round(st.happiness)).append(", health ")
                    .append(Math.round(st.health));
            sb.append("\n  Tenant traits: ").append(t.traits().isEmpty() ? "none" : String.join(", ", t.traits()))
                    .append('\n');
        } else if (!p.playerHome) {
            sb.append("  No tenant, ").append(p.applicants().size()).append(" applicants\n");
        }
        for (Child c : p.children()) {
            sb.append("  Child: ").append(c.name).append(", ").append(Family.age(c))
                    .append(c.grown ? ", grown" : "").append(c.path != null ? ", path " + c.path : "").append('\n');
        }
        java.util.List<String> log = p.log();
        int from = Math.max(0, log.size() - 5);
        if (!log.isEmpty()) sb.append("  Last log lines:\n");
        for (int i = from; i < log.size(); i++) sb.append("    ").append(log.get(i).replace('\t', ' ')).append('\n');
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }
}
