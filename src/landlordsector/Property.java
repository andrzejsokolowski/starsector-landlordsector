package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** A home the player owns. Saved in the portfolio. */
public class Property {

    public String id;
    public String marketId;
    /** The market's name when bought, shown if the market is gone. */
    public String marketName;
    public String tierId;
    public String climateId;
    public String artPath;
    /** Price inside the tier's base range, before the market multiplier. */
    public float basePrice;
    /** Random price wobble, starts at 1. */
    public float drift = 1f;
    /** 1 is new, 0 is ruined. */
    public float condition = 1f;
    public boolean condemned;
    public RentSetting setting = RentSetting.NORMAL;
    public float paidPrice;
    public long boughtAt;
    /** Rent paid last month after the tariff. */
    public float lastRent;
    public float totalRent;
    /** True when last month's rent was frozen because the owners were hostile. */
    public boolean frozen;

    public Tier tier() {
        Tier t = TierTable.get(tierId);
        return t != null ? t : TierTable.all().get(0);
    }

    public RentSetting setting() {
        if (setting == null) setting = RentSetting.NORMAL;
        return setting;
    }

    /** The market, or null when it is gone. */
    public MarketAPI market() {
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        if (m == null || m.isPlanetConditionMarketOnly() || !m.isInEconomy()) return null;
        return m;
    }

    public String displayName() {
        MarketAPI m = market();
        return tier().name + " on " + (m != null ? m.getName() : marketName);
    }

    /** Value at full condition, at today's market, with the decor bonus. */
    public float fullValue() {
        MarketAPI m = market();
        float mult = m != null ? MarketRules.mult(m) : Pricing.MULT_MIN;
        return basePrice * mult * drift * Pricing.decorValueMult(tier().decor);
    }

    public PropertyStats stats() {
        return PropertyStats.of(this);
    }

    public float value() {
        return Pricing.value(fullValue(), condition, condemned, TierTable.economy().landValue);
    }

    /** Next month's rent after the tariff, at today's numbers. */
    public float expectedRent() {
        MarketAPI m = market();
        if (m == null || condemned || MarketRules.hostile(m)) return 0f;
        float gross = Pricing.rent(fullValue(), tier().rentShare, setting().rentMult, condition, Settings.rentMult());
        return gross * (1f - MarketRules.tariff(m));
    }

    public float repairCost() {
        if (condition >= 0.999f && !condemned) return 0f;
        return Pricing.repairCost(fullValue(), condition, TierTable.economy().repairFullCost);
    }

    /** What a sale pays after the broker fee and the tariff. */
    public float saleValue() {
        MarketAPI m = market();
        float tariff = m != null ? MarketRules.tariff(m) : 0f;
        return value() * Math.max(0f, 1f - TierTable.economy().brokerFee - tariff);
    }
}
