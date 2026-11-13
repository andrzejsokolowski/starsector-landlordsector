package landlordsector;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import landlordsector.data.HouseTraits;

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

    // ---- 0.3.0. New fields must work at their zero value: older saves load them as 0, false or null. ----

    /** House trait ids (house_traits.json). */
    public List<String> traits;
    /** The tenant, or null while the home stands empty. */
    public Tenant tenant;
    /** People who answered the advert, waiting for an answer. */
    public List<Tenant> applicants;
    public boolean advertising;
    /** Monthly screening service: fewer bad traits among applicants. */
    public boolean screeningService;
    /** Decor added by tenants (new furniture, a garden) or taken away (graffiti). */
    public float decorBonus;
    /** The home's name, for example "Leafy Cottage". Named on load for homes from before 0.3.2. */
    public String name;
    /** Newest last. Each entry: flag (+, - or space), date, the colored part, the rest, split by tabs. */
    public List<String> log;
    public long lastEventAt;
    /** Share of this month's rent lost to late or skipped payments (0 to 1). */
    public float rentCut;

    // ---- 0.4.0 ----

    /** Placed furniture and renovations, built or being built. Null on older saves. */
    public List<Installed> upgrades;

    public List<Installed> upgrades() {
        if (upgrades == null) upgrades = new ArrayList<>();
        return upgrades;
    }

    /** House traits and finished upgrades: everything whose effects count (0.4.0). */
    public List<String> features() {
        return features(true);
    }

    public List<String> features(boolean withFurniture) {
        List<String> out = new ArrayList<>(traits());
        for (Installed i : upgrades()) {
            if (!i.ready() || (i.furniture && !withFurniture)) continue;
            out.add(i.id);
        }
        return out;
    }

    public int furnitureCount() {
        int n = 0;
        for (Installed i : upgrades()) if (i.furniture) n++;
        return n;
    }

    /** Renovations built or being built, not counting one-time jobs. */
    public int renovationCount() {
        int n = 0;
        for (Installed i : upgrades()) {
            landlordsector.data.Upgrades.Def d = i.def();
            if (!i.furniture && (d == null || !d.oneTime)) n++;
        }
        return n;
    }

    public boolean has(String upgradeId) {
        for (Installed i : upgrades()) if (i.id.equals(upgradeId)) return true;
        return false;
    }

    /** True for a home on a station. */
    public boolean onStation() {
        return Climate.STATION.id.equals(climateId);
    }

    /** What the furniture in it would sell for, at the items' base prices. */
    public float furnitureValue() {
        float v = 0f;
        for (Installed i : upgrades()) {
            if (!i.furniture) continue;
            try {
                v += Global.getSettings().getSpecialItemSpec(landlordsector.data.Upgrades.ITEM_PREFIX + i.id)
                        .getBasePrice();
            } catch (Throwable ignored) {
                // An item from a mod that is gone is worth nothing.
            }
        }
        return v;
    }

    public List<String> traits() {
        if (traits == null) traits = new ArrayList<>();
        return traits;
    }

    public List<Tenant> applicants() {
        if (applicants == null) applicants = new ArrayList<>();
        return applicants;
    }

    public List<String> log() {
        if (log == null) log = new ArrayList<>();
        return log;
    }

    public boolean isRented() {
        return tenant != null && !condemned;
    }

    public float decor() {
        return decor(true);
    }

    /** Decor with or without the furniture (a home sold empty is valued without it). */
    public float decor(boolean withFurniture) {
        float d = tier().decor + decorBonus;
        for (String id : features(withFurniture)) {
            HouseTraits.Def def = HouseTraits.get(id);
            if (def != null) d += def.decor;
        }
        return Pricing.clamp(d, 0f, 100f);
    }

    /** Price multiplier of the house traits and finished renovations. */
    public float traitPrice() {
        return MarketRules.traitPrice(features(false), market());
    }

    /** Wear multiplier of the house traits, upgrades and the tenant. */
    public float wearMult() {
        float m = 1f;
        for (String id : features()) {
            HouseTraits.Def d = HouseTraits.get(id);
            if (d != null) m *= d.wear;
        }
        return tenant != null ? m * tenant.wearMult() : m * TenantRules.VACANT_WEAR;
    }

    /** Adds a plain dated line to the home's log. */
    public void addLog(String text) {
        addLog(text, null, "");
    }

    /**
     * Adds a dated line to the home's log, keeping the last 25.
     *
     * @param good green (true), red (false) or plain (null)
     * @param colored the start of the text that is shown in that color
     */
    public void addLog(String text, Boolean good, String colored) {
        String date = "";
        try {
            date = Global.getSector().getClock().getShortDate();
        } catch (Throwable ignored) {
        }
        String flag = good == null ? " " : (good ? "+" : "-");
        String c = colored == null || !text.startsWith(colored) ? "" : colored;
        log().add(flag + "	" + date + "	" + c + "	" + text.substring(c.length()));
        while (log().size() > 25) log().remove(0);
    }

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

    /** "Leafy Cottage on Jangala", or the tier name for an unnamed home. */
    public String displayName() {
        MarketAPI m = market();
        String n = name != null && !name.isEmpty() ? name : tier().name;
        return n + " on " + (m != null ? m.getName() : marketName);
    }

    /** Value at full condition, at today's market, with the decor bonus. */
    public float fullValue() {
        return fullValue(true);
    }

    public float fullValue(boolean withFurniture) {
        MarketAPI m = market();
        float mult = m != null ? MarketRules.mult(m) : Pricing.MULT_MIN;
        return basePrice * mult * drift * Pricing.decorValueMult(decor(withFurniture)) * traitPrice();
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
        if (m == null || !isRented() || MarketRules.hostile(m)) return 0f;
        float gross = Pricing.rent(fullValue(), tier().rentShare, setting().rentMult, condition, Settings.rentMult());
        return gross * (1f - MarketRules.tariff(m));
    }

    public float repairCost() {
        if (condition >= 0.999f && !condemned) return 0f;
        return Pricing.repairCost(fullValue(), condition, TierTable.economy().repairFullCost);
    }

    /** What a sale pays after the broker fee and the tariff, furniture left in. */
    public float saleValue() {
        return saleValue(true);
    }

    /**
     * What a sale pays after the broker fee and the tariff. Sold empty, the furniture comes back to the player and
     * its decor no longer counts; sold furnished, the buyer also pays for the furniture (0.4.0).
     */
    public float saleValue(boolean furnished) {
        MarketAPI m = market();
        float tariff = m != null ? MarketRules.tariff(m) : 0f;
        float v = Pricing.value(fullValue(furnished), condition, condemned, TierTable.economy().landValue);
        float pay = v * Math.max(0f, 1f - TierTable.economy().brokerFee - tariff);
        return furnished ? pay + furnitureValue() : pay;
    }
}
