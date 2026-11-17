# Gemini 3.3.0

## Menus and screens

- **Title screen.** The vanilla panorama with the logo and one menu column on the left: Singleplayer, then **Continue**, which opens the world you played last straight away, then Multiplayer, Realms, Options and Quit, and small links to the Gemini menu, this guide and quick search. The sandbox switch is in the top-right corner; "Vanilla menu" in the bottom-left corner switches back to the normal title screen, and its "Gemini menu" button switches back again. A line of text only appears if a game hook failed to load.
- **Pause menu.** Module menu, Edit HUD and Guide as normal Minecraft buttons in the bottom-left corner (above the Game Menu title on small windows), plus a Panic link. A "key conflicts" link appears only while two actions share a key.
- **Window menu.** The sidebar lists the categories (with the number of modules switched on, when there are any) and three pages: **Presets** (load, save, overwrite, delete, export and import profiles; built-in presets), **Keybinds** (every bound key; conflicts in red, clashes with vanilla controls in amber; Rebind, Clear and Toggle/Hold buttons) and **Baritone** (current task, goal, progress and ETA; Pause, Resume and Cancel; quick starts for the bot modules). Modules with many settings show the main ones and a **More settings** row for the rest. It also opens from the title screen, so you can set Gemini up before loading a world.
- **Panel menu.** Panels snap to the grid and to each other. Right-click a module for its settings, middle-click to bind a key. Search also matches setting names and says which one matched; the arrow keys and Enter move through the results.
- **Setting controls.** Colour picker with a saturation/brightness square, hue and opacity sliders, hex input, copy and paste, and Static/Rainbow/Pulse. Hold Shift on a slider for fine steps, double-click it to type a value. Lists have icons, search and category filters.
- **HUD editor.** Widgets snap to the screen centre, to other widgets' edges and centres, and to equal gaps. Arrow keys nudge the selected widget by 1 px (Shift: 10 px).
- **Notifications** stack in a corner of your choice (HUD > Toast Position) and can be switched off (HUD > Toasts). Switching a module on or off shows one short line ("Sprint on"); the chat line is only written when notifications are off.

## Look

- One calm default look: flat dark panels, thin borders, no glow, gradients or pulsing. Glass Glow and Particles in the ClickGUI settings are now off by default.
- **Watermark** is a small "Gemini" label with FPS. Ping is shown only on a real connection, never in single player. Speed is off by default. Styles are now called Card, Compact and Text (were Nextgen Card, LiquidBounce Pill and Minimal), and HUD > Watermark switches it off.
- **ArrayList** uses the theme accent by default, without outline. The "Astolfo" colour mode is now called "Pink-Blue Wave".
- The default menu theme is now called "Blue" (was "LiquidBounce"), and "Neon" is now "Purple".

## Find and share

- **Quick search** (`Ctrl+K`, or `K` in game; rebind it in Controls). Finds modules, settings, commands and pages. **Enter** toggles a module, **Shift+Enter** opens it in the window menu, **Tab** switches between all results, modules, commands and pages. It works before a world is loaded; modules can then be set up but not switched on.
- **Share codes.** "Copy share code" in Presets & Configs, or `.config share <name>`, copies your settings as one line starting with `gemini:`. "Paste share code", or `.config import code`, lists what would change before you save or apply it. Damaged or oversized codes are refused with a reason.
- **Key-bind warnings.** Binding a key that a module or a vanilla control already uses shows a warning, wherever the bind was made. The first time you enter a world each session, one notification lists any existing conflicts.
- **This guide.** What's new, the controls of both menus, every command, and an About page. It opens by itself once after an update; afterwards from the title screen, the pause menu, the window menu, `.help` and `.changelog`.

## Aim and targeting

- Every module that picks a target (KillAura, AimAssist, TriggerBot, Bow Aimbot, Silly Aimbot, AutoCrystal, Anchor and Bed Aura, Auto Trap, Web Aura, Hole Filler, Auto Web Fill, Auto City, Hole Push, Hit Crystal, Shield Breaker, Auto Rod, Backtrack, Keep Range, Target Strafe, Auto Block, Flamethrower and Follow Bot) has the same settings: **Priority** (Closest, Lowest Health, Highest Health, Closest To Crosshair, Most Armor, Least Armor, Most Dangerous), **Prefer** (Any, Players First, Mobs First), **Stick To Target**, and filters for players, hostiles, passives and invisible mobs. The placing auras move on to the next target when nothing can be done to the first.
- **Target Box** draws an outline, a fill or both around the current target, in a fixed colour or coloured by health, optionally through walls. It replaces KillAura's Highlight. Target HUD follows whichever of KillAura, Shield Breaker, TriggerBot or the aimbots has a target.
- **Bow Aimbot** (was ProjectileAimbot; `.projectileaimbot` still works and your settings carry over). Aims bows, crossbows and tridents using the game's drag and gravity, leads moving targets and accounts for your own movement. Settings: Low, High or Auto arc; **Aim Point** (Head, Chest, Feet, or Auto: the highest point it can hit); **Wall Check** (skips targets it cannot reach); a drawn trajectory with an impact marker; **Auto Release** and **Auto Charge**.
- **Silly Aimbot** (new). Your arrows and tridents, and optionally snowballs, eggs and ender pearls, steer toward the target and around blocks and mobs in the way. Speed, turn rate, look-ahead, maximum time and retargeting are adjustable, and the path is drawn. It only works in your own single-player world, because it moves the projectiles on the integrated server.
- KillAura's Target Mode **Switch** cycles through targets in priority order; **Single** stays on one.

## HUD

