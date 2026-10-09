package logisticspipes.proxy;

import net.minecraftforge.fml.ModList;

import logisticspipes.LPConstants;
import logisticspipes.proxy.cc.CCProxy;
import logisticspipes.proxy.ccl.CCLProxy;
import logisticspipes.proxy.enderchest.EnderStorageProxy;

public class ProxyManager {

	public static void load() {
		// TODO(1.20.1): CCL is now LP's own built-in impl — bypass legacy ASM wrapper so
		// callers receive real LPRenderStateImpl/LPModel3DImpl instances instead of proxy wrappers.
		SimpleServiceLocator.setCCLProxy(new CCLProxy());

		SimpleServiceLocator.setConfigToolHandler(new ConfigToolHandler());
		SimpleServiceLocator.configToolHandler.registerWrapper();

		SimpleServiceLocator.setPowerProxy(new PowerProxy());

		if (ModList.get().isLoaded(LPConstants.computerCraftModID)) {
			SimpleServiceLocator.setCCProxy(new CCProxy());
		}
		if (ModList.get().isLoaded(LPConstants.enderStorageModID)) {
			SimpleServiceLocator.setEnderStorageProxy(new EnderStorageProxy());
		}
	}
}
