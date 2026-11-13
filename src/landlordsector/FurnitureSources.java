package landlordsector;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import landlordsector.data.Upgrades;

/**
 * Where furniture comes from (0.4.0): the open market of every market that sells homes stocks a few pieces, and
 * salvage or battle loot sometimes holds one (it shows on the loot screen like any other find). Transient.
 */
public class FurnitureSources implements ColonyInteractionListener, ShowLootListener {

    private final Random random = new Random();

    public static void install() {
        Global.getSector().getListenerManager().addListener(new FurnitureSources(), true);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        try {
            stock(market, random);
        } catch (Throwable t) {
            Global.getLogger(FurnitureSources.class).error("Landlordsector: stocking furniture failed", t);
        }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
    }

    /** Restocks the open market's furniture once every shopDays. Public for the headless check. */
    public static void stock(MarketAPI market, Random random) {
        if (market == null || !MarketRules.isEligible(market) || MarketRules.hostile(market)) return;
        SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        if (open == null) return;
        Portfolio p = Portfolio.get();
        Long last = p.shopStocked().get(market.getId());
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < Upgrades.shopDays()) return;
        p.shopStocked().put(market.getId(), Global.getSector().getClock().getTimestamp());
        CargoAPI cargo = open.getCargo();
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            if (s.isSpecialStack() && Upgrades.byItem(s.getSpecialDataIfSpecial().getId()) != null) {
                cargo.removeStack(s);
            }
        }
        int n = Upgrades.shopMin() + random.nextInt(Upgrades.shopMax() - Upgrades.shopMin() + 1);
        for (int i = 0; i < n; i++) {
            Upgrades.Def d = pick(random);
            if (d != null) cargo.addSpecial(new SpecialItemData(d.itemId(), null), 1f);
        }
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        try {
            if (loot == null || random.nextFloat() >= Upgrades.lootChance()) return;
            Upgrades.Def d = pick(random);
            if (d != null) loot.addSpecial(new SpecialItemData(d.itemId(), null), 1f);
        } catch (Throwable t) {
            Global.getLogger(FurnitureSources.class).error("Landlordsector: furniture loot failed", t);
        }
    }

    /** A random piece of furniture whose cargo item exists in this game. */
    static Upgrades.Def pick(Random random) {
        List<Upgrades.Def> all = Upgrades.of(Upgrades.Kind.FURNITURE);
        for (int tries = 0; tries < 10 && !all.isEmpty(); tries++) {
            Upgrades.Def d = all.get(random.nextInt(all.size()));
            try {
                if (Global.getSettings().getSpecialItemSpec(d.itemId()) != null) return d;
            } catch (Throwable ignored) {
                // No item row for it: skip it.
            }
        }
        return null;
    }
}
