package landlordsector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

/**
 * The month's end: rent, wear, price drift, reputation, NPC buyers and property tax on the player's
 * colonies. Also reacts to the player's own raids and bombardments. Transient, added on every load.
 */
public class MonthlyTick implements EconomyTickListener, ColonyPlayerHostileActListener {

    private static final Logger log = Global.getLogger(MonthlyTick.class);
    public static final String REPORT_NODE = "landlordsector_real_estate";
    /** Most reputation points one faction can gain or lose from rent in a month. */
    private static final float REP_CAP_POINTS = 2f;

    private final Random random = new Random();

    /** The income report's tooltip for every Landlordsector line. */
    private static final TooltipCreator TIP = new TooltipCreator() {
        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return 400f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addPara("Rent from your homes after the local tariff, and the money NPC homeowners bring"
                    + " your colonies. Manage your homes from the Manage (Y) button.", 0f);
        }
    };

    @Override
    public void reportEconomyTick(int iterIndex) {
        int last = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != last) return;
        try {
            runMonth();
        } catch (Throwable t) {
            // One bad month must never stop the economy tick of the game itself.
            log.error("Landlordsector: the monthly rent pass failed", t);
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    /** The month's rent pass (public since 1.6.0 for the console's lls_month). */
    public void runMonth() {
        Portfolio portfolio = Portfolio.get();
        Economy eco = TierTable.economy();
        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode root = report.getNode(REPORT_NODE);
        root.name = "Real estate";
        root.custom = REPORT_NODE;
        root.icon = Global.getSettings().getSpriteName("income_report", "generic_income");
        root.tooltipCreator = TIP;

        Map<String, Float> repByFaction = new HashMap<>();
        List<String> news = new ArrayList<>();
        float rentTotal = 0f;

        Iterator<Property> it = portfolio.properties().iterator();
        while (it.hasNext()) {
            Property p = it.next();
            MarketAPI m = p.market();
            if (m == null) {
                Family.release(p);
                it.remove();
                news.add("Your " + p.tier().name.toLowerCase() + " on " + p.marketName
                        + " is gone with the colony.");
                continue;
            }

            // Price drift first, so this month's rent uses this month's price.
            float wobble = 1f + (float) random.nextGaussian() * eco.driftPerMonth;
            p.drift = Pricing.clamp(p.drift * wobble, eco.driftMin, eco.driftMax);

            boolean hostile = MarketRules.hostile(m);
            p.frozen = hostile && p.isRented();
            float net = 0f;
            if (p.isRented() && !hostile) {
                float gross = Pricing.rent(p.fullValue(), p.tier().rentShare, p.setting().rentMult, p.condition,
                        Settings.rentMult());
                net = gross * MarketRules.rentKeep(m) * (1f - Pricing.clamp(p.rentCut, 0f, 1f));
                PropertyStats stats = p.stats();
                if (!MarketRules.isOwn(m)) {
                    float rep = p.setting().repPerMonth * (1f + 0.25f * p.tier().index)
                            * Pricing.decorRepMult(stats.decor);
                    if (rep != 0f) repByFaction.merge(m.getFactionId(), rep, Float::sum);
                }
            }
            p.rentCut = 0f;
            p.lastRent = net;
            p.totalRent += net;
            rentTotal += net;
            if (net > 0f) {
                FDNode node = report.getNode(root, p.id);
                node.name = p.displayName();
                node.income += net;
                node.custom = p.id;
                node.tooltipCreator = TIP;
            }

            // Services (0.6.0): paid every month while hired; some clean or repair.
            for (String sid : new ArrayList<>(p.services())) {
                landlordsector.data.Services.Def sd = landlordsector.data.Services.get(sid);
                if (sd == null) continue;
                float fee = Actions.servicePrice(p, sd);
                FDNode node = report.getNode(root, p.id + "_service_" + sid);
                node.name = sd.name + ", " + p.displayName();
                node.upkeep += fee;
                node.tooltipCreator = TIP;
                if (sd.cleans) {
                    boolean cleaned = p.traits().removeIf(id -> {
                        landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
                        return d != null && d.mark;
                    });
                    if (cleaned) p.addLog(sd.name + " cleaned the home.", true, sd.name);
                }
                if (sd.repairs > 0f && !p.condemned && p.condition < 1f) {
                    p.condition = Math.min(1f, p.condition + sd.repairs);
                }
            }

            // Wear after the rent: the tenants lived in it all month.
            if (!p.condemned) {
                PropertyStats stats = p.stats();
                float wear = Pricing.wearMult(stats.happiness) * p.wearMult() / eco.wearMonths;
                if (anyDisrupted(m)) wear += eco.disruptionWear * Pricing.damageMult(stats.security);
                p.condition -= wear;
                // 1.12.1 (Andrzej: "a tenant who is happy and repairs the house himself"): a happy tenant looks after
                // the home again (0.5.0 had taken it out).
                if (p.tenant != null) p.condition = Math.min(1f, p.condition + Pricing.happyRepair(stats.happiness,
                        eco.happyRepair, eco.happyRepairFrom));
                if (p.condition <= 0f) {
                    p.condition = 0f;
                    p.condemned = true;
                    news.add(p.displayName() + " is condemned.");
                }
            }
        }

        applyReputation(repByFaction);
        Agents.update(random, false);
        // 1.2.0: what grown kids send home, alimony, and closer officers.
        Family.monthly(report, root);
        float[] npc = runOwnColonies(portfolio, report, root, news);

        portfolio.lastRent = rentTotal;
        portfolio.lastSales = npc[0];
        portfolio.lastTax = npc[1];
        if (!news.isEmpty()) portfolio.news = true;

        if (rentTotal > 0f || npc[0] > 0f || npc[1] > 0f || !news.isEmpty() || portfolio.lastKidPay > 0f
                || portfolio.lastAlimony > 0f) {
            MessageIntel msg = new MessageIntel("Real estate this month", Misc.getBasePlayerColor());
            if (rentTotal > 0f) {
                msg.addLine("Rent: %s", Misc.getTextColor(), new String[] {Misc.getDGSCredits(rentTotal)},
                        Misc.getHighlightColor());
            }
            if (npc[0] > 0f) {
                msg.addLine("Home sales on your colonies: %s", Misc.getTextColor(),
                        new String[] {Misc.getDGSCredits(npc[0])}, Misc.getHighlightColor());
            }
            if (npc[1] > 0f) {
                msg.addLine("Property tax: %s", Misc.getTextColor(), new String[] {Misc.getDGSCredits(npc[1])},
                        Misc.getHighlightColor());
            }
            if (portfolio.lastKidPay > 0f) {
                msg.addLine("Sent home by your kids: %s", Misc.getTextColor(),
                        new String[] {Misc.getDGSCredits(portfolio.lastKidPay)}, Misc.getHighlightColor());
            }
            if (portfolio.lastAlimony > 0f) {
                msg.addLine("Alimony: %s", Misc.getTextColor(), new String[] {Misc.getDGSCredits(portfolio.lastAlimony)},
                        Misc.getNegativeHighlightColor());
            }
            for (String line : news) msg.addLine(line);
            msg.setIcon(Global.getSettings().getSpriteName("intel", "monthly_income_report"));
            Global.getSector().getCampaignUI().addMessage(msg);
        }
    }

    private static boolean anyDisrupted(MarketAPI m) {
        for (Industry ind : m.getIndustries()) if (ind.isDisrupted()) return true;
        return false;
    }

    private static void applyReputation(Map<String, Float> repByFaction) {
        for (Map.Entry<String, Float> e : repByFaction.entrySet()) {
            float points = Pricing.clamp(e.getValue(), -REP_CAP_POINTS, REP_CAP_POINTS);
            if (Math.abs(points) < 0.001f) continue;
            Global.getSector().getPlayerFaction().adjustRelationship(e.getKey(), points / 100f);
        }
    }

    /** NPC buyers and property tax on the player's colonies. Returns {sales, tax}. */
    private float[] runOwnColonies(Portfolio portfolio, MonthlyReport report, FDNode root, List<String> news) {
        Economy eco = TierTable.economy();
        float sales = 0f;
        float tax = 0f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!MarketRules.isOwn(m) || !MarketRules.isEligible(m)) continue;
            List<Float> homes = portfolio.npcHomes(m.getId());
            int cap = (int) (m.getSize() * eco.homesPerSize);
            MarketListings ml = MarketRules.listings(m);
            float sold = 0f;
            Iterator<Listing> li = ml.list().iterator();
            while (li.hasNext() && homes.size() < cap) {
                Listing l = li.next();
                if (random.nextFloat() >= eco.npcBuyChance) continue;
                float price = MarketRules.listingValue(m, l);
                li.remove();
                homes.add(price);
                sold += price * eco.npcSaleShare;
            }
            float t = 0f;
            for (Float v : homes) t += v * eco.propertyTax;
            if (sold > 0f) {
                FDNode n = report.getNode(root, "sales_" + m.getId());
                n.name = "Home sales on " + m.getName();
                n.income += sold;
                n.tooltipCreator = TIP;
            }
            if (t > 0f) {
                FDNode n = report.getNode(root, "tax_" + m.getId());
                n.name = "Property tax on " + m.getName();
                n.income += t;
                n.tooltipCreator = TIP;
            }
            sales += sold;
            tax += t;
        }
        // Colonies the player lost keep no NPC homes for them.
        portfolio.npcHomes().entrySet().removeIf(e -> {
            MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
            return m == null || !MarketRules.isOwn(m) || e.getValue().isEmpty();
        });
        return new float[] {sales, tax};
    }

    // ---- The player's own hostile acts on a market where they own homes ----

    private void damageAt(MarketAPI market, float wear, boolean destroy) {
        if (market == null) return;
        Iterator<Property> it = Portfolio.get().properties().iterator();
        while (it.hasNext()) {
            Property p = it.next();
            if (!market.getId().equals(p.marketId)) continue;
            if (destroy) {
                Family.release(p);
                it.remove();
                Global.getSector().getCampaignUI().addMessage("Your " + p.tier().name.toLowerCase() + " on "
                        + market.getName() + " burned with the rest of the city.", Misc.getNegativeHighlightColor());
                continue;
            }
            if (p.condemned) continue;
            p.condition = Math.max(0f, p.condition - wear * Pricing.damageMult(p.stats().security));
            if (p.condition <= 0f) p.condemned = true;
        }
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market,
            TempData actionData, CargoAPI cargo) {
        damageAt(market, TierTable.economy().raidWear, false);
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData,
            Industry industry) {
        damageAt(market, TierTable.economy().raidWear, false);
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market,
            TempData actionData) {
        damageAt(market, TierTable.economy().tacticalBombWear, false);
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market,
            TempData actionData) {
        damageAt(market, 1f, true);
    }
}
