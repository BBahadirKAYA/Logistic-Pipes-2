package logisticspipes.network.guis.pipe;

import net.minecraft.world.entity.player.Player;

import logisticspipes.gui.orderer.GuiRequestTable;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.DummyContainer;

@StaticResolve
public class RequestTableGui extends CoordinatesGuiProvider {

	public RequestTableGui(int id) {
		super(id);
	}

	@Override
	public Object getClientGui(Player player) {
		LogisticsTileGenericPipe tile = getTileAs(player.level(), LogisticsTileGenericPipe.class);
		if (tile == null || !(tile.pipe instanceof PipeBlockRequestTable)) return null;
		return new GuiRequestTable(player, (PipeBlockRequestTable) tile.pipe);
	}

	@Override
	public DummyContainer getContainer(Player player) {
		LogisticsTileGenericPipe tile = getTileAs(player.level(), LogisticsTileGenericPipe.class);
		if (tile == null || !(tile.pipe instanceof PipeBlockRequestTable)) return null;
		PipeBlockRequestTable table = (PipeBlockRequestTable) tile.pipe;
		DummyContainer dummy = new DummyContainer(player, table.matrix, table);
		int i = 0;
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 9; x++) {
				dummy.addNormalSlot(i++, table.inv, x * 18 + 20, y * 18 + 80);
			}
		}
		i = 0;
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				dummy.addDummySlot(i++, x * 18 + 20, y * 18 + 15);
			}
		}
		dummy.addCallableSlotHandler(0, table.resultInv, 101, 33, table::getResultForClick);
		dummy.addNormalSlot(0, table.toSortInv, 164, 51);
		dummy.addNormalSlot(0, table.diskInv, 164, 25);
		dummy.addNormalSlotsForPlayerInventory(20, 150);
		return dummy;
	}

	@Override
	public GuiProvider template() {
		return new RequestTableGui(getId());
	}
}
