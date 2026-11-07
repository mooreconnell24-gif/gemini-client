# LiquidBounce module coverage

Generated from `LiquidBounceCoverage.java` by `LiquidBounceCoverageTest` (run `./gradlew test -DupdateDocs=true` to refresh).

Every module in LiquidBounce's `ModuleManager` (nextgen branch, 228 modules), and the Inferno checks that catch it.

| Status | Modules |
|---|---|
| Detected | 117 |
| Partly detected | 13 |
| Client-only (no server can see it) | 93 |
| Not covered | 5 |

A server only sees the packets a client sends. Render modules (ESP, FullBright, Zoom...) and client-side helpers don't change them, so no server-side anti-cheat can detect them.

Checks on the client as a whole: Client (Brand/Mods) (`client-brand`) looks for the LiquidBounce brand and cheat-client mod channels when a player joins.

## Combat

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| Aimbot | Detected | Aim (GCD) (`aim-gcd`), Aim (Snap) (`aim-snap`), Aim (Smooth) (`aim-smooth`), Aim (Robotic) (`aim-pattern`), Aim (Silent) (`aim-silent`), KillAura (Angle) (`killaura-angle`) |  |
| AutoArmor | Detected | InvMove (Keys) (`inventory-input`), Inventory (Bot Timing) (`inventory-bot`), Inventory (Move) (`inventory-move`) |  |
| AutoBow | Detected | AutoBow (`auto-bow`), Aim (Snap) (`aim-snap`), Aim (GCD) (`aim-gcd`) |  |
| AutoClicker | Detected | AutoClicker (`autoclicker`), AutoClicker (Consistency) (`autoclicker-consistency`), Attacks (Per Tick) (`tick-attacks`) |  |
| AutoLeave | Client-only |  | Disconnects at low health; to the server that is an ordinary quit. |
| AutoRod | Detected | Hotbar (Silent Swap) (`hotbar-swap`), Aim (Snap) (`aim-snap`) |  |
| AutoShoot | Detected | AutoBow (`auto-bow`), Aim (Snap) (`aim-snap`), Aim (GCD) (`aim-gcd`) |  |
| AutoWeapon | Partly | Hotbar (Silent Swap) (`hotbar-swap`) | Its silent-switch mode is caught; a plain switch to the best weapon before a hit is something players do too. |
| Backtrack | Detected | Reach (`reach`), KillAura (Hitbox) (`hitbox`), Blink (`blink`) |  |
| Criticals | Detected | Criticals (`criticals`), Criticals B (`criticals-b`), Ground Spoof (`ground-spoof`), Packets (Multi-Move) (`multi-move`) |  |
| CrystalAura | Detected | CrystalAura (`crystal-aura`), Place/Use (Per Tick) (`tick-interact`), Hotbar (Silent Swap) (`hotbar-swap`), Aim (Snap) (`aim-snap`) |  |
| ElytraTarget | Detected | ElytraFly (`elytra-fly`), Elytra Speed (`elytra-speed`), Aim (Snap) (`aim-snap`), KillAura (Angle) (`killaura-angle`) |  |
| FakeLag | Detected | Blink (`blink`), Timer (Ticks) (`tick-timer`), Ping Spoof (`ping-spoof`) |  |
| Hitbox | Detected | KillAura (Hitbox) (`hitbox`), Reach (`reach`), KillAura (Angle) (`killaura-angle`) |  |
| KeepSprint | Detected | KillAura (Sprint) (`killaura-sprint`), SuperKnockback (`sprint-reset`) |  |
| KillAura | Detected | KillAura (Angle) (`killaura-angle`), KillAura (Multi) (`killaura-multi`), KillAura (Walls) (`killaura-walls`), KillAura (Hitbox) (`hitbox`), Reach (`reach`), Aim (Snap) (`aim-snap`), Aim (Silent) (`aim-silent`), AutoBlock (`autoblock`), NoSwing (`noswing`), Multi Angle (`multi-angle`) |  |
| MaceKill | Detected | MaceKill (`mace-kill`), Packets (Multi-Move) (`multi-move`), Motion A (`motion-a`) |  |
| NoMissCooldown | Partly | AutoClicker (`autoclicker`), AutoClicker (Consistency) (`autoclicker-consistency`) | A missed swing is never sent, so only the resulting extra clicks show. |
| SpearKill | Detected | Reach (`reach`), Packets (Multi-Move) (`multi-move`), Speed (Limit) (`speed`), Invalid Motion (`invalid-motion`) |  |
| SuperKnockback | Detected | SuperKnockback (`sprint-reset`), KillAura (Sprint) (`killaura-sprint`) |  |
| SwordBlock | Detected | AutoBlock (`autoblock`) |  |
| TickBase | Detected | Timer (Ticks) (`tick-timer`), Timer (`timer`), Timer B (`timer-b`), Blink (`blink`) |  |
| TimerRange | Detected | Timer (Ticks) (`tick-timer`), Timer (`timer`), Timer B (`timer-b`) |  |
| TpAura | Detected | Packets (Multi-Move) (`multi-move`), Reach (`reach`), Speed (Limit) (`speed`), Invalid Motion (`invalid-motion`) |  |
| Velocity | Detected | Velocity (Anti-KB) (`velocity`) |  |

