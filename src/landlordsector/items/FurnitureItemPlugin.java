package landlordsector.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Actions;
import landlordsector.data.Upgrades;
import landlordsector.ui.Canvas;
import landlordsector.ui.Tips;

/**
 * A piece of furniture as an item (0.4.0). 0.5.0 (Andrzej: furniture should be "consumed" on right click and added to
 * our own screen, like Captain's Quarters): right-clicking it in the cargo moves it into the furniture storage.
 */
public class FurnitureItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public boolean hasRightClickAction() {
        return Upgrades.byItem(getId()) != null;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return Upgrades.byItem(getId()) != null && Global.getSector() != null;
    }

    @Override
    public void performRightClickAction() {
        Upgrades.Def d = Upgrades.byItem(getId());
        if (d == null || Global.getSector() == null) return;
        String msg = Actions.store(d.id);
        try {
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(msg);
        } catch (Throwable ignored) {
            // No message display (the title screen): the piece is stored all the same.
        }
        Global.getSoundPlayer().playUISound("ui_cargo_special_industrial_drop", 1f, 1f);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler,
            Object stackSource, boolean useGray) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);
        Upgrades.Def d = Upgrades.byItem(getId());
        if (d != null) {
            tooltip.addPara("In a home:", Misc.getHighlightColor(), 10f);
            for (String[] line : Tips.upgrade(d)) {
                tooltip.addPara("   " + line[0], Canvas.colorOf(line[1]), 2f);
            }
        }
        addCostLabel(tooltip, 10f, transferHandler, stackSource);
        if (d != null) {
            tooltip.addPara("Right-click to put it in your furniture storage.", Misc.getPositiveHighlightColor(), 10f);
        }
    }
}
