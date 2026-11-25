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
        /** 1.5.0 icons: the home's size id, its house trait ids, and the nearby building's picture (null: none). */
        public String tierId;
        public final List<String> traitIds = new ArrayList<>();
        public String areaPicture;
        /** 0.4.0: installed upgrades, the same shape as traits. */
        public final List<Object[]> upgrades = new ArrayList<>();
        /** 1.0.1: the ids of those upgrades, for the combined effects in the row's tooltip. */
        public final List<String> upgradeIds = new ArrayList<>();
        /** 0.5.0: placed furniture, the same shape as traits. */
        public final List<Object[]> furniture = new ArrayList<>();
        /** 0.6.0: furniture slots of the home (0 hides the row). */
        public int furnitureSlots;
        /** 0.6.0: the size row ("Shack"). */
        public String size = "";
        /**
         * 0.6.0 (Andrzej: tenant happiness depends on the tenant, not the house): the tenant section. Null hides it
         * (a home for sale); "" shows "None" (an empty home); a name shows the tenant's own health and happiness.
         */
        public String tenant;
        public landlordsector.Tenant tenantRef;
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

    /** A home's upgrades (not furniture) for its sheet. */
    public static List<Object[]> upgradeItems(landlordsector.Property p) {
        return installedItems(p, false);
    }

    /** A home's placed furniture for its sheet (0.5.0: its own row). */
    public static List<Object[]> furnitureItems(landlordsector.Property p) {
        return installedItems(p, true);
    }

    private static List<Object[]> installedItems(landlordsector.Property p, boolean furniture) {
        List<Object[]> out = new ArrayList<>();
        for (landlordsector.Installed i : p.upgrades()) {
            landlordsector.data.Upgrades.Def d = i.def();
            if (d == null || i.furniture != furniture) continue;
            out.add(new Object[] {d.name, Boolean.TRUE, d.name, Tips.upgrade(d)});
        }
        return out;
    }

    /** The Environment row (0.5.0; 0.6.0: grades only, the building nearby has its own Area row). */
    public static List<Object[]> environmentItems(PropertyStats st) {
        List<Object[]> out = new ArrayList<>();
        for (landlordsector.data.Environment.Score sc : landlordsector.data.Environment.scores()) {
            Float v = st.env.get(sc.id);
            if (v == null) continue;
            String letter = landlordsector.data.Environment.letter(v);
            String label = landlordsector.PropertyStats.graded(sc.name, letter);
            out.add(new Object[] {label, gradeGood(letter), label + " (" + Math.round(v) + ")", st.envLines.get(sc.id)});
        }
        return out;
    }

    /**
     * The Climate row (0.6.0, Andrzej: "where can I see the house stats?"): Heat, Cold and Radiation, always, so an
     * upgrade like Lead lining can be judged.
     */
    public static List<Object[]> climateItems(PropertyStats st) {
        List<Object[]> out = new ArrayList<>();
        for (landlordsector.data.Levels.Def ld : landlordsector.data.Levels.all()) {
            int lvl = st.levels.getOrDefault(ld.id, 0);
            String label = landlordsector.data.Levels.label(ld, lvl);
            out.add(new Object[] {label, lvl == 0 ? null : Boolean.FALSE, label, st.levelLines.get(ld.id)});
        }
        return out;
    }

    /** "14 built" with every name in its tooltip (Andrzej, 0.6.0: no long list on the sheet). */
    public static List<Object[]> countItem(List<Object[]> items, String shown, String title) {
        List<Object[]> out = new ArrayList<>();
        List<String[]> lines = new ArrayList<>();
        for (Object[] i : items) lines.add(new String[] {String.valueOf(i[0]), "+"});
        out.add(new Object[] {shown, Boolean.TRUE, title, lines});
        return out;
    }

    /** S and A green, D, E and F red, B and C plain. */
    public static Boolean gradeGood(String letter) {
        if (letter.startsWith("S") || letter.startsWith("A")) return Boolean.TRUE;
        if (letter.startsWith("D") || letter.startsWith("E") || letter.startsWith("F")) return Boolean.FALSE;
        return null;
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
            out.add(t.isShown(i) ? new Object[] {d.name, d.good, d.name, Tips.tenantTrait(d), traitIcon(d.id)}
                    : new Object[] {"?", null, "Hidden trait", Tips.hidden(), traitIcon("hidden")});
        }
        return out;
    }

    /** LLS-31: a tenant trait's icon (kid traits too; "hidden" is the "?" icon). */
    public static String traitIcon(String id) {
        return landlordsector.Art.ICONS + "tenant_" + id + ".png";
    }

    private static Boolean tenantIcons;

    /** LLS-31: true when every tenant trait (and the hidden one) has its icon; until then all traits stay names. */
    public static boolean tenantIconsReady() {
        if (tenantIcons == null) {
            boolean ok = true;
            for (landlordsector.data.TenantTraits.Def d : landlordsector.data.TenantTraits.all()) {
                if (!landlordsector.Art.has(traitIcon(d.id))) {
                    ok = false;
                    break;
                }
            }
            tenantIcons = ok && landlordsector.Art.has(traitIcon("hidden"));
        }
        return tenantIcons;
    }

    /** LLS-31: the space trait icons take: the icon, a gap, the good or bad line under it. */
    public static final float TRAIT_LINE = 3f;

    /** LLS-31: how big trait icons are drawn when {@code n} of them share {@code maxW}. */
    protected static float traitIconSize(int n, float size, float maxW) {
        if (n <= 0) return size;
        return Math.max(20f, Math.min(size, (maxW - ICON_GAP * (n - 1)) / n));
    }

    /**
     * LLS-31 (Andrzej: "did we make tenants use icons for their traits yet? If not, let's do it"): the traits as icons
     * ({name, good, tooltip title, tooltip lines, icon}), each with its tooltip and a green or red line under good and
     * bad ones. When one icon is missing nothing is drawn and the names are written instead.
     */
    @SuppressWarnings("unchecked")
    protected void traitRow(List<Object[]> items, float x, float y, float size, float maxW) {
        if (!tenantIconsReady()) {
            coloredRow(items, x, y, maxW);
            return;
        }
        for (Object[] it : items) {
            if (it.length < 5 || !landlordsector.Art.has((String) it[4])) {
                coloredRow(items, x, y, maxW);
                return;
            }
        }
        float s = traitIconSize(items.size(), size, maxW);
        float cx = x;
        for (Object[] it : items) {
            if (cx + s > x + maxW + 0.5f) break;
            icon((String) it[4], cx, y, s, s, 1f);
            Boolean good = (Boolean) it[1];
            if (good != null) bar(cx, y + s + 1f, s, TRAIT_LINE, 1f, traitColor(good));
            tipColored(cx, y, s, s + 1f + TRAIT_LINE, (String) it[2], (List<String[]>) it[3]);
            cx += s + ICON_GAP;
        }
    }

    /** The home's events, newest first, the event part in red or green (1.16.0: shared by the Family page). */
    protected void homeLog(landlordsector.Property p, float x, float y0, float w) {
        List<String> log = p.log();
        if (log.isEmpty()) {
            text("Nothing yet.", Misc.getGrayColor(), x, y0, w);
            return;
        }
        float y = y0;
        for (int i = log.size() - 1; i >= 0 && y < height - 40f; i--) {
            String[] parts = log.get(i).split("\t", -1);
            String date, colored, rest;
            Boolean good = null;
            if (parts.length == 4) {
                good = "+".equals(parts[0]) ? Boolean.TRUE : ("-".equals(parts[0]) ? Boolean.FALSE : null);
                date = parts[1];
                colored = parts[2];
                rest = parts[3];
            } else {
                // Entries written before 0.3.2: "date  text".
                date = "";
                colored = "";
                rest = log.get(i);
            }
            if (!date.isEmpty()) {
                text(date, Misc.getGrayColor(), x, y, w);
                y += 16f;
            }
            String all = colored + rest;
            int n = good == null ? 0 : colored.length();
            y += wrapped(all, n, landlordsector.Notify.color(good), Misc.getTextColor(), x, y, w) + 6f;
        }
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
        traitRow(tenantTraits(t), tx + 60f, y + 46f, 36f, tw - 60f);
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
            text("Health there", Misc.getGrayColor(), x, ny, 120f);
            Color healthy = st.health >= landlordsector.data.Levels.sickBelow() ? Misc.getPositiveHighlightColor()
                    : (st.health >= landlordsector.data.Levels.treatmentBelow() ? Misc.getHighlightColor()
                            : Misc.getNegativeHighlightColor());
            bar(x + 120f, ny + 5f, 110f, 10f, st.health / 100f, healthy);
            text(String.valueOf(Math.round(st.health)), x + 238f, ny, 40f);
            tipColored(x, ny, 280f, ROW, "Health there: " + Math.round(st.health), visibleLines(t, st.healthLines));
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

    /**
     * "Chicomoztoc, a barren-desert world belonging to the Hegemony" (Andrzej, 0.5.0: the place goes at the top of
     * the screen, not under the picture).
     */
    public static String where(MarketAPI m, PropertyStats s) {
        if (m == null) return s.world;
        return where(m);
    }

    public static String where(MarketAPI m) {
        String w = PropertyStats.worldName(m);
        String world = w.isEmpty() ? "world" : w.toLowerCase();
        String owner;
        if (landlordsector.MarketRules.isOwn(m)) {
            owner = ", your colony";
        } else {
            String f;
            try {
                f = m.getFaction().getDisplayNameWithArticle();
            } catch (Throwable t) {
                f = null;
            }
            if (f == null || f.isEmpty()) f = m.getFaction().getDisplayName();
            owner = " belonging to " + f;
        }
        return m.getName() + ", " + landlordsector.PropertyStats.article(world) + owner;
    }

    /** Gap between two icons, across and down. */
    private static final float ICON_GAP = 4f;

    /** Size of a condition icon (1.5.1: the LunaLib setting, 120 by default; the game's own are 40). */
    public static float iconSize() {
        return landlordsector.Settings.iconSize();
    }

    /** The picture of the building a home sits next to (its industry picture), or null for none. */
    public static String areaPicture(MarketAPI m, String neighborId) {
        String id = landlordsector.Surroundings.current(m, neighborId);
        if (landlordsector.data.Environment.NOTHING.equals(id)) return null;
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI spec = com.fs.starfarer.api.Global.getSettings()
                    .getIndustrySpec(id);
            return spec != null ? spec.getImageName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String gradeIcon(String stat, String letter) {
        return landlordsector.Art.ICONS + "grade_" + stat + "_" + letter.toLowerCase().replace("+", "plus") + ".png";
    }

    /** The game's own climate icons: Heat, Cold, Radiation. Null for any other level. */
    private static String levelIcon(String id, int level) {
        String base = "graphics/icons/markets/";
        if ("heat".equals(id)) return base + (level >= 2 ? "hot_extreme.png" : "hot.png");
        if ("cold".equals(id)) return base + (level >= 2 ? "cold_extreme.png" : "cold.png");
        if ("radiation".equals(id)) return base + "irradiated.png";
        return null;
    }

    /** One icon of the row: its picture, how many icon widths it takes, and its tooltip. */
    private static final class IconSpec {
        final String path;
        final float span;
        final String title;
        final List<String[]> lines;

        IconSpec(String path, float span, String title, List<String[]> lines) {
            this.path = path;
            this.span = span;
            this.title = title;
            this.lines = lines != null ? lines : new ArrayList<>();
        }
    }

    /** Size, Location, Commute, Air, Noise, climate, Area, then each house trait. */
    private static List<IconSpec> icons(Sheet s) {
        List<IconSpec> out = new ArrayList<>();
        String ic = landlordsector.Art.ICONS;
        if (s.tierId != null) out.add(new IconSpec(ic + "size_" + s.tierId + ".png", 1f, "Size: " + s.size, null));
        PropertyStats st = s.stats;
        if (st != null) {
            List<String[]> gl = st.gradeLines;
            if (gl.isEmpty()) {
                gl = new ArrayList<>();
                gl.add(new String[] {st.gradeReason, null});
            }
            out.add(new IconSpec(gradeIcon("location", st.grade), 1f, PropertyStats.graded("Location", st.grade), gl));
            for (landlordsector.data.Environment.Score sc : landlordsector.data.Environment.scores()) {
                Float v = st.env.get(sc.id);
                if (v == null) continue;
                String letter = landlordsector.data.Environment.letter(v);
                out.add(new IconSpec(gradeIcon(sc.id, letter), 1f,
                        PropertyStats.graded(sc.name, letter) + " (" + Math.round(v) + ")", st.envLines.get(sc.id)));
            }
            for (landlordsector.data.Levels.Def ld : landlordsector.data.Levels.all()) {
                int lvl = st.levels.getOrDefault(ld.id, 0);
                if (lvl <= 0) continue;
                // 1.5.2 (Andrzej: "We can also make icons for the climate"): his painted icon once it is in, the
                // game's own condition icon until then.
                String own = ic + "climate_" + ld.id + "_" + Math.min(3, lvl) + ".png";
                String path = landlordsector.Art.has(own) ? own : levelIcon(ld.id, lvl);
                if (path != null) out.add(new IconSpec(path, 1f, landlordsector.data.Levels.label(ld, lvl),
                        st.levelLines.get(ld.id)));
            }
            if (s.areaPicture != null) {
                out.add(new IconSpec(s.areaPicture, 2f, "Area: " + st.area, st.neighborLines));
            } else {
                out.add(new IconSpec(ic + "area_residential.png", 1f, "Area: " + st.area, st.neighborLines));
            }
        }
        for (String id : s.traitIds) {
            landlordsector.data.HouseTraits.Def d = landlordsector.data.HouseTraits.get(id);
            if (d == null || landlordsector.data.Upgrades.get(id) != null) continue;
            out.add(new IconSpec(ic + "trait_" + id + ".png", 1f, d.name, Tips.houseTrait(d)));
        }
        return out;
    }

    /**
     * 1.5.3 (Andrzej: "let's maybe do one row of icons instead of stacking them on top?"): one row. The icons are
     * the setting's size, or smaller when that many would not fit in {@code w}.
     */
    private static float iconSizeFor(List<IconSpec> list, float w) {
        float span = 0f;
        for (IconSpec i : list) span += i.span;
        if (span <= 0f) return iconSize();
        return Math.max(16f, Math.min(iconSize(), (w - ICON_GAP * (list.size() - 1)) / span));
    }

    /** Height of the icon row for a row {@code w} wide. */
    protected static float iconRowsHeight(Sheet s, float w) {
        List<IconSpec> list = icons(s);
        return list.isEmpty() ? 0f : iconSizeFor(list, w);
    }

    /** How wide the icon row may be: all of {@code w} by default; the Real estate tab stops it before its log. */
    protected float iconRowWidth(float w, float cw) {
        return w;
    }

    /** The icons under the picture, in one row; each has the same tooltip the text row had. */
    protected void iconRow(Sheet s, float x, float y, float w) {
        List<IconSpec> list = icons(s);
        float size = iconSizeFor(list, w);
        float ix = x;
        for (IconSpec ic : list) {
            float iw = size * ic.span;
            icon(ic.path, ix, y, iw, size, ic.span);
            tipColored(ix, y, iw, size, ic.title, ic.lines);
            ix += iw + ICON_GAP;
        }
    }

    /** Height the sheet needs under its picture. */
    protected static float sheetTextHeight(Sheet s, float w) {
        // 1.5.2 (Andrzej: "when there are 3 rows of icons, the image of the house downscales. That's not good."):
        // room for the icon row and seven text rows whatever the home has, so the picture keeps one size.
        return 6f + iconSize() + 8f + 30f + 7 * ROW + s.extra.size() * ROW;
    }

    /** Where the right column (the bars) starts, from the sheet's left edge (0.5.0: nearer the middle). */
    protected static final float RIGHT_COL = 430f;

    /**
     * Width the sheet uses (0.5.0, Andrzej: the picture about a third smaller): two thirds of the 0.4 width, at
     * least wide enough for both columns.
     */
    protected static float contentWidth(float w) {
        return Math.min(w, Math.max(RIGHT_COL + 290f, (w / 2f + 288f) * 0.67f));
    }

    /**
     * The picture and the stat sheet under it. The picture is 16 by 10, as wide as the sheet's text and at most
     * {@code maxImgH} tall (narrower then, never cropped).
     *
     * Left: Value, Rent, Condition, Occupancy, Size, Location, Area. Right: the home's Comfort, Decor and Security,
     * then the Tenant section with the tenant's own Health and Happiness (0.6.0). Under both: Traits, Environment,
     * Climate, Upgrades, Furniture.
     *
     * @return the y just below the sheet
     */
    protected float sheet(Sheet s, float x, float y, float w, float maxImgH) {
        float cw = contentWidth(w);
        float imgH = Math.max(60f, Math.min(maxImgH, cw * ART_RATIO));
        float imgW = imgH / ART_RATIO;
        if (s.art != null) {
            picture(s.art, x, y, imgW, imgH);
        } else {
            box(x, y, imgW, imgH);
            label("No picture", Misc.getGrayColor(), x, y, imgW, imgH, com.fs.starfarer.api.ui.Alignment.MID,
                    com.fs.starfarer.api.ui.Fonts.DEFAULT_SMALL);
        }

        // 1.5.0 (Andrzej, LLS-16: "home conditions as icons like market conditions"): a row of icons under the
        // picture, like the conditions on a colony screen. 1.5.2: they replace the text rows, with the same tooltips.
        float iconW = iconRowWidth(w, cw);
        iconRow(s, x, y + imgH + 6f, iconW);
        float ty = y + imgH + 6f + iconRowsHeight(s, iconW) + 8f;
        title(s.title, x, ty, cw);

        float r = ty + 30f;
        float lx = x;
        float rx = x + RIGHT_COL;
        float vw = RIGHT_COL - 130f;

        row("Value", s.valueLine, Misc.getHighlightColor(), lx, r, vw);
        if (s.valueLines != null) tipColored(lx, r, 260f, ROW, "Value", s.valueLines);
        row("Rent", s.rentLine, Misc.getTextColor(), lx, r + ROW, vw);
        text("Condition", Misc.getGrayColor(), lx, r + 2 * ROW, 120f);
        Color cond = s.condition < 0.35f ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        bar(lx + 120f, r + 2 * ROW + 5f, 110f, 10f, s.condition, cond);
        text(Math.round(s.condition * 100f) + "%", lx + 238f, r + 2 * ROW, 90f);
        row("Occupancy", s.occupancy, s.occupancyColor, lx, r + 3 * ROW, vw);
        PropertyStats st = s.stats;
        float rightEnd = r;
        if (st != null) {
            rightEnd = r + (s.tenant != null ? 7 : 3) * ROW;
            stat("Comfort", st.comfort, GOLD, rx, r);
            tipColored(rx, r, 280f, ROW, "Comfort " + Math.round(st.comfort), st.comfortLines);
            stat("Decor", st.decor, GOLD, rx, r + ROW);
            tipColored(rx, r + ROW, 280f, ROW, "Decor " + Math.round(st.decor), st.decorLines);
            stat("Security", st.security, SECURITY, rx, r + 2 * ROW);
            tipColored(rx, r + 2 * ROW, 280f, ROW, "Security " + Math.round(st.security), st.securityLines);

            // The tenant's own bars (Andrzej, 0.6.0): only with a tenant; health first, happiness last.
            if (s.tenant != null) {
                if (s.tenant.isEmpty()) {
                    row("Tenant", "None", Misc.getGrayColor(), rx, r + 4 * ROW, 160f);
                } else {
                    com.fs.starfarer.api.ui.LabelAPI tl = text(s.tenant, Misc.getHighlightColor(), rx, r + 4 * ROW, 290f);
                    if (s.tenantRef != null) factionColored(tl, s.tenantRef);
                    Color healthy = st.health >= landlordsector.data.Levels.sickBelow() ? Misc.getPositiveHighlightColor()
                            : (st.health >= landlordsector.data.Levels.treatmentBelow() ? Misc.getHighlightColor()
                                    : Misc.getNegativeHighlightColor());
                    stat("Health", st.health, healthy, rx, r + 5 * ROW);
                    tipColored(rx, r + 5 * ROW, 280f, ROW, "Health " + Math.round(st.health), st.healthLines);
                    Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                            : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
                    stat("Happiness", st.happiness, happy, rx, r + 6 * ROW);
                    tipColored(rx, r + 6 * ROW, 280f, ROW, "Happiness " + Math.round(st.happiness),
                            st.happinessLines);
                }
            }
        }
        // 1.5.2 (Andrzej: "With the icons incoming, we can remove the text ... The only thing we keep are: 1. The bars
        // 2. The text that is not covered by the icons"): Size, Location, Area, Traits, Environment and Climate are
        // icons now, with the same tooltips.
        float er = r + 4 * ROW;
        float rowW = vw;
        // 0.7.2 (Andrzej): the row shows in gray with 0 when nothing is built, like Furniture.
        text("Upgrades", Misc.getGrayColor(), lx, er, 120f);
        if (s.upgrades.isEmpty()) {
            text("0 built", Misc.getGrayColor(), lx + 120f, er, rowW);
        } else {
            // 1.0.1 (Andrzej): the tooltip adds up what every upgrade does, instead of listing their names.
            List<Object[]> row = new ArrayList<>();
            row.add(new Object[] {s.upgrades.size() + " built", Boolean.TRUE, "Upgrades: " + s.upgrades.size()
                    + " built", Tips.upgradeTotals(s.upgradeIds)});
            coloredRow(row, lx + 120f, er, rowW);
        }
        er += ROW;
        if (s.furnitureSlots > 0) {
            text("Furniture", Misc.getGrayColor(), lx, er, 120f);
            if (s.furniture.isEmpty()) {
                text("0 of " + s.furnitureSlots + " slots", Misc.getGrayColor(), lx + 120f, er, rowW);
            } else {
                coloredRow(countItem(s.furniture, s.furniture.size() + " of " + s.furnitureSlots + " slots", "Furniture"),
                        lx + 120f, er, rowW);
            }
            er += ROW;
        }
        er = Math.max(er, rightEnd);
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
