package logisticspipes.proxy;

import net.minecraftforge.fml.ModList;

import static logisticspipes.LPConstants.appliedenergisticsModID;

import logisticspipes.proxy.specialtankhandler.AETankHandler;

public class SpecialTankHandlerManager {

	public static void load() {
		// BuildCraft tank handler removed: no 1.20.1 port exists.
		if (ModList.get().isLoaded(appliedenergisticsModID)) {
			SimpleServiceLocator.specialTankHandler.registerHandler(new AETankHandler());
		}
	}
}
