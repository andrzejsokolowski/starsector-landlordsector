package landlordsector;

import java.util.Iterator;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.data.TenantEvents;

/**
 * 1.12.0 (LLS-32, Andrzej: "I really like interactive events! ... stuff like "i cant pay rent > evict, or keep""):
 * events that wait for your answer. A question goes to the intel screen with a button per answer (and to the home's
 * Tenants tab). Answer within its days or the default answer happens; the tenant manager answers for you. An answer
 * may cost or pay credits, may go wrong (its "fail" outcome) and may have an outcome later.
 */
public final class Choices {

    public static final int PLAYER = 0, NO_ANSWER = 1, MANAGER = 2;
    private static final long DAY_MS = 86_400_000L;

    private Choices() {
    }

    private static long now() {
        return Global.getSector().getClock().getTimestamp();
    }

    /** Asks the player. The tenant manager answers at once. */
    public static void ask(Property p, TenantEvents.Def d, Random random) {
        if (p.decision != null || p.tenant == null || !d.isQuestion()) return;
        if (d.tenant) p.lastTenantEventAt = now();
        else p.lastEventAt = now();
        Tenant t = p.tenant;
        Decision q = new Decision();
        q.eventId = d.id;
        q.tenantId = t.id;
        q.askedAt = now();
        String text = d.text;
        for (TenantEvents.Def a : d.choices) {
            if (a.sellMult > 0f) text = text.replace("$price", Misc.getDGSCredits(price(p, a)));
        }
        q.text = TenantDaily.eventText(p, text, t);
        // Not paying the rent breaks the lease: evicting them is free for a while.
        if (d.breach) Leases.breach(t);
        q.who = t.name;
        q.said = TenantEvents.hasMessages(d.id) ? TenantEvents.say(d.id, t.traits(), t.factionId, random) : null;
        p.decision = q;
        p.addLog(q.text, d.good ? Boolean.TRUE : null, Notify.coloredPart(q.text, p));
        if (q.said != null) p.addLog(q.who + ": \"" + q.said + "\"", null, "");
        if (Manager.manages(p)) {
            answer(p, managerChoice(d), MANAGER, random);
            return;
        }
        Portfolio.get().news = true;
        try {
            QuestionIntel intel = new QuestionIntel(p, q);
            q.intel = intel;
            Global.getSector().getIntelManager().addIntel(intel, false);
            Global.getSector().addScript(intel);
        } catch (Throwable ignored) {
            // No campaign UI (tests): the Tenants tab still asks.
        }
    }

    public static int managerChoice(TenantEvents.Def d) {
        for (int i = 0; i < d.choices.size(); i++) if (d.choices.get(i).managerPick) return i;
        return d.defaultChoice;
    }

    /** The question waiting at this home, or null. */
    public static TenantEvents.Def question(Property p) {
        return p.decision == null ? null : TenantEvents.get(p.decision.eventId);
    }

    /** Credits an answer costs (negative: pays you). */
    public static float credits(Property p, TenantEvents.Def a) {
        float month = Actions.normalRent(p);
        return (a.cost - a.gain) * month;
    }

    /** What a buyer pays for the home in a "sell" answer. */
    public static float price(Property p, TenantEvents.Def a) {
        return p.saleValue(false) * a.sellMult;
    }

    /** The answer's button text with "$cost", "$gain" and "$price" filled in. */
    public static String label(Property p, TenantEvents.Def a) {
        float month = Actions.normalRent(p);
        String s = a.label.replace("$cost", Misc.getDGSCredits(a.cost * month))
                .replace("$gain", Misc.getDGSCredits(a.gain * month))
                .replace("$price", Misc.getDGSCredits(price(p, a)));
        return TenantDaily.eventText(p, s, p.tenant);
    }

    /** Null when the player can pick this answer now, or why not. */
    public static String cannot(Property p, TenantEvents.Def a) {
        float c = credits(p, a);
        if (c > 0f && Actions.credits() < c) return "Not enough credits.";
        if (a.sellMult > 0f) {
            String no = Actions.cannotSell(p);
            if (no != null) return no;
        }
        if (a.rentStep < 0 && p.setting() == RentSetting.FREE) return "The rent is already Free.";
        // 1.13.0 (LLS-43): only a happy tenant is offered a lease with no end.
        if (a.openEnded && !Leases.canOfferOpenEnded(p)) {
            return (p.tenant != null ? p.tenant.firstName() : "The tenant") + " is not happy enough here for an"
                    + " open-ended lease.";
        }
        return null;
    }

