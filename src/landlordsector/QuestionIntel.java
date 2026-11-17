package landlordsector;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.data.TenantEvents;

/**
 * 1.12.0 (LLS-32): a question from one of your homes in the intel screen, with a button per answer. Saved with the
 * intel: keep the field names.
 */
public class QuestionIntel extends BaseIntelPlugin {

    public static final String TAG = "Real estate";

    public String homeId;
    public String homeName;
    public String eventId;
    public String text;
    public String said;
    public String who;
    public boolean answered;
    public boolean dropped;
    public String result;

    public QuestionIntel(Property p, Decision q) {
        homeId = p.id;
        homeName = p.displayName();
        eventId = q.eventId;
        text = q.text;
        said = q.said;
        who = q.who;
        setImportant(true);
    }

    private Property home() {
        for (Property p : Portfolio.get().properties()) if (p.id.equals(homeId)) return p;
        return null;
    }

    /** The question still waits at the home and is this one. */
    private Property open() {
        Property p = home();
        if (p == null || p.decision == null || p.decision.intel != this) return null;
        return p;
    }

    public void answered(String what) {
        answered = true;
        result = what;
        setImportant(false);
        endAfterDelay(7f);
    }

    public void dropped() {
        dropped = true;
        setImportant(false);
        endAfterDelay(1f);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (!answered && !dropped && !isEnding() && open() == null) dropped();
    }

    @Override
    protected String getName() {
        if (answered) return homeName + ": answered";
        if (dropped) return homeName + ": no longer needs an answer";
        return homeName + ": your answer is needed";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color title = answered || dropped ? Misc.getTextColor() : Misc.getBasePlayerColor();
        info.addPara(getName(), title, 0f);
        info.addPara(text, Misc.getGrayColor(), 3f);
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(text, 0f);
        if (said != null && who != null) {
            info.addPara(who + ": \"" + said + "\"", 10f, Misc.getGrayColor(), Misc.getHighlightColor(), who);
        }
        if (answered) {
            info.addPara(result == null ? "" : result, 10f);
            return;
        }
        Property p = open();
        TenantEvents.Def d = TenantEvents.get(eventId);
        if (p == null || d == null) {
            info.addPara("This no longer needs an answer.", Misc.getGrayColor(), 10f);
            return;
        }
        float pad = 10f;
        for (int i = 0; i < d.choices.size(); i++) {
            TenantEvents.Def a = d.choices.get(i);
            ButtonAPI b = info.addButton(Choices.label(p, a), Integer.valueOf(i), Math.min(width - 10f, 420f), 25f, pad);
            pad = 6f;
            String no = Choices.cannot(p, a);
            if (no != null) {
                b.setEnabled(false);
                info.addPara(no, Misc.getNegativeHighlightColor(), 2f);
            }
        }
        float left = d.answerDays - Global.getSector().getClock().getElapsedDaysSince(p.decision.askedAt);
        int days = Math.max(1, (int) Math.ceil(left));
        info.addPara("If you don't answer in " + days + (days == 1 ? " day" : " days") + ": "
                + Choices.label(p, d.choices.get(d.defaultChoice)) + ".", 10f, Misc.getGrayColor(),
                Misc.getHighlightColor(), String.valueOf(days));
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId instanceof Integer) {
            Property p = open();
            if (p != null) {
                String what = Choices.answer(p, (Integer) buttonId, Choices.PLAYER, new java.util.Random());
                if (!answered) result = what;
            }
            ui.updateUIForItem(this);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId instanceof Integer) return false;
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "monthly_income_report");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);
        return tags;
    }
}
