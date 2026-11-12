package landlordsector;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/** Messages: one line in the fleet log, and for big news a message in the intel screen too. */
public final class Notify {

    private Notify() {
    }

    /** One line in the fleet log. good: green, false: red, null: plain. */
    public static void line(String text, Boolean good) {
        try {
            java.awt.Color c = good == null ? Misc.getTextColor()
                    : (good ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());
            Global.getSector().getCampaignUI().addMessage(text, c);
        } catch (Throwable ignored) {
            // No campaign UI (title screen, tests): the home's log still has it.
        }
    }

    /** A home's event: its log, the fleet log, and the intel screen when big. */
    public static void event(Property p, String text, boolean good, boolean big) {
        p.addLog(text);
        line(text, good);
        if (!big) return;
        Portfolio.get().news = true;
        try {
            MessageIntel msg = new MessageIntel(text, good ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor());
            msg.setIcon(Global.getSettings().getSpriteName("intel", "monthly_income_report"));
            Global.getSector().getCampaignUI().addMessage(msg);
        } catch (Throwable ignored) {
        }
    }
}