## Exploit

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AbortBreaking | Detected | Packets (Digging) (`dig-packets`) |  |
| AntiHunger | Detected | Sprint (Invalid) (`sprint-invalid`), Ground Spoof (`ground-spoof`), NoFall (`nofall`) |  |
| AntiReducedDebugInfo | Client-only |  | Shows the F3 coordinates the server hides; the client already has them. |
| ClickTp | Detected | Packets (Multi-Move) (`multi-move`), Invalid Motion (`invalid-motion`), Speed (Limit) (`speed`), Clip (`clip`) |  |
| Clip | Detected | Clip (`clip`), Phase (`phase`), Packets (Multi-Move) (`multi-move`) |  |
| Damage | Detected | Packets (Multi-Move) (`multi-move`), Motion A (`motion-a`), Invalid Motion (`invalid-motion`) |  |
| Disabler | Partly | Packets (Multi-Move) (`multi-move`), Packets (Flood) (`packet-flood`), BadPackets (State) (`bad-state`), Ping Spoof (`ping-spoof`), Timer (Ticks) (`tick-timer`) | Each mode targets one specific anti-cheat; the packet tricks most modes use are caught. |
| Dupe | Partly | Packets (Flood) (`packet-flood`), Inventory (Bot Timing) (`inventory-bot`) | Dupes need a server bug; on a server without the bug there is nothing to dupe. |
| ExtendedFirework | Detected | Elytra Speed (`elytra-speed`), ElytraFly (`elytra-fly`) |  |
| GhostHand | Detected | Interact (Walls) (`interact-walls`), Break (Aim/Walls) (`break-aim`) |  |
| Kick | Client-only |  | Only gets the user's own client kicked. |
| MoreCarry | Detected | MoreCarry (`more-carry`) |  |
| MultiActions | Detected | AutoBlock (`autoblock`), Attacks (Per Tick) (`tick-attacks`), BadPackets (State) (`bad-state`) |  |
| NameCollector | Client-only |  | Reads player names the client already receives. |
| NoPitchLimit | Detected | Invalid Pitch (`invalid-pitch`), BadPackets (Rotation) (`badpackets`) |  |
| Phase | Detected | Phase (`phase`), Clip (`clip`), Packets (Multi-Move) (`multi-move`) |  |
| PingSpoof | Detected | Ping Spoof (`ping-spoof`), Blink (`blink`) |  |
| Plugins | Client-only |  | Only asks the server for its command list, like tab-completion does. |
| PortalMenu | Client-only |  | Lets the client open screens inside portals; nothing is sent. |
| ResetVL | Detected | Packets (Multi-Move) (`multi-move`), Fly (Prediction) (`fly-prediction`), Jump (No Key) (`jump-input`) |  |
| ServerCrasher | Detected | Packets (Flood) (`packet-flood`), Invalid Motion (`invalid-motion`), Packets (Multi-Move) (`multi-move`), BookBot (`book-spam`) |  |
| SleepWalker | Detected | BadPackets (State) (`bad-state`) |  |
| TimeShift | Detected | Timer (Ticks) (`tick-timer`), Timer (`timer`), Blink (`blink`) |  |
| VehicleOneHit | Detected | Attacks (Per Tick) (`tick-attacks`) |  |
| YggdrasilSignatureFix | Client-only |  | Changes how the client checks Mojang signatures. |

