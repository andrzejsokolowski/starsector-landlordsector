package landlordsector;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * Property agents in market comm directories: one on every market that sells homes.
 */
public final class Agents {

    /** Person memory flag the rules look for. */
    public static final String FLAG = "$lls_agent";
    /** Post and rank id, named "Property Agent" in data/world/factions/default_ranks.json. */
    public static final String POST = "lls_property_agent";
    private static final Logger log = Global.getLogger(Agents.class);
    private static final float DAYS_PER_MONTH = 30f;

    private Agents() {
    }

    /** One agent on one market. Saved in the portfolio. */
    public static class Agent {
        public String marketId;
        public PersonAPI person;
        public long arrivedAt;
        /** Days the agent stays. 0 or less: stays for good (the player's own colonies). */
        public float stayDays;
    }

    /**
     * Every market that sells homes has one property agent, for good (Andrzej, 2026-11-12: agents were too
     * hard to find at one in four). Agents whose market no longer sells homes leave. Agents from 0.2 to 0.3.1
     * that were due to move on now stay.
     *
     * @param firstRun unused since 0.3.2, kept for the callers
     */
    public static void update(Random random, boolean firstRun) {
        Portfolio portfolio = Portfolio.get();
        Map<String, Agent> agents = portfolio.agents();

        Iterator<Map.Entry<String, Agent>> it = agents.entrySet().iterator();
        while (it.hasNext()) {
            Agent a = it.next().getValue();
            MarketAPI m = Global.getSector().getEconomy().getMarket(a.marketId);
            if (m != null && MarketRules.isEligible(m) && a.person != null) {
                a.stayDays = 0f;
                continue;
            }
            leave(a, m);
            it.remove();
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (agents.containsKey(m.getId()) || !MarketRules.isEligible(m)) continue;
            Agent a = arrive(m, random, 0f);
            if (a != null) agents.put(m.getId(), a);
        }
    }

    /** True when this market has a property agent right now. */
    public static boolean hasAgent(MarketAPI market) {
        return market != null && Portfolio.get().agents().containsKey(market.getId());
    }

    private static Agent arrive(MarketAPI m, Random random, float stayDays) {
        try {
            FactionAPI faction = m.getFaction();
            if (faction == null) return null;
            PersonAPI p = faction.createRandomPerson(random);
            if (p == null) return null;
            p.setRankId(POST);
            p.setPostId(POST);
            p.setVoice(Voices.BUSINESS);
            p.setImportance(PersonImportance.MEDIUM);
            p.getMemoryWithoutUpdate().set(FLAG, true);
            p.setMarket(m);
            m.addPerson(p);
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.addPerson(p);
            Agent a = new Agent();
            a.marketId = m.getId();
            a.person = p;
            a.arrivedAt = Global.getSector().getClock().getTimestamp();
            a.stayDays = stayDays;
            return a;
        } catch (Throwable t) {
            log.warn("Landlordsector: no property agent could be placed on " + m.getId(), t);
            return null;
        }
    }

    private static void leave(Agent a, MarketAPI m) {
        if (m == null || a.person == null) return;
        try {
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.removePerson(a.person);
            m.removePerson(a.person);
        } catch (Throwable t) {
            log.warn("Landlordsector: the property agent on " + m.getId() + " could not be removed", t);
        }
    }
}
