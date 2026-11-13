package landlordsector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.data.TenantEvents;

/**
 * Once a game day: tenant moods settle, advertised homes get applicants, tenants have their events
 * (at most one per home every 5 days), very unhappy tenants may move out, finders come back with tenants,
 * and old applicants and pool tenants give up. Transient, added on every load.
 */
public class TenantDaily implements EveryFrameScript {

    private static final Logger log = Global.getLogger(TenantDaily.class);
    private final Random random = new Random();
    private long lastDay = Long.MIN_VALUE;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        long now = Global.getSector().getClock().getTimestamp();
        if (lastDay == Long.MIN_VALUE) {
            lastDay = now;
            return;
        }
        if (Global.getSector().getClock().getElapsedDaysSince(lastDay) < 1f) return;
        lastDay = now;
        try {
            runDay(random);
        } catch (Throwable t) {
            log.error("Landlordsector: the daily tenant pass failed", t);
        }
    }

    /** One day for every home. Public for the headless check. */
    public static void runDay(Random random) {
        Portfolio portfolio = Portfolio.get();
        for (Property p : new ArrayList<>(portfolio.properties())) {
            MarketAPI m = p.market();
            if (m == null) continue;
            if (p.tenant != null) {
                p.tenant.mood = settle(p.tenant.mood);
                if (p.condemned) {
                    Notify.event(p, p.tenant.name + " moved out of " + p.displayName() + ": it is condemned.", false,
                            true);
                    p.tenant = null;
                    continue;
                }
                rollEvent(p, m, random);
                if (p.tenant != null) maybeMoveOut(p, random);
            } else {
                applicants(p, m, random);
            }
        }
        Finders.deliver(random);
        expirePool();
    }

    private static float settle(float mood) {
        if (mood > 1f) return mood - 1f;
        if (mood < -1f) return mood + 1f;
        return 0f;
    }

    private static void applicants(Property p, MarketAPI m, Random random) {
        Iterator<Tenant> it = p.applicants().iterator();
        while (it.hasNext()) {
            Tenant a = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(a.since) > TenantRules.APPLICANT_DAYS) it.remove();
        }
        if (!p.advertising || p.condemned || MarketRules.hostile(m)) return;
        if (p.applicants().size() >= TenantRules.MAX_APPLICANTS) return;
        PropertyStats st = p.stats();
        if (random.nextFloat() >= TenantRules.arrivalChance(st.marketMult, st.comfort, p.setting())) return;
        float bad = p.screeningService ? TenantRules.SCREENED_BAD_MULT : 1f;
        // Only people who can pay for the home and would live in it answer the advert.
        Tenant a = null;
        for (int i = 0; i < 6 && a == null; i++) {
            Tenant c = Tenants.create(m.getFactionId(), m, bad, false, "advert", random, 0,
                    Tenants.fitBoost(p.traits()), p.tier().index);
            if (TenantRules.fits(p.tier().index, p.setting(), c.wealth, c.homeShift())) a = c;
        }
        if (a == null) return;
        p.applicants().add(a);
        p.addLog(a.name + " applied to rent " + p.displayName() + ".");
        Portfolio.get().news = true;
    }

    private static void rollEvent(Property p, MarketAPI m, Random random) {
        if (Global.getSector().getClock().getElapsedDaysSince(p.lastEventAt) < TenantEvents.minDaysBetween()) return;
        if (random.nextFloat() >= TenantEvents.dailyChance()) return;
        PropertyStats st = p.stats();
        List<TenantEvents.Def> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0f;
        for (TenantEvents.Def d : TenantEvents.all()) {
            if (d.leave) continue;
            if (!d.requires.isEmpty()) {
                boolean ok = false;
                for (String tr : d.requires) if (p.tenant.has(tr)) ok = true;
                if (!ok) continue;
            }
            float w = TenantRules.eventWeight(d.good, d.damage, d.crime, d.weight, p.tenant.eventMult(d.id),
                    st.happiness, p.setting(), p.tier().index, st.security);
            if (w <= 0f) continue;
            pool.add(d);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty()) return;
        float r = random.nextFloat() * total;
        TenantEvents.Def pick = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r <= 0f) {
                pick = pool.get(i);
                break;
            }
        }
        apply(p, m, pick);
    }

    /** Applies one event. Public for the headless check. */
    public static void apply(Property p, MarketAPI m, TenantEvents.Def e) {
        p.lastEventAt = Global.getSector().getClock().getTimestamp();
        Tenant t = p.tenant;
        String text = e.text.replace("$tenant", t != null ? t.name : "The tenant").replace("$home", p.displayName());
        if (e.condition != 0f && !p.condemned) {
            p.condition = Pricing.clamp(p.condition + e.condition, 0f, 1f);
            if (p.condition <= 0f) p.condemned = true;
        }
        if (t != null) t.mood = Pricing.clamp(t.mood + e.mood, -30f, 30f);
        if (e.decor != 0f) p.decorBonus = Pricing.clamp(p.decorBonus + e.decor, -p.tier().decor, 100f);
        if (e.rentMult >= 0f) p.rentCut = Math.max(p.rentCut, 1f - e.rentMult);
        if (e.tip > 0f) {
            float tip = p.expectedRent() * e.tip;
            if (tip > 0f) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(tip);
                text = text + " " + com.fs.starfarer.api.util.Misc.getDGSCredits(tip) + ".";
            }
        }
        if (e.rep != 0f && m != null && !MarketRules.isOwn(m)) {
            Global.getSector().getPlayerFaction().adjustRelationship(m.getFactionId(), e.rep / 100f);
        }
        Notify.event(p, text, e.good, e.big);
        if (e.leave && t != null) p.tenant = null;
    }

    private static void maybeMoveOut(Property p, Random random) {
        PropertyStats st = p.stats();
        if (random.nextFloat() >= TenantRules.moveOutChance(st.happiness, p.tenant.loyal())) return;
        Notify.event(p, p.tenant.name + " moved out of " + p.displayName() + ".", false, true);
        p.tenant = null;
        p.advertising = true;
    }

    private static void expirePool() {
        Iterator<Tenant> it = Portfolio.get().tenantPool().iterator();
        while (it.hasNext()) {
            Tenant t = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(t.since) > TenantRules.POOL_DAYS) it.remove();
        }
    }
}
