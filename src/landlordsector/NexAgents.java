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
        add(person, level, market, false);
    }

    /**
     * 1.8.0: the level is capped by Nexerelin's own maximum; a kid who took extra classes learns a second specialty.
     */
    public static void add(PersonAPI person, int level, MarketAPI market, boolean secondSpecialty) {
        int max = Math.max(1, exerelin.campaign.intel.agents.AgentIntel.MAX_LEVEL);
        exerelin.campaign.intel.agents.AgentIntel intel = new exerelin.campaign.intel.agents.AgentIntel(person,
                Global.getSector().getPlayerFaction(), Math.max(1, Math.min(max, level)));
        exerelin.campaign.intel.agents.AgentIntel.Specialization spec =
                exerelin.campaign.intel.agents.AgentIntel.Specialization.pickRandomSpecialization();
        if (spec != null) intel.addSpecialization(spec);
        for (int tries = 0; secondSpecialty && tries < 10; tries++) {
            exerelin.campaign.intel.agents.AgentIntel.Specialization more =
                    exerelin.campaign.intel.agents.AgentIntel.Specialization.pickRandomSpecialization();
            if (more != null && more != spec) {
                intel.addSpecialization(more);
                break;
            }
        }
        intel.init();
        if (market != null) intel.setMarket(market);
        intel.setImportant(true);
    }
}
