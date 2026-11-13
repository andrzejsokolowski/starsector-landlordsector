package landlordsector.buy;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Actions;
import landlordsector.Listing;
import landlordsector.MarketListings;
import landlordsector.MarketRules;
import landlordsector.Portfolio;
import landlordsector.Pricing;
import landlordsector.PropertyStats;
import landlordsector.Settings;
import landlordsector.Tier;
import landlordsector.TierTable;
import landlordsector.ui.PropertyView;

/** The property agent's screen: homes for sale on the left, the chosen one large on the right. */
public class BuyScreen extends PropertyView implements CustomVisualDialogDelegate {

    /** Nearly the whole screen, like the Manage page (Andrzej, 0.3.6: the old 1060 by 720 was too small). */
    public static float screenW() {
        return Math.max(1000f, Global.getSettings().getScreenWidth() - 80f);
    }

    public static float screenH() {
        return Math.max(680f, Global.getSettings().getScreenHeight() - 80f);
    }
    private static final float MARGIN = 14f;
    private static final float LIST_W = 240f;
    private static final float ROW_H = 52f;
    private static final float TOP = 76f;
    private static final String LEAVE = "leave";
    private static final String BUY = "buy";

    private final MarketAPI market;
    private final Runnable onClose;
    private DialogCallbacks callbacks;
    private String message = "";
    private Color messageColor = Misc.getTextColor();
    private int selected;
    private boolean closed;

    public BuyScreen(MarketAPI market, Runnable onClose) {
        super(screenW(), screenH());
        this.market = market;
        this.onClose = onClose;
    }

    // ---- CustomVisualDialogDelegate ----

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        callbacks.getPanelFader().setDurationOut(0.3f);
        attach(panel);
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return this;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void reportDismissed(int option) {
        if (closed) return;
        closed = true;
        if (onClose != null) onClose.run();
    }

    // ---- Layout ----

    @Override
    protected void drawBackground(float alpha) {
        quad(0f, 0f, width, height, BG, alpha);
        frame(0f, 0f, width, height, GOLD, alpha * 0.5f);
    }

