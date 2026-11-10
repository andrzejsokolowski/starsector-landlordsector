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
import landlordsector.Climate;
import landlordsector.Listing;
import landlordsector.MarketListings;
import landlordsector.MarketRules;
import landlordsector.Pricing;
import landlordsector.Settings;
import landlordsector.Tier;
import landlordsector.TierTable;
import landlordsector.ui.Canvas;

/** The real estate screen of one market: up to eight homes for sale as picture cards. */
public class BuyScreen extends Canvas implements CustomVisualDialogDelegate {

    public static final float W = 1040f;
    public static final float H = 660f;
    private static final float MARGIN = 14f;
    private static final float GAP = 12f;
    private static final float CARD_W = (W - 2 * MARGIN - 3 * GAP) / 4f;
    private static final float IMG_H = CARD_W * 320f / 512f;
    private static final float CARD_H = IMG_H + 100f;
    private static final float TOP = 84f;
    private static final String LEAVE = "leave";

    private final MarketAPI market;
    private final Runnable onClose;
    private DialogCallbacks callbacks;
    private String message = "";
    private Color messageColor = Misc.getTextColor();
    private List<Listing> shown;
    private boolean closed;

    public BuyScreen(MarketAPI market, Runnable onClose) {
        super(W, H);
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
    protected void build() {
        MarketListings ml = MarketRules.listings(market);
        shown = ml.list();
        boolean own = MarketRules.isOwn(market);
        float tariff = MarketRules.tariff(market);

        title("Real estate on " + market.getName(), MARGIN, 12f, W - 2 * MARGIN);
        String terms = own ? "Your colony: homes cost half, with no tariff."
                : "Prices include the " + Math.round(tariff * 100f) + " percent tariff. Rent pays the same tariff.";
        text("Credits: " + Misc.getDGSCredits(Actions.credits()) + ".   " + terms, MARGIN, 42f, W - 2 * MARGIN);
        int ownedHere = landlordsector.Portfolio.get().countAt(market.getId());
        int cap = Settings.maxProperties();
        String owned = "You own " + ownedHere + (ownedHere == 1 ? " home" : " homes") + " here and "
                + landlordsector.Portfolio.get().properties().size() + " in all"
                + (cap > 0 ? " (most allowed: " + cap + ")." : ".");
        text(owned, Misc.getGrayColor(), MARGIN, 60f, W - 2 * MARGIN);

        if (shown.isEmpty()) {
            float days = TierTable.economy().listingRefreshDays
                    - Global.getSector().getClock().getElapsedDaysSince(ml.rolledAt);
            text("Nothing is for sale right now. New homes come on the market in about "
                    + Math.max(1, Math.round(days)) + " days.", MARGIN, TOP + 20f, W - 2 * MARGIN);
        }

        String blocked = Actions.cannotBuy(market, null);
        for (int i = 0; i < shown.size() && i < 8; i++) {
            Listing l = shown.get(i);
            Tier t = TierTable.get(l.tierId);
            if (t == null) continue;
            float x = MARGIN + (i % 4) * (CARD_W + GAP);
            float y = TOP + (i / 4) * (CARD_H + GAP);
            float price = MarketRules.buyPrice(market, l);
            float full = l.basePrice * MarketRules.mult(market);
            float rent = Pricing.rent(full, t.rentShare, 1f, 1f, Settings.rentMult()) * (1f - tariff);
            float ty = y + IMG_H + 6f;
            text(t.name + ", " + climateLabel(l.climateId), Misc.getHighlightColor(), x + 8f, ty, CARD_W - 16f);
            text("Value " + Misc.getDGSCredits(full), x + 8f, ty + 18f, CARD_W - 16f);
            text("Rent about " + Misc.getDGSCredits(rent) + " a month", Misc.getGrayColor(), x + 8f, ty + 36f,
                    CARD_W - 16f);
            boolean afford = Actions.credits() >= price;
            button(l, "Buy for " + Misc.getDGSCredits(price), x + 8f, y + CARD_H - 32f, CARD_W - 16f, 26f,
                    blocked == null && afford);
        }

        if (blocked != null && message.isEmpty()) {
            message = blocked;
            messageColor = Misc.getNegativeHighlightColor();
        }
        text(message, messageColor, MARGIN, H - 44f, W - 200f);
        button(LEAVE, "Leave", W - MARGIN - 150f, H - 46f, 150f, 30f, true);
    }

    private static String climateLabel(String id) {
        for (Climate c : Climate.values()) if (c.id.equals(id)) return c.label.toLowerCase();
        return "";
    }

    @Override
    protected void drawBelow(float alpha) {
        quad(0f, 0f, W, H, BG, alpha);
        frame(0f, 0f, W, H, GOLD, alpha * 0.5f);
        if (shown == null) return;
        for (int i = 0; i < shown.size() && i < 8; i++) {
            float x = MARGIN + (i % 4) * (CARD_W + GAP);
            float y = TOP + (i / 4) * (CARD_H + GAP);
            quad(x, y, CARD_W, CARD_H, CARD, alpha);
            sprite(shown.get(i).artPath, x, y, CARD_W, IMG_H, alpha);
            frame(x, y, CARD_W, CARD_H, CARD_EDGE, alpha);
        }
    }

    @Override
    protected void onAction(Object action) {
        if (LEAVE.equals(action)) {
            leave();
            return;
        }
        if (action instanceof Listing) {
            String result = Actions.buy(market, (Listing) action);
            message = result;
            messageColor = result.startsWith("Bought") ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor();
            rebuild();
        }
    }

    @Override
    protected void onEscape() {
        leave();
    }

    private void leave() {
        if (callbacks != null) callbacks.dismissDialog();
    }
}
