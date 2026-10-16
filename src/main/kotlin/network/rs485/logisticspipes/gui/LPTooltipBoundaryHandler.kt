/*
 * Copyright (c) 2026  RS485
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0.1, or MMPL. Please check the contents of the license located at
 * https://github.com/RS485/LogisticsPipes/blob/dev/LICENSE.md
 */
package network.rs485.logisticspipes.gui

import logisticspipes.gui.orderer.GuiRequestTable
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderTooltipEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent

/** Keeps item tooltips clear of the right edge in the wide Request Table screen. */
object LPTooltipBoundaryHandler {

    private const val RIGHT_EDGE_SAFETY_MARGIN = 32

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderTooltip(event: RenderTooltipEvent.Pre) {
        if (Minecraft.getInstance().screen !is GuiRequestTable) return

        // Forge's positioner flips a tooltip at the edge using the measured text width.
        // Some font/render-scale combinations draw slightly wider than that measurement.
        // This also affects non-LP items shown in the Request Table's JEI panel, so apply
        // the safety margin to every item tooltip rendered on this screen.
        if (event.x > event.screenWidth / 2) {
            event.x = (event.x - RIGHT_EDGE_SAFETY_MARGIN).coerceAtLeast(0)
        }
    }
}
