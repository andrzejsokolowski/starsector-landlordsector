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
}
