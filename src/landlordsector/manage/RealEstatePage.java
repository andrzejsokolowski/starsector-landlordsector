package landlordsector.manage;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Actions;
import landlordsector.MarketRules;
import landlordsector.Portfolio;
import landlordsector.Property;
import landlordsector.RentSetting;
import landlordsector.ui.Canvas;
import oddiszcommons.manage.ManagePage;

/** The "Real estate" tab of the OddiszCommons Manage screen. */
public class RealEstatePage implements ManagePage {

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

    /** One page of the player's homes, with rent, condition, repair and sale controls. */
    static final class View extends Canvas {

        private static final float ROW_H = 74f;
        private static final float TOP = 92f;
        private static final float THUMB_W = 96f;
        private static final float THUMB_H = 60f;

        private int page;
        private String pendingSell;
        private String message = "";
        private Color messageColor = Misc.getTextColor();
        private List<Property> rows;
        private int first;
        private int perPage;

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
            text(message, messageColor, 10f, 72f, width - 20f);

            if (all.isEmpty()) {
                text("You own no homes yet. Dock at a market that is not pirate or Luddic Path and pick"
                        + " Browse real estate.", 10f, TOP + 10f, width - 20f);
                rows = all;
                perPage = 0;
                return;
            }

            perPage = Math.max(1, (int) ((height - TOP - 48f) / ROW_H));
            int pages = (all.size() + perPage - 1) / perPage;
            page = Math.max(0, Math.min(page, pages - 1));
            first = page * perPage;
            rows = all;

            float sellX = sellX();
            float repairX = sellX - 170f;
            float rentX = repairX - 180f;
            float condX = condX();
            float textW = Math.max(220f, condX - 126f);

            for (int i = first; i < Math.min(all.size(), first + perPage); i++) {
                Property pr = all.get(i);
                float y = TOP + (i - first) * ROW_H;
                MarketAPI m = pr.market();
                boolean own = m != null && MarketRules.isOwn(m);
                text(pr.displayName() + (m != null ? " (" + m.getFaction().getDisplayName() + ")" : ""),
                        Misc.getHighlightColor(), 116f, y + 4f, textW);
                text("Value " + Misc.getDGSCredits(pr.value()) + ", rent about "
                        + Misc.getDGSCredits(pr.expectedRent()) + " a month", 116f, y + 22f, textW);
                String status;
                Color statusColor = Misc.getGrayColor();
                if (pr.condemned) {
                    status = "Condemned: no tenants until repaired";
                    statusColor = Misc.getNegativeHighlightColor();
                } else if (m != null && MarketRules.hostile(m)) {
                    status = "Rent frozen: the owners are hostile";
                    statusColor = Misc.getNegativeHighlightColor();
                } else if (own) {
                    status = "Your colony, no tariff. Last month " + Misc.getDGSCredits(pr.lastRent);
                } else {
                    status = "Last month " + Misc.getDGSCredits(pr.lastRent) + ", so far "
                            + Misc.getDGSCredits(pr.totalRent);
                }
                text(status, statusColor, 116f, y + 40f, textW);

                text("Condition " + Math.round(pr.condition * 100f) + " percent", condX, y + 4f, 170f);

                RentSetting s = pr.setting();
                text("Rent", Misc.getGrayColor(), rentX, y + 4f, 180f);
                button("lower:" + pr.id, "<", rentX, y + 24f, 28f, 26f, s != RentSetting.CHARITY);
                label(s.label, Misc.getHighlightColor(), rentX + 32f, y + 24f, 104f, 26f,
                        com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.Fonts.DEFAULT_SMALL);
                button("higher:" + pr.id, ">", rentX + 140f, y + 24f, 28f, 26f, s != RentSetting.GREEDY);

                float cost = pr.repairCost();
                button("repair:" + pr.id, cost > 0f ? "Repair " + Misc.getDGSCredits(cost) : "No repairs needed",
                        repairX, y + 24f, 160f, 26f, cost > 0f && Actions.credits() >= cost && m != null);

                boolean confirm = pr.id.equals(pendingSell);
                button("sell:" + pr.id, (confirm ? "Confirm " : "Sell ") + Misc.getDGSCredits(pr.saleValue()),
                        sellX, y + 24f, 160f, 26f, Actions.cannotSell(pr) == null);
            }

            if (pages > 1) {
                float fy = height - 40f;
                button("prev", "Previous", 10f, fy, 120f, 28f, page > 0);
                text("Page " + (page + 1) + " of " + pages, 140f, fy + 5f, 160f);
                button("next", "Next", 300f, fy, 120f, 28f, page < pages - 1);
            }
        }

        private float sellX() {
            return width - 170f;
        }

        /** Condition column: left of rent (180), repair (170) and sell (170). */
        private float condX() {
            return sellX() - 170f - 180f - 170f;
        }

        @Override
        protected void drawBelow(float alpha) {
            if (rows == null) return;
            float condX = condX();
            for (int i = first; i < Math.min(rows.size(), first + perPage); i++) {
                Property pr = rows.get(i);
                float y = TOP + (i - first) * ROW_H;
                quad(4f, y, width - 8f, ROW_H - 6f, CARD, alpha);
                frame(4f, y, width - 8f, ROW_H - 6f, CARD_EDGE, alpha * 0.7f);
                sprite(pr.artPath, 10f, y + 4f, THUMB_W, THUMB_H, alpha);
                Color fill = pr.condemned ? Misc.getNegativeHighlightColor()
                        : (pr.condition < 0.35f ? Misc.getHighlightColor() : Misc.getPositiveHighlightColor());
                bar(condX, y + 28f, 160f, 14f, pr.condition, fill, alpha);
            }
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
                    } else {
                        keepPending = pr.id;
                        message = "Click again to sell " + pr.displayName() + ". The price includes a broker fee"
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

        private static Property find(String id) {
            for (Property pr : Portfolio.get().properties()) if (pr.id.equals(id)) return pr;
            return null;
        }
    }
}
