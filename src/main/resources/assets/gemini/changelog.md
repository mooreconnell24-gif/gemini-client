# Gemini 3.3.0

## The rework

- A smooth font in every menu and HUD widget.
- Ten colour themes for the menus and the HUD.
- 239 modules, one per job, with plain names.
- A new home screen, a pause-menu bar, and reworked module menus, HUD, guide and tool screens.
- Configs from 3.2.0 and from earlier 3.3.0 builds are upgraded when they are loaded.

## Look

- **Font.** Gemini's menus, HUD and notifications are drawn in Inter, a clean sans-serif, in five sizes and three weights. ClickGUI > **Font** switches back to the Minecraft font; layouts are the same in both.
- **Themes.** Gemini, Aurora, Ocean, Mint, Ember, Sunset, Rose, Violet, Mono and Midnight. Each is a pair of accent colours on its own dark surface, and it colours the menus, the home screen, the ArrayList, the Target HUD and notifications at once. Pick one from the swatches on the home screen or in ClickGUI > **Theme**; **Custom** uses your own colours. Old theme names from earlier configs are mapped to the nearest new one.
- **Motion.** Hover, press, opening and closing, switches and value changes are eased. ClickGUI > **Animations** switches all of it off.
- Menus over the game blur and dim what is behind them (ClickGUI > **Blur**).

## Menus and screens

- **Home screen.** The dark backdrop with the drifting network of points stays, now tinted by the theme. On it: the name in the large font, then Singleplayer, **Continue** (opens the world you played last), Multiplayer, Realms, Options and Quit, and four tiles for Modules, Mods, Guide and Search. A card in the corner lists the first points of this changelog and opens the guide. The footer has the version and the theme swatches; "Vanilla menu" and the sandbox switch are in the top-right corner.
- **Pause menu.** One bar along the top edge with Modules, Edit HUD, Mods, Guide and Panic. It does not move or replace any Minecraft button. A "key conflicts" entry appears only while two actions share a key.
- **Module menu.** Two layouts, chosen in ClickGUI > **Layout**. **Window** has a sidebar of categories, module cards and the settings of the selected module in place, plus pages for presets, key binds, Baritone and mods. **Panels** has one column per category with module rows that expand in place. Both have rounded cards, soft shadows and eased animation.
- **Guide, World backups, Notes and the Keybind report** share one window style: a rounded window with a title, text tabs and a thin scrollbar.
- **Quick search** (`Ctrl+K`, or `K` in game; rebind it in Controls). Finds modules, settings, commands and pages, including by their old names. **Enter** toggles a module, **Shift+Enter** opens it in the module menu, **Tab** switches between result kinds.
- **Setting controls.** Colour picker with a saturation/brightness square, hue and opacity sliders, hex input, copy and paste, and Static/Rainbow/Pulse. Hold Shift on a slider for fine steps, double-click it to type a value. Lists have icons, search and category filters. Modules with many settings show the main ones first and the rest under **More settings**.
- **HUD editor.** Widgets snap to the screen centre, to other widgets' edges and centres, and to equal gaps. Arrow keys nudge the selected widget by 1 px (Shift: 10 px).
- **Notices** shown while a screen is open (a finished backup, the keybind report, the sandbox switch) use Gemini's own card instead of the Minecraft toast.

## HUD

- **ArrayList** sits on the right edge, sorted by width, with a coloured edge bar; entries slide in and out. It is a widget you can move like the others.
- **Watermark** has three styles: Simple (the name with an accent first letter and the version), Bar (name, FPS, time and world on a small card) and Logo.
- **Target HUD** is a card with the target's head, name and an animated health bar.
- **Notifications** stack in the bottom-right corner with an icon and a bar that shows the time left. HUD > **Toast Position** moves them and HUD > **Toasts** switches them off.
- Potion timers, armour and the other widgets use the same cards and follow the theme. Widgets placed automatically never overlap: when they do not all fit, they shrink a step.
- New widgets: Day Info, Spawn Info, Session Stats, Weather Forecast, Mob Caps, Ender Chest Preview and Pinned Notes.

## Modules

- The list went from 316 modules to 239. Modules that did nearly the same thing are now one module with a mode or a setting, and names are plain Title Case ("Kill Aura", "Trigger Bot", "Auto Totem"). Searching for an old name, or using it as a command, still finds the module.
- Merged, for example: Offhand into Auto Totem; AutoGapple, SmartEat and AutoSoup into Auto Eat; AutoClicker into Trigger Bot; Glide into Flight; Strafe into Speed; Eagle into Safe Walk; InstantMine and PacketMine into Fast Break; Fast Use into Fast Place; Time, Weather, Gamemode, Seed and World Rules into World Tools; AntiBlind, NoBob and NoHurtCam into No Render; Tower into Scaffold. The README has the full table.
- Removed: modules that only made sense against other players or a remote server, and ones that duplicated a vanilla command.
- **Ambience** (new) changes the time of day and the weather you see without changing the world.
- **Zoom** scrolls to zoom and slows the mouse with the zoom level. **Jesus** defaults to Solid. **Surround** closes the whole box around you.

## Aim and targeting

