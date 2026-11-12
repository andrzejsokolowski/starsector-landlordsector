package landlordsector;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Messages: one line in the fleet log, and for big news a message in the intel screen too. The event part of
 * a line is colored (red for bad, green for good) and the home's name stays plain, as Andrzej asked:
 * "(red) Burglars broke into (red end) Verdant Cottage on Jangala".
 */
public final class Notify {

    private Notify() {
    }

    public static Color color(Boolean good) {
        if (good == null) return Misc.getTextColor();
        return good ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
    }

    /**
     * One line in the fleet log.
     *
     * @param colored the start of the text drawn in the good or bad color, or null for the whole line
     */
    public static void line(String text, Boolean good, String colored) {
        try {
            if (colored == null || colored.isEmpty() || !text.startsWith(colored) || good == null) {
                Global.getSector().getCampaignUI().addMessage(text, color(good));
            } else {
                Global.getSector().getCampaignUI().addMessage(text, Misc.getTextColor(), colored, null, color(good),
                        null);
            }
        } catch (Throwable ignored) {
            // No campaign UI (title screen, tests): the home's log still has it.
        }
    }

    /** The part of an event text before the home's name: the part that is colored. */
    public static String coloredPart(String text, Property p) {
        String home = p.displayName();
        int at = text.indexOf(home);
        if (at <= 0) return text;
        return text.substring(0, at);
    }

    /** A home's event: its log, the fleet log, and the intel screen when big. */
    public static void event(Property p, String text, boolean good, boolean big) {
        String colored = coloredPart(text, p);
        p.addLog(text, good, colored);
        line(text, good, colored);
        if (!big) return;
        Portfolio.get().news = true;
        try {
            MessageIntel msg = new MessageIntel(text, Misc.getTextColor(), new String[] {colored}, color(good));
            msg.setIcon(Global.getSettings().getSpriteName("intel", "monthly_income_report"));
            Global.getSector().getCampaignUI().addMessage(msg);
        } catch (Throwable ignored) {
        }
    }
}
