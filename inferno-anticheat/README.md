# Inferno Anti Cheat+ 5.2.0 (LiquidBounce coverage)

Fabric anti-cheat for Minecraft 26.3 (Java 25), built from `inferno-anticheat-fabric-5.1.0+ops.jar`.

**Download:** [`inferno-anticheat-fabric-5.2.0+ops.jar`](inferno-anticheat-fabric-5.2.0+ops.jar). Needs Fabric Loader and Fabric API. Drop it in `mods/`, on a server or a client that hosts single-player or LAN worlds.

## What changed in 5.2

**36 new checks (107 in total), aimed at every LiquidBounce module a server can see.** [`docs/LIQUIDBOUNCE_COVERAGE.md`](docs/LIQUIDBOUNCE_COVERAGE.md) lists all 228 modules of LiquidBounce (nextgen) and the checks that catch each one:

| | Modules |
|---|---|
| Detected | 117 |
| Partly detected (some modes only) | 13 |
| Client-only (no server can see them) | 93 |
| Not covered (deliberately, see the notes) | 5 |

The "client-only" modules (ESP, FullBright, Zoom, Tracers and the rest of the render category, chat and GUI helpers) don't change anything the client sends, so no server-side anti-cheat can detect them.

Most new checks use packets that 26.3 clients send every tick, which the old checks ignored:

- **Real key state** (`ServerboundPlayerInputPacket`). A vanilla client always reports which movement keys are held, and opening any screen releases them all. That catches InventoryMove and MoreCarry, sprinting without forward, moving or jumping with no key held (Speed and BHop auto-jumps), Sneak at full speed, and SafeWalk (stopping dead at a ledge with W held).
- **Client tick boundaries** (`ServerboundClientTickEndPacket`). Vanilla sends at most one movement packet, one sprint toggle and one hotbar change per tick. That catches silent-rotation KillAura, SuperKnockback, SilentHotbar, Surround, AutoTrap and FastExp bursts, multi-hits, and Timer even while standing still.
- **Dig, attack and item packets.** PacketMine, AbortBreaking, digging through walls or without looking, FastBreak against the real break time (tool, effects, water and air penalties), LiquidPlace, CrystalAura, MaceKill, AutoBow, AutoRespawn, BookBot, Spammer, packet floods, client brand.
- **Physics.** HighJump, ReverseStep, NoJumpDelay, LiquidWalk speed, AntiLevitation, ElytraFly, TridentBoost, VehicleBoost.

Each new check can be switched off in the panel (key **I**). Its limits live in `config/inferno-anticheat.json` and are added automatically to existing configs.

## How it was tested

- **Faithful rebuild.** The 5.1 jar was decompiled and recompiled. Every method of all 149 original classes was compared with the original bytecode: the same calls, constants, field accesses and arithmetic.
- **Unit tests** (`./gradlew test`): the detection maths, and checks that all 228 LiquidBounce modules are mapped to checks that exist.
- **In-server scenarios** (`./gradlew runScenarioServer`): a real 26.3 dedicated server where fake players replay the packets a client sends. 9 legit play patterns, moving with vanilla physics (walking, sprint-jumping, strafing, sneaking, falling off ledges, walking into walls, mining, building, using the inventory), must raise **no flag from any of the 107 checks**. 35 cheat patterns must each raise their check. Result: 44/44, several runs in a row.
- **Production smoke test** (`./gradlew prodServer`): the built jar loads in a production Fabric server and every mixin applies.

Not covered by these tests: riding vehicles, sleeping, riptide and real elytra gliding, because Fabric's fake player can't do them. Those checks rely on reasoning about vanilla's code.

## Limits

- A determined cheater can hide the client brand, use random delays (ChestStealer, AutoClicker) or keep below the limits. No anti-cheat catches everything.
- Thresholds are set to avoid false flags first; one flag adds 1 VL, and kicks happen at `kickVl` (12 by default).

## Building

```bash
./gradlew build
```

Needs JDK 25. `build/libs/` gets the jar.
