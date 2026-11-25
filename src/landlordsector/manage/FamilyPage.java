package landlordsector.manage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Child;
import landlordsector.Family;
import landlordsector.Kids;
import landlordsector.Portfolio;
import landlordsector.Property;
import landlordsector.PropertyStats;
import landlordsector.Tenant;
import landlordsector.ui.PropertyView;
import oddiszcommons.manage.Manage;
import oddiszcommons.manage.ManagePage;

/**
 * 1.16.0 (LLS-46, from Landlordsector Renewed): the Family page of the Manage screen, next to Real estate. Homes with
 * family on the left, the chosen home's partner, proposals and kids in the middle (scrolling a block at a time), its
 * events on the right. The Real estate page's Family tab sends you here.
 */
public class FamilyPage implements ManagePage {

    public static final String PAGE_ID = "landlordsector_family";

    /** Set by "Go to Family page": the home to show when the page opens. */
    private static String showHome;

    private String selectedHome;
    /** Blocks of the middle column scrolled out of sight. */
    private int skip;
    private String message = "";
    private Color messageColor = Misc.getTextColor();

    /** Opens the Family page on this home. False when the Manage screen could not open it right now. */
    public static boolean open(String homeId) {
        showHome = homeId;
        boolean ok = false;
        try {
            ok = Manage.openPage(PAGE_ID);
        } catch (Throwable ignored) {
            // The Manage screen can't switch now; the queued tab below may still do it.
        }
        if (!ok) {
            try {
                oddiszcommons.manage.ManageBar.requestTabSelect(PAGE_ID);
            } catch (Throwable ignored) {
                showHome = null;
            }
        }
        return ok;
    }