    @Override
    protected void build() {
        MarketListings ml = MarketRules.listings(market);
        List<Listing> shown = ml.list();
        boolean own = MarketRules.isOwn(market);
        float tariff = MarketRules.tariff(market);

        title("Real estate on " + market.getName(), MARGIN, 12f, width - 2 * MARGIN);
        String terms = own ? "Your colony: homes cost half, with no tariff."
                : "Prices include the " + Math.round(tariff * 100f) + "% tariff. Rent pays the same tariff.";
        text("Credits: " + Misc.getDGSCredits(Actions.credits()) + ".   " + terms, MARGIN, 42f, width - 2 * MARGIN);

        // The list.
        if (shown.isEmpty()) {
            float days = TierTable.economy().listingRefreshDays
                    - Global.getSector().getClock().getElapsedDaysSince(ml.rolledAt);
            text("Nothing for sale right now.", MARGIN, TOP + 8f, LIST_W);
            text("New homes in about " + Math.max(1, Math.round(days)) + " days.", Misc.getGrayColor(), MARGIN,
                    TOP + 26f, LIST_W);
        }
        selected = Math.max(0, Math.min(selected, shown.size() - 1));
        int rows = Math.max(4, (int) ((height - TOP - 110f) / (ROW_H + 4f)));
        for (int i = 0; i < shown.size() && i < rows; i++) {
            Listing l = shown.get(i);
            Tier t = TierTable.get(l.tierId);
            float y = TOP + i * (ROW_H + 4f);
            area(i, MARGIN, y, LIST_W, ROW_H, i == selected);
            text(l.name != null ? l.name : (t != null ? t.name : l.tierId), Misc.getHighlightColor(), MARGIN + 10f,
                    y + 6f, LIST_W - 20f);
            text((t != null ? t.name + ", " : "") + Misc.getDGSCredits(MarketRules.buyPrice(market, l)), MARGIN + 10f,
                    y + 26f, LIST_W - 20f);
        }
        int ownedHere = Portfolio.get().countAt(market.getId());
        int cap = Settings.maxProperties();
        // Below the list, or below the "nothing for sale" lines when there is none (0.3.8: they overlapped).
        float oy = shown.isEmpty() ? TOP + 60f : TOP + Math.min(shown.size(), rows) * (ROW_H + 4f) + 6f;
        text("You own " + ownedHere + (ownedHere == 1 ? " home" : " homes") + " here,", Misc.getGrayColor(),
                MARGIN, oy, LIST_W);
        text(Portfolio.get().properties().size() + " in all" + (cap > 0 ? " (most allowed " + cap + ")." : "."),
                Misc.getGrayColor(), MARGIN, oy + 18f, LIST_W);

        // The chosen home.
        float rx = MARGIN + LIST_W + 16f;
        float rw = width - rx - MARGIN;
        String blocked = Actions.cannotBuy(market, null);
        if (!shown.isEmpty()) {
            Listing l = shown.get(selected);
            Tier t = TierTable.get(l.tierId);
            PropertyStats st = PropertyStats.of(l, market);
            float full = MarketRules.listingValue(market, l);
            float rent = t == null ? 0f
                    : Pricing.rent(full, t.rentShare, 1f, 1f, Settings.rentMult()) * (1f - tariff);
            Sheet s = new Sheet();
            s.title = l.name != null ? l.name : (t != null ? t.name : l.tierId);
            s.subtitle = (t != null ? t.name + ", " : "") + where(market, st);
            s.art = l.artPath;
            s.valueLine = Misc.getDGSCredits(full);
            if (t != null) {
                s.valueLines = PropertyStats.valueLines(t, l.basePrice, st.grade, st.marketMult, 1f,
                        MarketRules.traitPrice(l.traits, market), t.decor, 1f, false, full);
                float pay = MarketRules.buyPrice(market, l);
                s.valueLines.add(new String[] {(own ? "Your colony: you pay half, " : "With the tariff you pay ")
                        + Misc.getDGSCredits(pay) + ".", null});
            }
            s.rentLine = "about " + Misc.getDGSCredits(rent) + " a month" + (own ? "" : ", after tariff");
            s.condition = 1f;
            s.occupancy = "For sale";
            s.stats = st;
            s.traits.addAll(houseTraits(l.traits));
            float maxImg = height - TOP - sheetTextHeight(s) - 56f;
            sheet(s, rx, TOP, rw, maxImg);

            float price = MarketRules.buyPrice(market, l);
            boolean afford = Actions.credits() >= price;
            button(BUY, "Buy for " + Misc.getDGSCredits(price), width - MARGIN - 150f - 12f - 230f, height - 46f, 230f,
                    30f, blocked == null && afford);
            if (blocked == null && !afford && message.isEmpty()) {
                message = "Not enough credits for this one.";
                messageColor = Misc.getNegativeHighlightColor();
            }
        }
        if (blocked != null && message.isEmpty()) {
            message = blocked;
            messageColor = Misc.getNegativeHighlightColor();
        }
        text(message, messageColor, MARGIN, height - 42f, width - 2 * MARGIN - 410f);
        button(LEAVE, "Leave", width - MARGIN - 150f, height - 46f, 150f, 30f, true);
    }

    @Override
    protected void onAction(Object action) {
        if (LEAVE.equals(action)) {
            leave();
            return;
        }
        if (action instanceof Integer) {
            selected = (Integer) action;
            message = "";
            rebuild();
            return;
        }
        if (BUY.equals(action)) {
            List<Listing> shown = MarketRules.listings(market).list();
            if (selected < 0 || selected >= shown.size()) return;
            String result = Actions.buy(market, shown.get(selected));
            message = result;
            messageColor = result.startsWith("Bought") ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor();
            rebuild();
        }
    }

    @Override
    protected boolean onEscape() {
        leave();
        return true;
    }

    private void leave() {
        if (callbacks != null) callbacks.dismissDialog();
    }
}