- **ArrayList** is now a HUD widget you can move like the others. Sort by width, name or category; colour by accent, theme gradient, category, rainbow, pink-blue wave, wave or a fixed gradient; optional side bar, background and slide animations. Your old list settings carry over.
- **Target HUD** has three styles and shows the target's face or model, health with absorption, distance, totem pops, armour durability and who is ahead in the fight.
- **Profiler.** Performance and AntiLag can list the modules that take the most time per frame, in microseconds. It does nothing while switched off.

## Fixes

- Switching a bot module off cancelled any Baritone task, even one started by another module. Each module now stops only its own task, and switches itself off when its task ends.
- Timer and Tick Freeze could leave the world fast or frozen after leaving it or opening it to LAN. The tick rate is now always restored, and the two modules no longer undo each other.
- AutoTotem and Offhand kept the offhand reserved at low health with no totem left, blocking other offhand modules. AutoPot and AutoExp held your aim with nothing to throw.
- Flight could leave survival flight on after a game-mode change; AutoBlock released right-click while you held it; AutoWalk could leave sprint held; Freecam kept the previous dimension's position.
- Module suffixes showed "0,55" on systems that use a decimal comma.
- Many render modules and HUD widgets created new objects every frame; most of that is gone.
- Placing modules check that your aim hits the block face before placing (**Raycast Check**, on by default), can turn gradually (**Aim Speed**), and fade out the blocks they place. Scaffold has a **Sprint Jump** tower mode.
- The self-test also checks that every setting survives a save and load.
- **Player:** Middle Click Extra's Golden Apple did not eat the apple; Exp Thrower's Look Down left the camera facing the floor; Auto Mend did not put armour back on; Auto Tool swapped a better weapon for a worse one; Auto Armor retried cursed armour forever; inventory-clicking modules could drop the cursor item into the wrong slot; Anti Void and Clutch could use a safe spot from another world; Hotbar Sorter and Inventory Cleaner interrupted eating and bow draws.
- **World:** Auto Sign could crash in a packet loop; Collisions changed collisions for every mob and for server pathfinding; World Rules' Fire Spread used a game rule that no longer exists; Nuker, Vein Miner, Auto Farm, Packet Mine and Bed Breaker (was BedBreaker) kept mining the previous world's block after a world change, and Packet Mine mined twice while attack was held; Lawn Mower could not break cobwebs; Mob Sweeper's All Mobs killed armour stands and pets (new **Spare Pets**, on by default); Home and Death Coords could teleport you to coordinates from another save; Locate's Nether teleport landed on the bedrock roof; Stronghold Finder triangulates from two eye throws (new **Pin Estimate**); Auto Build Bot no longer cancels other modules' pathing.
- **Render and misc:** Panic switched off the Aim settings permanently; Presets' Disable Others missed most modules; AutoGG triggered when a target walked away; Flag Check counted joins, respawns and teleports as setbacks; Zoom moved in 20 Hz steps; Performance, Fullbright, No Bob and No Hurt Cam could save their temporary video settings to your options file; Logoff Spot marked players who walked away; Pearl Tracker re-simulated every pearl every frame; Name Protect removed chat colours. **Crosshair** and **Block Selection** can replace the vanilla crosshair and block outline (**Hide Vanilla**).

## New modules

| Module | What it does |
|---|---|
| Auto City | Mines the surround block of a target in a hole, optionally placing a crystal first |
| Hit Crystal | After a sword hit: places obsidian and a crystal, then detonates it |
| Hole Push | Pushes a target out of its hole with a piston and redstone |
| Auto Web Fill | Places webs where a target is about to walk |
| Elytra Recast | Reopens the elytra when you land so you keep gliding |
| Silent Pearl | Throws a pearl from anywhere in your inventory and switches back |
| Auto Regear | Places a shulker box, refills items to set counts, then mines the box |
| Multi Task | Attack or mine while eating or using an item |
| Chams | Shows entities through walls: Flat, Textured (outline), Wireframe or Pulse |
| Break ESP | Progress boxes and percentages on blocks being mined |
| View Model | Moves, scales and rotates the items in your hands |
| Pop Chams | Shows a fading copy of a player where they popped a totem |
| Totem Pop Counter | Counts totem pops per player (chat, notification, Nametags, Target HUD) |
| Stash Finder | Finds dense groups of storage blocks, logs them and adds a waypoint |
| Auto Build Bot | Builds a nether portal, wither, wall, platform, pillar or bridge from your inventory |
| Silly Aimbot | Steers your arrows and tridents around obstacles toward the target |
| Day Info | HUD: day number, time, moon phase and time until night or morning |
| Spawn Info | HUD: world spawn and your bed or anchor, with distance and direction (Nether-aware) |
| Session Stats | HUD: blocks mined and placed, kills, deaths, damage, crafts and XP this session, optionally per hour |
| Weather Forecast | HUD: time until rain or a thunderstorm starts or stops |
| Mob Caps | HUD: mobs counted against each spawn cap |
| Ender Chest Preview | HUD: your ender chest contents |
| Coords Copier | Copies your position (plain, comma-separated, `/tp` or `/execute`) to the clipboard |
| Screenshot Helper | Hides the HUD, takes one screenshot or a burst, then shows it again |
| Quick Move | Shift and drag across container slots to move every stack you pass over |
| Craft Repeat | Repeats the last recipe-book craft until ingredients or a batch limit run out |
| Biome Border | Draws a fence where the biome changes |
| Harvest ESP | Highlights ripe crops, nether wart, cocoa, berries, melons and pumpkins |

Fast Fall's *Ledges* mode (formerly ReverseStep) has a smooth pull, a *Holes Only* filter and an optional timer boost.

**Colours** have saturation, brightness, opacity and a Rainbow or Pulse mode. `.<module> <setting> #RRGGBB` sets one by hex code. Configs from 3.2.0 are upgraded automatically.