## Misc

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AntiBot | Client-only |  | Filters which players the client's own modules target. |
| AntiCheatDetect | Client-only |  | Guesses the server's anti-cheat from what it sends. |
| AntiStaff | Client-only |  | Watches the tab list for staff names. |
| AutoAccount | Client-only |  | Types /register and /login; the server sees normal commands. |
| AutoChatGame | Client-only |  | Answers chat games; one correct answer looks like a fast typist. |
| AutoConfig | Client-only |  | Downloads settings for the client itself. |
| AutoPearl | Detected | Hotbar (Silent Swap) (`hotbar-swap`), Aim (Snap) (`aim-snap`), Aim (GCD) (`aim-gcd`) |  |
| BetterChat | Client-only |  | Chat display changes only. |
| BetterTab | Client-only |  | Tab list display changes only. |
| BookBot | Detected | BookBot (`book-spam`) |  |
| DebugRecorder | Client-only |  | Records data for the developers. |
| EasyPearl | Detected | Aim (Snap) (`aim-snap`), Aim (GCD) (`aim-gcd`) |  |
| ElytraSwap | Detected | InvMove (Keys) (`inventory-input`), Inventory (Bot Timing) (`inventory-bot`), Inventory (Move) (`inventory-move`) |  |
| FlagCheck | Client-only |  | Tells the cheater when an anti-cheat pulled them back. |
| GUICloser | Client-only |  | Closes screens the server opened; closing a screen is normal. |
| InventoryTracker | Client-only |  | Remembers other players' items on the client. |
| ItemScroller | Detected | Inventory (Bot Timing) (`inventory-bot`), Inventory Clean (`inventory-clean`) |  |
| Macros | Client-only |  | Sends commands bound to keys; the server sees normal commands. |
| MiddleClickAction | Client-only |  | Middle-click shortcuts that end in normal actions. |
| NameProtect | Client-only |  | Hides names on the client's screen. |
| Notifier | Client-only |  | Client-side notifications. |
| PacketLogger | Client-only |  | Logs packets on the client. |
| ReportHelper | Client-only |  | Helps write reports. |
| Spammer | Detected | Spammer (`chat-spam`), Packets (Flood) (`packet-flood`) |  |
| TargetLock | Client-only |  | Chooses targets for the client's own modules. |
| Teams | Client-only |  | Chooses teammates for the client's own modules. |
| TextFieldProtect | Client-only |  | Protects the client's text fields. |