- Every module that picks a target (Kill Aura, Aim Assist, Trigger Bot, Bow Aimbot, Silly Aimbot, Auto Crystal, Anchor Aura, Bed Aura, Auto Trap, Web Aura, Hole Filler, Auto City, Hole Push, Hit Crystal, Shield Breaker, Keep Range, Target Strafe, Auto Block, Flamethrower and Follow Bot) has the same settings: **Priority** (Closest, Lowest Health, Highest Health, Closest To Crosshair, Most Armor, Least Armor, Most Dangerous), **Prefer** (Any, Players First, Mobs First), **Stick To Target**, and filters for players, hostiles, passives and invisible mobs.
- **Target Box** draws an outline, a fill or both around the current target, in a fixed colour or coloured by health, optionally through walls.
- **Bow Aimbot** (was ProjectileAimbot; your settings carry over). Aims bows, crossbows and tridents using the game's drag and gravity, leads moving targets and accounts for your own movement. Settings: Low, High or Auto arc; **Aim Point** (Head, Chest, Feet or Auto); **Wall Check**; a drawn trajectory with an impact marker; **Auto Release** and **Auto Charge**.
- **Silly Aimbot** (new). Your arrows and tridents, and optionally snowballs, eggs and ender pearls, steer toward the target and around blocks and mobs in the way. Speed, turn rate, look-ahead, maximum time and retargeting are adjustable, and the path is drawn. It only works in your own single-player world, because it moves the projectiles on the integrated server.

## World tools

- **World Backup** saves a zip copy of the open world in the background, on a timer or when you ask, and keeps the newest few. The **World backups** screen lists them and restores one as a new world; the original is never overwritten.
- **World Notes** (`N`) opens a notes pad saved inside the world. Lines starting with `[ ]` are to-dos, and the **Pinned Notes** widget shows them on the HUD.
- **Measure** gives the distance, size and block count between two blocks you middle-click.
- **Mods.** A page of the module menu, also on the home screen and the pause bar, that searches Modrinth for mods and resource packs for your game version. Mods are downloaded with the mods they need and load the next time you start Minecraft; resource packs can be switched on straight away. It also lists what is installed, checks for updates, and can switch a mod off or remove it.

## Find and share

- **Share codes.** "Copy share code" in Presets, or `.config share <name>`, copies your settings as one line starting with `gemini:`. "Paste share code", or `.config import code`, lists what would change before you save or apply it.
- **Key-bind warnings.** Binding a key that a module or a vanilla control already uses shows a warning. The first time you enter a world each session, one notification lists any existing conflicts.
- **This guide.** What's new, the controls of both menu layouts, every command, and an About page. It opens by itself once after an update; afterwards from the home screen, the pause bar, the module menu, `.help` and `.changelog`.

## Config upgrade

- Settings and key binds of renamed and merged modules move to the module that replaced them. A backup of the config file is kept next to it.
- Renamed settings and mode values (for example the Kill Aura priorities and the theme names) are mapped to their new names.
- Colours have saturation, brightness, opacity and a Rainbow or Pulse mode. `.<module> <setting> #RRGGBB` sets one by hex code.

## Fixes

- Switching a bot module off cancelled any Baritone task, even one started by another module. Each module now stops only its own task.
- Timer and Tick Freeze could leave the world fast or frozen after leaving it or opening it to LAN. The tick rate is now always restored.
- Auto Totem kept the offhand reserved at low health with no totem left, blocking other offhand modules.
- Flight could leave survival flight on after a game-mode change; Auto Walk could leave sprint held; Freecam kept the previous dimension's position.
- Module suffixes showed "0,55" on systems that use a decimal comma.
- Mob Caps counted against the wrong cap, and Weather Forecast showed the same time for rain and thunder.
- Placing modules check that your aim hits the block face before placing (**Raycast Check**, on by default) and can turn gradually (**Aim Speed**).
- Modules that changed video settings (Fullbright, Performance, No Render) could save their temporary values to your options file.

## New modules

| Module | What it does |
|---|---|
| Silly Aimbot | Steers your arrows and tridents around obstacles toward the target |
| Auto City | Mines the surround block of a target in a hole, optionally placing a crystal first |
| Hit Crystal | After a sword hit: places obsidian and a crystal, then detonates it |
| Hole Push | Pushes a target out of its hole with a piston and redstone |
| Auto Regear | Places a shulker box, refills items to set counts, then mines the box |
| Multi Task | Attack or mine while eating or using an item |
| Chams | Shows entities through walls: Flat, Textured (outline), Wireframe or Pulse |
| Break ESP | Progress boxes and percentages on blocks being mined |
| View Model | Moves, scales and rotates the items in your hands |
| Pop Chams | Shows a fading copy of a player where they popped a totem |
| Totem Pop Counter | Counts totem pops per player (chat, notification, Nametags, Target HUD) |
| Auto Build Bot | Builds a nether portal, wither, wall, platform, pillar or bridge from your inventory |
| Ambience | Changes the time of day and weather you see, not the world's |
| Measure | Distance, size and block count between two middle-clicked blocks |
| World Backup | Zips the open world in the background and restores copies as new worlds |
| World Notes | A notes pad saved inside the world; `[ ]` lines are to-dos |
| Pinned Notes | HUD: your open to-dos from World Notes |
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
