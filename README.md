<div align="center">
  <img src="src/main/resources/assets/gemini/textures/gui/logo.png" width="260" alt="Gemini Client logo" />

  # Gemini Client

  **A next-generation Fabric utility / hacked client for Minecraft 26.2.**

  Built-in Baritone A* pathfinding · 236 modules · 33 draggable HUD widgets · glassmorphic ClickGUI

  [![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a?style=flat-square&logo=minecraft)](https://www.minecraft.net/)
  [![Fabric](https://img.shields.io/badge/Loader-Fabric-dbb69c?style=flat-square)](https://fabricmc.net/)
  [![Modrinth](https://img.shields.io/badge/Modrinth-gemini--client-6d55c8?style=flat-square)](https://modrinth.com/project/gemini-client)
  [![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
  [![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

  [Download](https://modrinth.com/project/gemini-client) · [Install](#installation) · [Controls](#controls) · [Commands](#commands) · [Build](#building-from-source) · [Report an issue](https://github.com/mooreconnell24-gif/gemini-client/issues)

  _Keywords: Gemini Client, Minecraft 26.2, Fabric utility client, hacked client, utility mod, Baritone, auto-clicker, ESP, X-ray, scaffold, KillAura, 26.2 mod._
</div>

---

## Overview

Gemini Client is a client-side Fabric utility mod that brings a modern, LiquidBounce Nextgen-inspired interface to Minecraft 26.2. It ships with a large module set, a fully draggable HUD, per-module settings, config profiles, and a built-in Baritone-style pathfinding engine — with no external Baritone jar required.

Everything runs locally. There is no account system, API key, cloud service, or telemetry.

> **Version:** 2.0.0 · **Minecraft:** 26.2 · **Loader:** Fabric · **Java:** 25+

## Highlights

| | |
|---|---|
| **236 modules** | Combat, movement, player, world, render, and client utilities with per-module settings and keybinds. |
| **33 HUD widgets** | FPS, CPS, TPS, armor, potions, radar, target HUD, keystrokes, item counters, graphs, and more. |
| **Built-in Baritone** | Native 3D A* pathfinding with `#goto`, `#mine`, `#follow`, `#tunnel`, `#farm`, `#explore`, and `#excavate`. |
| **Glassmorphic ClickGUI** | Animated panels, themes, accent colors, search, tooltips, grid snap, scaling, and HUD edit mode. |
| **Profiles & config** | Automatic saving plus named profiles you can switch with a single command. |
| **Crash isolation** | A module that throws is disabled automatically instead of taking down the client. |

## Module overview

| Category | Count | Examples |
|---|---:|---|
| Combat | 42 | KillAura, AutoCrystal, AnchorAura, BedAura, Surround, AutoTrap, HoleFiller, Burrow, Criticals, Velocity, Backtrack, TriggerBot |
| Movement | 46 | Flight, Speed, Step, HighJump, LongJump, Spider, Jesus, Elytra Fly, Boat Fly, NoFall, NoSlow, TargetStrafe |
| Player | 46 | AutoArmor, AutoTotem, AutoEat, AutoTool, AutoGapple, ChestStealer, InventoryCleaner, FastPlace, FastBreak, AntiVoid, Regen |
| World | 34 | Nuker, Scaffold, Auto Farm, Auto Mine Bot, Vein Miner, Auto Tunnel, Auto Excavate, Auto Highway, Auto Breed, Seed, Weather, Time |
| Render | 50 | ESP, StorageESP, Xray, Tracers, Nametags, HoleESP, Trajectories, Chunk Borders, Light Overlay, Search, Freecam, Zoom |
| Client | 18 | ClickGUI, Panic, Presets, Notifier, NameProtect, AntiLag, PacketLogger, Spammer, Better Chat |
| HUD | 33 | FPS, CPS, TPS, Armor, Potion Timers, Radar, TargetHUD, Keystrokes, Inventory Viewer, FPS Graph, Cooldowns |

<details>
<summary><strong>Show all HUD widgets</strong></summary>

FPS · TPS · Speedometer · Memory · Session Timer · CPS · Position · Facing · Biome · Clock · Light Level · Last Death · Chunk Info · Looking At · Compass · Welcome · Lag Notifier · Custom Text · Potion Timers · Entity Counter · Armor · Held Item · Item Counter · Totem Counter · Inventory Viewer · Keystrokes · TargetHUD · Radar · Combat Items · FPS Graph · Keybinds · Cooldowns · AntiLag HUD

</details>

## Built-in Baritone

Gemini includes its own pathfinding and automation engine — you do **not** need to install Baritone separately. Commands use the `#` prefix:

| Command | Description |
|---|---|
| `#goto <x> [y] <z>` | Pathfind to coordinates. If `y` is omitted, Gemini finds the surface height. |
| `#mine <block> [block...]` | Scan for and mine the requested blocks, for example `#mine diamond_ore`. |
| `#follow [player\|players\|mobs]` | Follow a named player, nearby players, or nearby mobs. |
| `#tunnel [length]` | Dig a 1×2 tunnel in the direction you are facing. |
| `#farm` | Harvest ripe crops nearby. |
| `#explore` | Spiral outward and explore new chunks. |
| `#excavate [radius] [depth]` | Clear an area around your position. |
| `#pause` / `#resume` | Pause or resume the current task. |
| `#stop` / `#cancel` | Stop everything. |
| `#status` | Show the current task and state. |
| `#help` | List the built-in Baritone commands. |

The `.b <command>` form is also supported, for example `.b goto 1000 64 -200`.

## Installation

### Requirements

| Requirement | Version |
|---|---|
| Minecraft Java Edition | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | A 26.2-compatible build (developed against `0.161.0+26.2`) |
| Java | 25 or newer |

### Steps

1. Install **Fabric Loader** for Minecraft **26.2**.
2. Install **Fabric API** for 26.2 into your `mods` folder.
3. Download `gemini-2.0.0.jar` from [Modrinth](https://modrinth.com/project/gemini-client) (or grab [`gemini-2.0.0.jar`](gemini-2.0.0.jar) right from this repo) and place it in your `mods` folder.
4. Launch the game with the Fabric profile.
5. Press **Right Shift** to open the ClickGUI.

> Remove any standalone Baritone jar if you experience a conflict. Gemini provides the `baritone` entrypoint itself.

## Controls

| Input | Action |
|---|---|
| **Right Shift** | Open or close the ClickGUI (rebindable in **Options → Controls → Gemini Client**) |
| **Left click** | Toggle a module |
| **Right click** | Open a module's settings |
| **Middle click** | Bind a key to a module |
| **Shift + drag** | Move HUD widgets while the ClickGUI is open |
| **Edit HUD** | Enter HUD edit mode from the ClickGUI toolbar |
| **Module keybind** | Toggle that module from anywhere in-game |

The ClickGUI includes a search bar, `All` / `Active Only` / `Keybound` filters, collapse/expand controls, layout reset, and saved panel positions.

## Commands

Chat commands use a `.` prefix. Module names are case-insensitive.

| Command | Description |
|---|---|
| `.help` or `.?` | Show the command list |
| `.gui` or `.menu` | Open the ClickGUI |
| `.toggle <module> [on\|off]` | Toggle or set a module |
| `.<module>` | Quick-toggle a module, for example `.flight` |
| `.set <module> <setting> <value>` | Change a setting; use `_` for spaces |
| `.get <module> [setting]` | Inspect a module or setting |
| `.reset [module] [setting]` | Reset one setting or everything |
| `.bind <module> <key>` / `.unbind <module>` | Manage module keybinds |
| `.modules [category]` | List modules |
| `.config save\|load\|list\|reset <name>` | Manage named config profiles |
| `.reload` | Reload the config from disk |
| `.panic` | Disable every module immediately |
| `.vclip <blocks>` / `.hclip <blocks>` | Vertical / horizontal clip |
| `.coords` | Print your coordinates |
| `.sethome` / `.home` / `.back` | Save and return to a position in this world |
| `.drop hand\|offhand\|hotbar\|inventory\|all` | Drop items |
| `.dismount` | Dismount the current vehicle |
| `.say <text>` / `.leave [reason]` | Send chat / disconnect |
| `.gm <mode>` | Change gamemode (where permitted) |
| `.day` / `.night` / `.noon` / `.sun` / `.rain` | Change world time or weather |
| `.heal` / `.feed` / `.clear` | Restore health, hunger, or clear effects |
| `.selftest` | Run the module self-test in a throwaway world |
| `.rotate <yaw> [pitch]` | Set your rotation |

## Configuration

Gemini saves automatically when the game closes.

| Path | Contents |
|---|---|
| `config/gemini_config.json` | Modules, settings, keybinds, HUD layout, and panel positions |
| `config/gemini_profiles/` | Named profiles created with `.config save <name>` |

If the main config cannot be read, Gemini keeps a backup as `gemini_config.json.broken` and starts with defaults.

## Building from source

### Requirements

- JDK **25**
- Git
- An internet connection for the first Gradle build

### Windows

```powershell
git clone https://github.com/mooreconnell24-gif/gemini-client.git
cd gemini-client
.\gradlew.bat build
```

### Linux / macOS

```bash
git clone https://github.com/mooreconnell24-gif/gemini-client.git
cd gemini-client
./gradlew build
```

The mod jar is written to `build/libs/gemini-2.0.0.jar`.

To launch a development client:

```bash
./gradlew runClient
```

## Project layout

```text
src/main/java/gemini/
├── Gemini.java              Client entrypoint and lifecycle hooks
├── command/                 Chat command system
├── config/                  Config and profile persistence
├── input/                   Key mappings
├── mixin/                   Fabric mixins
├── module/                  Module framework, settings, categories
│   └── modules/             All gameplay and HUD modules
├── render/                  3D rendering helpers
├── ui/                      ClickGUI, HUD overlay, widgets
├── util/                    Baritone engine, rotations, inventory helpers
└── xray/                    Xray data and compilation

src/main/resources/
├── fabric.mod.json
├── gemini.mixins.json
└── assets/gemini/           Icon, logo, and language files
```

## Troubleshooting

| Problem | Fix |
|---|---|
| Game crashes on launch | Confirm Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API for 26.2, and Java 25. |
| ClickGUI does not open | Check **Options → Controls → Gemini Client** and make sure **Open Gemini** is bound. |
| A module is missing | Run `.modules` to confirm it loaded, then check the log for a module initialization error. |
| Baritone commands do nothing | Run `#status`; make sure you are in a world and no other Baritone mod is conflicting. |
| Settings were reset | Restore `config/gemini_config.json.broken` or load a saved profile with `.config load <name>`. |

Logs are in your Minecraft `logs/latest.log`.

## Disclaimer

Gemini Client is a client-side utility mod. Some modules can violate individual server rules or trigger anti-cheat systems. Use it only where such modifications are allowed. The authors are not responsible for bans, lost items, or other consequences.

Gemini Client is not affiliated with Mojang, Microsoft, Fabric, LiquidBounce, or Baritone.

## Credits

- **Fabric** for the mod loader and API.
- **LiquidBounce Nextgen** for the visual direction that inspired the ClickGUI and HUD.
- **Baritone** for pioneering Minecraft pathfinding and automation.
- Everyone who tests builds and reports issues.

## License

Released under the [MIT License](LICENSE).

Copyright © 2026 Gemini Client.
