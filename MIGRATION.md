# Migration Status

A high-level checkpoint of where the Forge 1.12.2 → NeoForge 1.20.1 port stands. Updated periodically; if you're picking up work, check the open issues for finer-grained scope.

## Working

- **Compile & tests** — full project compiles on NeoForge 1.20.1; unit tests pass.
- **Runtime** — pipes place, persist across save/load, and render; GUIs open; dev client/server **and a production dedicated server** all launch (the production server reaches *Done* with zero LogisticsPipes errors).
- **Production packaging (reobf)** — NeoGradle 7 does **not** reobfuscate for 1.20.1, so the shipped jar must be remapped Mojmap → SRG or it crashes in production (`NoSuchFieldError`, was issue #1). `build.gradle` does this with **TinyRemapper** (not AutoRenamingTool, which leaks MC renames into JDK collections). The no-classifier `logisticspipes-*.jar` in `build/libs` is the production artifact.
- **Dist-safety** — client-only code (renderers, render proxy, GUI/screen packets, client event handlers) is gated behind `Dist.CLIENT`/`@OnlyIn` so the common code path links on a dedicated server.
- **Registration** — blocks, items, block entities, menus, recipe types, entities all on `DeferredRegister`.
- **Networking** — packets wired via `SimpleChannel` and a unified `LPPacketPayload`; packet auto-discovery uses `ModFileScanData`; packet IDs are index-based so the client and server tables stay in sync even when a packet is dist-stripped.
- **Capabilities** — `RegisterCapabilitiesEvent` wired for the main block entities.
- **Routing & logistics** — `ServerRouter` / `ClientRouter`, pathfinding, promises, crafting orderer, chassis modules, request pipes.
- **Ore dictionary → Tags** — all tag lookups ported.
- **Commands** — Brigadier-based.
- **GUIs (non-rendering)** — every screen and container migrated to the 1.20.1 menu/screen API.
- **Item models & recipes** — modules, upgrades, tool items, most pipe items.

## In progress

- **Rendering polish** — traveling items render but visuals are still being tuned; some block-entity renderers need cleanup.
- **Item / fluid model coverage** — the majority are done; a handful of edge-case items still on placeholder models.

## Deferred

- **Third-party mod integrations** — BuildCraft, IndustrialCraft 2, Thermal (Dynamics/Expansion), NEI, EnderStorage, IronChest, OpenComputers, MCMultiPart. The 1.12-era stub layer was **removed** (their dummy behavior is constant-folded at the former call sites); re-adding any of these on a modern MC means a fresh integration against that mod's current API, not reviving the stubs. Live today: JEI, Applied Energistics 2 (items and fluids), CC: Tweaked, Storage Drawers and The One Probe.
- **Legacy data fixers** — no upgrade path from 1.12.2 worlds; start a fresh 1.20.1 save.
- **Fabric loader support** — planned after the NeoForge port reaches feature parity (via Architectury).

## Forward-port surface (1.21+)

What actually has to change when this branch moves past 1.20.1, measured on the current tree:

- **`net.minecraftforge` → `net.neoforged` namespace rename** — the bulk is ~138 `@OnlyIn`/`Dist` annotation imports (purely mechanical find/replace; a facade can't absorb annotations, so don't bother building one).
- **Capabilities (~20 usages)** — NeoForge 1.20.5+ removed `LazyOptional` and reworked `ForgeCapabilities`/`Capability` into typed block/item/entity capabilities. This is the largest *semantic* change: `PowerProxy` (Forge Energy), the item/fluid handler lookups, and `RegisterCapabilitiesEvent` wiring all need rewriting.
- **Networking** — the custom `SimpleChannel` + `LPPacketPayload` bridge must move to `CustomPacketPayload`/`StreamCodec` (vanilla 1.20.5+ model). The 174 `ModernPacket` classes themselves are loader-agnostic; only the bridge layer changes.
- **Events (~25 usages)** — bus split (`NeoForge.EVENT_BUS`), `TickEvent` shape change, and `MissingMappingsEvent` has no NeoForge equivalent (replace `MissingMappingHandler` with DataFixers or drop).
- **Fluids (~25 usages)** — `FluidStack`/`IFluidHandler`/`FluidTank` map 1:1 to the `net.neoforged.neoforge.fluids` equivalents; mechanical.
- **Registries** — already on vanilla `BuiltInRegistries`/`Registries` keys throughout (`DeferredRegister.create(Registries.X, ...)` included); no Forge registry API left on read paths.
- **Toolchain** — Kotlin 1.9.10 must move to 2.x (the 1.9 compiler cannot even run on JDK 25), and NeoGradle to the current ModDevGradle.

## Contributing

- Check open issues before starting — some areas are actively being worked on.
- Run Gradle on **JDK 21** (point `JAVA_HOME` at a JDK 21). A newer system default such as JDK 25 breaks the Kotlin compile daemon; the compile/bytecode target itself stays Java 17 via the toolchain.
- Build (the reobf step runs automatically inside `assemble`/`build`):
  `JAVA_HOME=<jdk-21> ./gradlew build -Dorg.gradle.java.home=<jdk-21> -Pkotlin.compiler.execution.strategy=in-process --no-daemon`.
  Add `-x test -x sign -x ktlint` for faster iteration. The production jar is `build/libs/logisticspipes-<ver>.jar` (no classifier).
- For rendering-related work, compare against the original 1.12.2 `dev` branch of upstream [RS485/LogisticsPipes](https://github.com/RS485/LogisticsPipes) as the visual reference.