    @Override
    public CustomPanelAPI createPanel(float width, float height) {
        if (showHome != null) {
            selectedHome = showHome;
            showHome = null;
            skip = 0;
        }
        message = "";
        View view = new View(width, height);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, view);
        view.attach(panel);
        return panel;
    }

    /** Homes you live in, and homes where a partner or kids of yours live. */
    public static List<Property> familyHomes() {
        List<Property> out = new ArrayList<>();
        for (Property p : Portfolio.get().properties()) {
            if (p.playerHome || p.partner() != null || !p.children().isEmpty()) out.add(p);
        }
        return out;
    }

    private static Property find(String id) {
        if (id == null) return null;
        for (Property p : Portfolio.get().properties()) if (p.id.equals(id)) return p;
        return null;
    }

    final class View extends PropertyView {

        private static final float LIST_W = 260f;
        private static final float ROW_H = 50f;
        private static final float TOP = 96f;
        private static final float BLOCKS_TOP = 130f;

        /** One piece of the middle column: its height, the gap after it and how it draws at a y. */
        private final class Block {
            final float h, gap;
            final java.util.function.Consumer<Float> draw;

            Block(float h, float gap, java.util.function.Consumer<Float> draw) {
                this.h = h;
                this.gap = gap;
                this.draw = draw;
            }
        }

        private float midX, midW;
        /** The scroll bar: shown when something is out of sight. */
        private boolean bar;
        private float barX, barTop, barH, thumbY, thumbH;

        View(float width, float height) {
            super(width, height);
        }

        @Override
        protected void build() {
            List<Property> homes = familyHomes();
            title("Family", 10f, 8f, 300f);
            text(summary(homes), 10f, 38f, width - 20f);
            int away = Portfolio.get().exKids().size();
            if (away > 0) {
                text(away + (away == 1 ? " child lives" : " children live") + " with a former spouse. Child support: "
                        + Misc.getDGSCredits(Family.supportOut()) + " a month.", Misc.getGrayColor(), 10f, 56f,
                        width - 20f);
            }
            text(message, messageColor, 10f, 74f, width - 20f);
            bar = false;
            if (homes.isEmpty()) {
                text("You don't live in any of your homes. Move into one on the Real estate page.", Misc.getGrayColor(),
                        10f, TOP + 10f, width - 20f);
                return;
            }
            Property p = find(selectedHome);
            if (p == null || !homes.contains(p)) p = homes.get(0);
            selectedHome = p.id;

            homeList(homes, p);
            float eventsW = Math.max(240f, Math.min(360f, Math.round(width * 0.26f)));
            float eventsX = width - 8f - eventsW;
            title("Events", eventsX, TOP, eventsW);
            homeLog(p, eventsX, TOP + 30f, eventsW);

            midX = 8f + LIST_W + 16f;
            midW = eventsX - 24f - midX;
            label(p.displayName(), Misc.getBasePlayerColor(), midX, TOP, midW - 270f, 24f, Alignment.LMID,
                    Fonts.DEFAULT_SMALL);
            label(p.tier().name, Misc.getGrayColor(), midX + midW - 260f, TOP + 3f, 260f, 18f, Alignment.RMID,
                    Fonts.DEFAULT_SMALL);
            layOut(blocks(p), BLOCKS_TOP, height - 48f);
            moveOut(p);
        }

        /** "2 homes with family: 1 partner, 3 kids. Raising them costs 2,000 a month." */
        private String summary(List<Property> homes) {
            int partners = 0, kids = 0;
            float cost = 0f;
            for (Property p : homes) {
                if (p.partner() != null) partners++;
                for (Child c : p.children()) {
                    if (c.grown) continue;
                    kids++;
                    cost += Kids.upkeep(c);
                }
            }
            // LLS-46 note on the Renewed picks: no "partner" in the text; you always marry, so "spouse".
            String s = homes.size() + (homes.size() == 1 ? " home" : " homes") + " with family: " + partners
                    + (partners == 1 ? " spouse" : " spouses") + " and " + kids + (kids == 1 ? " kid" : " kids");
            return cost > 0f ? s + ". Raising them costs " + Misc.getDGSCredits(cost) + " a month." : s + ".";
        }

        private void homeList(List<Property> homes, Property chosen) {
            float y = TOP;
            for (Property p : homes) {
                if (y + ROW_H > height - 8f) break;
                area("home:" + p.id, 8f, y, LIST_W, ROW_H, p == chosen);
                text(p.displayName(), Misc.getTextColor(), 18f, y + 5f, LIST_W - 20f);
                String warn = landlordsector.Partners.repairWarning(p);
                text(warn != null ? warn : status(p), warn != null ? Misc.getNegativeHighlightColor()
                        : Misc.getGrayColor(), 18f, y + 25f, LIST_W - 20f);
                y += ROW_H + 4f;
            }
        }

        /** "Mira Vance, 2 kids", "1 kid", "You live here alone". */
        private String status(Property p) {
            int kids = 0;
            for (Child c : p.children()) if (!c.grown) kids++;
            String k = kids + (kids == 1 ? " kid" : " kids");
            Tenant t = p.partner();
            if (t != null) return kids > 0 ? t.name + ", " + k : t.name;
            if (kids > 0) return k;
            return p.playerHome ? "You live here alone" : "";
        }

        // ---- The middle column ----

        private List<Block> blocks(Property p) {
            List<Block> out = new ArrayList<>();
            Tenant t = p.partner();
            if (t != null) {
                out.add(new Block(30f, 0f, y -> title("Your " + Family.noun(t), midX, y, midW)));
                List<Object[]> lines = childLines(p, t);
                out.add(new Block(PARTNER_H + (lines.isEmpty() ? 0f : lines.size() * 18f + 4f), 20f,
                        y -> partnerCard(p, t, lines, midX, y, midW)));
            } else if (p.playerHome) {
                List<PersonAPI> people = Family.candidates();
                out.add(new Block(30f, 0f, y -> {
                    title("Propose", midX, y, 200f);
                    label(people.size() + " in your service", Misc.getGrayColor(), midX + midW - 360f, y + 4f, 360f,
                            18f, Alignment.RMID, Fonts.DEFAULT_SMALL);
                }));
                if (people.isEmpty()) {
                    out.add(new Block(18f, 20f, y -> text("No officers or administrators of the opposite sex.",
                            Misc.getGrayColor(), midX, y, midW)));
                }
                for (int i = 0; i < people.size(); i++) {
                    PersonAPI who = people.get(i);
                    out.add(new Block(50f, i == people.size() - 1 ? 20f : 4f, y -> candidateRow(p, who, midX, y, midW)));
                }
            }
            if (!p.children().isEmpty()) {
                out.add(new Block(30f, 0f, y -> title("Kids", midX, y, midW)));
                for (Child c : p.children()) out.add(new Block(KID_H, 4f, y -> kidRow(p, c, midX, y, midW)));
            }
            return out;
        }

        /** Draws the blocks from the scroll position while they fit, and works out the scroll bar. */
        private void layOut(List<Block> blocks, float top, float bottom) {
            float room = bottom - top;
            int maxSkip = blocks.size();
            float tail = 0f;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                tail += blocks.get(i).h + blocks.get(i).gap;
                if (tail - blocks.get(i).gap > room) break;
                maxSkip = i;
            }
            if (blocks.isEmpty()) maxSkip = 0;
            skip = Math.max(0, Math.min(skip, maxSkip));
            float total = 0f, hidden = 0f, shown = 0f;
            for (int i = 0; i < blocks.size(); i++) {
                total += blocks.get(i).h + blocks.get(i).gap;
                if (i < skip) hidden += blocks.get(i).h + blocks.get(i).gap;
            }
            float y = top;
            boolean cut = false;
            for (int i = skip; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                if (y + b.h > bottom) {
                    cut = true;
                    break;
                }
                b.draw.accept(y);
                shown += b.h + b.gap;
                y += b.h + b.gap;
            }
            bar = skip > 0 || cut;
            if (!bar || total <= 0f) return;
            barX = midX + midW + 8f;
            barTop = top;
            barH = room;
            thumbH = Math.min(barH, Math.max(16f, barH * shown / total));
            thumbY = Math.min(barTop + barH - thumbH, barTop + barH * hidden / total);
        }

        @Override
        protected boolean onScroll(int dir, float x, float y) {
            if (!bar || x < midX || x > midX + midW + 16f || y < BLOCKS_TOP || y > height - 48f) return false;
            skip -= dir;
            rebuild();
            return true;
        }

        @Override
        public void renderBelow(float alphaMult) {
            super.renderBelow(alphaMult);
            if (!bar) return;
            quad(barX, barTop, 4f, barH, Misc.getDarkPlayerColor(), 0.4f * alphaMult);
            quad(barX, thumbY, 4f, thumbH, Misc.getBasePlayerColor(), 0.8f * alphaMult);
        }

        private static final float PARTNER_H = 102f;

        /** Your wife or husband: portrait, how long together, the last visit, traits, happiness and health. */
        private void partnerCard(Property p, Tenant t, List<Object[]> lines, float x, float y, float w) {
            box(x, y, w, PARTNER_H + (lines.isEmpty() ? 0f : lines.size() * 18f + 4f));
            portrait(t.portrait, x + 8f, y + 8f, 86f);
            float rx = x + w - 8f - 244f;
            float tx = x + 104f, tw = rx - 12f - tx;
            text(t.name, Misc.getHighlightColor(), tx, y + 8f, tw);
            int mo = Math.max(0, Math.round(Global.getSector().getClock().getElapsedDaysSince(t.since) / 30f));
            text(Family.capital(Family.noun(t)) + ". Together " + mo + (mo == 1 ? " month." : " months."),
                    Misc.getGrayColor(), tx, y + 27f, tw);
            text(Family.isSophia(t.person) ? "Keeps her research post. Last visited " + Family.lastVisited(t) + "."
                    : "Last visited " + Family.lastVisited(t) + ".", Misc.getTextColor(), tx, y + 46f, tw);
            traitRow(tenantTraits(t), tx, y + 68f, 24f, tw);
            PropertyStats st = p.stats();
            text("Happiness", Misc.getGrayColor(), rx, y + 8f, 90f);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            bar(rx + 96f, y + 13f, 110f, 10f, st.happiness / 100f, happy);
            label(String.valueOf(Math.round(st.happiness)), Misc.getTextColor(), rx + 210f, y + 8f, 34f, 18f,
                    Alignment.RMID, Fonts.DEFAULT_SMALL);
            tipColored(rx, y + 8f, 244f, 20f, "Happiness: " + Math.round(st.happiness), st.happinessLines);
            text("Health", Misc.getGrayColor(), rx, y + 30f, 90f);
            Color healthy = st.health >= landlordsector.data.Levels.sickBelow() ? Misc.getPositiveHighlightColor()
                    : (st.health >= landlordsector.data.Levels.treatmentBelow() ? Misc.getHighlightColor()
                            : Misc.getNegativeHighlightColor());
            bar(rx + 96f, y + 35f, 110f, 10f, st.health / 100f, healthy);
            label(String.valueOf(Math.round(st.health)), Misc.getTextColor(), rx + 210f, y + 30f, 34f, 18f,
                    Alignment.RMID, Fonts.DEFAULT_SMALL);
            tipColored(rx, y + 30f, 244f, 20f, "Health: " + Math.round(st.health), st.healthLines);
            float ly = y + PARTNER_H - 2f;
            for (Object[] l : lines) {
                text((String) l[0], (Color) l[1], x + 10f, ly, w - 20f);
                if (l[2] != null) tip(x + 10f, ly, w - 20f, 18f, (String) l[0], (String) l[2]);
                ly += 18f;
            }
        }

        /** Under the partner's card: a baby on the way, or the odds of a child and what another one needs. */
        private List<Object[]> childLines(Property p, Tenant t) {
            List<Object[]> out = new ArrayList<>();
            if (t.conceivedAt != 0L && t.toldPregnant) {
                int days = Math.max(1, Math.round(Family.BIRTH_DAYS - Family.daysSince(t.conceivedAt)));
                out.add(new Object[] {"A baby is on the way: " + days + (days == 1 ? " day left." : " days left."),
                        Misc.getPositiveHighlightColor(), null});
                return out;
            }
            float chance = Family.childChance(p);
            out.add(new Object[] {"Chance of a child each time you try: " + Math.round(chance * 100f) + "%.",
                    Misc.getGrayColor(), chance <= Family.CHILD_CHANCE ? "A Romantic bedroom makes a child likelier."
                            : null});
            float[] need = Family.childNeeds(p.children().size());
            if (need != null) {
                PropertyStats st = p.stats();
                boolean ok = st.happiness >= need[0] && st.comfort >= need[1];
                String s = "Another child needs happiness " + Math.round(need[0]) + " and comfort " + Math.round(need[1])
                        + ".";
                if (!ok) s += " Now: " + Math.round(st.happiness) + " and " + Math.round(st.comfort) + ".";
                out.add(new Object[] {s, ok ? Misc.getGrayColor() : Misc.getNegativeHighlightColor(), null});
            }
            return out;
        }

        /** An officer or administrator you could propose to, with what they need to say yes. */
        private void candidateRow(Property p, PersonAPI who, float x, float y, float w) {
            box(x, y, w, 50f);
            portrait(who.getPortraitSprite(), x + 3f, y + 3f, 44f);
            String no = Family.cannotPropose(p, who);
            float bx = x + w - 8f - 110f;
            text(who.getNameString(), Misc.getTextColor(), x + 56f, y + 7f, bx - x - 68f);
            String away = landlordsector.Partners.awayLine(who);
            text(Family.role(who) + (away != null ? ". " + away : ""), Misc.getGrayColor(), x + 56f, y + 26f,
                    bx - x - 68f);
            button("propose:" + p.id + ":" + who.getId(), "Propose", bx, y + 13f, 110f, 24f, no == null);
            if (no != null) {
                label(no, Misc.getNegativeHighlightColor(), x + 56f, y + 7f, bx - x - 68f, 18f, Alignment.RMID,
                        Fonts.DEFAULT_SMALL);
            }
            tip(bx, y + 13f, 110f, 24f, "Propose", no == null || landlordsector.Partners.ASK_REASON.equals(no)
                    ? landlordsector.Partners.proposeTip(who) : no);
        }

        private static final float KID_H = 78f;

        /** A kid: portrait, name, stage and wish, what was paid for, happiness and health, traits, the surname. */
        private void kidRow(Property p, Child c, float x, float y, float w) {
            box(x, y, w, KID_H);
            portrait(c.portrait, x + 4f, y + 4f, 70f);
            float rx = x + w - 232f;
            float tx = x + 82f, tw = rx - 8f - tx;
            text(c.name, Misc.getHighlightColor(), tx, y + 6f, tw);
            text(kidLine(c), Misc.getGrayColor(), tx, y + 28f, tw);
            text(training(c), Misc.getGrayColor(), tx, y + 50f, tw);
            float h = c.happiness;
            text("Happiness " + Math.round(h), traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)),
                    rx, y + 6f, 110f);
            tipColored(rx, y + 6f, 110f, 20f, "Happiness: " + Math.round(h), Family.kidHappinessLines(p, c));
            if (!c.grown) {
                PropertyStats ks = Kids.healthOf(p, c);
                float kh = ks.health;
                text("Health " + Math.round(kh), traitColor(kh >= landlordsector.data.Levels.sickBelow() ? Boolean.TRUE
                        : (kh < landlordsector.data.Levels.treatmentBelow() ? Boolean.FALSE : null)), rx + 116f, y + 6f,
                        110f);
                List<String[]> lines = new ArrayList<>(ks.healthLines);
                if (!lines.isEmpty()) lines.remove(lines.size() - 1);
                lines.add(new String[] {"Sick kids fall ill more often. A childhood in poor health (below "
                        + Math.round(Kids.SICKLY_CHILDHOOD) + " on average) leaves a mark for life, and how well a kid is"
                        + " raised (happiness and health) decides the risk of losing them.", null});
                tipColored(rx + 116f, y + 6f, 110f, 20f, "Health: " + Math.round(kh), lines);
            }
            traitSlots(kidTraits(c), rx, y + 27f, 22f);
            if (Family.offerSurname(c)) {
                String now = c.name, then = Family.yourNameText(c);
                String caption = Family.playerSurname().isEmpty() ? "Use your name" : "Use your surname";
                button("surname:" + p.id + ":" + c.id, caption, rx, y + 52f, 150f, 22f, true);
                tip(rx, y + 52f, 150f, 22f, caption, "Changes " + now + " to " + then + ".");
            }
        }

        /** "Toddler, 2 cycles old. No traits until school age.", "Teen, 13 cycles old. Wants to be an officer." */
        private String kidLine(Child c) {
            if (c.grown) {
                String what = Family.pathName(c.path);
                return what != null ? "Grown up. Will become " + what + "." : "Grown up.";
            }
            String s = Kids.stageName(c) + ", " + Family.age(c).toLowerCase();
            if (c.traits().isEmpty() && Kids.stageOf(c) < Kids.CHILD) return s + ". No traits until school age.";
            String what = Family.pathName(c.path);
            if (what != null) return s + (c.pathLocked ? ". Will become " : ". Wants to be ") + what + ".";
            return s + ".";
        }

        /** "Costs 1,250 a month. Lessons 1 of 3, 5 days together, the Academy, extra classes." */
        private String training(Child c) {
            List<String> parts = new ArrayList<>();
            int s = Kids.stageOf(c);
            if (s >= Kids.TEEN) {
                parts.add("lessons " + c.lessons + " of " + Kids.MAX_LESSONS);
                parts.add(c.bonds + (c.bonds == 1 ? " day" : " days") + " together");
            }
            if (c.academy) parts.add("the Academy");
            if (c.classes) parts.add("extra classes");
            float cost = Kids.upkeep(c);
            String head = cost > 0f ? "Costs " + Misc.getDGSCredits(cost) + " a month." : "Costs you nothing.";
            if (parts.isEmpty()) return head;
            String list = String.join(", ", parts);
            return head + " " + Character.toUpperCase(list.charAt(0)) + list.substring(1) + ".";
        }

        private List<Object[]> kidTraits(Child c) {
            List<Object[]> out = new ArrayList<>();
            for (String id : c.traits()) {
                landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(id);
                if (d != null) out.add(new Object[] {d.name, d.good, d.name, landlordsector.ui.Tips.kidTrait(d),
                        traitIcon(d.id)});
            }
            return out;
        }

        /** Up to 3 traits as icons; with more, the first 2 and a "+N" box whose tooltip lists the rest. */
        @SuppressWarnings("unchecked")
        private void traitSlots(List<Object[]> items, float x, float y, float size) {
            if (items.size() <= 3) {
                traitRow(items, x, y, size, 3 * size + 2 * 4f + 2f);
                return;
            }
            traitRow(items.subList(0, 2), x, y, size, 2 * size + 4f + 2f);
            int more = items.size() - 2;
            float bx = x + 2 * (size + 4f);
            box(bx, y, size, size);
            label("+" + more, Misc.getHighlightColor(), bx, y + 3f, size, 18f, Alignment.MID, Fonts.DEFAULT_SMALL);
            List<String[]> lines = new ArrayList<>();
            for (int i = 2; i < items.size(); i++) {
                Object[] it = items.get(i);
                if (i > 2) lines.add(new String[] {"", null});
                Boolean good = (Boolean) it[1];
                lines.add(new String[] {String.valueOf(it[0]), good == null ? "g" : (good ? "+" : "-")});
                if (it.length > 3 && it[3] != null) lines.addAll((List<String[]>) it[3]);
            }
            tipColored(bx, y, size, size + 4f, more + (more == 1 ? " more trait" : " more traits"), lines);
        }

        /** Move out of the chosen home, kept under the scrolling column. */
        private void moveOut(Property p) {
            if (!p.playerHome) return;
            float y = height - 32f;
            String no = Family.cannotLeave(p);
            button("moveout:" + p.id, "Move out", midX, y, 140f, 24f, no == null);
            if (no != null) {
                text(no, Misc.getNegativeHighlightColor(), midX + 150f, y + 3f, midW - 150f);
                tip(midX, y, 140f, 24f, "Move out", no);
            }
        }

        // ---- Actions ----

        @Override
        protected void onAction(Object action) {
            String[] parts = String.valueOf(action).split(":");
            Property p = parts.length > 1 ? find(parts[1]) : null;
            switch (parts[0]) {
                case "home":
                    selectedHome = parts[1];
                    skip = 0;
                    message = "";
                    break;
                case "moveout":
                    if (p != null) result(Family.stopLiving(p), p.playerHome);
                    break;
                case "propose": {
                    PersonAPI who = null;
                    for (PersonAPI c : Family.candidates()) if (parts.length > 2 && c.getId().equals(parts[2])) who = c;
                    if (p != null && who != null) {
                        String r = Family.propose(p, who, new java.util.Random());
                        result(r, p.partner() == null);
                    }
                    break;
                }
                case "surname": {
                    Child c = null;
                    if (p != null && parts.length > 2) for (Child k : p.children()) if (k.id.equals(parts[2])) c = k;
                    if (c != null) result(Family.useYourSurname(p, c), false);
                    break;
                }
                default:
                    break;
            }
            rebuild();
        }

        private void result(String text, boolean failed) {
            message = text == null ? "" : text;
            messageColor = failed ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        }
    }
}