## Movement

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AirJump | Detected | Fly (Prediction) (`fly-prediction`), Fly (Gravity) (`fly-gravity`), Motion A (`motion-a`), HighJump (`high-jump`) |  |
| Anchor | Detected | ReverseStep (`reverse-step`), Fly (Prediction) (`fly-prediction`) |  |
| AntiBounce | Not covered |  | Not bouncing on slime or beds is what sneaking does in vanilla. |
| AntiLevitation | Detected | AntiLevitation (`anti-levitation`) |  |
| AutoDodge | Partly | Move (No Keys) (`input-move`), Speed (Prediction) (`speed-prediction`) | Dodges that use normal movement keys look like a player dodging. |
| AvoidHazards | Detected | Jesus (`jesus`), Ground Spoof (`ground-spoof`), Fly (Prediction) (`fly-prediction`) |  |
| BlockBounce | Not covered |  | Slime and bed bounces are exempt from movement checks, so bigger bounces aren't judged. |
| BlockWalk | Detected | NoWeb (`noweb`), Fly (Prediction) (`fly-prediction`), Ground Spoof (`ground-spoof`) |  |
| ElytraFly | Detected | ElytraFly (`elytra-fly`), Elytra Speed (`elytra-speed`) |  |
| ElytraRecast | Partly | ElytraFly (`elytra-fly`) | Re-opening the elytra is legal; the flight it allows is checked. |
| EntityControl | Detected | Vehicle Speed (`vehicle-speed`), Vehicle Steer (`vehicle-steer`) |  |
| Fly | Detected | Fly (`fly`), Fly (Gravity) (`fly-gravity`), Fly (Prediction) (`fly-prediction`), Flight (Permission) (`flight-permission`), Motion A (`motion-a`), Packet Fly (`packet-fly`) |  |
| Freeze | Detected | Blink (`blink`), Timer (Ticks) (`tick-timer`) |  |
| HighJump | Detected | HighJump (`high-jump`), Motion A (`motion-a`), Fly (Prediction) (`fly-prediction`) |  |
| InventoryMove | Detected | InvMove (Keys) (`inventory-input`), Inventory (Move) (`inventory-move`), MoreCarry (`more-carry`) |  |
| LiquidWalk | Detected | Jesus (`jesus`), Speed (Liquid) (`liquid-speed`) |  |
| LongJump | Detected | Speed (Air) (`speed-air`), Speed (Prediction) (`speed-prediction`), Strafe (`strafe`), Jump (No Key) (`jump-input`), Motion A (`motion-a`) |  |
| NoClip | Detected | Phase (`phase`), Clip (`clip`) |  |
| NoJumpDelay | Detected | NoJumpDelay (`jump-delay`) |  |
| NoPose | Partly | Phase (`phase`) | Standing where vanilla forces crawling is caught as moving inside blocks. |
| NoPush | Partly | Velocity (Anti-KB) (`velocity`) | Knockback is checked; entity and water pushes are too small to measure reliably. |
| NoSlow | Detected | NoSlow (`noslow`), Sneak (NoSlow) (`sneak-speed`), NoWeb (`noweb`) |  |
| NoWeb | Detected | NoWeb (`noweb`) |  |
| Parkour | Detected | Jump (No Key) (`jump-input`) |  |
| ReverseStep | Detected | ReverseStep (`reverse-step`) |  |
| SafeWalk | Detected | SafeWalk (`safe-walk`) |  |
| SnapTap | Client-only |  | Only decides which of two opposite keys wins on the client. |
| Sneak | Detected | Sneak (NoSlow) (`sneak-speed`) |  |
| Speed | Detected | Speed (Limit) (`speed`), Speed (Air) (`speed-air`), Speed (Average) (`speed-average`), Speed (Prediction) (`speed-prediction`), Simulation (`simulation`), Jump (No Key) (`jump-input`), Move (No Keys) (`input-move`) |  |
| Spider | Detected | Spider (`spider`), Fly (Prediction) (`fly-prediction`) |  |
| Sprint | Detected | Sprint (Invalid) (`sprint-invalid`), Sprint (Omni) (`omnisprint`) |  |
| Step | Detected | Step (`step`), Motion A (`motion-a`) |  |
| Strafe | Detected | Strafe (`strafe`), Speed (Air) (`speed-air`), Move (No Keys) (`input-move`) |  |
| TargetStrafe | Detected | Move (No Keys) (`input-move`), Speed (Air) (`speed-air`), Strafe (`strafe`) |  |
| Teleport | Detected | Packets (Multi-Move) (`multi-move`), Invalid Motion (`invalid-motion`), Speed (Limit) (`speed`), Clip (`clip`) |  |
| TerrainSpeed | Detected | Speed (Limit) (`speed`), Speed (Prediction) (`speed-prediction`), NoWeb (`noweb`) |  |
| TridentBoost | Detected | TridentBoost (`riptide-speed`) |  |
| VehicleBoost | Detected | Vehicle Speed (`vehicle-speed`), Boat Fly (`boat-fly`) |  |
| VehicleControl | Detected | Vehicle Speed (`vehicle-speed`), Boat Fly (`boat-fly`), Vehicle Steer (`vehicle-steer`) |  |

