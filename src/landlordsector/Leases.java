package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

/**
 * 1.12.0 leases (LLS-33, Andrzej: "I also like leases. Legally binding stuff ye?"). A new tenant signs for the home's
 * lease term (none, 6, 12 or 24 months) at the rent you ask. While it runs:
 * <ul>
 * <li>the rent stays as signed: you may lower it at once, a raise waits for the next lease;</li>
 * <li>a tenant who moves out early pays you a month's rent for breaking it;</li>
 * <li>evicting costs you half the rent of the months left (at least a month), unless the tenant broke the lease
 * in the last {@link #BREACH_DAYS} days (skipped rent, a police raid, a trashed home);</li>
 * <li>a longer lease makes tenants a little happier (they feel settled).</li>
 * </ul>
 * When it ends, a happy enough tenant asks to renew and you answer; an unhappy one leaves.
 */
public final class Leases {

    public static final int DEFAULT_MONTHS = 12;
    /** The terms the lease button cycles through. */
    public static final int[] TERMS = {0, 6, 12, 24};
    public static final float BREACH_DAYS = 60f;
    /** Below this happiness a tenant does not renew (1.12.1: the same bar as moving out, was 35). */
    public static final float RENEW_HAPPINESS = TenantRules.MOVE_OUT_BELOW;
    /** Evicting during a lease costs this share of the rent of the months left. */
    public static final float EVICT_SHARE = 0.5f;
    public static final String END_EVENT = "lease_end";
    private static final long DAY_MS = 86_400_000L;

    private Leases() {
    }

    private static long now() {
        return Global.getSector().getClock().getTimestamp();
    }

    /** True when the tenant has a lease, running or just ended and waiting for your answer. */
    public static boolean has(Tenant t) {
        return t != null && !t.isPartner() && t.leaseEnd != 0L;
    }

    /** True while the lease runs. */
    public static boolean running(Tenant t) {
        return has(t) && daysLeft(t) > 0f;
    }

    public static float daysLeft(Tenant t) {
        if (!has(t)) return 0f;
        return -Global.getSector().getClock().getElapsedDaysSince(t.leaseEnd);
    }

    /** Whole months left, rounded up. */
    public static int monthsLeft(Tenant t) {
        return (int) Math.ceil(Math.max(0f, daysLeft(t)) / 30f);
    }

    /** "No lease", "6 months", ... */
    public static String termName(int months) {
        return months <= 0 ? "No lease" : months + " months";
    }

    /** The next term for the lease button. */
    public static int nextTerm(int months) {
        for (int i = 0; i < TERMS.length; i++) if (TERMS[i] == months) return TERMS[(i + 1) % TERMS.length];
        return DEFAULT_MONTHS;
    }

    public static void setTerm(Property p, int months) {
        p.leaseTerm = months;
        p.leaseSet = true;
    }

    /** The tenant signs the home's lease at the rent you ask (on moving in, on renewing, old saves on load). */
    public static void start(Property p, Tenant t) {
        int months = p.leaseTerm();
        if (months <= 0) {
            clear(t);
            return;
        }
        t.leaseMonths = months;
        t.leaseEnd = now() + months * 30L * DAY_MS;
        t.leaseRent = p.askingRent();
    }

    public static void clear(Tenant t) {
        if (t == null) return;
        t.leaseEnd = 0L;
        t.leaseMonths = 0;
        t.leaseRent = null;
    }

    /** Happiness from feeling settled: a longer lease, a happier tenant. */
    public static float happiness(Tenant t) {
        if (!running(t)) return 0f;
        if (t.leaseMonths >= 24) return 6f;
        if (t.leaseMonths >= 12) return 4f;
        return 2f;
    }

    public static void breach(Tenant t) {
        if (t != null) t.breachAt = now();
    }

    /** True when the tenant broke the lease lately: evicting them is free. */
    public static boolean breached(Tenant t) {
        return t != null && t.breachAt != 0L && Global.getSector().getClock().getElapsedDaysSince(t.breachAt) < BREACH_DAYS;
    }

