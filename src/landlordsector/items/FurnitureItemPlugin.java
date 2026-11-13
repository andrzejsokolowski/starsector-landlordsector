package landlordsector.items;

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.data.Upgrades;
import landlordsector.ui.Canvas;
import landlordsector.ui.Tips;

/** Cargo tooltip for a piece of furniture (0.4.0): what it does in a home and where to place it. */
public class FurnitureItemPlugin extends BaseSpecialItemPlugin {

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
    }
}