    /**
     * Answers the question at this home. Returns what happened, for the player. {@code how}: {@link #PLAYER},
     * {@link #NO_ANSWER} (the days ran out) or {@link #MANAGER}.
     */
    public static String answer(Property p, int index, int how, Random random) {
        Decision q = p.decision;
        TenantEvents.Def d = question(p);
        if (q == null || d == null) return "Nothing waits for your answer there.";
        if (p.tenant == null || !p.tenant.id.equals(q.tenantId)) {
            cancel(p);
            return "Nothing waits for your answer there.";
        }
        TenantEvents.Def a = TenantEvents.answer(d.id, index);
        if (a == null) return "No such answer.";
        String no = cannot(p, a);
        if (no != null) {
            if (how == PLAYER) return no;
            a = d.choices.get(d.defaultChoice);
            index = d.defaultChoice;
        }
        p.decision = null;
        Tenant t = p.tenant;
        String prefix = how == NO_ANSWER ? "No answer from you in " + Math.round(d.answerDays) + " days. "
                : (how == MANAGER ? "Your tenant manager answered: " : "");

        float pay = credits(p, a);
        if (pay > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(pay);
        if (pay < 0f) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(-pay);
            p.totalRent += -pay;
        }
        String money = pay > 0.5f ? " You paid " + Misc.getDGSCredits(pay) + "." : (pay < -0.5f ? " You got "
                + Misc.getDGSCredits(-pay) + "." : "");

        String result;
        if (a.sellMult > 0f) {
            result = prefix + Actions.sellFor(p, false, price(p, a), TenantDaily.eventText(p, a.text, t));
            close(q, result);
            return result;
        }
        if (a.renew || a.openEnded) {
            result = prefix + (a.openEnded ? Leases.openEnded(p) : Leases.renew(p));
            Notify.event(p, result, p.tenant != null, false);
            close(q, result);
            return result;
        }
        if (a.rentStep != 0) {
            RentSetting to = p.setting();
            for (int i = 0; i < Math.abs(a.rentStep); i++) to = a.rentStep < 0 ? to.lower() : to.higher();
            p.setting = to;
            if (Leases.has(t)) t.leaseRent = to;
        }
        TenantEvents.Def outcome = a;
        boolean unhappyEnough = a.failBelow <= 0f || (t != null && p.stats().happiness < a.failBelow);
        if (a.fail != null && unhappyEnough && random.nextFloat() < a.failChance) {
            // The plan went wrong: say what was tried, then what happened.
            outcome = a.fail;
        }
        if (a.later != null && t != null) {
            Decision l = new Decision();
            l.eventId = d.id;
            l.answer = index;
            l.tenantId = t.id;
            l.dueAt = now() + (long) (a.laterDays * DAY_MS);
            p.later().add(l);
        }
        MarketAPI m = p.market();
        result = TenantDaily.applyOutcome(p, m, outcome, prefix, money);
        close(q, result);
        return result;
    }

    private static void close(Decision q, String result) {
        if (q != null && q.intel != null) q.intel.answered(result);
    }

    /** Drops the question (the tenant left, the home was sold). */
    public static void cancel(Property p) {
        Decision q = p.decision;
        p.decision = null;
        if (q != null && q.intel != null) q.intel.dropped();
    }

    /** Once a day: the days run out on a question, outcomes come due. */
    public static void daily(Property p, Random random) {
        Decision q = p.decision;
        if (q != null) {
            TenantEvents.Def d = question(p);
            if (d == null || p.tenant == null || !p.tenant.id.equals(q.tenantId)) {
                cancel(p);
            } else if (Global.getSector().getClock().getElapsedDaysSince(q.askedAt) >= d.answerDays) {
                answer(p, d.defaultChoice, NO_ANSWER, random);
            }
        }
        if (p.later == null || p.later.isEmpty()) return;
        Iterator<Decision> it = p.later.iterator();
        while (it.hasNext()) {
            Decision l = it.next();
            if (p.tenant == null || !p.tenant.id.equals(l.tenantId)) {
                it.remove();
                continue;
            }
            if (Global.getSector().getClock().getElapsedDaysSince(l.dueAt) < 0f) continue;
            it.remove();
            TenantEvents.Def a = TenantEvents.answer(l.eventId, l.answer);
            if (a == null || a.later == null || random.nextFloat() >= a.laterChance) continue;
            TenantDaily.applyOutcome(p, p.market(), a.later, "", "");
            if (p.tenant == null) break;
        }
    }
}
