package com.inferno.fabric.checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every module of LiquidBounce (nextgen, from its ModuleManager) and the Inferno checks that catch it.
 *
 * <p>A server only sees the packets a client sends. Modules that change nothing in those packets (ESP,
 * FullBright, Zoom and the rest of the render category, chat/GUI helpers) cannot be detected by any
 * server-side anti-cheat; they are listed as CLIENT_ONLY with the reason. The unit test
 * LiquidBounceCoverageTest checks that all 228 modules are listed and that every check id exists.
 */
public final class LiquidBounceCoverage {
   public enum Status {
      /** Inferno has checks aimed at what this module does. */
      DETECTED,
      /** Only some modes / side effects are visible to the server. */
      PARTIAL,
      /** Changes nothing the server can see. */
      CLIENT_ONLY,
      /** Visible in theory, but not checked (see note). */
      NOT_COVERED
   }

   public record Entry(String module, String category, Status status, List<String> checks, String note) {
   }

   private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
   /** Checks aimed at the client as a whole rather than one module. */
   public static final List<String> CLIENT_WIDE_CHECKS = List.of("client-brand");

   private LiquidBounceCoverage() {
   }

   private static void put(String category, String module, Status status, String checks, String note) {
      List<String> ids = checks.isEmpty() ? List.of() : List.of(checks.split(","));
      ENTRIES.put(module.toLowerCase(Locale.ROOT), new Entry(module, category, status, ids, note));
   }

   private static void det(String category, String module, String checks) {
      put(category, module, Status.DETECTED, checks, "");
   }

   private static void part(String category, String module, String checks, String note) {
      put(category, module, Status.PARTIAL, checks, note);
   }

   private static void client(String category, String module, String note) {
      put(category, module, Status.CLIENT_ONLY, "", note);
   }

   private static void none(String category, String module, String note) {
      put(category, module, Status.NOT_COVERED, "", note);
   }

   public static List<Entry> all() {
      return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
   }

   /** Case-insensitive lookup, e.g. "killaura". */
   public static Entry find(String module) {
      return module == null ? null : ENTRIES.get(module.toLowerCase(Locale.ROOT));
   }

