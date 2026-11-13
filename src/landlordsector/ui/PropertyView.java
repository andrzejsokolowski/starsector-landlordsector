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
        /** What makes up the value, for the Value row's tooltip. */
        public List<String[]> valueLines;
        public String rentLine = "";
        public float condition = 1f;
        public String occupancy = "";
        public Color occupancyColor = Misc.getTextColor();
        public PropertyStats stats;
        /** Extra label and value pairs under the sheet. */
        public final List<String[]> extra = new ArrayList<>();
        /** House traits: name and good (green), bad (red). */
        public final List<Object[]> traits = new ArrayList<>();
        /** 0.4.0: installed upgrades, the same shape as traits. */
        public final List<Object[]> upgrades = new ArrayList<>();
    }

    /** Upgrade names for a sheet: {name, good, tooltip title, tooltip lines} (0.4.0). */
    public static List<Object[]> upgradeNames(List<String> ids) {
        List<Object[]> out = new ArrayList<>();
        if (ids == null) return out;
        for (String id : ids) {
            landlordsector.data.Upgrades.Def d = landlordsector.data.Upgrades.get(id);
            if (d != null) out.add(new Object[] {d.name, true, d.name, Tips.upgrade(d)});
        }
        return out;
    }

    /** A home's upgrades for its sheet; renovations still being built read "Pool (12 days)" in plain white. */
    public static List<Object[]> upgradeItems(landlordsector.Property p) {
        List<Object[]> out = new ArrayList<>();
        for (landlordsector.Installed i : p.upgrades()) {
            landlordsector.data.Upgrades.Def d = i.def();
            if (d == null) continue;
            boolean done = i.ready();
            String name = done ? d.name : d.name + " (" + Math.max(1, Math.round(i.daysLeft())) + " days)";
            out.add(new Object[] {name, done ? Boolean.TRUE : null, d.name, Tips.upgrade(d)});
        }
        return out;
    }

    /** Home levels for a sheet: only the ones that are a problem, or "All fine" (0.4.0). */
    public static List<Object[]> levelItems(PropertyStats st) {
        List<Object[]> out = new ArrayList<>();
        List<String[]> all = new ArrayList<>();
        for (landlordsector.data.Levels.Def ld : landlordsector.data.Levels.all()) {
            int lvl = st.levels.getOrDefault(ld.id, 0);
            String label = landlordsector.data.Levels.label(ld, lvl);
            all.add(new String[] {label, lvl == 0 ? "+" : "-"});
            if (lvl == 0) continue;
            out.add(new Object[] {label, Boolean.FALSE, label, st.levelLines.get(ld.id)});
        }
        if (out.isEmpty()) {
            all.add(new String[] {"Heat, cold, bad air, noise and darkness each cost tenants happiness and health.",
                    null});
            out.add(new Object[] {"All fine", Boolean.TRUE, "Home levels", all});
        }
        return out;
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
        String head = nameAndFaction(t);
        factionColored(title(head, tx, y, tw), t);
        String letter = landlordsector.TenantRules.wealthLetter(t.wealth);
        String wl = landlordsector.PropertyStats.graded("Wealth", letter);
        com.fs.starfarer.api.ui.LabelAPI o = text(wl, Misc.getGrayColor(), tx, y + 28f, tw);
        o.setHighlight("[" + letter + "]");
        o.setHighlightColors(Misc.getHighlightColor());
        tip(tx, y + 28f, Math.max(80f, o.computeTextWidth(wl) + 8f), 18f, wl, Tips.wealth(t.wealth, t.homeShift()));
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
            tipColored(x, ny, 280f, ROW, "Happiness there: " + Math.round(st.happiness), visibleLines(t,
                    st.happinessLines));
            ny += ROW;
            for (landlordsector.PropertyStats.Note n : st.notes) {
                if (n.text.contains(":") && hiddenTraitName(t, n.text.substring(0, n.text.indexOf(':')))) {
                    // Hidden traits keep their likes and dislikes hidden too.
                    continue;
                }
                com.fs.starfarer.api.ui.LabelAPI nl2 = text(n.text, traitColor(n.good), x + 12f, ny, w - 12f);
                Canvas.highlight(nl2, n.text, n.line());
                ny += 18f;
            }
        }
        return ny;
    }

    /**
     * "Matteo Zaniah - Hegemony" (Andrzej, 0.3.7: players new to the Sector may not know every faction's color,
     * so the faction is named, in its own color).
     */
    public static String nameAndFaction(landlordsector.Tenant t) {
        return t.name + " - " + landlordsector.Tenants.factionName(t.factionId);
    }

    /** Colors the faction part of a {@link #nameAndFaction} label in the faction's own color. */
    public static void factionColored(com.fs.starfarer.api.ui.LabelAPI label, landlordsector.Tenant t) {
        label.setHighlight(landlordsector.Tenants.factionName(t.factionId));
        label.setHighlightColors(landlordsector.Tenants.factionColor(t.factionId));
    }

    /** Tooltip lines without the ones about the tenant's hidden traits. */
    public static List<String[]> visibleLines(landlordsector.Tenant t, List<String[]> lines) {
        List<String[]> out = new ArrayList<>();
        for (String[] l : lines) {
            int c = l[0].indexOf(':');
            if (c > 0 && hiddenTraitName(t, l[0].substring(0, c))) continue;
            out.add(l);
        }
        return out;
    }

    /** True when {@code name} is one of the tenant's traits that is still hidden. */
    public static boolean hiddenTraitName(landlordsector.Tenant t, String name) {
        for (int i = 0; i < t.traits().size(); i++) {
            landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(t.traits().get(i));
            if (d != null && d.name.equals(name)) return !t.isShown(i);
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
        return 8f + 26f + 24f + 5 * ROW + (s.traits.isEmpty() ? 0f : ROW) + (s.stats != null ? ROW : 0f)
                + (s.upgrades.isEmpty() ? 0f : ROW) + s.extra.size() * ROW;
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
        if (s.valueLines != null) tipColored(lx, r, 260f, ROW, "Value", s.valueLines);
        row("Rent", s.rentLine, Misc.getTextColor(), lx, r + ROW, vw);
        text("Condition", Misc.getGrayColor(), lx, r + 2 * ROW, 120f);
        Color cond = s.condition < 0.35f ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        bar(lx + 120f, r + 2 * ROW + 5f, 110f, 10f, s.condition, cond);
        text(Math.round(s.condition * 100f) + "%", lx + 238f, r + 2 * ROW, 90f);
        row("Occupancy", s.occupancy, s.occupancyColor, lx, r + 3 * ROW, vw);

        PropertyStats st = s.stats;
        if (st != null) {
            stat("Comfort", st.comfort, GOLD, rx, r);
            tipColored(rx, r, 280f, ROW, "Comfort " + Math.round(st.comfort), st.comfortLines);
            stat("Decor", st.decor, GOLD, rx, r + ROW);
            tipColored(rx, r + ROW, 280f, ROW, "Decor " + Math.round(st.decor), st.decorLines);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            stat("Tenant happiness", st.happiness, happy, rx, r + 2 * ROW);
            tipColored(rx, r + 2 * ROW, 280f, ROW, "Tenant happiness " + Math.round(st.happiness), st.happinessLines);
            stat("Security", st.security, SECURITY, rx, r + 3 * ROW);
            tipColored(rx, r + 3 * ROW, 280f, ROW, "Security " + Math.round(st.security), st.securityLines);
            Color healthy = st.health >= landlordsector.data.Levels.sickBelow() ? Misc.getPositiveHighlightColor()
                    : (st.health >= landlordsector.data.Levels.treatmentBelow() ? Misc.getHighlightColor()
                            : Misc.getNegativeHighlightColor());
            stat("Tenant health", st.health, healthy, rx, r + 4 * ROW);
            tipColored(rx, r + 4 * ROW, 280f, ROW, "Tenant health " + Math.round(st.health), st.healthLines);
            text("Location", Misc.getGrayColor(), lx, r + 4 * ROW, 120f);
            text("[" + st.grade + "]", Misc.getHighlightColor(), lx + 120f, r + 4 * ROW, 50f);
            List<String[]> gl = st.gradeLines;
            if (gl.isEmpty()) {
                gl = new ArrayList<>();
                gl.add(new String[] {st.gradeReason, null});
            }
            tipColored(lx, r + 4 * ROW, 172f, ROW, landlordsector.PropertyStats.graded("Location", st.grade), gl);
        }
        float er = r + 5 * ROW;
        if (!s.traits.isEmpty()) {
            text("Traits", Misc.getGrayColor(), lx, er, 120f);
            coloredRow(s.traits, lx + 120f, er, contentWidth(w) - 120f);
            er += ROW;
        }
        if (st != null) {
            text("Home levels", Misc.getGrayColor(), lx, er, 120f);
            coloredRow(levelItems(st), lx + 120f, er, contentWidth(w) - 120f);
            er += ROW;
        }
        if (!s.upgrades.isEmpty()) {
            text("Upgrades", Misc.getGrayColor(), lx, er, 120f);
            coloredRow(s.upgrades, lx + 120f, er, contentWidth(w) - 120f);
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
