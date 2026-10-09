package logisticspipes.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

import org.lwjgl.glfw.GLFW;

import logisticspipes.LPConstants;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.pipefxhandlers.EntitySparkleFX;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.QuickSortChestMarkerStorage;
import logisticspipes.utils.gui.InputBar;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SimpleGraphics;
import network.rs485.logisticspipes.module.AsyncExtractorModule;

/**
 * Client checks for fixes that need a client (runClientCheck). Results are "LPCHECK PASS|FAIL" log lines;
 * the HUD check also saves run/screenshots/lpcheck_hud.png for a visual look. Quits when done.
 * Registered by LogisticsPipes when lp.clientcheck is set.
 */
public final class LPClientChecks {

	private static final boolean ENABLED = Boolean.getBoolean("lp.clientcheck");
	private static final int SETTLE_TICKS = 100;

	private static int tick = -1;
	private static int failures = 0;
	private static int particleBaseline;
	private static boolean drawHud = false;
	private static final List<IHUDModuleRenderer> hudRenderers = new ArrayList<>();

	private LPClientChecks() {}

	private static void result(String name, boolean ok, String detail) {
		if (!ok) failures++;
		logisticspipes.LogisticsPipes.log.info("LPCHECK {} {}: {}", ok ? "PASS" : "FAIL", name, detail);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		tick++;
		try {
			switch (tick) {
				case SETTLE_TICKS - 20 -> facePipe(mc);
				case SETTLE_TICKS -> {
					checkQuickSort(mc);
					checkInputBar(mc);
					checkSolidSides(mc);
					checkOrdererColours(mc);
					checkFluidContainerModel(mc);
					checkModuleGuis(mc);
					checkSlotFinder(mc);
					startParticleCheck(mc);
				}
				case SETTLE_TICKS + 3 -> finishParticleCheck(mc);
				case SETTLE_TICKS + 4 -> startHud();
				case SETTLE_TICKS + 8 -> Screenshot.grab(mc.gameDirectory, "lpcheck_hud.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO hud screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 9 -> {
					drawHud = false;
					openSideConfig(mc);
				}
				case SETTLE_TICKS + 11 -> Screenshot.grab(mc.gameDirectory, "lpcheck_sideconfig.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO side config screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 12 -> openCraftingExtensions(mc);
				case SETTLE_TICKS + 14 -> Screenshot.grab(mc.gameDirectory, "lpcheck_crafting.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO crafting screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 15 -> {
					mc.setScreen(null);
					startTheOneProbe(mc);
				}
				// TOP polls the server for probe info; give it time to answer and draw
				case SETTLE_TICKS + 55 -> {
					if (TOP_LOADED) Screenshot.grab(mc.gameDirectory, "lpcheck_top.png", mc.getMainRenderTarget(),
							msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO top screenshot: {}", msg.getString()));
				}
				case SETTLE_TICKS + 56 -> {
					mc.options.keyShift.setDown(false);
					startTubes(mc);
				}
				// let the client receive and mesh the tube chunks before the shot
				case SETTLE_TICKS + 86 -> Screenshot.grab(mc.gameDirectory, "lpcheck_tubes.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO tubes screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 87 -> startGlasses(mc);
				case SETTLE_TICKS + 117 -> {
					checkGlasses();
					Screenshot.grab(mc.gameDirectory, "lpcheck_glasses.png", mc.getMainRenderTarget(),
							msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO glasses screenshot: {}", msg.getString()));
					mc.options.keyShift.setDown(true);
				}
				case SETTLE_TICKS + 122 -> Screenshot.grab(mc.gameDirectory, "lpcheck_glasses_focus.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO glasses focus screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 123 -> {
					mc.options.keyShift.setDown(false);
					startSign(mc);
				}
				// the sign packet and teleport need a few server ticks
				case SETTLE_TICKS + 143 -> checkSign(mc);
				case SETTLE_TICKS + 144 -> Screenshot.grab(mc.gameDirectory, "lpcheck_sign.png", mc.getMainRenderTarget(),
						msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO sign screenshot: {}", msg.getString()));
				case SETTLE_TICKS + 145 -> finishTubes(mc);
				case SETTLE_TICKS + 146 -> openMonitor(mc);
				// two rendered frames so the popup has drawn before the shot
				case SETTLE_TICKS + 149 -> checkMonitor(mc);
				case SETTLE_TICKS + 151 -> openItemPicker(mc);
				case SETTLE_TICKS + 153 -> checkItemPicker(mc);
				// a frame with the typed text before reading pixels
				case SETTLE_TICKS + 155 -> checkItemPickerDrawn(mc);
				case SETTLE_TICKS + 156 -> openDiskPopup(mc);
				case SETTLE_TICKS + 158 -> checkDiskPopup(mc);
				case SETTLE_TICKS + 160 -> {
					Screenshot.grab(mc.gameDirectory, "lpcheck_disk.png", mc.getMainRenderTarget(),
							msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO disk screenshot: {}", msg.getString()));
					mc.setScreen(null);
				}
				case SETTLE_TICKS + 162 -> {
					logisticspipes.LogisticsPipes.log.info("LPCHECK DONE failures={}", failures);
					mc.stop();
				}
				default -> {}
			}
		} catch (Throwable t) {
			result("driver", false, t.toString());
			logisticspipes.LogisticsPipes.log.error("LPCHECK driver crashed", t);
			mc.stop();
		}
	}

	/** Worklog item 3: markers stay on for chest screens (also across a swap), clear on close or other screens. */
	private static void checkQuickSort(Minecraft mc) {
		QuickSortChestMarkerStorage qs = QuickSortChestMarkerStorage.getInstance();
		var inv = mc.player.getInventory();

		qs.enable();
		mc.setScreen(new InventoryScreen(mc.player));
		result("quicksort.inventoryScreenDisables", !qs.isActivated(), "activated=" + qs.isActivated());
		mc.setScreen(null);

		qs.enable();
		mc.setScreen(new ContainerScreen(ChestMenu.threeRows(990, inv), inv, Component.literal("a")));
		boolean afterOpen = qs.isActivated();
		mc.setScreen(new ContainerScreen(ChestMenu.threeRows(991, inv), inv, Component.literal("b")));
		boolean afterSwap = qs.isActivated();
		mc.setScreen(null);
		boolean afterClose = qs.isActivated();
		result("quicksort.chestKeepsMarkers", afterOpen && afterSwap, "open=" + afterOpen + " swap=" + afterSwap);
		result("quicksort.closeClears", !afterClose, "activated after close=" + afterClose);
	}

	/** Each orderer colour (CustomModelData 0..16) resolves to its own texture via the model overrides. */
	private static void checkOrdererColours(Minecraft mc) {
		StringBuilder bad = new StringBuilder();
		for (int v = 0; v <= 16; v++) {
			net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(logisticspipes.LPItems.remoteOrderer.get());
			if (v > 0) stack.getOrCreateTag().putInt(logisticspipes.recipes.OrdererDyeRecipe.COLOR_TAG, v);
			String tex = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0).getParticleIcon().contents().name().getPath();
			if (!tex.equals("items/remote_orderer/" + v)) bad.append(v).append("->").append(tex).append(' ');
		}
		result("orderer.colourModels", bad.length() == 0, bad.length() == 0 ? "17 variants" : bad.toString());
	}

	/** Filled fluid container bakes the fluid's still sprite through the stencil, like LP1. */
	private static void checkFluidContainerModel(Minecraft mc) {
		var water = logisticspipes.utils.FluidIdentifierStack.getFromStack(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
		net.minecraft.world.item.ItemStack filled = logisticspipes.proxy.SimpleServiceLocator.logisticsFluidManager.getFluidContainer(water).makeNormalStack();
		net.minecraft.world.item.ItemStack empty = new net.minecraft.world.item.ItemStack(logisticspipes.LPItems.fluidContainer.get());
		String filledTex = mc.getItemRenderer().getModel(filled, mc.level, mc.player, 0).getParticleIcon().contents().name().getPath();
		String emptyTex = mc.getItemRenderer().getModel(empty, mc.level, mc.player, 0).getParticleIcon().contents().name().getPath();
		result("fluidContainer.model", filledTex.equals("block/water_still") && emptyTex.equals("items/liquids/empty"),
				"filled=" + filledTex + " empty=" + emptyTex);
	}

	/** Kotlin module GUIs: shift-click fills a filter slot, hovering a fuzzy slot opens the flag popup, a click flips a flag. */
	private static void checkModuleGuis(Minecraft mc) throws ReflectiveOperationException {
		var inv = mc.player.getInventory();
		net.minecraft.world.item.ItemStack saved = inv.getItem(9);
		inv.setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
		var provider = network.rs485.logisticspipes.gui.module.ProviderGui.create(inv, new logisticspipes.modules.ModuleProvider(), net.minecraft.world.item.ItemStack.EMPTY);
		mc.setScreen(provider);
		var container = (network.rs485.logisticspipes.inventory.container.ProviderContainer) provider.getMenu();
		net.minecraft.world.inventory.Slot from = container.slots.stream().filter(s -> s.container == inv && s.getContainerSlot() == 9).findFirst().orElseThrow();
		var slotClicked = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredMethod("slotClicked",
				net.minecraft.world.inventory.Slot.class, int.class, int.class, net.minecraft.world.inventory.ClickType.class);
		slotClicked.setAccessible(true);
		slotClicked.invoke(provider, from, from.index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
		net.minecraft.world.item.ItemStack filter = container.getFilterSlots().get(0).getItem();
		mc.setScreen(null);
		inv.setItem(9, saved);
		result("moduleGui.shiftClickFilter", filter.is(net.minecraft.world.item.Items.DIAMOND), "filter0=" + filter);

		var sink = network.rs485.logisticspipes.gui.module.ItemSinkGui.create(inv, new logisticspipes.modules.ModuleItemSink(), net.minecraft.world.item.ItemStack.EMPTY, true, false);
		mc.setScreen(sink);
		var fuzzySlot = (network.rs485.logisticspipes.gui.widget.FuzzyItemSlot) sink.getMenu().slots.stream()
				.filter(s -> s instanceof network.rs485.logisticspipes.gui.widget.FuzzyItemSlot).findFirst().orElseThrow();
		GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
		sink.render(graphics, sink.getGuiLeft() + fuzzySlot.x + 8, sink.getGuiTop() + fuzzySlot.y + 8, 0F);
		graphics.flush();
		var selector = sink.getFuzzySelector();
		boolean opened = selector.getActive() && selector.getCurrentSlot() == fuzzySlot;
		var flag = fuzzySlot.getUsedFlags().iterator().next();
		var body = selector.getRelativeBody();
		boolean clicked = sink.mouseClicked(body.getRoundedLeft() + 7, body.getRoundedTop() + 10 + 10 * flag.ordinal(), 0);
		boolean flipped = network.rs485.logisticspipes.util.FuzzyUtil.INSTANCE.get(fuzzySlot.getFlagGetter().invoke(), flag);
		mc.setScreen(null);
		result("moduleGui.fuzzySelector", opened && clicked && flipped, "opened=" + opened + " clicked=" + clicked + " flipped " + flag + "=" + flipped);
	}

	/** Slot finder: a left click on a slot is consumed, picks it and closes; a click off-slot is consumed only. */
	private static void checkSlotFinder(Minecraft mc) {
		logisticspipes.renderer.GuiOverlay overlay = logisticspipes.renderer.GuiOverlay.getInstance();
		InventoryScreen screen = new InventoryScreen(mc.player);
		mc.setScreen(screen);
		overlay.setOverlaySlotActive(true);
		net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.get(9);
		double sx = screen.getGuiLeft() + slot.x + 8, sy = screen.getGuiTop() + slot.y + 8;

		boolean offSlot = click(screen, 1, 1, 0);
		boolean rightClick = click(screen, sx, sy, 1);
		boolean stillActive = overlay.isOverlaySlotActive();
		logisticspipes.LogisticsPipes.log.info("LPCHECK INFO slotFinder: next SlotFinderNumberPacket targets 0,0,0; its debug 'Packet handling error' is expected");
		boolean onSlot = click(screen, sx, sy, 0);
		boolean closed = mc.screen == null;
		boolean deactivated = !overlay.isOverlaySlotActive();
		overlay.setOverlaySlotActive(false);
		mc.setScreen(null);
		result("slotFinder.click", offSlot && !rightClick && stillActive && onSlot && closed && deactivated,
				"offSlot=" + offSlot + " right=" + rightClick + " active=" + stillActive + " onSlot=" + onSlot + " closed=" + closed + " off=" + deactivated);
	}

	private static boolean click(net.minecraft.client.gui.screens.Screen screen, double x, double y, int button) {
		return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
				new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen, x, y, button));
	}

	/** Worklog item 6: click inside focuses, editing keys work, click outside unfocuses. */
	private static void checkInputBar(Minecraft mc) {
		LogisticsBaseGuiScreen screen = new LogisticsBaseGuiScreen(176, 166, 0, 0) {
			@Override
			protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
		};
		InputBar bar = new InputBar(mc.font, screen, 10, 10, 100, 14);
		bar.handleClick(20, 15, 0);
		boolean focusedIn = bar.isFocused();
		bar.handleKey('a', 0);
		bar.handleKey('b', 0);
		bar.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
		String text = bar.getValue();
		bar.handleClick(400, 400, 0);
		boolean focusedOut = bar.isFocused();
		result("inputbar.clickFocuses", focusedIn, "focused after inside click=" + focusedIn);
		result("inputbar.editingKeys", "a".equals(text), "text after a,b,backspace='" + text + "'");
		result("inputbar.clickOutsideUnfocuses", !focusedOut, "focused after outside click=" + focusedOut);
	}

	private static final boolean TOP_LOADED = net.minecraftforge.fml.ModList.get().isLoaded(LPConstants.theOneProbeModID);

	/** TOP: LPText draws through the reflected RenderHelper, then the real overlay over a routed pipe for the screenshot. */
	private static void startTheOneProbe(Minecraft mc) throws ReflectiveOperationException {
		if (!TOP_LOADED) {
			logisticspipes.LogisticsPipes.log.info("LPCHECK INFO top: The One Probe not installed, skipped");
			return;
		}
		// the probe step waits 40 ticks; a pause menu from lost focus would cover the screenshot
		mc.options.pauseOnLostFocus = false;
		String problem = TheOneProbeClientCheck.drawElement(mc);
		result("top.drawElement", problem == null, problem == null ? "LPText drawn" : problem);
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			BlockPos pos = pipe.getBlockPos();
			if (pipe.pipe instanceof logisticspipes.pipes.basic.CoreRoutedPipe && mc.level.isEmptyBlock(pos.above()) && mc.level.isEmptyBlock(pos.above(2))) {
				TheOneProbeClientCheck.lookAtPipe(mc, pos);
				logisticspipes.LogisticsPipes.log.info("LPCHECK INFO top: looking down at {} {}", pipe.pipe.getClass().getSimpleName(), pos);
				return;
			}
		}
		result("top.overlay", false, "no routed pipe with two air blocks above");
	}

	/** Disconnection popup over a routed pipe; the screenshot should show the pipe model. */
	private static void openSideConfig(Minecraft mc) {
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			if (pipe.pipe instanceof logisticspipes.pipes.basic.CoreRoutedPipe routed) {
				mc.setScreen(new logisticspipes.gui.popup.DisconnectionConfigurationPopup(routed, null));
				logisticspipes.LogisticsPipes.log.info("LPCHECK INFO side config opened at {}", pipe.getBlockPos());
				return;
			}
		}
		result("sideConfig", false, "no routed pipe loaded");
	}

	/** Crafting GUI with fluid x3, byproduct and cleanup extensions; the screenshot should show their collapsed icons. */
	private static void openCraftingExtensions(Minecraft mc) {
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			if (pipe.pipe instanceof logisticspipes.pipes.PipeItemsCraftingLogistics crafting) {
				mc.setScreen(new logisticspipes.gui.GuiCraftingPipe(mc.player, crafting.getLogisticsModule(), false, 3, new int[3], true, false, 1, false));
				return;
			}
		}
		result("craftingGui", false, "no crafting pipe loaded");
	}

	private static BlockPos tubeBase;
	private static net.minecraft.world.phys.Vec3 tubeReturn;
	private static float tubeYaw, tubePitch;

	/** High-speed curve and gain tubes on a sky platform; the screenshot should show both bending the right way. */
	private static void startTubes(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null) {
			result("tubes.placed", false, "no integrated server");
			return;
		}
		mc.options.pauseOnLostFocus = false;
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
			if (sp == null) {
				result("tubes.placed", false, "no server player");
				return;
			}
			tubeReturn = sp.position();
			tubeYaw = sp.getYRot();
			tubePitch = sp.getXRot();
			tubeBase = sp.blockPosition().above(40);
			BlockPos b = tubeBase;
			tubeCommand(sp, "gamemode creative");
			tubeCommand(sp, "time set noon");
			tubeCommand(sp, String.format("fill %d %d %d %d %d %d stone", b.getX() - 10, b.getY() - 1, b.getZ() - 10, b.getX() + 10, b.getY() - 1, b.getZ() + 6));
			tubeCommand(sp, String.format("fill %d %d %d %d %d %d air", b.getX() - 10, b.getY(), b.getZ() - 10, b.getX() + 10, b.getY() + 6, b.getZ() + 6));
			sp.getAbilities().flying = true;
			sp.onUpdateAbilities();
			String curve = placePipe(sp, "pipe_hs_curve", b.offset(-4, 0, 0), "HSTube");
			String gain = placePipe(sp, "pipe_hs_gain", b.offset(4, 0, 0), "HSTube");
			result("tubes.placed", curve == null && gain == null, "curve=" + (curve == null ? "ok" : curve) + " gain=" + (gain == null ? "ok" : gain));
			sp.teleportTo(sp.serverLevel(), b.getX() + 0.5, b.getY() + 5, b.getZ() + 7.5, 180f, 35f);
		});
	}

	/** Uses the pipe item from {@code stand} on the floor three blocks north, like a player would. */
	private static String placePipe(net.minecraft.server.level.ServerPlayer sp, String id, BlockPos stand, String classPrefix) {
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse("logisticspipes:" + id));
		var stack = new net.minecraft.world.item.ItemStack(item);
		if (stack.isEmpty()) return "item missing";
		sp.teleportTo(sp.serverLevel(), stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180f, 30f);
		sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		BlockPos floor = stand.offset(0, -1, -3);
		var hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		var res = stack.useOn(new net.minecraft.world.item.context.UseOnContext(sp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -6; dz <= 0; dz++) {
				if (sp.serverLevel().getBlockEntity(floor.offset(dx, 1, dz)) instanceof LogisticsTileGenericPipe pipe && pipe.pipe != null
						&& pipe.pipe.getClass().getSimpleName().startsWith(classPrefix)) {
					logisticspipes.LogisticsPipes.log.info("LPCHECK INFO placed: {} at {} ({})", pipe.pipe.getClass().getSimpleName(), pipe.getBlockPos(), res);
					return null;
				}
			}
		}
		return "not placed (" + res + ")";
	}

	private static void tubeCommand(net.minecraft.server.level.ServerPlayer sp, String command) {
		sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
	}

	/** HUD glasses on the tube platform: two chest providers one behind the other plus a satellite; screenshots plain and shift-focused. */
	private static void startGlasses(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null || tubeBase == null) {
			result("glasses.placed", false, "no platform");
			return;
		}
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
			if (sp == null) {
				result("glasses.placed", false, "no server player");
				return;
			}
			BlockPos b = tubeBase;
			tubeCommand(sp, String.format("fill %d %d %d %d %d %d air", b.getX() - 10, b.getY(), b.getZ() - 10, b.getX() + 10, b.getY() + 6, b.getZ() + 6));
			glassesChest(sp, b.offset(0, 0, -3), net.minecraft.world.item.Items.DIAMOND, 5);
			glassesChest(sp, b.offset(1, 0, -6), net.minecraft.world.item.Items.REDSTONE, 32);
			String front = placePipe(sp, "pipe_provider", b.offset(0, 0, 1), "PipeItemsProvider");
			String back = placePipe(sp, "pipe_provider", b.offset(1, 0, -2), "PipeItemsProvider");
			String sat = placePipe(sp, "pipe_satellite", b.offset(2, 0, 1), "PipeItemsSatellite");
			result("glasses.placed", front == null && back == null && sat == null,
					"front=" + (front == null ? "ok" : front) + " back=" + (back == null ? "ok" : back) + " satellite=" + (sat == null ? "ok" : sat));
			var glasses = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse("logisticspipes:hud_glasses")));
			sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, glasses);
			// eye 1.62 above the floor, front provider centre 4 blocks north and 1.12 lower
			sp.teleportTo(sp.serverLevel(), b.getX() + 0.5, b.getY(), b.getZ() + 2.5, 180f, 15.6f);
		});
	}

	private static void glassesChest(net.minecraft.server.level.ServerPlayer sp, BlockPos pos, net.minecraft.world.item.Item item, int count) {
		sp.serverLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
		if (sp.serverLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			chest.setItem(0, new net.minecraft.world.item.ItemStack(item, count));
		}
	}

	/** Item amount sign on the front provider's free south face, seen up close without glasses. */
	private static void startSign(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null || tubeBase == null) return;
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
			if (sp == null) return;
			BlockPos b = tubeBase;
			sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
			BlockPos at = b.offset(0, 0, -2);
			boolean ok = sp.serverLevel().getBlockEntity(at) instanceof LogisticsTileGenericPipe pipe
					&& pipe.pipe instanceof logisticspipes.pipes.basic.CoreRoutedPipe;
			if (ok) {
				var sign = new logisticspipes.pipes.signs.ItemAmountPipeSign();
				sign.itemTypeInv.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
				sign.amount = 64;
				((logisticspipes.pipes.basic.CoreRoutedPipe) ((LogisticsTileGenericPipe) sp.serverLevel().getBlockEntity(at)).pipe)
						.addPipeSign(Direction.SOUTH, sign, sp);
			}
			result("sign.placed", ok, "provider at " + at);
			// eye 1.62 above the floor, pipe centre 1.5 blocks north and 1.12 lower
			sp.teleportTo(sp.serverLevel(), b.getX() + 0.5, b.getY(), b.getZ(), 180f, 36.7f);
		});
	}

	/** The client copy of the provider must have received the sign. */
	private static void checkSign(Minecraft mc) {
		int signs = tubeBase != null && mc.level.getBlockEntity(tubeBase.offset(0, 0, -2)) instanceof LogisticsTileGenericPipe pipe
				&& pipe.pipe instanceof logisticspipes.pipes.basic.CoreRoutedPipe routed ? routed.getPipeSigns().size() : -1;
		result("sign.client", signs == 1, "signs=" + signs);
	}

	private static LogisticsBaseGuiScreen monitorHost;
	private static logisticspipes.gui.popup.RequestMonitorPopup monitor;

	/** 2026-07-02 batch: request monitor popup over a host screen, with a three-level order tree. */
	private static void openMonitor(Minecraft mc) {
		var table = new logisticspipes.pipes.PipeBlockRequestTable(logisticspipes.LPItems.requestTable.get());
		var root = new logisticspipes.routing.order.LinkedLogisticsOrderList();
		root.add(monitorOrder(net.minecraft.world.item.Items.CHEST, 4, true));
		var planks = new logisticspipes.routing.order.LinkedLogisticsOrderList();
		planks.add(monitorOrder(net.minecraft.world.item.Items.OAK_PLANKS, 32, false));
		var logs = new logisticspipes.routing.order.LinkedLogisticsOrderList();
		logs.add(monitorOrder(net.minecraft.world.item.Items.OAK_LOG, 8, false));
		planks.getSubOrders().add(logs);
		var iron = new logisticspipes.routing.order.LinkedLogisticsOrderList();
		iron.add(monitorOrder(net.minecraft.world.item.Items.IRON_INGOT, 2, false));
		root.getSubOrders().add(planks);
		root.getSubOrders().add(iron);
		table.watchedRequests.put(7, new logisticspipes.utils.tuples.Pair<>(null, root));
		monitorHost = new LogisticsBaseGuiScreen(176, 166, 0, 0) {
			@Override
			protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
		};
		mc.setScreen(monitorHost);
		monitor = new logisticspipes.gui.popup.RequestMonitorPopup(table, 7);
		monitorHost.setSubGui(monitor);
	}

	private static logisticspipes.routing.order.IOrderInfoProvider monitorOrder(net.minecraft.world.item.Item item, int count, boolean inProgress) {
		var display = logisticspipes.utils.item.ItemIdentifierStack.getFromStack(new net.minecraft.world.item.ItemStack(item, count));
		return new logisticspipes.routing.order.IOrderInfoProvider() {
			public boolean isFinished() { return false; }
			public logisticspipes.utils.item.ItemIdentifierStack getAsDisplayItem() { return display; }
			public ResourceType getType() { return inProgress ? ResourceType.CRAFTING : ResourceType.PROVIDER; }
			public int getRouterId() { return 1; }
			public boolean isInProgress() { return inProgress; }
			public boolean isWatched() { return true; }
			public void setWatched() {}
			public List<Float> getProgresses() { return List.of(); }
			public byte getMachineProgress() { return (byte) (inProgress ? 40 : 0); }
			public logisticspipes.utils.item.ItemIdentifier getTargetType() { return null; }
			public network.rs485.logisticspipes.world.DoubleCoordinates getTargetPosition() { return null; }
		};
	}

	/** Popup drew, wheel zooms, drag pans, Save as Image writes a tree PNG, ESC closes only the popup. */
	private static void checkMonitor(Minecraft mc) throws ReflectiveOperationException, java.io.IOException {
		Screenshot.grab(mc.gameDirectory, "lpcheck_monitor.png", mc.getMainRenderTarget(),
				msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO monitor screenshot: {}", msg.getString()));
		boolean drawn = monitorHost.hasSubGui() && monitor.getGuiGraphics() != null;
		result("monitor.drawn", drawn, "open=" + monitorHost.hasSubGui() + " rendered=" + (monitor.getGuiGraphics() != null));

		double cx = monitorHost.width / 2.0, cy = monitorHost.height / 2.0;
		Field zoomField = logisticspipes.gui.popup.RequestMonitorPopup.class.getDeclaredField("zoom");
		zoomField.setAccessible(true);
		Object zoomBefore = zoomField.get(monitor);
		monitorHost.mouseScrolled(cx, cy, -1);
		Object zoomAfter = zoomField.get(monitor);
		result("monitor.wheelZoom", zoomBefore != zoomAfter, zoomBefore + " -> " + zoomAfter);

		Field mapXField = logisticspipes.gui.popup.RequestMonitorPopup.class.getDeclaredField("guiMapX");
		mapXField.setAccessible(true);
		double mapBefore = mapXField.getDouble(monitor);
		monitorHost.mouseDragged(cx, cy, 0, 10, 0);
		double mapAfter = mapXField.getDouble(monitor);
		result("monitor.dragPan", mapAfter != mapBefore, "guiMapX " + mapBefore + " -> " + mapAfter);

		java.io.File shots = new java.io.File(mc.gameDirectory, "screenshots");
		java.util.Set<String> before = treeShots(shots);
		boolean clicked = monitorHost.mouseClicked(cx + 50, cy + 84, 0);
		java.util.Set<String> added = treeShots(shots);
		added.removeAll(before);
		String png = "none";
		boolean drawnTree = false;
		if (added.size() == 1) {
			png = added.iterator().next();
			try (var in = new java.io.FileInputStream(new java.io.File(shots, png));
					var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
				int clear = image.getPixelRGBA(0, 0), other = 0;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						if (image.getPixelRGBA(x, y) != clear) other++;
					}
				}
				drawnTree = other > 500;
				png += " " + image.getWidth() + "x" + image.getHeight() + " drawnPixels=" + other;
			}
		}
		result("monitor.savePng", clicked && drawnTree, "clicked=" + clicked + " png=" + png);

		monitorHost.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
		boolean popupClosed = !monitorHost.hasSubGui();
		boolean hostOpen = mc.screen == monitorHost;
		mc.setScreen(null);
		result("monitor.escClosesPopupOnly", popupClosed && hostOpen, "popupClosed=" + popupClosed + " hostOpen=" + hostOpen);
	}

	private static java.util.Set<String> treeShots(java.io.File dir) {
		String[] names = dir.list((d, n) -> n.endsWith(".png") && n.contains("_tree"));
		return names == null ? new java.util.HashSet<>() : new java.util.HashSet<>(java.util.Arrays.asList(names));
	}

	private static LogisticsBaseGuiScreen popupHost;
	private static logisticspipes.gui.popup.SelectItemOutOfList itemPicker;
	private static logisticspipes.gui.popup.GuiDiskPopup diskPopup;

	private static LogisticsBaseGuiScreen openPopupHost(Minecraft mc) {
		popupHost = new LogisticsBaseGuiScreen(176, 166, 0, 0) {
			@Override
			protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}
		};
		mc.setScreen(popupHost);
		return popupHost;
	}

	private static void typeInto(LogisticsBaseGuiScreen host, String text) {
		for (char c : text.toCharArray()) host.charTyped(c, 0);
	}

	private static void openItemPicker(Minecraft mc) {
		var items = new ArrayList<logisticspipes.utils.item.ItemIdentifierStack>();
		items.add(logisticspipes.utils.item.ItemIdentifierStack.getFromStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
		items.add(logisticspipes.utils.item.ItemIdentifierStack.getFromStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG)));
		itemPicker = new logisticspipes.gui.popup.SelectItemOutOfList(items, slot -> {});
		openPopupHost(mc).setSubGui(itemPicker);
	}

	/** The picker's search bar takes focus on click, types through the base screen and filters the list. */
	private static void checkItemPicker(Minecraft mc) throws ReflectiveOperationException {
		Field searchField = logisticspipes.gui.popup.SelectItemOutOfList.class.getDeclaredField("search");
		searchField.setAccessible(true);
		InputBar search = (InputBar) searchField.get(itemPicker);
		int left = popupHost.width / 2 - 156 / 2, bottom = popupHost.height / 2 + 188 / 2;
		popupHost.mouseClicked(left + 20, bottom - 15, 0);
		typeInto(popupHost, "irn");
		popupHost.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
		typeInto(popupHost, "on");
		String text = search.getText();
		result("picker.searchTypes", "iron".equals(text), "focused=" + search.isFocused() + " text='" + text + "'");
		boolean iron = itemPicker.itemSearched(logisticspipes.utils.item.ItemIdentifier.get(net.minecraft.world.item.Items.IRON_INGOT, 0, null));
		boolean log = itemPicker.itemSearched(logisticspipes.utils.item.ItemIdentifier.get(net.minecraft.world.item.Items.OAK_LOG, 0, null));
		result("picker.searchFilters", iron && !log, "iron shown=" + iron + " log shown=" + log);
	}

	/** InputBar.drawTextBox must draw the box: its inner fill is black. */
	private static void checkItemPickerDrawn(Minecraft mc) {
		double scale = mc.getWindow().getGuiScale();
		int left = popupHost.width / 2 - 156 / 2, bottom = popupHost.height / 2 + 188 / 2;
		int x0 = (int) ((left + 10) * scale), x1 = (int) ((left + 90) * scale);
		int y0 = (int) ((bottom - 21) * scale), y1 = (int) ((bottom - 11) * scale);
		int black = 0, total = 0;
		try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
			for (int y = y0; y < y1; y++) {
				for (int x = x0; x < x1; x++) {
					total++;
					if ((image.getPixelRGBA(x, y) & 0xFFFFFF) == 0) black++;
				}
			}
		}
		result("picker.searchDrawn", black > total / 2, "black " + black + "/" + total);
		Screenshot.grab(mc.gameDirectory, "lpcheck_picker.png", mc.getMainRenderTarget(),
				msg -> logisticspipes.LogisticsPipes.log.info("LPCHECK INFO picker screenshot: {}", msg.getString()));
		mc.setScreen(null);
	}

	private static void openDiskPopup(Minecraft mc) {
		net.minecraft.world.item.ItemStack disk = new net.minecraft.world.item.ItemStack(logisticspipes.LPItems.disk.get());
		diskPopup = new logisticspipes.gui.popup.GuiDiskPopup(new logisticspipes.interfaces.IDiskProvider() {
			@Override public net.minecraft.world.item.ItemStack getDisk() { return disk; }
			@Override public int getX() { return 0; }
			@Override public int getY() { return 0; }
			@Override public int getZ() { return 0; }
			@Override public logisticspipes.utils.gui.ItemDisplay getItemDisplay() { return null; }
		});
		openPopupHost(mc).setSubGui(diskPopup);
	}

	/** Name field: click edits, typing, backspace and the cursor keys work; Enter/ESC/outside click would send a rename. */
	private static void checkDiskPopup(Minecraft mc) throws ReflectiveOperationException {
		int left = popupHost.width / 2 - 150 / 2, top = popupHost.height / 2 - 200 / 2;
		popupHost.mouseClicked(left + 20, top + 35, 0);
		typeInto(popupHost, "ab");
		popupHost.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
		popupHost.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
		typeInto(popupHost, "x");
		Field name1 = logisticspipes.gui.popup.GuiDiskPopup.class.getDeclaredField("name1");
		Field name2 = logisticspipes.gui.popup.GuiDiskPopup.class.getDeclaredField("name2");
		name1.setAccessible(true);
		name2.setAccessible(true);
		String name = name1.get(diskPopup) + "|" + name2.get(diskPopup);
		result("disk.nameTypes", "Diskx|a".equals(name), "before|after cursor='" + name + "'");
	}

	/** The HUD renderer must have picked up all three pipes as panels. */
	private static void checkGlasses() throws ReflectiveOperationException {
		Field f = logisticspipes.renderer.LogisticsHUDRenderer.class.getDeclaredField("list");
		f.setAccessible(true);
		int panels = ((java.util.List<?>) f.get(logisticspipes.renderer.LogisticsHUDRenderer.instance())).size();
		result("glasses.panels", panels >= 3, "panels=" + panels);
	}

	/** Removes the platform and tubes so the LP Testbed save stays as built. */
	private static void finishTubes(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null || tubeBase == null) return;
		server.execute(() -> {
			var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
			if (sp == null) return;
			BlockPos b = tubeBase;
			sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
			tubeCommand(sp, String.format("fill %d %d %d %d %d %d air", b.getX() - 10, b.getY() - 1, b.getZ() - 10, b.getX() + 10, b.getY() + 6, b.getZ() + 6));
			sp.teleportTo(sp.serverLevel(), tubeReturn.x, tubeReturn.y, tubeReturn.z, tubeYaw, tubePitch);
		});
	}

	private static List<LogisticsTileGenericPipe> loadedPipes(Minecraft mc) {
		List<LogisticsTileGenericPipe> pipes = new ArrayList<>();
		ChunkPos center = mc.player.chunkPosition();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(center.x + dx, center.z + dz, false);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof LogisticsTileGenericPipe pipe && pipe.pipe != null) pipes.add(pipe);
				}
			}
		}
		return pipes;
	}

	/** Worklog item 4: the client tick fills solidSidesCache on cold load (end caps). */
	private static void checkSolidSides(Minecraft mc) throws ReflectiveOperationException {
		Field cacheField = logisticspipes.renderer.state.PipeRenderState.class.getDeclaredField("solidSidesCache");
		cacheField.setAccessible(true);
		int pipes = 0, withSolid = 0, mismatched = 0;
		String firstMismatch = "";
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			pipes++;
			boolean[] cache = (boolean[]) cacheField.get(pipe.renderState);
			boolean any = false;
			for (Direction dir : Direction.values()) {
				BlockPos side = pipe.getBlockPos().relative(dir);
				boolean expected = mc.level.getBlockState(side).isFaceSturdy(mc.level, side, dir.getOpposite())
						&& !pipe.renderState.pipeConnectionMatrix.isConnected(dir);
				any |= expected;
				if (cache[dir.ordinal()] != expected && mismatched++ == 0) {
					firstMismatch = pipe.getBlockPos() + " " + dir;
				}
			}
			if (any) withSolid++;
		}
		result("solidSides.cacheMatchesWorld", pipes > 0 && withSolid > 0 && mismatched == 0,
				"pipes=" + pipes + " withSolidSide=" + withSolid + " mismatched=" + mismatched + " " + firstMismatch);
	}

	private static BlockPos particlePos;
	private static EntitySparkleFX sparkle;

	/** Worklog item 7: breaking a rendered pipe spawns LP model shards via addDestroyEffects. */
	/** Turns to the nearest pipe so the particle check finds a rendered one, whatever rotation the save holds. */
	private static void facePipe(Minecraft mc) {
		var eye = mc.player.getEyePosition();
		net.minecraft.world.phys.Vec3 best = null;
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			var c = pipe.getBlockPos().getCenter();
			if (best == null || c.distanceToSqr(eye) < best.distanceToSqr(eye)) best = c;
		}
		if (best == null) return;
		var d = best.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		mc.player.setYRot(yaw);
		mc.player.setXRot(pitch);
		mc.player.yRotO = yaw;
		mc.player.xRotO = pitch;
	}

	private static void startParticleCheck(Minecraft mc) {
		ClientLevel level = mc.level;
		for (LogisticsTileGenericPipe pipe : loadedPipes(mc)) {
			if (pipe.renderState.cachedRenderer != null) {
				particlePos = pipe.getBlockPos();
				break;
			}
		}
		if (particlePos == null) {
			result("particles.destroyEffects", false, "no rendered pipe in range");
			return;
		}
		// textured sparkle sheet: a broken vertex format or texture throws during the next frames
		// placed in view so frustum culling does not skip render()
		var eye = mc.gameRenderer.getMainCamera().getPosition().add(mc.player.getLookAngle().scale(2));
		sparkle = new EntitySparkleFX(level, eye.x, eye.y, eye.z, 1F, 1F, 0.5F, 0F, 6);
		mc.particleEngine.add(sparkle);
		particleBaseline = Integer.parseInt(mc.particleEngine.countParticles());
		BlockState state = level.getBlockState(particlePos);
		BlockHitResult hit = new BlockHitResult(particlePos.getCenter(), Direction.UP, particlePos, false);
		result("particles.hitEffects", IClientBlockExtensions.of(state).addHitEffects(state, level, hit, mc.particleEngine),
				"pipe-icon crack particles at " + particlePos);
		boolean handled = IClientBlockExtensions.of(state).addDestroyEffects(state, level, particlePos, mc.particleEngine);
		if (!handled) {
			result("particles.destroyEffects", false, "addDestroyEffects returned false at " + particlePos);
			particlePos = null;
		}
	}

	private static void finishParticleCheck(Minecraft mc) {
		if (particlePos == null) return;
		result("particles.sparkle", sparkle.isAlive(), "sparkle in view rendered for 3 ticks");
		int now = Integer.parseInt(mc.particleEngine.countParticles());
		result("particles.destroyEffects", now > particleBaseline, "particles " + particleBaseline + " -> " + now + " at " + particlePos);
	}

	/** Worklog item 8: HUDAsyncExtractor text, drawn on screen for the screenshot. */
	private static void startHud() throws ReflectiveOperationException {
		AsyncExtractorModule defaultSide = new AsyncExtractorModule();
		AsyncExtractorModule north = new AsyncExtractorModule();
		// the public setter syncs to the server and needs an in-world module; set the property directly
		Field prop = AsyncExtractorModule.class.getDeclaredField("sneakyDirectionProp");
		prop.setAccessible(true);
		Object property = prop.get(north);
		property.getClass().getMethod("setValue", Object.class).invoke(property, Direction.NORTH);
		hudRenderers.clear();
		hudRenderers.add(defaultSide.getHUDRenderer());
		hudRenderers.add(north.getHUDRenderer());
		drawHud = true;
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		if (!ENABLED || !drawHud) return;
		GuiGraphics gg = event.getGuiGraphics();
		GuiGraphics previous = SimpleGraphics.guiGraphics;
		SimpleGraphics.guiGraphics = gg;
		try {
			int x = gg.guiWidth() / 2 - 60;
			int y = gg.guiHeight() / 2;
			for (IHUDModuleRenderer renderer : hudRenderers) {
				gg.pose().pushPose();
				gg.pose().translate(x, y, 0);
				gg.fill(-30, -30, 30, 30, 0xffc6c6c6);
				renderer.renderContent(false);
				gg.pose().popPose();
				x += 120;
			}
		} finally {
			SimpleGraphics.guiGraphics = previous;
		}
	}
}
