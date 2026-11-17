package landlordsector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.data.HouseTraits;
import landlordsector.data.Levels;
import landlordsector.data.TenantEvents;
import landlordsector.data.Upgrades;

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
        // 0.7.2 (Andrzej: "I didn't get the property agent" on a new colony): a market that starts selling homes (a
        // new colony, a market that grew to size 3) gets its agent the next day instead of at the end of the month.
        try {
            Agents.update(random, false);
        } catch (Throwable t) {
            log.error("Landlordsector: placing property agents failed", t);
        }
    }

    /** One day for every home. Public for the headless check. */
    public static void runDay(Random random) {
        Portfolio portfolio = Portfolio.get();
        for (Property p : new ArrayList<>(portfolio.properties())) {
            MarketAPI m = p.market();
            if (m == null) continue;
            finishWork(p);
            if (p.tenant != null) {
                p.tenant.mood = settle(p.tenant.mood);
                if (p.condemned) {
                    if (p.tenant.isPartner()) {
                        Family.leaves(p, false, Family.subject(p.tenant) + " left you: " + p.displayName()
                                + " is falling apart.");
                        continue;
                    }
                    Notify.event(p, p.tenant.name + " moved out of " + p.displayName() + ": it is condemned.", false,
                            true);
                    p.tenant = null;
                    Choices.cancel(p);
                    continue;
                }
                // 1.12.0: questions run out, outcomes come due, leases end.
                Choices.daily(p, random);
                if (p.tenant != null) Leases.daily(p, random);
                if (p.tenant == null) continue;
                rollEvent(p, m, random);
                if (p.tenant != null) rollTenantEvent(p, m, random);
                if (p.tenant != null) maybeMoveOut(p, random);
                if (p.tenant != null) maybeTreatment(p, random);
            } else {
                applicants(p, m, random);
            }
            // 1.7.0: the tenant manager service.
            Manager.day(p);
        }
        Finders.deliver(random);
        expirePool();
        // 1.2.0: pregnancies, births and the kids growing up.
        Family.daily(random);
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
        float bad = p.hasService(landlordsector.data.Services.SCREENING) ? TenantRules.SCREENED_BAD_MULT : 1f;
        // Only people who can pay for the home and would live in it answer the advert.
        Tenant a = null;
        for (int i = 0; i < 6 && a == null; i++) {
            Tenant c = Tenants.create(m.getFactionId(), m, bad, false, "advert", random, 0,
                    Tenants.fitBoost(p.features()), p.tier().index);
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
        List<String> features = p.features();
        List<TenantEvents.Def> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0f;
        for (TenantEvents.Def d : TenantEvents.all()) {
            if (d.leave || d.tenant || d.system || !allowed(p, d)) continue;
            float w = TenantRules.eventWeight(d.good, d.damage, d.crime, d.weight, p.tenant.eventMult(d.id),
                    st.happiness, p.playerHome ? RentSetting.NORMAL : p.setting(), p.tier().index, st.security);
            if (d.good) w *= spouseMult(p.tenant);
            // Traits and upgrades change the odds (Fire sprinkler system).
            w *= featureMult(features, d.id);
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
        if (pick.isQuestion()) Choices.ask(p, pick, random);
        else apply(p, m, pick);
    }

    /**
     * The tenant's own life (0.5.0, Andrzej: "House events are separate from tenant events ... Killed, Hospital,
     * Child born"): rolled on its own timer, steered by health, security and the tenant's traits, not by happiness.
     */
    private static void rollTenantEvent(Property p, MarketAPI m, Random random) {
        if (Global.getSector().getClock().getElapsedDaysSince(p.lastTenantEventAt) < TenantEvents.tenantMinDays()) {
            return;
        }
        if (random.nextFloat() >= TenantEvents.tenantDailyChance()) return;
        TenantEvents.Def pick = pickTenantEvent(p, random);
        if (pick == null) return;
        if (pick.isQuestion()) Choices.ask(p, pick, random);
        else apply(p, m, pick);
    }

    /** One tenant event by weight, or null. Public for the headless check. */
    public static TenantEvents.Def pickTenantEvent(Property p, Random random) {
        PropertyStats st = p.stats();
        List<String> features = p.features();
        List<TenantEvents.Def> pool = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0f;
        for (TenantEvents.Def d : TenantEvents.all()) {
            if (d.leave || !d.tenant || d.system || !allowed(p, d)) continue;
            float w = tenantWeight(d, p.tenant.eventMult(d.id), st.health, st.security) * featureMult(features, d.id);
            if (d.good) w *= spouseMult(p.tenant);
            if (w <= 0f) continue;
            pool.add(d);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty()) return null;
        float r = random.nextFloat() * total;
        for (int i = 0; i < pool.size(); i++) {
            r -= weights.get(i);
            if (r <= 0f) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    /** One tenant event's weight for a tenant with this health in a home this safe. */
    public static float tenantWeight(TenantEvents.Def d, float traitMult, float health, float security) {
        float w = TenantRules.tenantEventWeight(d.good, d.health, d.crime, d.weight, traitMult, health, security);
        // Sick tenants fall sick and miss rent more; dying of an illness only happens to the very sick.
        w *= TenantRules.sickWeight(d.sickOnly, d.sickMult, health, Levels.sickBelow());
        w *= TenantRules.belowWeight(d.maxHealth, health);
        return w;
    }

    /** 1.2.0: a wife or husband you visit has more good events (Andrzej: "as long as she's visited"). */
    static float spouseMult(Tenant t) {
        if (t == null || !t.isSpouse()) return 1f;
        return Family.visitPenalty(Family.daysSince(t.lastVisit)) <= 0f ? Family.SPOUSE_GOOD_EVENTS : 1f;
    }

    /** True when the tenant has one of the event's required traits, and it would change something. */
    private static boolean allowed(Property p, TenantEvents.Def d) {
        // 1.2.0: no rent, tips or new partners for the one who lives with you.
        if (p.tenant != null && p.tenant.isPartner() && Family.PARTNER_SKIP_EVENTS.contains(d.id)) return false;
        // 1.2.2: Sophia Anderson still runs AoTD research, so tenant events never kill her.
        if (d.dies && p.tenant != null && Family.isSophia(p.tenant.person)) return false;
        if (d.isQuestion()) {
            // 1.12.0: one question at a time, never for your family or your own home.
            if (p.decision != null || p.playerHome || p.tenant == null || p.tenant.isPartner()) return false;
            for (TenantEvents.Def a : d.choices) {
                if (a.sellMult > 0f && Actions.cannotSell(p) != null) return false;
            }
        }
        if (d.minRent != null) {
            try {
                if (p.setting().ordinal() < RentSetting.valueOf(d.minRent).ordinal()) return false;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (d.lease && !Leases.running(p.tenant)) return false;
        if (!d.requires.isEmpty()) {
            boolean ok = false;
            for (String tr : d.requires) if (p.tenant.has(tr)) ok = true;
            if (!ok) return false;
        }
        if (d.addTenantTrait != null && p.tenant.has(d.addTenantTrait) && !"family".equals(d.addTenantTrait)) return false;
        if (d.removeTenantTrait != null && !p.tenant.has(d.removeTenantTrait)) return false;
        if (d.addTrait != null && p.traits().contains(d.addTrait)) return false;
        if (d.removeTrait != null && !p.traits().contains(d.removeTrait)) return false;
        if (d.wealth > 0 && p.tenant.wealth >= TenantRules.MAX_WEALTH) return false;
        if (d.wealth < 0 && p.tenant.wealth <= 1) return false;
        return true;
    }

    /** Applies one event. Public for the headless check. */
    public static void apply(Property p, MarketAPI m, TenantEvents.Def e) {
        if (e.tenant) p.lastTenantEventAt = Global.getSector().getClock().getTimestamp();
        else p.lastEventAt = Global.getSector().getClock().getTimestamp();
        applyOutcome(p, m, e, "", "");
    }

    /** An event's text with the tenant's name, the home's name and "She's" filled in (1.12.0: also for answers). */
    public static String eventText(Property p, String text, Tenant t) {
        return tenantWords(text, t).replace("$home", p.displayName()).replace("$HeIs", heIs(t))
                .replace("$first", t != null ? t.firstName() : "They");
    }

    /**
     * The effects of an event or of an answer (1.12.0), and its message: {@code prefix} goes before the text ("No
     * answer from you..."), {@code suffix} after it ("You paid 1,200."). Returns the message.
     */
    public static String applyOutcome(Property p, MarketAPI m, TenantEvents.Def e, String prefix, String suffix) {
        Tenant t = p.tenant;
        String text = prefix + eventText(p, e.text, t) + suffix;
        if (e.condition != 0f && !p.condemned) {
            p.condition = Pricing.clamp(p.condition + e.condition, 0f, 1f);
            if (p.condition <= 0f) p.condemned = true;
        }
        if (t != null) t.mood = Pricing.clamp(t.mood + e.mood, -30f, 30f);
        if (t != null && e.breach) Leases.breach(t);
        if (e.decor != 0f) p.decorBonus = Pricing.clamp(p.decorBonus + e.decor, -p.tier().decor, 100f);
        if (e.rentMult >= 0f) p.rentCut = Math.max(p.rentCut, 1f - e.rentMult);
        if (e.tip > 0f) {
            float tip = p.expectedRent() * e.tip;
            if (tip > 0f) {
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(tip);
                p.totalRent += tip;
                text = text + " " + com.fs.starfarer.api.util.Misc.getDGSCredits(tip) + ".";
            }
        }
        if (e.addTrait != null && !p.traits().contains(e.addTrait)) p.traits().add(e.addTrait);
        if (e.removeTrait != null) p.traits().remove(e.removeTrait);
        if (t != null) {
            if (e.addTenantTrait != null && !t.has(e.addTenantTrait)) t.traits().add(e.addTenantTrait);
            if (e.removeTenantTrait != null) t.traits().remove(e.removeTenantTrait);
            if (e.wealth != 0) t.wealth = Math.max(1, Math.min(TenantRules.MAX_WEALTH, t.wealth + e.wealth));
        }
        if (e.rep != 0f && m != null && !MarketRules.isOwn(m)) {
            Global.getSector().getPlayerFaction().adjustRelationship(m.getFactionId(), e.rep / 100f);
        }
        boolean leaves = e.leave || e.dies || e.evict;
        if (leaves && t != null && t.isPartner()) {
            Family.leaves(p, e.dies, text);
            return text;
        }
        String said = e.big && t != null && !e.dies
                ? TenantEvents.say(e.id, t.traits(), t.factionId, new java.util.Random()) : null;
        Notify.event(p, text, e.good, e.big, t != null ? t.name : null, said);
        if (leaves && t != null) {
            p.tenant = null;
            p.advertising = true;
            Choices.cancel(p);
        }
        return text;
    }

    /** House traits and upgrades multiply an event's odds: a fire extinguisher halves small fires. */
    public static float featureMult(List<String> features, String eventId) {
        float m = 1f;
        for (String id : features) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d == null) continue;
            Float f = d.events.get(eventId);
            if (f != null) m *= f;
        }
        return m;
    }

    /**
     * Renovations from older saves that were still being built: done now (0.5.0 builds at once), logged, and one-time
     * jobs fix their traits and leave.
     */
    public static void finishWork(Property p) {
        Iterator<Installed> it = p.upgrades().iterator();
        while (it.hasNext()) {
            Installed i = it.next();
            if (i.furniture || i.days <= 0f) continue;
            Upgrades.Def d = i.def();
            i.days = 0f;
            String name = d != null ? d.name : i.id;
            if (d != null && !d.fixes.isEmpty()) {
                p.traits().removeAll(d.fixes);
            }
            if (d != null && d.oneTime) it.remove();
            Notify.event(p, name + " at " + p.displayName() + " is done.", true, false);
        }
    }

    /** A very sick tenant may move out to get treatment (0.4.0: no deaths). */
    /** "She's", "He's" or "They're" for event text ($HeIs, 1.3.0). */
    static String heIs(Tenant t) {
        String he = t == null ? "they" : (t.person != null ? Family.he(t) : ("F".equals(t.gender) ? "she"
                : ("M".equals(t.gender) ? "he" : "they")));
        return "she".equals(he) ? "She's" : ("he".equals(he) ? "He's" : "They're");
    }

    private static void maybeTreatment(Property p, Random random) {
        if (p.tenant.isPartner()) return;
        PropertyStats st = p.stats();
        if (random.nextFloat() >= TenantRules.treatmentChance(st.health, Levels.treatmentBelow(), p.tenant.loyal())) {
            return;
        }
        Notify.event(p, p.tenant.name + " moved out of " + p.displayName() + " to get treatment.", false, true);
        p.tenant = null;
        p.advertising = true;
        Choices.cancel(p);
    }

    /**
     * 1.8.0 (Andrzej: "the notifications for your wife's name should start with "Your wife, {name}, did something""):
     * a partner's event starts "Your wife, Mira Okoye," and names "your wife Mira Okoye" inside a line.
     */
    static String tenantWords(String text, Tenant t) {
        if (t == null) return text.replace("$tenant", "The tenant");
        if (!t.isPartner()) return text.replace("$tenant", t.name);
        if (text.startsWith("$tenant")) text = Family.subject(t) + text.substring("$tenant".length());
        return text.replace("$tenant", Family.who(t));
    }

    private static void maybeMoveOut(Property p, Random random) {
        PropertyStats st = p.stats();
        if (random.nextFloat() >= TenantRules.moveOutChance(st.happiness, p.tenant.loyal())) return;
        if (p.tenant.isPartner()) {
            Tenant t = p.tenant;
            boolean away = Family.visitPenalty(Family.daysSince(t.lastVisit)) >= 20f;
            Family.leaves(p, false, Family.subject(t) + (t.isSpouse() ? " divorced you" : " left you")
                    + (away ? ". " + Family.capital(Family.he(t)) + " got tired of waiting for you to come home."
                            : "."));
            return;
        }
        // 1.12.0: moving out during a lease costs them a month's rent.
        String fee = Leases.payBreakFee(p);
        Notify.event(p, p.tenant.name + " moved out of " + p.displayName() + "." + fee, false, true);
        p.tenant = null;
        p.advertising = true;
        Choices.cancel(p);
    }

    private static void expirePool() {
        Iterator<Tenant> it = Portfolio.get().tenantPool().iterator();
        while (it.hasNext()) {
            Tenant t = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(t.since) > TenantRules.POOL_DAYS) it.remove();
        }
    }
}
