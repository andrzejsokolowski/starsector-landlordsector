package landlordsector;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;

import oddiszcommons.ledger.Ledger;

/**
 * Stability and growth on the player's colonies from the homes NPC buyers bought there. Uses the
 * OddiszCommons ledger, so no change is left in the save. Transient, made again on every load.
 */
public class HomeownerScript implements EveryFrameScript, MarketImmigrationModifier {

    private static final String MOD_ID = "landlordsector_homeowners";
    private static final String WIFE_ID = "landlordsector_family";
    private static final String KIDS_ID = "landlordsector_kids";
    private static HomeownerScript current;

    private final Ledger ledger = new Ledger("Landlordsector");
    private final Set<MarketAPI> withGrowth = Collections.newSetFromMap(new IdentityHashMap<>());
    private long lastPass = Long.MIN_VALUE;

    public static void install() {
        if (current != null) current.ledger.releaseAll();
        current = new HomeownerScript();
        Global.getSector().addTransientScript(current);
    }

    public static void beforeSave() {
        if (current != null) current.ledger.releaseForSave();
    }

    public static void afterSave() {
        // Put the bonuses back on the next frame, not a day later.
        if (current != null) current.lastPass = Long.MIN_VALUE;
    }

    /** Puts a change on at once (a wedding, a divorce) instead of the next day. */
    public static void refresh() {
        if (current != null) current.lastPass = Long.MIN_VALUE;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        long now = Global.getSector().getClock().getTimestamp();
        if (lastPass != Long.MIN_VALUE && Global.getSector().getClock().getElapsedDaysSince(lastPass) < 1f) return;
        lastPass = now;
        Economy eco = TierTable.economy();
        for (Map.Entry<String, List<Float>> e : Portfolio.get().npcHomes().entrySet()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
            if (m == null || !MarketRules.isOwn(m) || e.getValue().isEmpty()) continue;
            float stability = Math.min(eco.stabilityMax, e.getValue().size() * eco.stabilityPerHome);
            ledger.flat(m.getStability(), MOD_ID, stability, "Homeowners", m);
            if (withGrowth.add(m)) m.addTransientImmigrationModifier(this);
        }
        // 1.2.0: no tariff where your wife or husband lives with you; growth where your kids inspire people.
        for (String id : Family.spouseMarkets()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m != null) ledger.mult(m.getTariff(), WIFE_ID, 0f, "Your family lives here", m);
        }
        for (Child c : Portfolio.get().adultKids()) {
            if (c.marketId == null) continue;
            MarketAPI m = Global.getSector().getEconomy().getMarket(c.marketId);
            if (m != null && MarketRules.isOwn(m) && withGrowth.add(m)) m.addTransientImmigrationModifier(this);
        }
        ledger.sweep();
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (!MarketRules.isOwn(market)) return;
        float kids = Family.growthAt(market.getId());
        if (kids > 0f) incoming.getWeight().modifyFlat(KIDS_ID, kids, "Your kids");
        List<Float> homes = Portfolio.get().npcHomes().get(market.getId());
        if (homes == null || homes.isEmpty()) return;
        Economy eco = TierTable.economy();
        float growth = Math.min(eco.growthMax, homes.size() * eco.growthPerHome);
        incoming.getWeight().modifyFlat(MOD_ID, growth, "Homeowners");
    }
}
