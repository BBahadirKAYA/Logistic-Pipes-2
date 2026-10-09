package logisticspipes.proxy.cc.wrapper;

import javax.annotation.Nonnull;

import net.minecraft.core.Direction;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;

import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class LPPeripheralTilePipeWrapper implements IDynamicPeripheral {

	private final Direction dir;
	private final CCCommandWrapper wrapped;
	private final LogisticsTileGenericPipe pipe;

	public LPPeripheralTilePipeWrapper(LogisticsTileGenericPipe pipe, CCCommandWrapper wrapped, Direction dir) {
		this.pipe = pipe;
		this.wrapped = wrapped;
		this.dir = dir;
	}

	@Nonnull
	@Override
	public MethodResult callMethod(@Nonnull IComputerAccess computer, @Nonnull ILuaContext context, int method, @Nonnull IArguments arguments) throws LuaException {
		pipe.currentPC = computer;
		try {
			return wrapped.callMethod(method, arguments, true);
		} finally {
			pipe.currentPC = null;
		}
	}

	@Override
	public void attach(@Nonnull IComputerAccess computer) {
		pipe.connections.put(computer, dir);
		pipe.scheduleNeighborChange();
	}

	@Override
	public void detach(@Nonnull IComputerAccess computer) {
		pipe.connections.remove(computer);
	}

	@Override
	public Object getTarget() {
		return pipe;
	}

	@Override
	public boolean equals(IPeripheral other) {
		return other instanceof LPPeripheralTilePipeWrapper && ((LPPeripheralTilePipeWrapper) other).pipe.equals(pipe);
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
