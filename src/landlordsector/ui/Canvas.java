package landlordsector.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A panel that draws and hit-tests its own buttons. Buttons made from tooltips inside custom panels can
 * look right and still never report a click (Gambling Den 0.1.0), so every control here is a rectangle
 * this class draws, with a label on top, and clicks are read from the mouse in {@link #advance}.
 *
 * <p>Coordinates passed in are from the panel's top left, y growing downwards. {@link #build} lays out
 * labels, buttons, boxes, pictures and bars; they are drawn in that order under the labels.
 */
public abstract class Canvas implements CustomUIPanelPlugin {

    public static final Color BG = new Color(10, 14, 22, 235);
    public static final Color CARD = new Color(22, 30, 44, 255);
    public static final Color CARD_EDGE = new Color(70, 84, 110, 255);
    public static final Color BTN = new Color(30, 44, 66, 255);
    public static final Color BTN_HOVER = new Color(46, 66, 98, 255);
    public static final Color BTN_SELECTED = new Color(70, 62, 30, 255);
    public static final Color BTN_OFF = new Color(26, 28, 34, 255);
    public static final Color GOLD = new Color(200, 170, 80, 255);

    /** A control this panel draws itself. */
    protected static final class Btn {
        public final Object action;
        public final float x, y, w, h;
        public boolean enabled = true;
        public boolean selected;
        public boolean hovered;
        public LabelAPI label;

        Btn(Object action, float x, float y, float w, float h) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Box {
        final float x, y, w, h;
        final Color fill, edge;

        Box(float x, float y, float w, float h, Color fill, Color edge) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.fill = fill;
            this.edge = edge;
        }
    }

    private static final class Pic {
        final String path;
        final float x, y, w, h, aspect;

        Pic(String path, float x, float y, float w, float h, float aspect) {
            this.path = path;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.aspect = aspect;
        }
    }

    private static final class Bar {
        final float x, y, w, h, share;
        final Color fill;

        Bar(float x, float y, float w, float h, float share, Color fill) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.share = share;
            this.fill = fill;
        }
    }

    protected CustomPanelAPI panel;
    protected PositionAPI p;
    protected final float width;
    protected final float height;
    private final List<Btn> buttons = new ArrayList<>();
    private final List<UIComponentAPI> components = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Pic> pics = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();

    /** A hover area and what its tooltip says. */
    private static final class Tip {
        final float x, y, w, h;
        final String title;
        final List<String[]> lines;

        Tip(float x, float y, float w, float h, String title, List<String[]> lines) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.title = title;
            this.lines = lines;
        }
    }

    private static final float TIP_W = 380f;
    private final List<Tip> tips = new ArrayList<>();
    private Tip shownTip;
    private CustomPanelAPI tipPanel;
    private LabelAPI probe;
    private boolean wasDown = true;

    protected Canvas(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    /** Hands the panel over and builds the contents. */
    public void attach(CustomPanelAPI panel) {
        this.panel = panel;
        // A mouse button already held (the click that opened this panel) is not a new click.
        wasDown = Mouse.isButtonDown(0);
        rebuild();
    }

    /** Throws away everything and asks the subclass to lay it out again. */
    public final void rebuild() {
        if (panel == null) return;
        for (UIComponentAPI c : components) panel.removeComponent(c);
        components.clear();
        buttons.clear();
        boxes.clear();
        pics.clear();
        bars.clear();
        tips.clear();
        hideTip();
        build();
    }

    protected abstract void build();

    protected abstract void onAction(Object action);

    /**
     * Escape was pressed. Return true to take the key; false leaves it to whatever holds this panel
     * (the Manage screen closes on it).
     */
    protected boolean onEscape() {
        return false;
    }

    /** A key was pressed (an LWJGL key code). Return true to take it. */
    protected boolean onKey(int key) {
        return false;
    }

    /** A button whose caption ends in a hotkey, "Homes (H)", the "(H)" in gold like the game's own. */
    protected Btn hotkeyButton(Object action, String caption, String key, float x, float y, float w, float h) {
        String full = caption + " (" + key + ")";
        Btn b = button(action, full, x, y, w, h, true);
        b.label.setHighlight("(" + key + ")");
        b.label.setHighlightColors(Misc.getHighlightColor());
        return b;
    }

    /** The background under everything, in panel coordinates. */
    protected void drawBackground(float alpha) {
    }

    // ---- Building ----

    protected LabelAPI label(String text, Color color, float x, float y, float w, float h, Alignment align,
            String font) {
        LabelAPI label = Global.getSettings().createLabel(text == null ? "" : text, font);
        label.setColor(color);
        label.setAlignment(align);
        panel.addComponent((UIComponentAPI) label).inTL(x, y).setSize(w, h);
        components.add((UIComponentAPI) label);
        return label;
    }

    protected LabelAPI text(String text, float x, float y, float w) {
        return label(text, Misc.getTextColor(), x, y, w, 18f, Alignment.LMID, Fonts.DEFAULT_SMALL);
    }

    protected LabelAPI text(String text, Color color, float x, float y, float w) {
        return label(text, color, x, y, w, 18f, Alignment.LMID, Fonts.DEFAULT_SMALL);
    }

    protected LabelAPI title(String text, float x, float y, float w) {
        return label(text, GOLD, x, y, w, 26f, Alignment.LMID, Fonts.ORBITRON_20AA);
    }

    protected Btn button(Object action, String caption, float x, float y, float w, float h, boolean enabled) {
        Btn b = new Btn(action, x, y, w, h);
        b.enabled = enabled;
        b.label = label(caption, enabled ? Misc.getButtonTextColor() : Misc.getGrayColor(), x, y, w, h,
                Alignment.MID, Fonts.DEFAULT_SMALL);
        buttons.add(b);
        return b;
    }

    /** A clickable area with no caption of its own: put labels on top of it. */
    protected Btn area(Object action, float x, float y, float w, float h, boolean selected) {
        Btn b = new Btn(action, x, y, w, h);
        b.selected = selected;
        buttons.add(b);
        return b;
    }

    protected void box(float x, float y, float w, float h) {
        boxes.add(new Box(x, y, w, h, CARD, CARD_EDGE));
    }

    /** A 16 by 10 picture, cropped from the middle to fit the box. */
    protected void picture(String path, float x, float y, float w, float h) {
        if (path != null) pics.add(new Pic(path, x, y, w, h, PICTURE_ASPECT));
    }

    /** A square picture (a portrait). */
    protected void portrait(String path, float x, float y, float size) {
        if (path != null) pics.add(new Pic(path, x, y, size, size, 1f));
    }

    /** A bar filled to {@code share} (0 to 1). */
    // ---- Tooltips ----

    /** Plain tooltip lines. */
    protected void tip(float x, float y, float w, float h, String title, String... lines) {
        List<String[]> l = new ArrayList<>();
        for (String s : lines) if (s != null && !s.isEmpty()) l.add(new String[] {s, null});
        tips.add(new Tip(x, y, w, h, title, l));
    }

    /** Tooltip lines with a color each: {text, "+"} green, {text, "-"} red, {text, null} plain. */
    protected void tipColored(float x, float y, float w, float h, String title, List<String[]> lines) {
        tips.add(new Tip(x, y, w, h, title, lines));
    }

    /** "+" green, "-" red, "g" gold, anything else the plain text color. */
    public static Color colorOf(String code) {
        if ("+".equals(code)) return Misc.getPositiveHighlightColor();
        if ("-".equals(code)) return Misc.getNegativeHighlightColor();
        if ("g".equals(code)) return Misc.getHighlightColor();
        return Misc.getTextColor();
    }

    /**
     * Colors parts of a label: {@code line} is {text, color, part, color, part, color, ...} (Andrzej, 0.3.6: in
     * "Likes homes that are Tranquil" only "Likes" and "Tranquil" are colored, each by its own good or bad). Only
     * parts found in {@code shown} (one wrapped row of the line) are colored, in the order they appear.
     */
    public static void highlight(LabelAPI label, String shown, String[] line) {
        if (line == null || line.length < 4 || shown == null) return;
        List<Object[]> found = new ArrayList<>();
        int from = 0;
        for (int i = 2; i + 1 < line.length; i += 2) {
            String part = line[i];
            if (part == null || part.isEmpty()) continue;
            int at = shown.indexOf(part, from);
            if (at < 0) at = shown.indexOf(part);
            if (at < 0) continue;
            found.add(new Object[] {at, part, colorOf(line[i + 1])});
            from = at + part.length();
        }
        if (found.isEmpty()) return;
        found.sort((a, b) -> Integer.compare((Integer) a[0], (Integer) b[0]));
        String[] parts = new String[found.size()];
        Color[] colors = new Color[found.size()];
        for (int i = 0; i < found.size(); i++) {
            parts[i] = (String) found.get(i)[1];
            colors[i] = (Color) found.get(i)[2];
        }
        label.setHighlight(parts);
        label.setHighlightColors(colors);
    }

    private void hideTip() {
        if (tipPanel != null && panel != null) panel.removeComponent(tipPanel);
        tipPanel = null;
        shownTip = null;
    }

    private void showTip(Tip t, float mx, float my) {
        hideTip();
        List<String[]> rows = new ArrayList<>();
        List<String[]> rowLines = new ArrayList<>();
        for (String[] line : t.lines) {
            for (String part : wrap(line[0], TIP_W - 20f)) {
                rows.add(new String[] {part, line[1]});
                rowLines.add(line);
            }
        }
        float h = 12f + (t.title == null ? 0f : 22f) + rows.size() * 18f + 6f;
        tipPanel = panel.createCustomPanel(TIP_W, h, new TipPlugin());
        float y = 6f;
        if (t.title != null) {
            LabelAPI l = Global.getSettings().createLabel(t.title, Fonts.DEFAULT_SMALL);
            l.setColor(Misc.getHighlightColor());
            tipPanel.addComponent((UIComponentAPI) l).inTL(10f, y).setSize(TIP_W - 20f, 20f);
            y += 22f;
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            LabelAPI l = Global.getSettings().createLabel(r[0], Fonts.DEFAULT_SMALL);
            l.setColor(colorOf(r[1]));
            highlight(l, r[0], rowLines.get(i));
            tipPanel.addComponent((UIComponentAPI) l).inTL(10f, y).setSize(TIP_W - 20f, 18f);
            y += 18f;
        }
        float px = Math.min(mx + 18f, width - TIP_W - 4f);
        float py = my + 18f;
        if (py + h > height - 4f) py = Math.max(4f, my - h - 8f);
        panel.addComponent(tipPanel).inTL(Math.max(4f, px), py);
        panel.bringComponentToTop(tipPanel);
        shownTip = t;
    }

    /** Draws its own dark box under the tooltip's labels. */
    private static final class TipPlugin implements CustomUIPanelPlugin {
        private PositionAPI pos;

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null) return;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                box(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), new Color(8, 12, 20, 245), alphaMult);
                Color g = GOLD;
                box(pos.getX(), pos.getY(), pos.getWidth(), 1f, g, alphaMult);
                box(pos.getX(), pos.getY() + pos.getHeight() - 1f, pos.getWidth(), 1f, g, alphaMult);
                box(pos.getX(), pos.getY(), 1f, pos.getHeight(), g, alphaMult);
                box(pos.getX() + pos.getWidth() - 1f, pos.getY(), 1f, pos.getHeight(), g, alphaMult);
            } finally {
                GL11.glPopAttrib();
            }
        }

        private static void box(float x, float y, float w, float h, Color c, float a) {
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f * a);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + w, y);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x, y + h);
            GL11.glEnd();
        }

        @Override
        public void render(float alphaMult) {
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }
    }

    // ---- Wrapped text ----

    /** Splits text into lines no wider than {@code w}, measured with the small font. */
    protected List<String> wrap(String text, float w) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        if (probe == null) probe = Global.getSettings().createLabel("", Fonts.DEFAULT_SMALL);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && probe.computeTextWidth(next) > w) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /**
     * Text wrapped to {@code w}, with its first {@code coloredChars} characters in {@code colored} and the
     * rest in {@code plain}.
     *
     * @return the height used
     */
    protected float wrapped(String text, int coloredChars, Color colored, Color plain, float x, float y, float w) {
        List<String> lines = wrap(text, w);
        int done = 0;
        float yy = y;
        for (String line : lines) {
            LabelAPI l = label(line, plain, x, yy, w, 18f, Alignment.LMID, Fonts.DEFAULT_SMALL);
            int start = done;
            if (coloredChars > start && !line.isEmpty()) {
                int upTo = Math.min(line.length(), coloredChars - start);
                l.setHighlight(line.substring(0, upTo));
                l.setHighlightColor(colored);
            }
            done = start + line.length() + 1;
            yy += 18f;
        }
        return yy - y;
    }

    protected void bar(float x, float y, float w, float h, float share, Color fill) {
        bars.add(new Bar(x, y, w, h, share, fill));
    }

    // ---- Engine callbacks ----

    @Override
    public void positionChanged(PositionAPI position) {
        this.p = position;
    }

    @Override
    public void advance(float amount) {
        if (p == null || panel == null) return;
        float scale = Global.getSettings().getScreenScaleMult();
        float mx = Mouse.getX() / scale - p.getX();
        float my = p.getY() + p.getHeight() - Mouse.getY() / scale;
        boolean inside = mx >= 0 && my >= 0 && mx <= p.getWidth() && my <= p.getHeight();
        for (Btn b : buttons) b.hovered = inside && b.enabled && contains(b, mx, my);
        Tip over = null;
        if (inside) {
            for (Tip t : tips) {
                if (mx >= t.x && mx <= t.x + t.w && my >= t.y && my <= t.y + t.h) over = t;
            }
        }
        if (over != shownTip) {
            if (over == null) hideTip();
            else showTip(over, mx, my);
        }

        boolean down = Mouse.isButtonDown(0);
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (!clicked || !inside) return;
        for (Btn b : new ArrayList<>(buttons)) {
            if (!contains(b, mx, my)) continue;
            if (!b.enabled) {
                Global.getSoundPlayer().playUISound("ui_button_disabled_pressed", 1f, 1f);
                return;
            }
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
            onAction(b.action);
            return;
        }
    }

    private static boolean contains(Btn b, float x, float y) {
        return x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed() || !e.isKeyDownEvent()) continue;
            if (e.getEventValue() == Keyboard.KEY_ESCAPE) {
                // Only take Escape when this panel uses it, so the Manage screen can still close on it.
                if (onEscape()) e.consume();
                return;
            }
            if (onKey(e.getEventValue())) {
                e.consume();
                return;
            }
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (p == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawBackground(alphaMult);
            for (Box b : boxes) {
                quad(b.x, b.y, b.w, b.h, b.fill, alphaMult);
                frame(b.x, b.y, b.w, b.h, b.edge, alphaMult * 0.8f);
            }
            for (Btn b : buttons) {
                Color fill;
                if (!b.enabled) fill = BTN_OFF;
                else if (b.selected) fill = BTN_SELECTED;
                else if (b.hovered) fill = BTN_HOVER;
                else fill = BTN;
                quad(b.x, b.y, b.w, b.h, fill, alphaMult);
                boolean lit = b.enabled && (b.hovered || b.selected);
                frame(b.x, b.y, b.w, b.h, b.enabled ? GOLD : CARD_EDGE, alphaMult * (lit ? 1f : 0.5f));
            }
            for (Pic pic : pics) {
                sprite(pic.path, pic.x, pic.y, pic.w, pic.h, alphaMult, pic.aspect);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                frame(pic.x, pic.y, pic.w, pic.h, CARD_EDGE, alphaMult);
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            for (Bar b : bars) {
                quad(b.x, b.y, b.w, b.h, BTN_OFF, alphaMult);
                quad(b.x, b.y, b.w * Math.max(0f, Math.min(1f, b.share)), b.h, b.fill, alphaMult);
                frame(b.x, b.y, b.w, b.h, CARD_EDGE, alphaMult);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    // ---- Drawing helpers, all in top-down panel coordinates ----

    protected float glX(float x) {
        return p.getX() + x;
    }

    protected float glY(float y, float h) {
        return p.getY() + p.getHeight() - y - h;
    }

    protected void quad(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f * alpha);
        float gx = glX(x);
        float gy = glY(y, h);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(gx, gy);
        GL11.glVertex2f(gx + w, gy);
        GL11.glVertex2f(gx + w, gy + h);
        GL11.glVertex2f(gx, gy + h);
        GL11.glEnd();
    }

    protected void frame(float x, float y, float w, float h, Color c, float alpha) {
        quad(x, y, w, 1f, c, alpha);
        quad(x, y + h - 1f, w, 1f, c, alpha);
        quad(x, y, 1f, h, c, alpha);
        quad(x + w - 1f, y, 1f, h, c, alpha);
    }

    /** Pictures are drawn as 16 by 10 and cropped from the middle to fit a box of another shape. */
    public static final float PICTURE_ASPECT = 1.6f;

    protected void sprite(String path, float x, float y, float w, float h, float alpha) {
        sprite(path, x, y, w, h, alpha, PICTURE_ASPECT);
    }

    protected void sprite(String path, float x, float y, float w, float h, float alpha, float aspect) {
        if (path == null || w <= 0f || h <= 0f) return;
        SpriteAPI s = Global.getSettings().getSprite(path);
        if (s == null) return;
        // Absolute texture window every frame, so a shared sprite never drifts.
        float tw = s.getTextureWidth();
        float th = s.getTextureHeight();
        float box = w / h;
        if (box > aspect) {
            float keep = aspect / box;
            s.setTexX(0f);
            s.setTexWidth(tw);
            s.setTexY(th * (1f - keep) / 2f);
            s.setTexHeight(th * keep);
        } else {
            float keep = box / aspect;
            s.setTexX(tw * (1f - keep) / 2f);
            s.setTexWidth(tw * keep);
            s.setTexY(0f);
            s.setTexHeight(th);
        }
        s.setSize(w, h);
        s.setAlphaMult(alpha);
        s.setNormalBlend();
        s.render(glX(x), glY(y, h));
    }
}
