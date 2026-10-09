package logisticspipes.proxy.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// defaults are the behavior without EnderStorage installed
public interface IEnderStorageProxy {

	default boolean isEnderChestBlock(Block block) {
		return false;
	}

	default void openEnderChest(Level level, BlockPos pos, Player player) {}
}
