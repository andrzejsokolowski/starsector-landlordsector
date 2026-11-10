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
 * <p>Coordinates passed in are from the panel's top left, y growing downwards.
 */
public abstract class Canvas implements CustomUIPanelPlugin {

    public static final Color BG = new Color(10, 14, 22, 235);
    public static final Color CARD = new Color(22, 30, 44, 255);
    public static final Color CARD_EDGE = new Color(70, 84, 110, 255);
    public static final Color BTN = new Color(30, 44, 66, 255);
    public static final Color BTN_HOVER = new Color(46, 66, 98, 255);
    public static final Color BTN_OFF = new Color(26, 28, 34, 255);
    public static final Color GOLD = new Color(200, 170, 80, 255);

    /** A control this panel draws itself. */
    protected static final class Btn {
        public final Object action;
        public final float x, y, w, h;
        public boolean enabled = true;
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

    protected CustomPanelAPI panel;
    protected PositionAPI p;
    protected final float width;
    protected final float height;
    private final List<Btn> buttons = new ArrayList<>();
    private final List<UIComponentAPI> components = new ArrayList<>();
    private boolean wasDown = true;

    protected Canvas(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /** Hands the panel over and builds the contents. */
    public void attach(CustomPanelAPI panel) {
        this.panel = panel;
        // A mouse button already held (the click that opened this panel) is not a new click.
        wasDown = Mouse.isButtonDown(0);
        rebuild();
    }

    /** Throws away every label and button and asks the subclass to lay them out again. */
    public final void rebuild() {
        if (panel == null) return;
        for (UIComponentAPI c : components) panel.removeComponent(c);
        components.clear();
        buttons.clear();
        build();
    }

    protected abstract void build();

    protected abstract void onAction(Object action);

    /** Escape was pressed. */
    protected void onEscape() {
    }

    /** Custom drawing under the buttons and labels, in GL coordinates (see {@link #glX}, {@link #glY}). */
    protected void drawBelow(float alpha) {
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
                e.consume();
                onEscape();
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
            drawBelow(alphaMult);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            for (Btn b : buttons) {
                Color fill = !b.enabled ? BTN_OFF : (b.hovered ? BTN_HOVER : BTN);
                quad(b.x, b.y, b.w, b.h, fill, alphaMult);
                frame(b.x, b.y, b.w, b.h, b.enabled ? GOLD : CARD_EDGE, alphaMult * (b.hovered ? 1f : 0.6f));
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

    /** A bar filled to {@code share} (0 to 1). */
    protected void bar(float x, float y, float w, float h, float share, Color fill, float alpha) {
        quad(x, y, w, h, BTN_OFF, alpha);
        quad(x, y, w * Math.max(0f, Math.min(1f, share)), h, fill, alpha);
        frame(x, y, w, h, CARD_EDGE, alpha);
    }

    protected void sprite(String path, float x, float y, float w, float h, float alpha) {
        if (path == null) return;
        SpriteAPI s = Global.getSettings().getSprite(path);
        if (s == null) return;
        s.setSize(w, h);
        s.setAlphaMult(alpha);
        s.setNormalBlend();
        s.render(glX(x), glY(y, h));
    }
}
