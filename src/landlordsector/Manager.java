package landlordsector;

import com.fs.starfarer.api.Global;

import landlordsector.data.TenantTraits;

/**
 * 1.7.0, the tenant manager service (a fan, via Andrzej: "a service that will automatically manage tenants ... find
 * and approve new ones ... pretty expensive as a flat fee"). Once a day, on every home that hires it: an empty home is
 * advertised, every applicant is checked for free and the best one moves in; a tenant who is unhappy gets the rent one
 * step lower, a happy one one step higher, between Low and High. Rent you set to Free or Charity, or to Greedy, is
 * left alone. Your own home is never managed.
 */
public final class Manager {

    public static final String ID = "tenant_manager";
    /** An applicant with a bad trait is only taken after waiting this long, or when the list is full. */
    public static final float WAIT_DAYS = 5f;
    /** Below this happiness the rent goes one step down. */
    public static final float UNHAPPY = 30f;
    /** At or above this happiness the rent goes one step up. */
    public static final float HAPPY = 70f;

    private Manager() {
    }

    public static boolean manages(Property p) {
        return p != null && !p.playerHome && p.hasService(ID);
    }

    /** One day for one home. Public for the headless check. */
    public static void day(Property p) {
        if (!manages(p) || p.condemned || p.market() == null) return;
        if (p.tenant == null) fill(p);
        else if (!p.tenant.isPartner()) rent(p);
    }

    private static void fill(Property p) {
        if (!p.advertising) {
            p.advertising = true;
            p.addLog("Your tenant manager started advertising.");
        }
        Tenant best = null;
        int bestBad = Integer.MAX_VALUE;
        boolean waited = false;
        for (Tenant a : p.applicants()) {
            a.screened = true;
            if (Actions.refusal(p, a) != null) continue;
            int bad = badTraits(a);
            if (best == null || bad < bestBad || (bad == bestBad && a.wealth > best.wealth)) {
                best = a;
                bestBad = bad;
            }
            if (Global.getSector().getClock().getElapsedDaysSince(a.since) >= WAIT_DAYS) waited = true;
        }
        if (best == null) return;
        if (bestBad > 0 && !waited && p.applicants().size() < TenantRules.MAX_APPLICANTS) return;
        if (Actions.moveIn(p, best, false) == null) {
            Notify.event(p, "Your tenant manager moved " + best.name + " into " + p.displayName() + ".", true, false);
        }
    }

    private static void rent(Property p) {
        // 1.12.0: the rent you ask (a raise during a lease waits for the next one).
        RentSetting s = p.askingRent();
        float h = p.stats().happiness;
        RentSetting to = null;
        if (h < UNHAPPY && (s == RentSetting.NORMAL || s == RentSetting.HIGH)) {
            to = s.lower();
        } else if (h >= HAPPY && (s == RentSetting.LOW || s == RentSetting.NORMAL)
                && TenantRules.canAfford(p.tier().index, s.higher(), p.tenant.wealth)) {
            to = s.higher();
        }
        if (to == null) return;
        Actions.setRent(p, to);
        p.addLog("Your tenant manager set the rent to " + to.label + ".");
    }

    /** Bad traits, as the screening shows them. */
    public static int badTraits(Tenant t) {
        int n = 0;
        for (String id : t.traits()) {
            TenantTraits.Def d = TenantTraits.get(id);
            if (d != null && Boolean.FALSE.equals(d.good)) n++;
        }
        return n;
    }
}