    /** A month's rent at the rent the tenant pays now. */
    public static float monthRent(Property p) {
        return Actions.normalRent(p) * p.setting().rentMult;
    }

    /** What evicting the tenant costs you now (0 without a lease, after it ends, or when they broke it). */
    public static float evictFee(Property p) {
        Tenant t = p.tenant;
        if (!running(t) || breached(t)) return 0f;
        return Math.max(1f, monthsLeft(t) * EVICT_SHARE) * monthRent(p);
    }

    /** What a tenant who moves out early pays you. */
    public static float breakFee(Property p) {
        return running(p.tenant) ? monthRent(p) : 0f;
    }

    /**
     * The rent button: lowering the rent counts at once, also for the lease; a raise is for new tenants and the next
     * lease. Returns a note for the player, or null.
     */
    public static String setRent(Property p, RentSetting s) {
        p.setting = s;
        Tenant t = p.tenant;
        if (!has(t) || t.leaseRent == null) return null;
        if (s.ordinal() < t.leaseRent.ordinal()) {
            t.leaseRent = s;
            return null;
        }
        if (s.ordinal() > t.leaseRent.ordinal()) {
            return t.firstName() + " pays " + t.leaseRent.label + " rent until the lease ends. " + s.label
                    + " rent starts with the next lease.";
        }
        return null;
    }

    /** A tenant moves out early: they pay for breaking the lease. Returns the words for the log, or "". */
    public static String payBreakFee(Property p) {
        float fee = breakFee(p);
        if (fee <= 0f) return "";
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(fee);
        p.totalRent += fee;
        return " " + Misc.getDGSCredits(fee) + " paid for breaking the lease.";
    }

    /**
     * Once a day: a lease that ended asks the player about renewing. An unhappy tenant does not renew and leaves; the
     * tenant manager renews for you.
     */
    public static void daily(Property p, java.util.Random random) {
        Tenant t = p.tenant;
        if (!has(t) || running(t) || p.decision != null) return;
        if (p.stats().happiness < RENEW_HAPPINESS) {
            Notify.event(p, t.name + " did not renew the lease at " + p.displayName() + " and moved out.", false, true);
            p.tenant = null;
            p.advertising = true;
            return;
        }
        landlordsector.data.TenantEvents.Def d = landlordsector.data.TenantEvents.get(END_EVENT);
        if (d == null) {
            renew(p);
            return;
        }
        Choices.ask(p, d, random);
    }

    /**
     * Renews the lease at the rent you ask now. A tenant who cannot pay it leaves; a raise puts them in a bad mood.
     * Returns what happened, for the log.
     */
    public static String renew(Property p) {
        Tenant t = p.tenant;
        if (t == null) return "";
        RentSetting before = t.leaseRent != null ? t.leaseRent : p.askingRent();
        RentSetting asking = p.askingRent();
        if (!TenantRules.canAfford(p.tier().index, asking, t.wealth)) {
            String name = t.name;
            p.tenant = null;
            p.advertising = true;
            return name + " can't pay " + asking.label + " rent and moved out of " + p.displayName() + ".";
        }
        start(p, t);
        if (p.leaseTerm() <= 0) {
            return t.name + " stays at " + p.displayName() + " without a lease.";
        }
        String raise = "";
        if (asking.ordinal() > before.ordinal()) {
            t.mood = Pricing.clamp(t.mood - 8f, -30f, 30f);
            raise = " " + t.firstName() + " is not happy about the raise.";
        }
        return t.name + " signed a new " + p.leaseTerm() + "-month lease at " + p.displayName() + "." + raise;
    }

    /** One line for the tenant card: "Lease: 7 months left, Normal rent". */
    public static String cardLine(Tenant t) {
        if (!has(t)) return "No lease.";
        if (!running(t)) return "The lease has ended. Waiting for your answer about renewing.";
        int m = monthsLeft(t);
        return "Lease: " + m + (m == 1 ? " month" : " months") + " left of " + t.leaseMonths + ", at "
                + (t.leaseRent != null ? t.leaseRent.label : "Normal") + " rent.";
    }
}
