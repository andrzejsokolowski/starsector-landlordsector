package landlordsector.manage;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Actions;
import landlordsector.MarketRules;
import landlordsector.Portfolio;
import landlordsector.Property;
import landlordsector.PropertyStats;
import landlordsector.RentSetting;
import landlordsector.ui.PropertyView;
import oddiszcommons.manage.ManagePage;

/** The "Real estate" tab of the OddiszCommons Manage screen. */
public class RealEstatePage implements ManagePage {

    /** The home last looked at, kept while the game runs so reopening the tab shows it again. */
    private String lastSelected;

    @Override
    public CustomPanelAPI createPanel(float width, float height) {
        Portfolio.get().news = false;
        View view = new View(width, height);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, view);
        view.attach(panel);
        return panel;
    }

    @Override
    public boolean hasNews() {
        return Global.getSector() != null && Portfolio.get().news;
    }

    /** The player's homes on the left, the chosen one large on the right with its controls. */
    final class View extends PropertyView {

        private static final float LIST_W = 260f;
        private static final float ROW_H = 50f;
        private static final float TOP = 96f;

        private int page;
        private String pendingSell;
        private String message = "";
        private Color messageColor = Misc.getTextColor();

        View(float width, float height) {
            super(width, height);
        }

        @Override
        protected void build() {
            Portfolio portfolio = Portfolio.get();
            List<Property> all = portfolio.properties();
            float worth = 0f;
            for (Property pr : all) worth += pr.value();

            title("Real estate", 10f, 8f, width - 20f);
            text(all.size() + (all.size() == 1 ? " property" : " properties") + " worth "
                    + Misc.getDGSCredits(worth) + ". Credits: " + Misc.getDGSCredits(Actions.credits()) + ".",
                    10f, 38f, width - 20f);
            text("Last month: rent " + Misc.getDGSCredits(portfolio.lastRent) + ", home sales on your colonies "
                    + Misc.getDGSCredits(portfolio.lastSales) + ", property tax "
                    + Misc.getDGSCredits(portfolio.lastTax) + ".", Misc.getGrayColor(), 10f, 56f, width - 20f);
            text(message, messageColor, 10f, 74f, width - 20f);

            if (all.isEmpty()) {
                text("You own no homes yet. Property agents sell them: look for one in a market's comm"
                        + " directory.", 10f, TOP + 10f, width - 20f);
                return;
            }

            Property chosen = find(lastSelected);
            if (chosen == null) chosen = all.get(0);
            lastSelected = chosen.id;

            // The list, in pages.
            int perPage = Math.max(1, (int) ((height - TOP - 44f) / (ROW_H + 4f)));
            int pages = (all.size() + perPage - 1) / perPage;
            page = Math.max(0, Math.min(page, pages - 1));
            for (int i = page * perPage; i < Math.min(all.size(), (page + 1) * perPage); i++) {
                Property pr = all.get(i);
                float y = TOP + (i - page * perPage) * (ROW_H + 4f);
                area("pick:" + pr.id, 8f, y, LIST_W, ROW_H, pr == chosen);
                text(pr.displayName(), Misc.getHighlightColor(), 18f, y + 5f, LIST_W - 20f);
                String line;
                Color c = Misc.getGrayColor();
                MarketAPI m = pr.market();
                if (pr.condemned) {
                    line = "Condemned";
                    c = Misc.getNegativeHighlightColor();
                } else if (m != null && MarketRules.hostile(m)) {
                    line = "Rent frozen";
                    c = Misc.getNegativeHighlightColor();
                } else {
                    line = Misc.getDGSCredits(pr.expectedRent()) + " a month";
                }
                text(line, c, 18f, y + 25f, LIST_W - 20f);
            }
            if (pages > 1) {
                float fy = height - 36f;
                button("prev", "<", 8f, fy, 40f, 28f, page > 0);
                label("Page " + (page + 1) + " of " + pages, Misc.getTextColor(), 52f, fy, LIST_W - 88f, 28f,
                        Alignment.MID, Fonts.DEFAULT_SMALL);
                button("next", ">", 8f + LIST_W - 40f, fy, 40f, 28f, page < pages - 1);
            }

            // The chosen home.
            MarketAPI m = chosen.market();
            PropertyStats st = chosen.stats();
            boolean own = m != null && MarketRules.isOwn(m);
            Sheet s = new Sheet();
            s.title = chosen.tier().name;
            s.subtitle = m != null ? where(m, st) : chosen.marketName + " (gone)";
            s.art = chosen.artPath;
            s.valueLine = Misc.getDGSCredits(chosen.value());
            float next = chosen.expectedRent();
            s.rentLine = next > 0f ? Misc.getDGSCredits(next) + " a month" + (own ? "" : ", after the tariff")
                    : "none";
            s.condition = chosen.condition;
            if (chosen.condemned) {
                s.occupancy = "Condemned: no tenants until repaired";
                s.occupancyColor = Misc.getNegativeHighlightColor();
            } else if (m != null && MarketRules.hostile(m)) {
                s.occupancy = "Rent frozen: the owners are hostile";
                s.occupancyColor = Misc.getNegativeHighlightColor();
            } else {
                s.occupancy = "Rented";
            }
            s.stats = st;
            s.extra.add(new String[] {"Bought for", Misc.getDGSCredits(chosen.paidPrice)});
            s.extra.add(new String[] {"Rent so far", Misc.getDGSCredits(chosen.totalRent)
                    + " (last month " + Misc.getDGSCredits(chosen.lastRent) + ")"});

            float rx = 8f + LIST_W + 16f;
            float rw = width - rx - 8f;
            float maxImg = height - TOP - sheetTextHeight(s) - 48f;
            float end = sheet(s, rx, TOP, rw, Math.min(400f, maxImg));

            // Controls under the sheet.
            float cy = Math.min(end + 8f, height - 36f);
            RentSetting set = chosen.setting();
            label("Rent", Misc.getGrayColor(), rx, cy, 50f, 28f, Alignment.LMID, Fonts.DEFAULT_SMALL);
            button("lower:" + chosen.id, "<", rx + 50f, cy, 30f, 28f, set != RentSetting.CHARITY);
            label(set.label, Misc.getHighlightColor(), rx + 84f, cy, 90f, 28f, Alignment.MID, Fonts.DEFAULT_SMALL);
            button("higher:" + chosen.id, ">", rx + 178f, cy, 30f, 28f, set != RentSetting.GREEDY);
            float cost = chosen.repairCost();
            button("repair:" + chosen.id, cost > 0f ? "Repair for " + Misc.getDGSCredits(cost) : "No repairs needed",
                    rx + 230f, cy, 220f, 28f, cost > 0f && Actions.credits() >= cost && m != null);
            boolean confirm = chosen.id.equals(pendingSell);
            button("sell:" + chosen.id, (confirm ? "Confirm: sell for " : "Sell for ")
                    + Misc.getDGSCredits(chosen.saleValue()), rx + 462f, cy, 240f, 28f,
                    Actions.cannotSell(chosen) == null);
        }

        @Override
        protected void onAction(Object action) {
            String a = String.valueOf(action);
            if ("prev".equals(a)) {
                page--;
                pendingSell = null;
                rebuild();
                return;
            }
            if ("next".equals(a)) {
                page++;
                pendingSell = null;
                rebuild();
                return;
            }
            int colon = a.indexOf(':');
            if (colon < 0) return;
            String verb = a.substring(0, colon);
            Property pr = find(a.substring(colon + 1));
            if (pr == null) return;
            String keepPending = null;
            switch (verb) {
                case "pick":
                    lastSelected = pr.id;
                    message = "";
                    break;
                case "lower":
                    Actions.setRent(pr, pr.setting().lower());
                    message = "";
                    break;
                case "higher":
                    Actions.setRent(pr, pr.setting().higher());
                    message = "";
                    break;
                case "repair":
                    show(Actions.repair(pr));
                    break;
                case "sell":
                    if (pr.id.equals(pendingSell)) {
                        show(Actions.sell(pr));
                        lastSelected = null;
                    } else {
                        keepPending = pr.id;
                        message = "Click again to sell " + pr.displayName() + ". The price takes off a broker fee"
                                + " and the local tariff.";
                        messageColor = Misc.getHighlightColor();
                    }
                    break;
                default:
                    return;
            }
            pendingSell = keepPending;
            rebuild();
        }

        private void show(String result) {
            message = result;
            boolean good = result.startsWith("Sold") || result.startsWith("Repaired") || result.startsWith("Rebuilt");
            messageColor = good ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
        }
    }

    private static Property find(String id) {
        if (id == null) return null;
        for (Property pr : Portfolio.get().properties()) if (pr.id.equals(id)) return pr;
        return null;
    }
}
