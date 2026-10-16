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
import kotlin.math.abs

/** Keeps item tooltips clear of the right edge in LP request screens. */
object LPTooltipBoundaryHandler {

    private const val CURSOR_OFFSET = 12
    private const val RIGHT_EDGE_SAFETY_MARGIN = 4

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderTooltip(event: RenderTooltipEvent.Pre) {
        val screen = Minecraft.getInstance().screen
        if (screen !is GuiOrderer && screen !is GuiRequestTable) return

        val tooltipWidth = event.components.maxOfOrNull { it.getWidth(event.font) } ?: return
        val tooltipHeight = event.components.sumOf { it.height } + if (event.components.size > 1) 2 else 0
        val mouseX = event.x
        val targetX = (mouseX + CURSOR_OFFSET)
            .coerceAtMost(event.screenWidth - tooltipWidth - RIGHT_EDGE_SAFETY_MARGIN)
            .coerceAtLeast(RIGHT_EDGE_SAFETY_MARGIN)

        // Different tooltip producers (vanilla and JEI) use different positioners.
        // Ask the active positioner where every possible anchor would render, then
        // retain the anchor whose visible result is closest to the cursor's right.
        val bestAnchor = (0..event.screenWidth)
            .map { anchor ->
                val position = event.tooltipPositioner.positionTooltip(
                    event.screenWidth,
                    event.screenHeight,
                    anchor,
                    event.y,
                    tooltipWidth,
                    tooltipHeight,
                )
                Triple(anchor, position.x(), position.y())
            }
            .filter { (_, x, _) ->
                x >= RIGHT_EDGE_SAFETY_MARGIN &&
                    x + tooltipWidth <= event.screenWidth - RIGHT_EDGE_SAFETY_MARGIN
            }
            .minWithOrNull(
                compareBy<Triple<Int, Int, Int>> { (_, x, _) -> abs(x - targetX) }
                    .thenBy { (anchor, _, _) -> abs(anchor - mouseX) },
            )
            ?.first

        if (bestAnchor != null) event.x = bestAnchor
    }
}
