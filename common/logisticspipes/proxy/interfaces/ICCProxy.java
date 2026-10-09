package logisticspipes.proxy.interfaces;

import net.minecraft.world.level.block.entity.BlockEntity;

import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

// defaults are the behavior without CC: Tweaked installed
public interface ICCProxy {

	default boolean isTurtle(BlockEntity tile) {
		return false;
	}

	default boolean isCC() {
		return false;
	}

	default boolean isLuaThread(Thread thread) {
		return false;
	}

	default void queueEvent(String event, Object[] arguments, LogisticsTileGenericPipe tile) {}

	default void setTurtleConnect(boolean flag, LogisticsTileGenericPipe tile) {}

	default boolean getTurtleConnect(LogisticsTileGenericPipe tile) {
		return false;
	}

	default int getLastCCID(LogisticsTileGenericPipe tile) {
		return 0;
	}

	default void handleMesssage(int computerId, Object message, LogisticsTileGenericPipe tile, int sourceId) {}
}
