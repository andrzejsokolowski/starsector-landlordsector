package landlordsector;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import java.util.Map;

import landlordsector.data.ConditionTable;
import landlordsector.data.FinderDefs;
import landlordsector.data.HouseTraits;
import landlordsector.data.TenantEvents;
import landlordsector.data.TenantTraits;
import landlordsector.manage.RealEstatePage;
import oddiszcommons.manage.Manage;

public class LandlordsectorModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() throws Exception {
        TierTable.load(Global.getSettings().getMergedJSON(TierTable.PATH));
        Art.load(Global.getSettings().getMergedJSON(Art.PATH));
        ConditionTable.load(Global.getSettings().getMergedJSON(ConditionTable.PATH));
        HouseTraits.load(Global.getSettings().getMergedJSON(HouseTraits.PATH));
        TenantTraits.load(Global.getSettings().getMergedJSON(TenantTraits.PATH));
        TenantEvents.load(Global.getSettings().getMergedJSON(TenantEvents.PATH));
        FinderDefs.load(Global.getSettings().getMergedJSON(FinderDefs.PATH));
        landlordsector.data.Names.load(Global.getSettings().getMergedJSON(landlordsector.data.Names.PATH));
        Manage.addPage(Settings.MOD_ID, "Real estate", new Color(200, 170, 80), new RealEstatePage());
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().getListenerManager().addListener(new MonthlyTick(), true);
        HomeownerScript.install();
        Global.getSector().addTransientScript(new TenantDaily());
        repairArtPaths();
        Portfolio p = Portfolio.get();
        Random random = new Random();
        Agents.update(random, !p.agentsSeeded);
        p.agentsSeeded = true;
        Finders.place(random);
        p.findersSeeded = true;
        upgradeTo030(p, random);
    }

    @Override
    public void beforeGameSave() {
        HomeownerScript.beforeSave();
    }

    @Override
    public void afterGameSave() {
        HomeownerScript.afterSave();
    }

    /**
     * Saves from before 0.3.0: homes and listings roll their house traits, and homes that were rented
     * get a tenant of their own (they all counted as rented then).
     */
    private static void upgradeTo030(Portfolio p, Random random) {
        for (Property pr : p.properties()) {
            com.fs.starfarer.api.campaign.econ.MarketAPI m = pr.market();
            if (pr.traits == null) {
                Climate c = climate(pr.climateId);
                pr.traits = HouseTraits.roll(Tenants.conditions(m), c.id, c == Climate.STATION, pr.tier().index,
                        random);
            }
            if (pr.name == null) pr.name = landlordsector.data.Names.make(pr.tierId, pr.traits, random);
            if (!p.tenantsMigrated && pr.tenant == null && !pr.condemned && m != null) {
                pr.tenant = Tenants.create(m.getFactionId(), m, 1f, true, "advert", random);
                pr.addLog(pr.tenant.name + " was already living here.");
            }
        }
        p.tenantsMigrated = true;
        for (Map.Entry<String, MarketListings> e : p.listings().entrySet()) {
            com.fs.starfarer.api.campaign.econ.MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
            // 0.3.2 lists fewer homes per market: drop the extra ones from older saves.
            if (m != null) {
                java.util.List<Listing> list = e.getValue().list();
                while (list.size() > Pricing.listingCount(m.getSize())) list.remove(list.size() - 1);
            }
            for (Listing l : e.getValue().list()) {
                if (l.traits == null) {
                    Tier t = TierTable.get(l.tierId);
                    Climate c = climate(l.climateId);
                    l.traits = HouseTraits.roll(Tenants.conditions(m), c.id, c == Climate.STATION,
                            t == null ? 0 : t.index, random);
                }
                if (l.name == null) l.name = landlordsector.data.Names.make(l.tierId, l.traits, random);
            }
        }
    }

    /** A picture dropped from art.json since the save was made gets a fresh one from the same tier. */
    private static void repairArtPaths() {
        Random random = new Random();
        Portfolio p = Portfolio.get();
        for (Property pr : p.properties()) {
            if (!Art.isKnown(pr.artPath)) pr.artPath = Art.pick(pr.tierId, climate(pr.climateId), random);
        }
        for (MarketListings ml : p.listings().values()) {
            for (Listing l : ml.list()) {
                if (!Art.isKnown(l.artPath)) l.artPath = Art.pick(l.tierId, climate(l.climateId), random);
            }
        }
    }

    private static Climate climate(String id) {
        for (Climate c : Climate.values()) if (c.id.equals(id)) return c;
        return Climate.HOSTILE;
    }
}
