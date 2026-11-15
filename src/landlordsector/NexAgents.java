package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;

/**
 * 1.2.0: a grown child becomes a Nexerelin agent. Nexerelin is optional: this class is the only one that names its
 * classes, and it is only loaded while Nexerelin is on ({@link Family#nexerelin()} is checked first).
 */
public final class NexAgents {

    private NexAgents() {
    }

    /** Hires the person as an agent of the player, the same way Nexerelin's bar event does. */
    public static void add(PersonAPI person, int level, MarketAPI market) {
        exerelin.campaign.intel.agents.AgentIntel intel = new exerelin.campaign.intel.agents.AgentIntel(person,
                Global.getSector().getPlayerFaction(), Math.max(1, Math.min(3, level)));
        exerelin.campaign.intel.agents.AgentIntel.Specialization spec =
                exerelin.campaign.intel.agents.AgentIntel.Specialization.pickRandomSpecialization();
        if (spec != null) intel.addSpecialization(spec);
        intel.init();
        if (market != null) intel.setMarket(market);
        intel.setImportant(true);
    }
}