## Player

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AntiAFK | Client-only |  | Random walking and turning looks like a player. |
| AntiExploit | Client-only |  | Protects the client from server exploits. |
| AntiVoid | Detected | Motion A (`motion-a`), Fly (Prediction) (`fly-prediction`), Packets (Multi-Move) (`multi-move`), Invalid Motion (`invalid-motion`) |  |
| AutoBreak | Client-only |  | Holds the attack key; mining speed is still checked. |
| AutoBuff | Detected | Hotbar (Silent Swap) (`hotbar-swap`), Place/Use (Per Tick) (`tick-interact`), Fast Use (`fast-use`) |  |
| AutoCrafter | Detected | Inventory (Bot Timing) (`inventory-bot`) |  |
| AutoDeposit | Detected | Inventory (Bot Timing) (`inventory-bot`), Inventory (Fast Click) (`inventory-fastclick`), InvMove (Keys) (`inventory-input`) |  |
| AutoFish | Detected | AutoFish (`autofish`) |  |
| AutoQueue | Client-only |  | Joins minigame queues with normal commands. |
| AutoRespawn | Detected | AutoRespawn (`auto-respawn`) |  |
| AutoShop | Detected | Inventory (Bot Timing) (`inventory-bot`), Inventory (Fast Click) (`inventory-fastclick`) |  |
| AutoWalk | Client-only |  | Holds the forward key. |
| AutoWindCharge | Detected | Hotbar (Silent Swap) (`hotbar-swap`), Aim (Snap) (`aim-snap`) |  |
| Blink | Detected | Blink (`blink`), Timer (Ticks) (`tick-timer`) |  |
| ChestCleaner | Detected | Inventory (Bot Timing) (`inventory-bot`), Inventory Clean (`inventory-clean`) |  |
| ChestStealer | Detected | Inventory (Fast Click) (`inventory-fastclick`), Inventory Clean (`inventory-clean`), Inventory (Bot Timing) (`inventory-bot`), InvMove (Keys) (`inventory-input`) |  |
| Eagle | Not covered |  | Sneaking at block edges is what careful bridgers do; Scaffold's other traces are checked. |
| FastExp | Detected | Place/Use (Per Tick) (`tick-interact`) |  |
| FastUse | Detected | Fast Use (`fast-use`), Place/Use (Per Tick) (`tick-interact`) |  |
| InventoryCleaner | Detected | Inventory (Bot Timing) (`inventory-bot`), Inventory Clean (`inventory-clean`), InvMove (Keys) (`inventory-input`) |  |
| NoBlockInteract | Client-only |  | Stops the client opening blocks; nothing extra is sent. |
| NoEntityInteract | Client-only |  | Stops the client interacting with entities; nothing extra is sent. |
| NoFall | Detected | NoFall (`nofall`), Ground Spoof (`ground-spoof`), Packet Fly (`packet-fly`) |  |
| NoRotateSet | Not covered |  | Only shows when a server force-turns the player, which Inferno doesn't do. |
| NoSlotSet | Not covered |  | Only shows when a server changes the selected slot, which Inferno doesn't do. |
| Offhand | Detected | AutoTotem (`autototem`), Inventory (Bot Timing) (`inventory-bot`), InvMove (Keys) (`inventory-input`) |  |
| PotionSpoof | Client-only |  | Shows fake effects on the client. |
| Reach | Detected | Reach (`reach`), Reach (Blocks) (`block-reach`), Attributes (Modded) (`attribute-tamper`) |  |
| Replenish | Detected | Inventory (Bot Timing) (`inventory-bot`), InvMove (Keys) (`inventory-input`) |  |
| SmartEat | Partly | Hotbar (Silent Swap) (`hotbar-swap`) | Eating is normal; its silent slot switching is caught. |

