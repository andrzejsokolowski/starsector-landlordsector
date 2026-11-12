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
        /** House traits: name and good (green), bad (red). */
        public final List<Object[]> traits = new ArrayList<>();
    }

    /** House trait names, colors and tooltips for a sheet: {name, good, tooltip title, tooltip lines}. */
    public static List<Object[]> houseTraits(List<String> ids) {
        List<Object[]> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
            if (d != null) out.add(new Object[] {d.name, d.good, d.name, Tips.houseTrait(d)});
        }
        return out;
    }

    /** A tenant's traits for a row: hidden ones as "?". */
    public static List<Object[]> tenantTraits(landlordsector.Tenant t) {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < t.traits().size(); i++) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(t.traits().get(i));
            if (d == null) continue;
            out.add(t.isShown(i) ? new Object[] {d.name, d.good, d.name, Tips.tenantTrait(d)}
                    : new Object[] {"?", null, "Hidden trait", Tips.hidden()});
        }
        return out;
    }

    public static Color traitColor(Boolean good) {
        if (good == null) return Misc.getTextColor();
        return good ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
    }

    /**
     * Names in a row, each in its own color, separated by commas, each with its tooltip when the item has one
     * ({name, good, tooltip title, tooltip lines}). Returns the x after the last one.
     */
    @SuppressWarnings("unchecked")
    protected float coloredRow(List<Object[]> items, float x, float y, float maxW) {
        float cx = x;
        for (int i = 0; i < items.size(); i++) {
            Object[] item = items.get(i);
            String name = String.valueOf(item[0]) + (i < items.size() - 1 ? "," : "");
            com.fs.starfarer.api.ui.LabelAPI l = text(name, traitColor((Boolean) item[1]), cx, y,
                    Math.max(20f, x + maxW - cx));
            float w = l.computeTextWidth(name);
            float iw = w > 0f ? w : name.length() * 7f;
            if (item.length >= 4 && item[3] != null) tipColored(cx, y, iw, 18f, (String) item[2], (List<String[]>) item[3]);
            cx += iw + 8f;
            if (cx > x + maxW) break;
        }
        return cx;
    }

    /**
     * A tenant: portrait, name, origin and wealth, traits (hidden ones as "?") and, when a home is given,
     * the happiness they would have there and why.
     *
     * @return the y below the card
     */
    protected float tenantCard(landlordsector.Tenant t, landlordsector.Property home, float x, float y, float w) {
        float size = 86f;
        portrait(t.portrait, x, y, size);
        float tx = x + size + 12f;
        float tw = w - size - 12f;
        title(t.name, tx, y, tw);
        String origin = "From " + landlordsector.Tenants.factionName(t.factionId) + ".  Wealth ";
        com.fs.starfarer.api.ui.LabelAPI o = text(origin, Misc.getGrayColor(), tx, y + 28f, tw);
        float ow = o.computeTextWidth(origin);
        String letter = landlordsector.TenantRules.wealthLetter(t.wealth);
        text(letter + ": fits a " + Tips.fitTierName(t.wealth), Misc.getHighlightColor(), tx + ow + 4f, y + 28f, tw - ow);
        tip(tx + ow, y + 28f, 220f, 18f, "Wealth " + letter, Tips.wealth(t.wealth));
        text("Traits", Misc.getGrayColor(), tx, y + 48f, 60f);
        coloredRow(tenantTraits(t), tx + 60f, y + 48f, tw - 60f);
        float ny = y + size + 8f;
        if (home != null) {
            landlordsector.PropertyStats st = landlordsector.PropertyStats.withTenant(home, t);
            text("Happiness there", Misc.getGrayColor(), x, ny, 120f);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            bar(x + 120f, ny + 5f, 110f, 10f, st.happiness / 100f, happy);
            text(String.valueOf(Math.round(st.happiness)), x + 238f, ny, 40f);
            ny += ROW;
            for (landlordsector.PropertyStats.Note n : st.notes) {
                if (!t.screened && n.text.contains(":")) {
                    // Hidden traits keep their likes and dislikes hidden too.
                    String trait = n.text.substring(0, n.text.indexOf(':'));
                    if (!shownTraitName(t, trait)) continue;
                }
                text(n.text, traitColor(n.good), x + 12f, ny, w - 12f);
                ny += 18f;
            }
        }
        return ny;
    }

    private static boolean shownTraitName(landlordsector.Tenant t, String name) {
        for (int i = 0; i < t.traits().size(); i++) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(t.traits().get(i));
            if (d != null && d.name.equals(name)) return t.isShown(i);
        }
        return false;
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
        return 8f + 26f + 24f + 5 * ROW + (s.traits.isEmpty() ? 0f : ROW) + s.extra.size() * ROW;
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
            tip(rx, r, 280f, ROW, "Comfort", Tips.COMFORT);
            stat("Decor", st.decor, GOLD, rx, r + ROW);
            tip(rx, r + ROW, 280f, ROW, "Decor", Tips.DECOR);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            stat("Tenant happiness", st.happiness, happy, rx, r + 2 * ROW);
            List<String[]> hl = new ArrayList<>();
            hl.add(new String[] {Tips.HAPPINESS[0], null});
            for (landlordsector.PropertyStats.Note n : st.notes) {
                hl.add(new String[] {n.text, n.good == null ? null : (n.good ? "+" : "-")});
            }
            tipColored(rx, r + 2 * ROW, 280f, ROW, "Tenant happiness", hl);
            stat("Security", st.security, SECURITY, rx, r + 3 * ROW);
            tip(rx, r + 3 * ROW, 280f, ROW, "Security", Tips.SECURITY);
            text("Location", Misc.getGrayColor(), lx, r + 4 * ROW, 120f);
            text(st.grade, Misc.getHighlightColor(), lx + 120f, r + 4 * ROW, 30f);
            List<String[]> gl = st.gradeLines;
            if (gl.isEmpty()) {
                gl = new ArrayList<>();
                gl.add(new String[] {st.gradeReason, null});
            }
            tipColored(lx, r + 4 * ROW, 152f, ROW, "Location " + st.grade, gl);
        }
        float er = r + 5 * ROW;
        if (!s.traits.isEmpty()) {
            text("Traits", Misc.getGrayColor(), lx, er, 120f);
            coloredRow(s.traits, lx + 120f, er, contentWidth(w) - 120f);
            er += ROW;
        }
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
