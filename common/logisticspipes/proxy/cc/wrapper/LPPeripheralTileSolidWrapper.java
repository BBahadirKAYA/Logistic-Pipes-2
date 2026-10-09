package logisticspipes.proxy.cc.wrapper;

import javax.annotation.Nonnull;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;

import logisticspipes.blocks.LogisticsSolidTileEntity;

public class LPPeripheralTileSolidWrapper implements IDynamicPeripheral {

	private final CCCommandWrapper wrapped;
	private final LogisticsSolidTileEntity tile;

	public LPPeripheralTileSolidWrapper(LogisticsSolidTileEntity tile, CCCommandWrapper wrapped) {
		this.tile = tile;
		this.wrapped = wrapped;
	}

	@Nonnull
	@Override
	public MethodResult callMethod(@Nonnull IComputerAccess computer, @Nonnull ILuaContext context, int method, @Nonnull IArguments arguments) throws LuaException {
		return wrapped.callMethod(method, arguments, true);
	}

	@Override
	public Object getTarget() {
		return tile;
	}

	@Override
	public boolean equals(IPeripheral other) {
		return other instanceof LPPeripheralTileSolidWrapper && ((LPPeripheralTileSolidWrapper) other).tile.equals(tile);
	}

	@Nonnull
	@Override
	public String getType() {
		return wrapped.getType();
	}

	@Nonnull
	@Override
	public String[] getMethodNames() {
		return wrapped.getMethodNames();
	}
}