## Render

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AntiBlind | Client-only |  | Only changes what the client draws. |
| Aspect | Client-only |  | Only changes what the client draws. |
| AutoF5 | Client-only |  | Only changes what the client draws. |
| BedPlates | Client-only |  | Only changes what the client draws. |
| BetterInventory | Client-only |  | Only changes what the client draws. |
| BlockESP | Client-only |  | Only changes what the client draws. |
| BlockOutline | Client-only |  | Only changes what the client draws. |
| Breadcrumbs | Client-only |  | Only changes what the client draws. |
| CameraClip | Client-only |  | Only changes what the client draws. |
| Chams | Client-only |  | Only changes what the client draws. |
| ClickGui | Client-only |  | Only changes what the client draws. |
| CombineMobs | Client-only |  | Only changes what the client draws. |
| Crosshair | Client-only |  | Only changes what the client draws. |
| CrystalView | Client-only |  | Only changes what the client draws. |
| CustomAmbience | Client-only |  | Only changes what the client draws. |
| DamageParticles | Client-only |  | Only changes what the client draws. |
| Debug | Client-only |  | Only changes what the client draws. |
| ESP | Client-only |  | Only changes what the client draws. |
| FreeCam | Partly | Reach (Blocks) (`block-reach`), Interact (Walls) (`interact-walls`), Break (Aim/Walls) (`break-aim`) | Looking around is invisible; reaching or breaking from the camera's position is caught. |
| FreeLook | Client-only |  | Only changes what the client draws. |
| FullBright | Client-only |  | Only changes what the client draws. |
| Hats | Client-only |  | Only changes what the client draws. |
| HitFX | Client-only |  | Only changes what the client draws. |
| HoleESP | Client-only |  | Only changes what the client draws. |
| Hud | Client-only |  | Only changes what the client draws. |
| ItemChams | Client-only |  | Only changes what the client draws. |
| ItemESP | Client-only |  | Only changes what the client draws. |
| ItemTags | Client-only |  | Only changes what the client draws. |
| JumpEffect | Client-only |  | Only changes what the client draws. |
| LogoffSpot | Client-only |  | Only changes what the client draws. |
| MobOwners | Client-only |  | Only changes what the client draws. |
| MurderMystery | Client-only |  | Only changes what the client draws. |
| Nametags | Client-only |  | Only changes what the client draws. |
| NewChunks | Client-only |  | Only changes what the client draws. |
| NoBob | Client-only |  | Only changes what the client draws. |
| NoFov | Client-only |  | Only changes what the client draws. |
| NoHurtCam | Client-only |  | Only changes what the client draws. |
| NoSwing | Detected | NoSwing (`noswing`), NoSwing (Mining) (`noswing-mining`) |  |
| Particles | Client-only |  | Only changes what the client draws. |
| PotionFX | Client-only |  | Only changes what the client draws. |
| ProphuntESP | Client-only |  | Only changes what the client draws. |
| ProtectionZones | Client-only |  | Only changes what the client draws. |
| QuickPerspectiveSwap | Client-only |  | Only changes what the client draws. |
| Radar | Client-only |  | Only changes what the client draws. |
| SilentHotbar | Detected | Hotbar (Silent Swap) (`hotbar-swap`) |  |
| SkinChanger | Client-only |  | Only changes what the client draws. |
| SmoothCamera | Client-only |  | Only changes what the client draws. |
| StorageESP | Client-only |  | Only changes what the client draws. |
| TNTTimer | Client-only |  | Only changes what the client draws. |
| TotemEffect | Client-only |  | Only changes what the client draws. |
| Tracers | Client-only |  | Only changes what the client draws. |
| Trajectories | Client-only |  | Only changes what the client draws. |
| TrueSight | Client-only |  | Only changes what the client draws. |
| VoidESP | Client-only |  | Only changes what the client draws. |
| Wings | Client-only |  | Only changes what the client draws. |
| XRay | Partly | XRay (Ratio) (`xray`) | Seeing ores is invisible; finding far more diamonds/debris than mining normally would is flagged. |
| Zoom | Client-only |  | Only changes what the client draws. |

