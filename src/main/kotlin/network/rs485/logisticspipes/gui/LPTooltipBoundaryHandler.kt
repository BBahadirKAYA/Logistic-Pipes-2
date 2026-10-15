/*
 * Copyright (c) 2026  RS485
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0.1, or MMPL. Please check the contents of the license located at
 * https://github.com/RS485/LogisticsPipes/blob/dev/LICENSE.md
 */
package network.rs485.logisticspipes.gui

import logisticspipes.LPConstants
import logisticspipes.gui.orderer.GuiRequestTable
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraftforge.client.event.RenderTooltipEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent

/** Keeps LP item tooltips clear of the right edge in the wide Request Table screen. */
object LPTooltipBoundaryHandler {

    private const val RIGHT_EDGE_SAFETY_MARGIN = 32

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderTooltip(event: RenderTooltipEvent.Pre) {
        if (Minecraft.getInstance().screen !is GuiRequestTable) return

        val itemId = BuiltInRegistries.ITEM.getKey(event.itemStack.item)
        if (itemId.namespace != LPConstants.LP_MOD_ID) return

        // Forge's positioner flips a tooltip at the edge using the measured text width.
        // Some font/render-scale combinations draw slightly wider than that measurement,
        // leaving long LP names clipped. Moving the anchor left preserves Forge's normal
        // placement while providing enough room for the rendered glyphs and border.
        if (event.x > event.screenWidth / 2) {
            event.x = (event.x - RIGHT_EDGE_SAFETY_MARGIN).coerceAtLeast(0)
        }
    }
}
