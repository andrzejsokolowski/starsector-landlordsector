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
 * Property agents in market comm directories. About a quarter of eligible markets have one at a time
 * (more on big markets), each stays 3 to 6 months. The player's own colonies always have one.
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
     * Sends agents away whose time is up or whose market no longer sells homes, and brings new ones.
     *
     * @param firstRun true on the first load of a save: markets get an agent at the target share at once
     */
    public static void update(Random random, boolean firstRun) {
        Portfolio portfolio = Portfolio.get();
        Map<String, Agent> agents = portfolio.agents();
        Economy eco = TierTable.economy();

        Iterator<Map.Entry<String, Agent>> it = agents.entrySet().iterator();
        while (it.hasNext()) {
            Agent a = it.next().getValue();
            MarketAPI m = Global.getSector().getEconomy().getMarket(a.marketId);
            boolean keep = m != null && MarketRules.isEligible(m) && a.person != null;
            if (keep && a.stayDays > 0f
                    && Global.getSector().getClock().getElapsedDaysSince(a.arrivedAt) > a.stayDays) {
                keep = false;
            }
            // A permanent agent belongs to a player colony. A colony lost to someone else loses them.
            if (keep && a.stayDays <= 0f && !MarketRules.isOwn(m)) keep = false;
            if (!keep) {
                leave(a, m);
                it.remove();
            }
        }

        float avgStay = (eco.agentMinMonths + eco.agentMaxMonths) / 2f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (agents.containsKey(m.getId()) || !MarketRules.isEligible(m)) continue;
            boolean own = MarketRules.isOwn(m);
            float share = Pricing.agentShare(eco.agentShare, m.getSize());
            float chance = own ? 1f : (firstRun ? share : Pricing.agentArrival(share, avgStay));
            if (random.nextFloat() >= chance) continue;
            float months = eco.agentMinMonths + random.nextFloat() * (eco.agentMaxMonths - eco.agentMinMonths);
            Agent a = arrive(m, random, own ? 0f : months * DAYS_PER_MONTH);
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
