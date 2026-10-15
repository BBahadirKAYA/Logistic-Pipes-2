package logisticspipes.modplugins.jei;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import logisticspipes.gui.GuiLogisticsCraftingTable;
import logisticspipes.gui.orderer.GuiRequestTable;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.NEISetCraftingRecipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

/** Restores LP's JEI recipe-transfer button for its crafting and request tables. */
public class RecipeTransferHandler implements IRecipeTransferHandler<DummyContainer, CraftingRecipe> {

	private final IRecipeTransferHandlerHelper helper;

	public RecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
		this.helper = helper;
	}

	@Override
	public Class<? extends DummyContainer> getContainerClass() {
		return DummyContainer.class;
	}

	@Override
	public Optional<MenuType<DummyContainer>> getMenuType() {
		return Optional.empty();
	}

	@Override
	public RecipeType<CraftingRecipe> getRecipeType() {
		return RecipeTypes.CRAFTING;
	}

	@Override
	@Nullable
	public IRecipeTransferError transferRecipe(@Nonnull DummyContainer container, @Nonnull CraftingRecipe recipe,
			@Nonnull IRecipeSlotsView recipeSlots, @Nonnull Player player, boolean maxTransfer, boolean doTransfer) {
		LogisticsBaseGuiScreen gui = container.guiHolderForJEI;
		BlockEntity tile;
		if (gui instanceof GuiLogisticsCraftingTable craftingTable) {
			tile = craftingTable._crafter;
		} else if (gui instanceof GuiRequestTable requestTable) {
			tile = requestTable._table.container;
		} else {
			return helper.createInternalError();
		}

		if (tile == null) return helper.createInternalError();
		if (!doTransfer) return null;

		NonNullList<ItemStack> displayed = NonNullList.withSize(9, ItemStack.EMPTY);
		ItemStack[][] candidates = new ItemStack[9][];
		List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
		boolean hasCandidates = false;
		for (int slot = 0; slot < Math.min(9, inputs.size()); slot++) {
			IRecipeSlotView input = inputs.get(slot);
			List<ItemStack> choices = input.getItemStacks()
					.filter(stack -> !stack.isEmpty())
					.map(ItemStack::copy)
					.toList();
			if (choices.isEmpty()) continue;
			displayed.set(slot, input.getDisplayedItemStack().orElse(choices.get(0)).copy());
			candidates[slot] = choices.toArray(new ItemStack[0]);
			hasCandidates |= choices.size() > 1;
		}

		if (hasCandidates) {
			gui.setSubGui(new GuiRecipeImport(tile, candidates));
		} else {
			NEISetCraftingRecipe packet = PacketHandler.getPacket(NEISetCraftingRecipe.class);
			for (int slot = 0; slot < displayed.size(); slot++) {
				packet.getStackList().set(slot, displayed.get(slot));
			}
			MainProxy.sendPacketToServer(packet.setTilePos(tile));
		}
		return null;
	}
}
