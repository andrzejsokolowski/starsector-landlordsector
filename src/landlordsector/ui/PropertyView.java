package landlordsector.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.PropertyStats;

/** A screen with a list on the left and one home on the right: a large picture and its stat sheet. */
public abstract class PropertyView extends Canvas {

    /** Pictures are 16 by 10. */
    public static final float ART_RATIO = 0.625f;
    private static final float ROW = 22f;
    private static final Color SECURITY = new Color(110, 160, 230, 255);

    /** What the sheet shows. */
    public static final class Sheet {
        public String title = "";
        public String subtitle = "";
        public String art;
        public String valueLine = "";
        public String rentLine = "";
        public float condition = 1f;
        public String occupancy = "";
        public Color occupancyColor = Misc.getTextColor();
        public PropertyStats stats;
        /** Extra label and value pairs under the sheet. */
        public final List<String[]> extra = new ArrayList<>();
    }

    protected PropertyView(float width, float height) {
        super(width, height);
    }

    /** "Sindria, arid world, Sindrian Diktat". */
    protected static String where(MarketAPI m, PropertyStats s) {
        if (m == null) return s.world;
        String world = s.world.isEmpty() ? "" : ", " + s.world.toLowerCase();
        return m.getName() + world + ", " + m.getFaction().getDisplayName();
    }

    /** Height the sheet needs under its picture. */
    protected static float sheetTextHeight(Sheet s) {
        return 8f + 26f + 24f + 5 * ROW + s.extra.size() * ROW;
    }

    /** Width the sheet's text uses: the left column, then the right column up to the end of its numbers. */
    protected static float contentWidth(float w) {
        return w / 2f + 10f + 278f;
    }

    /**
     * The picture and the stat sheet under it. The picture spans the sheet's text, from the title on the
     * left to the end of the Security row on the right, at most {@code maxImgH} tall (cropped from the
     * middle when the box is flatter than 16 by 10).
     *
     * @return the y just below the sheet
     */
    protected float sheet(Sheet s, float x, float y, float w, float maxImgH) {
        float imgW = Math.min(w, contentWidth(w));
        float imgH = Math.max(60f, Math.min(maxImgH, imgW * ART_RATIO));
        float ix = x;
        if (s.art != null) {
            picture(s.art, ix, y, imgW, imgH);
        } else {
            box(ix, y, imgW, imgH);
            label("No picture", Misc.getGrayColor(), ix, y, imgW, imgH, com.fs.starfarer.api.ui.Alignment.MID,
                    com.fs.starfarer.api.ui.Fonts.DEFAULT_SMALL);
        }

        float ty = y + imgH + 8f;
        title(s.title, x, ty, w);
        text(s.subtitle, Misc.getGrayColor(), x, ty + 26f, w);

        float r = ty + 50f;
        float col = w / 2f;
        float lx = x;
        float rx = x + col + 10f;
        float vw = col - 130f;

        row("Value", s.valueLine, Misc.getHighlightColor(), lx, r, vw);
        row("Rent", s.rentLine, Misc.getTextColor(), lx, r + ROW, vw);
        text("Condition", Misc.getGrayColor(), lx, r + 2 * ROW, 120f);
        Color cond = s.condition < 0.35f ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        bar(lx + 120f, r + 2 * ROW + 5f, 110f, 10f, s.condition, cond);
        text(Math.round(s.condition * 100f) + " percent", lx + 238f, r + 2 * ROW, 90f);
        row("Occupancy", s.occupancy, s.occupancyColor, lx, r + 3 * ROW, vw);

        PropertyStats st = s.stats;
        if (st != null) {
            stat("Comfort", st.comfort, GOLD, rx, r);
            stat("Decor", st.decor, GOLD, rx, r + ROW);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            stat("Tenant happiness", st.happiness, happy, rx, r + 2 * ROW);
            stat("Security", st.security, SECURITY, rx, r + 3 * ROW);
            text("Location", Misc.getGrayColor(), lx, r + 4 * ROW, 120f);
            text(st.grade, Misc.getHighlightColor(), lx + 120f, r + 4 * ROW, 30f);
            text(st.gradeReason, Misc.getGrayColor(), lx + 152f, r + 4 * ROW, w - 152f);
        }
        float er = r + 5 * ROW;
        for (String[] pair : s.extra) {
            row(pair[0], pair[1], Misc.getTextColor(), lx, er, w - 130f);
            er += ROW;
        }
        return er;
    }

    private void row(String name, String value, Color color, float x, float y, float valueW) {
        text(name, Misc.getGrayColor(), x, y, 120f);
        text(value, color, x + 120f, y, Math.max(80f, valueW));
    }

    private void stat(String name, float value, Color color, float x, float y) {
        text(name, Misc.getGrayColor(), x, y, 120f);
        bar(x + 120f, y + 5f, 110f, 10f, value / 100f, color);
        text(String.valueOf(Math.round(value)), x + 238f, y, 40f);
    }
}
