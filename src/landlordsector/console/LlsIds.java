package landlordsector.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.*;
import landlordsector.data.*;

/** lls_ids <kind>: the ids the other commands take. */
public class LlsIds implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        String[] w = Lls.words(args);
        String kind = w.length > 0 ? w[0].toLowerCase() : "";
        StringBuilder sb = new StringBuilder();
        switch (kind) {
            case "sizes":
                for (Tier t : TierTable.all()) sb.append("  ").append(t.id).append(" (").append(t.name).append(")\n");
                break;
            case "housetraits":
                for (HouseTraits.Def d : HouseTraits.all()) {
                    if (Upgrades.get(d.id) != null || Services.get(d.id) != null) continue;
                    sb.append("  ").append(d.id).append(" (").append(d.name).append(d.good ? ", good" : ", bad")
                            .append(")\n");
                }
                break;
            case "tenanttraits":
                for (TenantTraits.Def d : TenantTraits.all()) sb.append("  ").append(d.id).append(" (").append(d.name)
                        .append(")\n");
                break;
            case "events":
                for (TenantEvents.Def d : TenantEvents.all()) sb.append("  ").append(d.id)
                        .append(d.tenant ? " (tenant)" : " (house)").append('\n');
                break;
            case "upgrades":
                for (Upgrades.Def d : Upgrades.of(Upgrades.Kind.RENOVATION)) sb.append("  ").append(d.id).append(" (")
                        .append(d.name).append(")\n");
                break;
            case "furniture":
                for (Upgrades.Def d : Upgrades.of(Upgrades.Kind.FURNITURE)) sb.append("  ").append(d.id).append(" (")
                        .append(d.name).append(")\n");
                break;
            case "services":
                for (Services.Def d : Services.all()) sb.append("  ").append(d.id).append(" (").append(d.name)
                        .append(")\n");
                break;
            default:
                return CommandResult.BAD_SYNTAX;
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }
}
