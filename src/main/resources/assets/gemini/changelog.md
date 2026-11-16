# Gemini 3.3.0

**A new look everywhere.** Every Gemini surface now shares one dark-glass theme: rounded panels, thin accent borders and smooth motion that runs at the same speed whatever your frame rate.

- **Home screen.** Gemini replaces the title screen with its own: a drifting particle background with mouse parallax, the logo with a version badge, glass buttons (Singleplayer, Multiplayer, Realms, ClickGUI, WindowGUI, Presets & Configs, Options, Quit), the sandbox switch, and a status bar with FPS, the active profile, key-bind conflicts and mixin health. "Vanilla menu" in the footer switches back to the normal title screen, and the vanilla screen has a "Gemini menu" button to return.
- **Pause dock.** The Esc menu gets one-click buttons for ClickGUI, WindowGUI, the HUD editor, the config manager, a key-bind report (with the number of conflicts), quick search, this guide and Panic.
- **Window menu dashboard.** The sidebar has the categories (with active-module counts) plus three pages: **Presets & Configs** (load, save, overwrite, delete, export and import profiles, and the built-in presets), **Keybinds** (every bound key, conflicts in red, clashes with vanilla controls in amber, and one-click Rebind / Clear / Toggle-Hold), and **Baritone** (live engine, task, owner, goal, progress and ETA, Pause/Resume/Cancel, and quick starts for Auto Goto, Auto Mine Bot, Follow Bot, Auto Tunnel, Auto Highway and Auto Explore). It can be opened from the title screen to configure Gemini before loading a world.
- **Panel menu.** Opens and closes with a fade, scrolls with inertia, and panels snap to the grid and to each other. Module rows have animated switches and description tooltips; right-click opens settings, middle-click binds a key. Search covers setting names (and says which one matched); arrow keys and Enter work through the results.
- **Setting controls.** A full colour picker (saturation/brightness square, hue and alpha sliders, hex input, copy/paste, Static/Rainbow/Pulse), number sliders with Shift for fine control and double-click to type a value, and a list editor with icons, fuzzy search and category filters.
- **HUD editor.** Centre guides, snapping to other widgets' edges, centres and equal gaps, and arrow-key nudging (Shift for 10px).
- **Toasts** slide in, stack and close up smoothly, show a countdown, and are colour-coded by what they are about. They can be switched off or moved to any corner.

## Find anything, share everything

- **Quick search** (`Ctrl+K`, or `K` in game; rebind it in Controls). Type to search every module, setting name, command and page. **Enter** toggles a module, **Shift+Enter** opens it in the window menu, **Tab** cycles between all results, modules, commands and pages. It also opens from the title screen and the pause dock, and works before a world is loaded (modules can then be configured but not switched on).
- **Share codes.** "Copy share code" in Presets & Configs, or `.config share <name>`, puts your settings on the clipboard as one line starting with `gemini:`. "Paste share code", or `.config import code`, shows what it would change (modules switched on and off, key binds, settings) before you save or apply it. Damaged and oversized codes are refused with a reason.
- **Key-bind warnings.** Binding a key that a module or a vanilla control already uses shows a warning toast, wherever the bind was made: the panel menu, the window menu, `.bind`, the menu key, or a profile or import being loaded. The first time you enter a world each session, one toast sums up any existing conflicts.
- **This guide.** What's new, the controls of both menus, every command and an About page. It opens by itself once after an update; afterwards from the title screen, the pause dock, the window menu, `.help` and `.changelog`.

## HUD

- **ArrayList** is now a widget you can drag like the others, with width/name/category sorting, category, rainbow, Astolfo, wave and gradient colours, a side bar, a frosted background and slide-in/out animations. Your old list settings are carried over.
- **TargetHUD** has three styles (Modern Glass, Compact, Neon) with the target's face or model, an animated health bar with absorption, distance, totem pops, armour durability and a Winning/Even/Losing estimate.
- **Profiler**: Performance and AntiLag can show a live breakdown of the heaviest modules in microseconds. It costs nothing while off.

## Fixes from a module audit

- Switching a bot module off cancelled whatever Baritone task was running, even one started by another module. Each module now stops only its own task, and switches itself off when its task ends.
- Timer and Tick Freeze could leave the world fast or frozen after leaving it or opening it to LAN. The clock is now always restored, and two modules no longer undo each other's change.
- AutoTotem and Offhand kept the offhand reserved at low health with no totem left, which blocked other offhand modules. AutoPot and AutoExp held your aim every tick even with nothing to throw.
- Flight could leave survival flight on after a game-mode switch; AutoBlock forced right-click up while you held it; AutoWalk could leave sprint held; Freecam kept the old dimension's position.
- Module suffixes printed "0,55" on systems that use a decimal comma.
- Many render modules and HUD widgets allocated objects every frame; most of that is gone.
- Placing modules now check that your aim really hits the face before placing (**Raycast Check**, on by default), can turn smoothly (**Aim Speed**), and fade out the blocks they place. Scaffold gains a **Sprint Jump** tower mode.
- The self-test now also checks that every setting survives a save and load.

## New modules

| Module | What it does |
|---|---|
| Auto City | Mines the surround block of a target in a hole, optionally pre-placing a crystal |
| Hit Crystal | After a sword hit: obsidian, crystal, detonate |
| Hole Push | Piston plus redstone to push a target out of its hole |
| Auto Web Fill | Predicts a target's path and webs ahead of it |
| Elytra Recast | Re-opens the elytra as you land so you keep bouncing |
| Silent Pearl | Throws a pearl from anywhere in your inventory and puts your slot back |
| Auto Regear | Places a shulker, refills to set counts, mines it back up |
| Multi Task | Attack or mine while eating or using an item |
| Chams | See entities through walls in Flat, Textured (outline), Wireframe or Pulse |
| Break ESP | Progress boxes and percentages on blocks being mined |
| View Model | Move, scale and rotate the items in your hands |
| Pop Chams | A fading ghost where someone popped a totem |
| Totem Pop Counter | Counts pops per player (chat, toast, Nametags, TargetHUD) |
| Stash Finder | Finds dense storage clusters, logs them, toasts and pins a waypoint |
| Auto Build Bot | Builds a nether portal, wither, wall, platform, pillar or bridge from your inventory |

**Upgraded:** Fast Fall's *Ledges* mode (ReverseStep) gains a smooth pull, a *Holes Only* filter and an optional timer boost.

**Colours** now have saturation, brightness, opacity and a Rainbow/Pulse mode. `.<module> <setting> #RRGGBB` sets one by hex. Configs from 3.2.0 are upgraded automatically.
