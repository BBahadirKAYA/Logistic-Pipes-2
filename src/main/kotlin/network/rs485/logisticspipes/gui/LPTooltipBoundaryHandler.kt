/*
 * Copyright (c) 2026  RS485
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0.1, or MMPL. Please check the contents of the license located at
 * https://github.com/RS485/LogisticsPipes/blob/dev/LICENSE.md
 */
package network.rs485.logisticspipes.gui

import logisticspipes.gui.orderer.GuiOrderer
import logisticspipes.gui.orderer.GuiRequestTable
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderTooltipEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent

/** Keeps item tooltips clear of the right edge in LP request screens. */
object LPTooltipBoundaryHandler {

    private const val TOOLTIP_CURSOR_OFFSET = 12
    private const val RIGHT_EDGE_SAFETY_MARGIN = 4

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderTooltip(event: RenderTooltipEvent.Pre) {
        val screen = Minecraft.getInstance().screen
        if (screen !is GuiOrderer && screen !is GuiRequestTable) return

        // Give Forge an anchor that leaves enough room for the actual widest rendered
        // component. A fixed offset is insufficient for long names and mod-added lines.
        val tooltipWidth = event.components.maxOfOrNull { it.getWidth(event.font) } ?: return
        val rightmostSafeAnchor = event.screenWidth - tooltipWidth - TOOLTIP_CURSOR_OFFSET - RIGHT_EDGE_SAFETY_MARGIN
        event.x = event.x.coerceAtMost(rightmostSafeAnchor).coerceAtLeast(0)
    }
}