## World

| Module | Status | Inferno checks | Notes |
|---|---|---|---|
| AirPlace | Detected | AirPlace (`airplace`), LiquidPlace (`liquid-place`) |  |
| AutoBuild | Detected | FastPlace (`fastplace`), Place/Use (Per Tick) (`tick-interact`), Scaffold (Face) (`scaffold-face`) |  |
| AutoDisable | Client-only |  | Switches the client's own modules off. |
| AutoFarm | Detected | Break (Aim/Walls) (`break-aim`), Nuker (`nuker`), FastPlace (`fastplace`) |  |
| AutoTool | Partly | Hotbar (Silent Swap) (`hotbar-swap`) | Its silent mode is caught; switching to the right tool is also what players do. |
| AutoTrap | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`), Scaffold (Face) (`scaffold-face`), Reach (Blocks) (`block-reach`) |  |
| BedDefender | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`), Scaffold (Face) (`scaffold-face`) |  |
| BlockIn | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`) |  |
| BlockTrap | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`), Scaffold (Face) (`scaffold-face`) |  |
| Extinguish | Partly | Hotbar (Silent Swap) (`hotbar-swap`), Aim (Snap) (`aim-snap`) | Putting a fire out is normal; the silent switch and snap rotation are caught. |
| FastBreak | Detected | Fast Break (`fast-break`), FastBreak (Progress) (`break-progress`), Packets (Digging) (`dig-packets`) |  |
| FastPlace | Detected | FastPlace (`fastplace`), Place/Use (Per Tick) (`tick-interact`) |  |
| Fucker | Detected | Break (Aim/Walls) (`break-aim`), Nuker (`nuker`), Reach (Blocks) (`block-reach`), Interact (Walls) (`interact-walls`) |  |
| HoleFiller | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`), Reach (Blocks) (`block-reach`) |  |
| LiquidFiller | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`), LiquidPlace (`liquid-place`) |  |
| LiquidPlace | Detected | LiquidPlace (`liquid-place`) |  |
| NoInterpolation | Client-only |  | Changes how the client draws other entities. |
| NoSlowBreak | Detected | FastBreak (Progress) (`break-progress`) |  |
| Nuker | Detected | Nuker (`nuker`), Break (Aim/Walls) (`break-aim`), Packets (Digging) (`dig-packets`), Reach (Blocks) (`block-reach`) |  |
| PacketMine | Detected | Packets (Digging) (`dig-packets`), NoSwing (Mining) (`noswing-mining`), Break (Aim/Walls) (`break-aim`) |  |
| ProjectilePuncher | Detected | KillAura (Hitbox) (`hitbox`), KillAura (Angle) (`killaura-angle`), Aim (Snap) (`aim-snap`), Reach (`reach`) |  |
| Scaffold | Detected | Scaffold (Face) (`scaffold-face`), Scaffold (Rot) (`scaffold-rot`), Scaffold (Tower) (`tower`), AirPlace (`airplace`), SafeWalk (`safe-walk`), FastPlace (`fastplace`), Hotbar (Silent Swap) (`hotbar-swap`) |  |
| StrongholdFinder | Client-only |  | Works out stronghold positions from thrown eyes on the client. |
| Surround | Detected | Place/Use (Per Tick) (`tick-interact`), FastPlace (`fastplace`) |  |
| Timer | Detected | Timer (`timer`), Timer B (`timer-b`), Timer (Ticks) (`tick-timer`) |  |