   static {
      String c = "combat";
      det(c, "Aimbot", "aim-gcd,aim-snap,aim-smooth,aim-pattern,aim-silent,killaura-angle");
      det(c, "AutoArmor", "inventory-input,inventory-bot,inventory-move");
      det(c, "AutoBow", "auto-bow,aim-snap,aim-gcd");
      det(c, "AutoClicker", "autoclicker,autoclicker-consistency,tick-attacks");
      client(c, "AutoLeave", "Disconnects at low health; to the server that is an ordinary quit.");
      det(c, "AutoRod", "hotbar-swap,aim-snap");
      det(c, "AutoShoot", "auto-bow,aim-snap,aim-gcd");
      part(c, "AutoWeapon", "hotbar-swap", "Its silent-switch mode is caught; a plain switch to the best weapon before a hit is something players do too.");
      det(c, "Backtrack", "reach,hitbox,blink");
      det(c, "Criticals", "criticals,criticals-b,ground-spoof,multi-move");
      det(c, "CrystalAura", "crystal-aura,tick-interact,hotbar-swap,aim-snap");
      det(c, "ElytraTarget", "elytra-fly,elytra-speed,aim-snap,killaura-angle");
      det(c, "FakeLag", "blink,tick-timer,ping-spoof");
      det(c, "Hitbox", "hitbox,reach,killaura-angle");
      det(c, "KeepSprint", "killaura-sprint,sprint-reset");
      det(c, "KillAura", "killaura-angle,killaura-multi,killaura-walls,hitbox,reach,aim-snap,aim-silent,autoblock,noswing,multi-angle");
      det(c, "MaceKill", "mace-kill,multi-move,motion-a");
      part(c, "NoMissCooldown", "autoclicker,autoclicker-consistency", "A missed swing is never sent, so only the resulting extra clicks show.");
      det(c, "SpearKill", "reach,multi-move,speed,invalid-motion");
      det(c, "SuperKnockback", "sprint-reset,killaura-sprint");
      det(c, "SwordBlock", "autoblock");
      det(c, "TickBase", "tick-timer,timer,timer-b,blink");
      det(c, "TimerRange", "tick-timer,timer,timer-b");
      det(c, "TpAura", "multi-move,reach,speed,invalid-motion");
      det(c, "Velocity", "velocity");

      String e = "exploit";
      det(e, "AbortBreaking", "dig-packets");
      det(e, "AntiHunger", "sprint-invalid,ground-spoof,nofall");
      client(e, "AntiReducedDebugInfo", "Shows the F3 coordinates the server hides; the client already has them.");
      det(e, "ClickTp", "multi-move,invalid-motion,speed,clip");
      det(e, "Clip", "clip,phase,multi-move");
      det(e, "Damage", "multi-move,motion-a,invalid-motion");
      part(e, "Disabler", "multi-move,packet-flood,bad-state,ping-spoof,tick-timer", "Each mode targets one specific anti-cheat; the packet tricks most modes use are caught.");
      part(e, "Dupe", "packet-flood,inventory-bot", "Dupes need a server bug; on a server without the bug there is nothing to dupe.");
      det(e, "ExtendedFirework", "elytra-speed,elytra-fly");
      det(e, "GhostHand", "interact-walls,break-aim");
      client(e, "Kick", "Only gets the user's own client kicked.");
      det(e, "MoreCarry", "more-carry");
      det(e, "MultiActions", "autoblock,tick-attacks,bad-state");
      client(e, "NameCollector", "Reads player names the client already receives.");
      det(e, "NoPitchLimit", "invalid-pitch,badpackets");
      det(e, "Phase", "phase,clip,multi-move");
      det(e, "PingSpoof", "ping-spoof,blink");
      client(e, "Plugins", "Only asks the server for its command list, like tab-completion does.");
      client(e, "PortalMenu", "Lets the client open screens inside portals; nothing is sent.");
      det(e, "ResetVL", "multi-move,fly-prediction,jump-input");
      det(e, "ServerCrasher", "packet-flood,invalid-motion,multi-move,book-spam");
      det(e, "SleepWalker", "bad-state");
      det(e, "TimeShift", "tick-timer,timer,blink");
      det(e, "VehicleOneHit", "tick-attacks");
      client(e, "YggdrasilSignatureFix", "Changes how the client checks Mojang signatures.");

      String m = "misc";
      client(m, "AntiBot", "Filters which players the client's own modules target.");
      client(m, "AntiCheatDetect", "Guesses the server's anti-cheat from what it sends.");
      client(m, "AntiStaff", "Watches the tab list for staff names.");
      client(m, "AutoAccount", "Types /register and /login; the server sees normal commands.");
      client(m, "AutoChatGame", "Answers chat games; one correct answer looks like a fast typist.");
      client(m, "AutoConfig", "Downloads settings for the client itself.");
      det(m, "AutoPearl", "hotbar-swap,aim-snap,aim-gcd");
      client(m, "BetterChat", "Chat display changes only.");
      client(m, "BetterTab", "Tab list display changes only.");
      det(m, "BookBot", "book-spam");
      client(m, "DebugRecorder", "Records data for the developers.");
      det(m, "EasyPearl", "aim-snap,aim-gcd");
      det(m, "ElytraSwap", "inventory-input,inventory-bot,inventory-move");
      client(m, "FlagCheck", "Tells the cheater when an anti-cheat pulled them back.");
      client(m, "GUICloser", "Closes screens the server opened; closing a screen is normal.");
      client(m, "InventoryTracker", "Remembers other players' items on the client.");
      det(m, "ItemScroller", "inventory-bot,inventory-clean");
      client(m, "Macros", "Sends commands bound to keys; the server sees normal commands.");
      client(m, "MiddleClickAction", "Middle-click shortcuts that end in normal actions.");
      client(m, "NameProtect", "Hides names on the client's screen.");
      client(m, "Notifier", "Client-side notifications.");
      client(m, "PacketLogger", "Logs packets on the client.");
      client(m, "ReportHelper", "Helps write reports.");
      det(m, "Spammer", "chat-spam,packet-flood");
      client(m, "TargetLock", "Chooses targets for the client's own modules.");
      client(m, "Teams", "Chooses teammates for the client's own modules.");
      client(m, "TextFieldProtect", "Protects the client's text fields.");

      String v = "movement";
      det(v, "AirJump", "fly-prediction,fly-gravity,motion-a,high-jump");
      det(v, "Anchor", "reverse-step,fly-prediction");
      none(v, "AntiBounce", "Not bouncing on slime or beds is what sneaking does in vanilla.");
      det(v, "AntiLevitation", "anti-levitation");
      part(v, "AutoDodge", "input-move,speed-prediction", "Dodges that use normal movement keys look like a player dodging.");
      det(v, "AvoidHazards", "jesus,ground-spoof,fly-prediction");
      none(v, "BlockBounce", "Slime and bed bounces are exempt from movement checks, so bigger bounces aren't judged.");
      det(v, "BlockWalk", "noweb,fly-prediction,ground-spoof");
      det(v, "ElytraFly", "elytra-fly,elytra-speed");
      part(v, "ElytraRecast", "elytra-fly", "Re-opening the elytra is legal; the flight it allows is checked.");
      det(v, "EntityControl", "vehicle-speed,vehicle-steer");
      det(v, "Fly", "fly,fly-gravity,fly-prediction,flight-permission,motion-a,packet-fly");
      det(v, "Freeze", "blink,tick-timer");
      det(v, "HighJump", "high-jump,motion-a,fly-prediction");
      det(v, "InventoryMove", "inventory-input,inventory-move,more-carry");
      det(v, "LiquidWalk", "jesus,liquid-speed");
      det(v, "LongJump", "speed-air,speed-prediction,strafe,jump-input,motion-a");
      det(v, "NoClip", "phase,clip");
      det(v, "NoJumpDelay", "jump-delay");
      part(v, "NoPose", "phase", "Standing where vanilla forces crawling is caught as moving inside blocks.");
      part(v, "NoPush", "velocity", "Knockback is checked; entity and water pushes are too small to measure reliably.");
      det(v, "NoSlow", "noslow,sneak-speed,noweb");
      det(v, "NoWeb", "noweb");
      det(v, "Parkour", "jump-input");
      det(v, "ReverseStep", "reverse-step");
      det(v, "SafeWalk", "safe-walk");
      client(v, "SnapTap", "Only decides which of two opposite keys wins on the client.");
      det(v, "Sneak", "sneak-speed");
      det(v, "Speed", "speed,speed-air,speed-average,speed-prediction,simulation,jump-input,input-move");
      det(v, "Spider", "spider,fly-prediction");
      det(v, "Sprint", "sprint-invalid,omnisprint");
      det(v, "Step", "step,motion-a");
      det(v, "Strafe", "strafe,speed-air,input-move");
      det(v, "TargetStrafe", "input-move,speed-air,strafe");
      det(v, "Teleport", "multi-move,invalid-motion,speed,clip");
      det(v, "TerrainSpeed", "speed,speed-prediction,noweb");
      det(v, "TridentBoost", "riptide-speed");
      det(v, "VehicleBoost", "vehicle-speed,boat-fly");
      det(v, "VehicleControl", "vehicle-speed,boat-fly,vehicle-steer");

      String p = "player";
      client(p, "AntiAFK", "Random walking and turning looks like a player.");
      client(p, "AntiExploit", "Protects the client from server exploits.");
      det(p, "AntiVoid", "motion-a,fly-prediction,multi-move,invalid-motion");
      client(p, "AutoBreak", "Holds the attack key; mining speed is still checked.");
      det(p, "AutoBuff", "hotbar-swap,tick-interact,fast-use");
      det(p, "AutoCrafter", "inventory-bot");
      det(p, "AutoDeposit", "inventory-bot,inventory-fastclick,inventory-input");
      det(p, "AutoFish", "autofish");
      client(p, "AutoQueue", "Joins minigame queues with normal commands.");
      det(p, "AutoRespawn", "auto-respawn");
      det(p, "AutoShop", "inventory-bot,inventory-fastclick");
      client(p, "AutoWalk", "Holds the forward key.");
      det(p, "AutoWindCharge", "hotbar-swap,aim-snap");
      det(p, "Blink", "blink,tick-timer");
      det(p, "ChestCleaner", "inventory-bot,inventory-clean");
      det(p, "ChestStealer", "inventory-fastclick,inventory-clean,inventory-bot,inventory-input");
      none(p, "Eagle", "Sneaking at block edges is what careful bridgers do; Scaffold's other traces are checked.");
      det(p, "FastExp", "tick-interact");
      det(p, "FastUse", "fast-use,tick-interact");
      det(p, "InventoryCleaner", "inventory-bot,inventory-clean,inventory-input");
      client(p, "NoBlockInteract", "Stops the client opening blocks; nothing extra is sent.");
      client(p, "NoEntityInteract", "Stops the client interacting with entities; nothing extra is sent.");
      det(p, "NoFall", "nofall,ground-spoof,packet-fly");
      none(p, "NoRotateSet", "Only shows when a server force-turns the player, which Inferno doesn't do.");
      none(p, "NoSlotSet", "Only shows when a server changes the selected slot, which Inferno doesn't do.");
      det(p, "Offhand", "autototem,inventory-bot,inventory-input");
      client(p, "PotionSpoof", "Shows fake effects on the client.");
      det(p, "Reach", "reach,block-reach,attribute-tamper");
      det(p, "Replenish", "inventory-bot,inventory-input");
      part(p, "SmartEat", "hotbar-swap", "Eating is normal; its silent slot switching is caught.");

      String r = "render";
      String[] renderOnly = new String[]{
         "AntiBlind", "Aspect", "AutoF5", "BedPlates", "BetterInventory", "BlockESP", "BlockOutline", "Breadcrumbs", "CameraClip", "Chams",
         "ClickGui", "CombineMobs", "Crosshair", "CrystalView", "CustomAmbience", "DamageParticles", "Debug", "ESP", "FreeLook", "FullBright",
         "Hats", "HitFX", "HoleESP", "Hud", "ItemChams", "ItemESP", "ItemTags", "JumpEffect", "LogoffSpot", "MobOwners", "MurderMystery",
         "Nametags", "NewChunks", "NoBob", "NoFov", "NoHurtCam", "Particles", "PotionFX", "ProphuntESP", "ProtectionZones",
         "QuickPerspectiveSwap", "Radar", "SkinChanger", "SmoothCamera", "StorageESP", "TNTTimer", "TotemEffect", "Tracers", "Trajectories",
         "TrueSight", "VoidESP", "Wings", "Zoom"
      };

      for (String name : renderOnly) {
         client(r, name, "Only changes what the client draws.");
      }

      part(r, "FreeCam", "block-reach,interact-walls,break-aim", "Looking around is invisible; reaching or breaking from the camera's position is caught.");
      det(r, "NoSwing", "noswing,noswing-mining");
      det(r, "SilentHotbar", "hotbar-swap");
      part(r, "XRay", "xray", "Seeing ores is invisible; finding far more diamonds/debris than mining normally would is flagged.");

      String w = "world";
      det(w, "AirPlace", "airplace,liquid-place");
      det(w, "AutoBuild", "fastplace,tick-interact,scaffold-face");
      client(w, "AutoDisable", "Switches the client's own modules off.");
      det(w, "AutoFarm", "break-aim,nuker,fastplace");
      part(w, "AutoTool", "hotbar-swap", "Its silent mode is caught; switching to the right tool is also what players do.");
      det(w, "AutoTrap", "tick-interact,fastplace,scaffold-face,block-reach");
      det(w, "BedDefender", "tick-interact,fastplace,scaffold-face");
      det(w, "BlockIn", "tick-interact,fastplace");
      det(w, "BlockTrap", "tick-interact,fastplace,scaffold-face");
      part(w, "Extinguish", "hotbar-swap,aim-snap", "Putting a fire out is normal; the silent switch and snap rotation are caught.");
      det(w, "FastBreak", "fast-break,break-progress,dig-packets");
      det(w, "FastPlace", "fastplace,tick-interact");
      det(w, "Fucker", "break-aim,nuker,block-reach,interact-walls");
      det(w, "HoleFiller", "tick-interact,fastplace,block-reach");
      det(w, "LiquidFiller", "tick-interact,fastplace,liquid-place");
      det(w, "LiquidPlace", "liquid-place");
      client(w, "NoInterpolation", "Changes how the client draws other entities.");
      det(w, "NoSlowBreak", "break-progress");
      det(w, "Nuker", "nuker,break-aim,dig-packets,block-reach");
      det(w, "PacketMine", "dig-packets,noswing-mining,break-aim");
      det(w, "ProjectilePuncher", "hitbox,killaura-angle,aim-snap,reach");
      det(w, "Scaffold", "scaffold-face,scaffold-rot,tower,airplace,safe-walk,fastplace,hotbar-swap");
      client(w, "StrongholdFinder", "Works out stronghold positions from thrown eyes on the client.");
      det(w, "Surround", "tick-interact,fastplace");
      det(w, "Timer", "timer,timer-b,tick-timer");
   }
}
