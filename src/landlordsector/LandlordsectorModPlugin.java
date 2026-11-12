package landlordsector;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import landlordsector.manage.RealEstatePage;
import oddiszcommons.manage.Manage;

public class LandlordsectorModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() throws Exception {
        TierTable.load(Global.getSettings().getMergedJSON(TierTable.PATH));
        Art.load(Global.getSettings().getMergedJSON(Art.PATH));
        Manage.addPage(Settings.MOD_ID, "Real estate", new Color(200, 170, 80), new RealEstatePage());
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getSector().getListenerManager().addListener(new MonthlyTick(), true);
        HomeownerScript.install();
        repairArtPaths();
        Portfolio p = Portfolio.get();
        Agents.update(new Random(), !p.agentsSeeded);
        p.agentsSeeded = true;
    }

    @Override
    public void beforeGameSave() {
        HomeownerScript.beforeSave();
    }

    @Override
    public void afterGameSave() {
        HomeownerScript.afterSave();
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
