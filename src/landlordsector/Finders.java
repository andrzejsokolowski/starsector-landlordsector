package landlordsector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

import landlordsector.data.FinderDefs;

/**
 * Tenant finders: unique people from finders.json, each waiting in one comm directory of their faction
 * until the player hires them. Hired finders work from the Manage page: a search takes some days and
 * brings back screened tenants into the player's tenant pool.
 */
public final class Finders {

    public static final String FLAG = "$lls_finder";
    public static final String POST = "lls_tenant_finder";
    private static final Logger log = Global.getLogger(Finders.class);

    private Finders() {
    }

    /** One finder in this save. */
    public static class State {
        public String id;
        public PersonAPI person;
        public String marketId;
        public boolean hired;
        public boolean searching;
        public long searchStartedAt;

        public FinderDefs.Def def() {
            return FinderDefs.get(id);
        }

        /** Days left on the current search, 0 when idle or done. */
        public float daysLeft() {
            FinderDefs.Def d = def();
            if (!searching || d == null) return 0f;
            return Math.max(0f, d.days - Global.getSector().getClock().getElapsedDaysSince(searchStartedAt));
        }
    }

    /** Places every finder not yet in this save, and moves unhired ones whose market is gone. */
    public static void place(Random random) {
        Map<String, State> states = Portfolio.get().finders();
        for (FinderDefs.Def d : FinderDefs.all()) {
            State s = states.get(d.id);
            if (s != null && s.hired) continue;
            MarketAPI here = s == null ? null : Global.getSector().getEconomy().getMarket(s.marketId);
            if (s != null && here != null && here.isInEconomy() && s.person != null) continue;
            if (s != null) leave(s);
            MarketAPI m = pickMarket(d.faction, random);
            if (m == null) continue;
            State n = arrive(d, m, random);
            if (n != null) states.put(d.id, n);
        }
    }

    private static MarketAPI pickMarket(String factionId, Random random) {
        List<MarketAPI> own = new ArrayList<>();
        List<MarketAPI> any = new ArrayList<>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m == null || !m.isInEconomy() || m.isHidden() || m.isPlanetConditionMarketOnly() || m.isPlayerOwned()) {
                continue;
            }
            if (factionId.equals(m.getFactionId())) own.add(m);
            if (MarketRules.isEligible(m)) any.add(m);
        }
        List<MarketAPI> from = own.isEmpty() ? any : own;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    private static State arrive(FinderDefs.Def d, MarketAPI m, Random random) {
        try {
            FactionAPI faction = m.getFaction();
            if (faction == null) return null;
            PersonAPI p = faction.createRandomPerson(random);
            if (p == null) return null;
            FullName.Gender g = "male".equals(d.gender) ? FullName.Gender.MALE
                    : ("female".equals(d.gender) ? FullName.Gender.FEMALE : p.getGender());
            if (g != p.getGender() && g != FullName.Gender.ANY) {
                PersonAPI q = faction.createRandomPerson(g, random);
                if (q != null) p = q;
            }
            p.setName(new FullName(d.first, d.last, p.getGender()));
            if (d.portrait != null && !d.portrait.isEmpty()) p.setPortraitSprite(d.portrait);
            p.setRankId(POST);
            p.setPostId(POST);
            p.setVoice(Voices.BUSINESS);
            p.setImportance(PersonImportance.HIGH);
            p.getMemoryWithoutUpdate().set(FLAG, d.id);
            p.getMemoryWithoutUpdate().set("$lls_isFinder", true);
            p.getMemoryWithoutUpdate().set("$lls_finderPitch", d.pitch);
            p.getMemoryWithoutUpdate().set("$lls_finderFee", Misc.getDGSCredits(d.hire));
            p.setMarket(m);
            m.addPerson(p);
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.addPerson(p);
            State s = new State();
            s.id = d.id;
            s.person = p;
            s.marketId = m.getId();
            return s;
        } catch (Throwable t) {
            log.warn("Landlordsector: tenant finder " + d.id + " could not be placed on " + m.getId(), t);
            return null;
        }
    }

    private static void leave(State s) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(s.marketId);
        if (m == null || s.person == null) return;
        try {
            CommDirectoryAPI dir = m.getCommDirectory();
            if (dir != null) dir.removePerson(s.person);
            m.removePerson(s.person);
        } catch (Throwable t) {
            log.warn("Landlordsector: tenant finder " + s.id + " could not be removed", t);
        }
    }

    public static State byFlag(String finderId) {
        return finderId == null ? null : Portfolio.get().finders().get(finderId);
    }

    public static List<State> hired() {
        List<State> out = new ArrayList<>();
        for (FinderDefs.Def d : FinderDefs.all()) {
            State s = Portfolio.get().finders().get(d.id);
            if (s != null && s.hired) out.add(s);
        }
        return out;
    }

    /** Null when hired, or the reason not. */
    public static String hire(State s) {
        FinderDefs.Def d = s == null ? null : s.def();
        if (d == null) return "Nobody to hire.";
        if (s.hired) return d.name() + " already works for you.";
        if (Actions.credits() < d.hire) return "Not enough credits.";
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(d.hire);
        s.hired = true;
        leave(s);
        return null;
    }

    /** Null when the search started, or the reason not. */
    public static String startSearch(State s) {
        FinderDefs.Def d = s == null ? null : s.def();
        if (d == null || !s.hired) return "That finder does not work for you.";
        if (s.searching) return d.name() + " is already searching.";
        if (Actions.credits() < d.search) return "Not enough credits for the search.";
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(d.search);
        s.searching = true;
        s.searchStartedAt = Global.getSector().getClock().getTimestamp();
        return null;
    }

    /** Finished searches put their tenants in the pool. Called once a day. */
    public static void deliver(Random random) {
        for (State s : hired()) {
            FinderDefs.Def d = s.def();
            if (!s.searching || d == null || s.daysLeft() > 0f) continue;
            s.searching = false;
            for (int i = 0; i < d.tenants; i++) {
                Tenant t = Tenants.create(d.profile, null, d.badTraits, true, d.id, random);
                Portfolio.get().tenantPool().add(t);
            }
            Portfolio.get().news = true;
            Notify.line(d.name() + " found " + d.tenants + " tenants. They wait in your tenant pool.", true);
        }
    }
}
