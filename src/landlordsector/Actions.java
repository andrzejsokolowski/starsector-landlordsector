package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

/** What the buy screen and the Manage page can do. Each returns a line to show the player. */
public final class Actions {

    private Actions() {
    }

    public static float credits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    /** Null when the player may buy here, or the reason they may not. */
    public static String cannotBuy(MarketAPI market, Listing listing) {
        if (!MarketRules.isEligible(market)) return "Nobody here sells homes.";
        if (MarketRules.hostile(market)) return "Nobody here will sell to you.";
        int cap = Settings.maxProperties();
        if (cap > 0 && Portfolio.get().properties().size() >= cap) {
            return "You already own " + cap + " properties, the most your settings allow.";
        }
        if (listing != null && credits() < MarketRules.buyPrice(market, listing)) return "Not enough credits.";
        return null;
    }

    public static String buy(MarketAPI market, Listing listing) {
        String no = cannotBuy(market, listing);
        if (no != null) return no;
        Portfolio portfolio = Portfolio.get();
        MarketListings ml = MarketRules.listings(market);
        if (!ml.list().remove(listing)) return "That home is no longer for sale.";
        float price = MarketRules.buyPrice(market, listing);
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(price);

        Property p = new Property();
        p.id = listing.id;
        p.marketId = market.getId();
        p.marketName = market.getName();
        p.tierId = listing.tierId;
        p.climateId = listing.climateId;
        p.artPath = listing.artPath;
        p.basePrice = listing.basePrice;
        p.paidPrice = price;
        p.boughtAt = Global.getSector().getClock().getTimestamp();
        p.traits = listing.traits != null ? new java.util.ArrayList<>(listing.traits) : new java.util.ArrayList<>();
        p.name = listing.name;
        p.advertising = true;
        p.addLog("Bought for " + Misc.getDGSCredits(price) + ". Advertising for tenants.");
        portfolio.properties().add(p);
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        return "Bought: " + p.displayName() + " for " + Misc.getDGSCredits(price) + ".";
    }

    public static String repair(Property p) {
        if (p.market() == null) return "The world is gone. There is nothing left to repair.";
        float cost = p.repairCost();
        if (cost <= 0f) return "It needs no repairs.";
        if (credits() < cost) return "Not enough credits for the repairs (" + Misc.getDGSCredits(cost) + ").";
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        boolean wasCondemned = p.condemned;
        p.condition = 1f;
        p.condemned = false;
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        return (wasCondemned ? "Rebuilt: " : "Repaired: ") + p.displayName() + " for " + Misc.getDGSCredits(cost)
                + ".";
    }

    /** Null when the property can be sold now, or the reason it cannot. */
    public static String cannotSell(Property p) {
        MarketAPI m = p.market();
        if (m != null && MarketRules.hostile(m)) return "No broker there will deal with you while you are hostile.";
        return null;
    }

    public static String sell(Property p) {
        String no = cannotSell(p);
        if (no != null) return no;
        float pay = p.saleValue();
        Portfolio.get().properties().remove(p);
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(pay);
        Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        return "Sold: " + p.displayName() + " for " + Misc.getDGSCredits(pay) + ".";
    }

    public static void setRent(Property p, RentSetting s) {
        p.setting = s;
    }

    // ---- Tenants ----

    /** A month's rent at Normal for this home, after the tariff, as if rented. */
    public static float normalRent(Property p) {
        MarketAPI m = p.market();
        if (m == null) return 0f;
        return Pricing.rent(p.fullValue(), p.tier().rentShare, 1f, 1f, Settings.rentMult()) * (1f - MarketRules.tariff(m));
    }

    public static void toggleAdvertising(Property p) {
        p.advertising = !p.advertising;
        p.addLog(p.advertising ? "Started advertising for tenants." : "Stopped advertising.");
    }

    public static void toggleScreening(Property p) {
        p.screeningService = !p.screeningService;
        p.addLog(p.screeningService ? "Hired a screening service." : "Cancelled the screening service.");
    }

    public static String screen(Property p, Tenant a) {
        if (a.screened) return a.name + " is already screened.";
        float fee = TenantRules.screeningFee(normalRent(p));
        if (credits() < fee) return "Not enough credits to screen " + a.name + " (" + Misc.getDGSCredits(fee) + ").";
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(fee);
        a.screened = true;
        return "Screened " + a.name + " for " + Misc.getDGSCredits(fee) + ".";
    }

    /** Why this tenant would not move into this home at its rent setting, or null when they would. */
    public static String refusal(Property p, Tenant t) {
        int tier = p.tier().index;
        if (!TenantRules.wouldLiveIn(tier, t.wealth)) return "Too rich for a home this cheap";
        if (!TenantRules.canAfford(tier, p.setting(), t.wealth)) {
            return "Can't pay for it at " + p.setting().label + " rent";
        }
        return null;
    }

    /** Moves an applicant or a pool tenant into an empty home. */
    public static String moveIn(Property p, Tenant t) {
        if (p.tenant != null) return p.displayName() + " already has a tenant.";
        String no = refusal(p, t);
        if (no != null) return t.name + " won't move into " + p.displayName() + ": " + no.toLowerCase() + ".";
        if (p.condemned) return p.displayName() + " is condemned. Repair it first.";
        p.applicants().remove(t);
        Portfolio.get().tenantPool().remove(t);
        t.since = Global.getSector().getClock().getTimestamp();
        t.mood = 0f;
        p.tenant = t;
        p.advertising = false;
        p.applicants().clear();
        p.addLog(t.name + " moved in.", true, t.name + " moved in.");
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        return t.name + " moved into " + p.displayName() + ".";
    }

    public static String reject(Property p, Tenant a) {
        p.applicants().remove(a);
        return "Turned " + a.name + " away.";
    }

    public static String evict(Property p) {
        if (p.tenant == null) return "Nobody lives there.";
        String name = p.tenant.name;
        p.tenant = null;
        p.addLog("Evicted " + name + ".", false, "Evicted " + name + ".");
        return "Evicted " + name + " from " + p.displayName() + ".";
    }
}
