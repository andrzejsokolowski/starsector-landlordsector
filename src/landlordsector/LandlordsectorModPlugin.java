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
        landlordsector.data.Upgrades.load(Global.getSettings().getMergedJSON(landlordsector.data.Upgrades.PATH));
        landlordsector.data.Levels.load(Global.getSettings().getMergedJSON(landlordsector.data.Levels.PATH));
        landlordsector.data.Environment.load(
                Global.getSettings().getMergedJSON(landlordsector.data.Environment.PATH));
        Art.loadFurnitureIcons();
        Manage.addPage(Settings.MOD_ID, "Real estate", new Color(200, 170, 80), new RealEstatePage());
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().getListenerManager().addListener(new MonthlyTick(), true);
        HomeownerScript.install();
        Global.getSector().addTransientScript(new TenantDaily());
        FurnitureSources.install();
        repairArtPaths();
        Portfolio p = Portfolio.get();
        Random random = new Random();
        Agents.update(random, !p.agentsSeeded);
        p.agentsSeeded = true;
        Finders.place(random);
        p.findersSeeded = true;
        upgradeTo030(p, random);
        upgradeTo040(p);
        upgradeTo050(p, random);
    }

    /**
     * 0.5.0: homes get the building they sit next to, renovations still being built finish (upgrades are instant
     * now), and every market's homes for sale are rolled again so they have neighbours and the new counts (one home
     * per point of market size). Public for the headless check.
     */
    public static void upgradeTo050(Portfolio p, Random random) {
        for (Property pr : p.properties()) {
            if (pr.neighbor == null) {
                com.fs.starfarer.api.campaign.econ.MarketAPI m = pr.market();
                pr.neighbor = m != null ? Surroundings.roll(m, random) : landlordsector.data.Environment.NOTHING;
            }
            TenantDaily.finishWork(pr);
            // Renovation slots are gone: nothing to trim. Two of the same furniture in one home: one goes to storage.
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.Iterator<Installed> it = pr.upgrades().iterator();
            while (it.hasNext()) {
                Installed i = it.next();
                if (!i.furniture) continue;
                if (!seen.add(i.id)) {
                    it.remove();
                    p.addFurniture(i.id, 1);
                }
            }
        }
        if (!p.upgraded050) {
            p.listings().clear();
            p.upgraded050 = true;
        }
        for (Map.Entry<String, MarketListings> e : p.listings().entrySet()) {
            com.fs.starfarer.api.campaign.econ.MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
            for (Listing l : e.getValue().list()) {
                if (l.neighbor == null) {
                    l.neighbor = m != null ? Surroundings.roll(m, random) : landlordsector.data.Environment.NOTHING;
                }
            }
        }
    }

    /**
     * 0.4.0: house traits that became renovations (Toasty, Air-conditioned, Renovated, Fortified, Barred, Shielded,
     * Poolside) move from a home's traits to its installed upgrades, same id, already built. Works for any id that is
     * no longer a trait but is an upgrade, so later moves need no new code. Public for the headless check.
     */
    public static void upgradeTo040(Portfolio p) {
        for (Property pr : p.properties()) {
            java.util.Iterator<String> it = pr.traits().iterator();
            while (it.hasNext()) {
                String id = it.next();
                if (HouseTraits.isTrait(id) || landlordsector.data.Upgrades.get(id) == null) continue;
                it.remove();
                if (!pr.has(id)) pr.upgrades().add(Installed.done(id));
            }
        }
        for (MarketListings ml : p.listings().values()) {
            for (Listing l : ml.list()) {
                if (l.traits == null) continue;
                java.util.Iterator<String> it = l.traits.iterator();
                while (it.hasNext()) {
                    String id = it.next();
                    if (HouseTraits.isTrait(id) || landlordsector.data.Upgrades.get(id) == null) continue;
                    it.remove();
                    if (l.upgrades == null) l.upgrades = new java.util.ArrayList<>();
                    if (!l.upgrades.contains(id)) l.upgrades.add(id);
                }
            }
        }
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
            // Never more homes for sale than the market has room for (0.5.0: one per point of size).
            if (m != null) {
                java.util.List<Listing> list = e.getValue().list();
                while (list.size() > MarketRules.housingSlots(m)) list.remove(list.size() - 1);
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

    /**
     * A picture dropped from art.json since the save was made gets a fresh one from the same tier, and a home
     * still showing the tier's placeholder gets its planet type's own picture once one exists (0.4.2).
     */
    private static void repairArtPaths() {
        Random random = new Random();
        Portfolio p = Portfolio.get();
        for (Property pr : p.properties()) {
            PlanetType type = pr.market() != null ? PlanetType.of(pr.market()) : PlanetType.fromClimate(pr.climateId);
            pr.artPath = repairedArt(pr.tierId, pr.artPath, type, random);
        }
        for (Map.Entry<String, MarketListings> e : p.listings().entrySet()) {
            com.fs.starfarer.api.campaign.econ.MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
            for (Listing l : e.getValue().list()) {
                PlanetType type = m != null ? PlanetType.of(m) : PlanetType.fromClimate(l.climateId);
                l.artPath = repairedArt(l.tierId, l.artPath, type, random);
            }
        }
    }

    static String repairedArt(String tierId, String path, PlanetType type, Random random) {
        if (!Art.isKnown(path) || (Art.isDefault(tierId, path) && Art.hasOwn(tierId, type))) {
            return Art.pick(tierId, type, random);
        }
        return path;
    }

    private static Climate climate(String id) {
        for (Climate c : Climate.values()) if (c.id.equals(id)) return c;
        return Climate.HOSTILE;
    }
}
