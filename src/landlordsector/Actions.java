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
        int slots = MarketRules.housingSlots(market);
        if (Portfolio.get().countAt(market.getId()) >= slots) {
            return "This market has room for " + slots + (slots == 1 ? " home" : " homes") + ", and you own them all.";
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
        p.neighbor = listing.neighbor;
        if (listing.upgrades != null) for (String id : listing.upgrades) p.upgrades().add(Installed.done(id));
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
        // 0.5.0: repairs also clean off marks (Graffiti, Dirty).
        p.traits().removeIf(id -> {
            landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
            return d != null && d.mark;
        });
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
        return sell(p, false);
    }

    /**
     * Sells a home. Sold empty, its furniture goes back into your cargo; sold furnished, the buyer pays for the
     * furniture too (0.4.0).
     */
    public static String sell(Property p, boolean furnished) {
        String no = cannotSell(p);
        if (no != null) return no;
        float pay = p.saleValue(furnished);
        int back = 0;
        if (!furnished) {
            for (Installed i : p.upgrades()) {
                if (!i.furniture) continue;
                Portfolio.get().addFurniture(i.id, 1);
                back++;
            }
        }
        Portfolio.get().properties().remove(p);
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(pay);
        Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        return "Sold: " + p.displayName() + " for " + Misc.getDGSCredits(pay) + "."
                + (back > 0 ? " " + back + (back == 1 ? " piece of furniture is" : " pieces of furniture are")
                        + " back in your furniture storage." : "");
    }

    // ---- Upgrades (0.4.0; 0.5.0: furniture storage, instant upgrades, no upgrade slots) ----

    /** How many pieces of this furniture are in the player's cargo (right-click them to store them). */
    public static int inCargo(landlordsector.data.Upgrades.Def d) {
        com.fs.starfarer.api.campaign.CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        return Math.round(cargo.getQuantity(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL,
                new com.fs.starfarer.api.campaign.SpecialItemData(d.itemId(), null)));
    }

    /** Pieces of furniture of any kind in the player's cargo. */
    public static int furnitureInCargo() {
        int n = 0;
        for (landlordsector.data.Upgrades.Def d : landlordsector.data.Upgrades.of(
                landlordsector.data.Upgrades.Kind.FURNITURE)) {
            n += inCargo(d);
        }
        return n;
    }

    /** Moves one piece of furniture from the cargo into the furniture storage (the item's right-click). */
    public static String store(String upgradeId) {
        landlordsector.data.Upgrades.Def d = landlordsector.data.Upgrades.get(upgradeId);
        if (d == null || !d.furniture()) return "That is not furniture.";
        Portfolio.get().addFurniture(d.id, 1);
        return d.name + " is in your furniture storage. Place it from the Real estate tab of the Manage screen.";
    }

    /** Null when this furniture can go into this home now, or the reason it cannot. */
    public static String cannotPlace(Property p, landlordsector.data.Upgrades.Def d) {
        if (p.market() == null) return "The world is gone.";
        if (!landlordsector.data.Upgrades.fits(d, p.tier().index, p.onStation(), p.traits())) {
            return "It does not fit " + PropertyStats.article(p.tier().name.toLowerCase()) + ".";
        }
        if (p.hasFurniture(d.id)) return "There is already one in this home.";
        if (p.furnitureCount() >= p.tier().slots) return "All furniture slots are full.";
        if (Portfolio.get().furnitureCount(d.id) <= 0) return "You have none in storage.";
        return null;
    }

    /** Takes one piece of furniture out of the storage and puts it into the home. */
    public static String place(Property p, landlordsector.data.Upgrades.Def d) {
        String no = cannotPlace(p, d);
        if (no != null) return no;
        Portfolio.get().addFurniture(d.id, -1);
        p.upgrades().add(new Installed(d.id, true, 0f));
        p.addLog("Placed: " + d.name + ".", true, "Placed: " + d.name + ".");
        Global.getSoundPlayer().playUISound("ui_cargo_special_industrial_drop", 1f, 1f);
        return "Placed: " + d.name + " in " + p.displayName() + ".";
    }

    /** Takes a piece of furniture out of the home and back into the storage. */
    public static String takeBack(Property p, Installed i) {
        if (i == null || !i.furniture || !p.upgrades().remove(i)) return "That furniture is not there.";
        Portfolio.get().addFurniture(i.id, 1);
        p.addLog("Took back: " + i.name() + ".");
        Global.getSoundPlayer().playUISound("ui_cargo_special_industrial_pickup", 1f, 1f);
        return "Took back: " + i.name() + ".";
    }

    public static float renovationCost(Property p, landlordsector.data.Upgrades.Def d) {
        return landlordsector.data.Upgrades.renovationCost(d, p.valueBeforeUpgrades(false));
    }

    /** Null when this upgrade can be built in this home now, or the reason it cannot. */
    public static String cannotBuild(Property p, landlordsector.data.Upgrades.Def d) {
        if (p.market() == null) return "The world is gone.";
        if (p.condemned) return "Repair the home first.";
        if (p.has(d.id)) return "Already there.";
        if (!landlordsector.data.Upgrades.fits(d, p.tier().index, p.onStation(), p.traits())) {
            return "Not for this home.";
        }
        String clash = landlordsector.data.Upgrades.clash(d, p.renovationIds());
        if (clash != null) {
            landlordsector.data.Upgrades.Def c = landlordsector.data.Upgrades.get(clash);
            return "Does not go with " + (c != null ? c.name : clash) + ".";
        }
        if (credits() < renovationCost(p, d)) return "Not enough credits.";
        return null;
    }

    /** Builds an upgrade at once (Andrzej, 0.5.0: "performed immediately, not after X days"). */
    public static String build(Property p, landlordsector.data.Upgrades.Def d) {
        String no = cannotBuild(p, d);
        if (no != null) return no;
        float cost = renovationCost(p, d);
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        if (!d.fixes.isEmpty()) p.traits().removeAll(d.fixes);
        if (!d.oneTime) p.upgrades().add(Installed.done(d.id));
        p.addLog("Built: " + d.name + " for " + Misc.getDGSCredits(cost) + ".", true, "Built: " + d.name);
        Global.getSoundPlayer().playUISound("ui_upgrade_industry", 1f, 1f);
        return "Built: " + d.name + " at " + p.displayName() + " for " + Misc.getDGSCredits(cost) + ".";
    }

    /** Removes an upgrade from the home. */
    public static String remove(Property p, Installed i) {
        if (i == null || i.furniture || !p.upgrades().remove(i)) return "That upgrade is not there.";
        p.addLog("Removed: " + i.name() + ".");
        return "Removed: " + i.name() + ".";
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
        if (!TenantRules.wouldLiveIn(tier, t.wealth, t.homeShift(), p.setting())) return "Too rich for a home this cheap";
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
