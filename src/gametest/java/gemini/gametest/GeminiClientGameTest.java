package gemini.gametest;

import gemini.Gemini;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.SelfTest;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.ui.SandboxUi;
import gemini.ui.clickgui.Menus;
import gemini.util.BaritoneBridge;
import gemini.util.BuiltInBaritone;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;

/**
 * Plays Gemini in a real client: creates a single-player world and runs every module, the pathfinder, the chat
 * commands, the menus and the sandbox switch, looking for crashed hooks, commands the server rejects and errors in
 * the log.
 *
 * <p>Everything found is written to {@code gemini-gametest-report.txt} in the run directory, and the test fails
 * when there is at least one problem. Screenshots of the screens it visits land in {@code screenshots/}.
 */
public final class GeminiClientGameTest implements FabricClientGameTest {
   /** Modules that reconfigure other modules or run the player's own commands; they would disturb the run. */
   private static final Set<String> NOT_RUN_ALONE = Set.of("Presets", "Macro 1", "Macro 2", "Macro 3", "Panic");
   /** Rejections that are expected: the command is right but had nothing to do in an empty test world. */
   private static final String[] HARMLESS_REJECTIONS = {
      "has no effects to remove",
      "no effects to remove",
      "Unable to apply this effect",
      "Nothing changed",
      "No entity was found",
      "No blocks were filled",
      "No player was found",
      "No items were found",
      "already",
      "Unknown or incomplete command",
      "Incorrect argument for command"
   };
   private final List<String> problems = new ArrayList<>();
   private final List<String> notes = new ArrayList<>();
   private int modulesRun;
   private int heldBackSeen;

   @Override
   public void runTest(ClientGameTestContext context) {
      context.waitTicks(5);
      context.takeScreenshot("gemini-01-title");
      context.setScreen(() -> SandboxUi.blockedScreen(Minecraft.getInstance(), new TitleScreen()));
      context.waitTicks(3);
      context.takeScreenshot("gemini-02-connection-refused");
      context.setScreen(TitleScreen::new);
      context.waitTicks(2);

      Path gameDir = context.computeOnClient(client -> client.gameDirectory.toPath());
      try (TestSingleplayerContext world = context.worldBuilder().create()) {
         world.getConnection().waitForChunksRender();
         world.getServer().runCommand("gamemode creative @a");
         context.waitTicks(20);

         boolean allowed = context.computeOnClient(client -> SingleplayerGate.allowed());
         if (!allowed) {
            this.problems.add("Gemini is not allowed to run in a fresh single-player world: " + context.computeOnClient(client -> SingleplayerGate.denialMessage()));
         }

         this.note("engine: " + context.computeOnClient(client -> BaritoneBridge.engineDetail()));
         this.phase("single-module pass", () -> this.runEveryModuleAlone(context, world));
         this.phase("self-test", () -> this.runSelfTest(context, world));
         this.phase("pathfinder", () -> this.runPathfinder(context, world));
         this.phase("critical hits", () -> this.runCriticalHits(context, world));
         this.phase("new modules", () -> this.runNewModules(context, world));
         this.phase("commands", () -> this.runCommands(context));
         this.phase("menus", () -> this.runMenus(context));
         this.phase("sandbox switch", () -> this.runSandboxSwitch(context));
      } catch (RuntimeException | Error e) {
         this.problems.add("the world could not be used to the end: " + e);
      }

      try {
         context.waitTicks(5);
      } catch (RuntimeException | Error e) {
         this.note("the client was no longer ticking at the end: " + e);
      }

      this.scanLog(gameDir);
      this.writeReport(gameDir);
      if (!this.problems.isEmpty()) {
         throw new AssertionError("Gemini game test found " + this.problems.size() + " problem(s):\n - " + String.join("\n - ", this.problems));
      }
   }

   /** Runs one part of the test; a failure in it is recorded and the next part still runs. */
   private void phase(String name, Runnable body) {
      try {
         body.run();
      } catch (RuntimeException | Error e) {
         StackTraceElement[] trace = e.getStackTrace();
         String where = "";
         for (StackTraceElement element : trace) {
            if (element.getClassName().startsWith("gemini.") && !element.getClassName().startsWith("gemini.gametest")) {
               where = " at " + element;
               break;
            }
         }

         this.problems.add(name + " threw " + e + where);
      }
   }

   private void note(String line) {
      this.notes.add(line);
   }

   private static ModuleManager modules() {
      return Gemini.getInstance().modules();
   }

   /** Collects what went wrong since the last call, labelled with what was being exercised. */
   private void collect(ClientGameTestContext context, String during) {
      List<ModuleManager.Failure> failures = context.computeOnClient(client -> ModuleManager.drainFailures());
      for (ModuleManager.Failure failure : failures) {
         this.problems.add(during + ": " + failure.module() + " crashed in " + failure.hook() + " (" + failure.error() + ")");
      }

      int heldBack = context.computeOnClient(client -> ModuleManager.heldBackPositionPackets());
      if (heldBack > this.heldBackSeen) {
         this.problems.add(during + ": " + (heldBack - this.heldBackSeen) + " extra position packet(s) had to be held back (Minecraft 26.3 disconnects for those)");
         this.heldBackSeen = heldBack;
      }

      for (String rejected : context.computeOnClient(client -> SpCommands.drainFailures())) {
         if (harmless(rejected)) {
            this.note(during + ": server declined /" + rejected);
         } else {
            this.problems.add(during + ": the server rejected /" + rejected);
         }
      }
   }

   private static boolean harmless(String rejection) {
      // World Rules sends each rule under every spelling it has had; the spellings this version dropped are meant to fail.
      if (rejection.startsWith("gamerule ")) {
         return true;
      }

      // A syntax error is reported in two lines: the reason, then the command with "<--[HERE]". Either marks a broken command.
      if (rejection.contains("<--[HERE]") || rejection.contains("Expected ") || rejection.contains("Invalid ") || rejection.contains("Can't find element")) {
         return false;
      }

      for (String phrase : HARMLESS_REJECTIONS) {
         if (rejection.contains(phrase) && !phrase.startsWith("Unknown") && !phrase.startsWith("Incorrect")) {
            return true;
         }
      }

      return false;
   }

   /** Switches each module on by itself for a few ticks, so a crash or a rejected command points at one module. */
   private void runEveryModuleAlone(ClientGameTestContext context, TestSingleplayerContext world) {
      this.collect(context, "world start");
      List<Module> all = context.computeOnClient(client -> new ArrayList<>(modules().getModules()));
      for (Module module : all) {
         String name = module.getName();
         if (module instanceof ClickGuiModule || NOT_RUN_ALONE.contains(name)) {
            continue;
         }

         boolean wasOn = context.computeOnClient(client -> module.isSavedEnabled());
         boolean mayStart = context.computeOnClient(client -> {
            boolean can = module.canActivate();
            module.setHeld(true);
            return can;
         });
         if (!mayStart) {
            this.note(name + " did not start: " + context.computeOnClient(client -> module.blockedReason().replaceAll("§.", "")));
         }

         context.waitTicks(4);
         context.runOnClient(client -> {
            module.info();
            // Back to how it was: some modules (the HUD) are on by default and later checks rely on that.
            module.setHeld(wasOn);
            if (client.gui.screen() != null) {
               client.gui.setScreen(null);
            }
         });
         context.waitTicks(1);
         this.collect(context, name);
         this.modulesRun++;
         if (this.modulesRun % 20 == 0) {
            // Some modules change the game mode, the time or the player's position; put the basics back regularly.
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tick unfreeze");
            world.getServer().runCommand("tp @a 0 ~ 0");
         }
      }

      List<String> stuck = context.computeOnClient(client -> {
         List<String> names = new ArrayList<>();
         for (Module module : modules().getModules()) {
            if (module.isEnabled() && !(module instanceof ClickGuiModule) && !"HUD".equals(module.getName()) && !"Aim".equals(module.getName())) {
               names.add(module.getName());
            }
         }

         return names;
      });
      this.note("on after the pass (on by default, or switched on by another module): " + String.join(", ", stuck));

      world.getServer().runCommand("gamemode creative @a");
      world.getServer().runCommand("tick unfreeze");
      context.waitTicks(5);
      this.collect(context, "after the single-module pass");
      this.note("modules run alone: " + this.modulesRun);
   }

   /** The built-in self-test: everything on at once for seven seconds, in survival so damage and hunger code runs. */
   private void runSelfTest(ClientGameTestContext context, TestSingleplayerContext world) {
      world.getServer().runCommand("gamemode survival @a");
      world.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
      context.waitTicks(5);
      context.runOnClient(client -> SelfTest.start(modules()));
      context.waitFor(client -> !SelfTest.running(), 20 * 60);
      for (String problem : context.computeOnClient(client -> SelfTest.lastProblems())) {
         this.problems.add("self-test: " + problem.replaceAll("§.", ""));
      }

      world.getServer().runCommand("gamemode creative @a");
      world.getServer().runCommand("tick unfreeze");
      world.getServer().runCommand("effect clear @a");
      context.runOnClient(client -> {
         if (client.gui.screen() != null) {
            client.gui.setScreen(null);
         }
      });
      context.waitTicks(10);
      this.collect(context, "after the self-test");
   }

   /** Walks, mines and explores with the built-in engine, which is the one this project owns. */
   private void runPathfinder(ClientGameTestContext context, TestSingleplayerContext world) {
      world.getServer().runCommand("gamemode survival @a");
      world.getServer().runCommand("effect give @a minecraft:resistance infinite 255 true");
      context.waitTicks(10);
      BlockPos goal = context.computeOnClient(client -> {
         BaritoneBridge.stopAll();
         BlockPos start = client.player.blockPosition();
         int x = start.getX() + 14;
         int z = start.getZ() + 9;
         BlockPos target = new BlockPos(x, BuiltInBaritone.findSurfaceY(client.level, x, z, start.getY()), z);
         BuiltInBaritone.gotoBlock(target);
         return target;
      });
      int ticks;
      try {
         ticks = context.waitFor(client -> !BuiltInBaritone.isActive(), 20 * 40);
      } catch (RuntimeException | AssertionError e) {
         ticks = -1;
      }

      double left = context.computeOnClient(client -> Math.sqrt(client.player.blockPosition().distSqr(goal)));
      String stats = context.computeOnClient(client -> BuiltInBaritone.searchStats());
      if (ticks < 0 || left > 2.5) {
         this.problems.add(
            "pathfinder: #goto did not arrive within 40 seconds; " + String.format(Locale.ROOT, "%.1f", left) + " blocks from the goal (" + stats + ")"
         );
      } else {
         this.note("pathfinder: walked to the goal in " + ticks + " ticks; last search " + stats);
      }

      context.runOnClient(client -> BuiltInBaritone.stop());
      this.collect(context, "pathfinder goto");

      context.runOnClient(client -> BuiltInBaritone.mineByNames(new String[]{"dirt"}));
      context.waitTicks(100);
      this.note("pathfinder: mine status after 5 s: " + context.computeOnClient(client -> BuiltInBaritone.statusSummary()));
      context.runOnClient(client -> BuiltInBaritone.stop());
      this.collect(context, "pathfinder mine");

      context.runOnClient(client -> BuiltInBaritone.startExplore(client.player.blockPosition()));
      context.waitTicks(60);
      context.runOnClient(client -> BuiltInBaritone.stop());
      this.collect(context, "pathfinder explore");

      // The bridge picks the engine; whichever it is, a request and a stop must go through cleanly.
      context.runOnClient(client -> BaritoneBridge.gotoBlock(client.player.blockPosition().offset(6, 0, 0)));
      context.waitTicks(40);
      this.note("bridge: owner while pathing = " + context.computeOnClient(client -> BaritoneBridge.owner()) + ", status " + context.computeOnClient(client -> BaritoneBridge.statusSummary()));
      context.runOnClient(client -> BaritoneBridge.stopAll());
      context.waitTicks(5);
      this.collect(context, "bridge goto");
      world.getServer().runCommand("gamemode creative @a");
   }

   /**
    * Criticals no longer fakes a fall with packets (Minecraft 26.3 disconnects for that); the hit is made critical
    * on the integrated server instead. This checks that it really is: the same punch must do half again as much.
    */
   private void runCriticalHits(ClientGameTestContext context, TestSingleplayerContext world) {
      world.getServer().runCommand("gamemode survival @a");
      // Bare hands: earlier parts of the test hand out enchanted weapons, and one hit from those leaves nothing to compare.
      world.getServer().runCommand("clear @a");
      world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
      context.waitTicks(5);
      world.getServer().runCommand("execute at @p run summon minecraft:pig ~ ~ ~2 {NoAI:1b,Silent:1b,Health:10f}");
      context.waitTicks(30);
      float plain = this.punch(context, world);
      context.runOnClient(client -> modules().getByName("Criticals").ifPresent(module -> module.setHeld(true)));
      context.waitTicks(30);
      float critical = this.punch(context, world);
      context.runOnClient(client -> modules().getByName("Criticals").ifPresent(module -> module.setHeld(false)));
      world.getServer().runCommand("kill @e[type=minecraft:pig]");
      world.getServer().runCommand("kill @e[type=minecraft:item]");
      world.getServer().runCommand("gamemode creative @a");
      if (plain <= 0.0F) {
         this.problems.add("critical hits: the plain punch did no damage, so the check could not run (" + plain + ")");
      } else if (critical < plain * 1.4F) {
         this.problems.add("critical hits: with Criticals on a punch did " + critical + " instead of 1.5 x " + plain);
      } else {
         this.note("critical hits: plain punch " + plain + ", with Criticals " + critical);
      }

      this.collect(context, "critical hits");
   }

   /** Punches the pig once at full strength and returns the health it lost. */
   private float punch(ClientGameTestContext context, TestSingleplayerContext world) {
      float before = pigHealth(world);
      context.runOnClient(client -> {
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof Pig) {
               client.gameMode.attack(client.player, entity);
               return;
            }
         }
      });
      context.waitTicks(6);
      return before - pigHealth(world);
   }

   private static float pigHealth(TestSingleplayerContext world) {
      return world.getServer().computeOnServer(server -> {
         for (Entity entity : server.overworld().getAllEntities()) {
            if (entity instanceof Pig pig) {
               return pig.getHealth();
            }
         }

         return -1.0F;
      });
   }

   /** The modules added in 3.3.0 do something observable, so check the result and not only that nothing crashed. */
   private void runNewModules(ClientGameTestContext context, TestSingleplayerContext world) {
      world.getServer().runCommand("gamemode survival @a");
      world.getServer().runCommand("clear @a");

      world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe[damage=900]");
      context.waitTicks(5);
      int damageBefore = context.computeOnClient(client -> client.player.getMainHandItem().getDamageValue());
      this.hold(context, "Repair", true);
      context.waitTicks(25);
      int damageAfter = context.computeOnClient(client -> client.player.getMainHandItem().getDamageValue());
      this.hold(context, "Repair", false);
      if (damageBefore < 800 || damageAfter != 0) {
         this.problems.add("Repair: a pickaxe with " + damageBefore + " damage still has " + damageAfter + " after Repair ran");
      }

      world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:cobblestone 3");
      context.waitTicks(5);
      this.hold(context, "Refill", true);
      context.waitTicks(20);
      int count = context.computeOnClient(client -> client.player.getMainHandItem().getCount());
      this.hold(context, "Refill", false);
      if (count != 64) {
         this.problems.add("Refill: a stack of 3 cobblestone became " + count + " instead of 64");
      }

      double scaleBase = this.attribute(context, world, Attributes.SCALE)[0];
      this.hold(context, "Size", true);
      this.hold(context, "Extra Hearts", true);
      this.hold(context, "Gravity", true);
      context.waitTicks(10);
      double[] scale = this.attribute(context, world, Attributes.SCALE);
      double[] health = this.attribute(context, world, Attributes.MAX_HEALTH);
      double[] gravity = this.attribute(context, world, Attributes.GRAVITY);
      this.hold(context, "Size", false);
      this.hold(context, "Extra Hearts", false);
      this.hold(context, "Gravity", false);
      context.waitTicks(10);
      double[] scaleOff = this.attribute(context, world, Attributes.SCALE);
      double[] healthOff = this.attribute(context, world, Attributes.MAX_HEALTH);
      this.expect("Size on (client, server)", scale, scaleBase * 0.5);
      this.expect("Extra Hearts on (client, server)", health, 40.0);
      this.expect("Gravity on (client, server)", gravity, 0.08 * 0.35);
      this.expect("Size off again (client, server)", scaleOff, scaleBase);
      this.expect("Extra Hearts off again (client, server)", healthOff, 20.0);

      // A roof three blocks up: Surface must put the player on top of it.
      world.getServer().runCommand("execute at @p run fill ~-1 ~3 ~-1 ~1 ~3 ~1 minecraft:stone");
      context.waitTicks(10);
      double yBefore = context.computeOnClient(client -> client.player.getY());
      this.hold(context, "Surface", true);
      context.waitTicks(15);
      double yAfter = context.computeOnClient(client -> client.player.getY());
      if (Math.abs(yAfter - (yBefore + 4.0)) > 0.6) {
         this.problems.add("Surface: expected to rise 4 blocks onto the roof, went from y " + yBefore + " to " + yAfter);
      }

      world.getServer().runCommand("clear @a");
      world.getServer().runCommand("gamemode creative @a");
      this.note("new modules: Repair " + damageBefore + " -> " + damageAfter + " damage, Refill 3 -> " + count + ", Surface y " + yBefore + " -> " + yAfter);
      this.collect(context, "new modules");
   }

   private void hold(ClientGameTestContext context, String module, boolean on) {
      context.runOnClient(client -> modules().getByName(module).orElseThrow(() -> new IllegalStateException("no module " + module)).setHeld(on));
   }

   /** The attribute's value on the client player and on the server player. */
   private double[] attribute(ClientGameTestContext context, TestSingleplayerContext world, Holder<Attribute> attribute) {
      double client = context.computeOnClient(mc -> mc.player.getAttributeValue(attribute));
      double server = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getAttributeValue(attribute));
      return new double[]{client, server};
   }

   private void expect(String what, double[] clientAndServer, double wanted) {
      for (double value : clientAndServer) {
         if (Math.abs(value - wanted) > 1.0E-4) {
            this.problems.add("new modules: " + what + " should be " + wanted + " but is " + clientAndServer[0] + " / " + clientAndServer[1]);
            return;
         }
      }
   }

   private void runCommands(ClientGameTestContext context) {
      String[] lines = {
         ".help", ".modules", ".modules combat", ".binds", ".version", ".get Flight", ".set Flight Speed 1.0", ".toggle Fullbright", ".toggle Fullbright",
         ".fastfall", ".fastfall", ".config save gametest", ".config list", ".config export gametest", ".config import gametest",
         ".config load gametest", ".config delete gametest", ".config export gametest clipboard", ".sandbox", ".coords", ".waypoints add test",
         ".waypoints list", ".waypoints clear", ".sethome", ".home", ".back", ".day", ".heal", ".feed", ".gm creative", ".bind Flight g", ".unbind Flight",
         ".reset Flight", "#status", "#help", "#stop", ".nonsense"
      };
      for (String line : lines) {
         context.runOnClient(client -> Gemini.getInstance().commands().handle(line));
         context.waitTicks(2);
         this.collect(context, "command " + line);
      }

      // A merged module must still answer to its old name.
      boolean before = context.computeOnClient(client -> modules().getByName("Fast Fall").map(Module::isSavedEnabled).orElse(false));
      context.runOnClient(client -> Gemini.getInstance().commands().handle(".reversestep"));
      context.waitTicks(2);
      boolean after = context.computeOnClient(client -> modules().getByName("Fast Fall").map(Module::isSavedEnabled).orElse(false));
      if (before == after) {
         this.problems.add("commands: .reversestep did not toggle Fast Fall, the module ReverseStep was merged into");
      }

      context.runOnClient(client -> modules().getByName("Fast Fall").ifPresent(module -> module.setHeld(false)));
      this.note("commands: " + lines.length + " run");
   }

   private void runMenus(ClientGameTestContext context) {
      for (String widget : new String[]{"Free Slots", "Food Info", "Xp Info", "Clock", "Position"}) {
         context.runOnClient(client -> modules().getByName(widget).ifPresent(module -> module.setHeld(true)));
      }

      context.waitTicks(10);
      context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
      context.waitTicks(2);
      context.takeScreenshot("gemini-03-hud");
      context.setScreen(Menus::create);
      context.waitTicks(10);
      context.takeScreenshot("gemini-04-menu-window");
      context.getInput().typeChars("fall");
      context.waitTicks(5);
      context.takeScreenshot("gemini-05-menu-search");
      context.setScreen(() -> null);
      context.waitTicks(2);
      context.runOnClient(client -> Gemini.getInstance().commands().handle(".set ClickGUI Layout Panels"));
      context.waitTicks(2);
      context.setScreen(Menus::create);
      context.waitTicks(10);
      context.takeScreenshot("gemini-06-menu-panels");
      context.setScreen(() -> null);
      context.runOnClient(client -> Gemini.getInstance().commands().handle(".set ClickGUI Layout Window"));
      context.waitTicks(2);
      this.collect(context, "menus");
   }

   private void runSandboxSwitch(ClientGameTestContext context) {
      context.runOnClient(client -> modules().getByName("Fullbright").ifPresent(module -> module.setHeld(true)));
      context.waitTicks(3);
      String off = context.computeOnClient(client -> SingleplayerGate.setSandboxMode(false).message());
      context.waitTicks(5);
      List<String> stillRunning = context.computeOnClient(client -> {
         List<String> names = new ArrayList<>();
         for (Module module : modules().getModules()) {
            if (module.isEnabled() && !(module instanceof ClickGuiModule) && !"HUD".equals(module.getName()) && !"Aim".equals(module.getName())) {
               names.add(module.getName());
            }
         }

         return names;
      });
      if (!stillRunning.isEmpty()) {
         this.problems.add("sandbox off: these modules kept running: " + stillRunning);
      }

      if (context.computeOnClient(client -> SingleplayerGate.allowed())) {
         this.problems.add("sandbox off: Gemini still reports that it may act");
      }

      context.takeScreenshot("gemini-07-switched-off");
      context.runOnClient(client -> Gemini.getInstance().commands().handle(".toggle Flight"));
      context.waitTicks(2);
      if (context.computeOnClient(client -> modules().getByName("Flight").map(Module::isEnabled).orElse(false))) {
         this.problems.add("sandbox off: .toggle still switched a module on");
      }

      String on = context.computeOnClient(client -> SingleplayerGate.setSandboxMode(true).message());
      context.waitTicks(5);
      if (!context.computeOnClient(client -> SingleplayerGate.allowed())) {
         this.problems.add("sandbox on again: Gemini did not come back");
      }

      boolean restored = context.computeOnClient(client -> modules().getByName("Fullbright").map(Module::isEnabled).orElse(false));
      if (!restored) {
         this.problems.add("sandbox on again: a module that was on before did not come back");
      }

      context.runOnClient(client -> modules().getByName("Fullbright").ifPresent(module -> module.setHeld(false)));
      this.note("sandbox: off -> " + off.replaceAll("§.", "") + " | on -> " + on.replaceAll("§.", ""));
      this.collect(context, "sandbox switch");
   }

   /** Errors Gemini logged that never reached a failure list: command handler exceptions and anything thrown elsewhere. */
   private void scanLog(Path gameDir) {
      Path log = gameDir.resolve("logs").resolve("latest.log");
      try {
         List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
         for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean ours = line.contains("Command failed:")
               || line.contains("crashed in")
               || line.contains("failed during")
               || line.contains("[Gemini") && line.contains("/ERROR]")
               || line.contains("/ERROR]") && i + 2 < lines.size() && (lines.get(i + 1).contains("gemini.") || lines.get(i + 2).contains("gemini."));
            if (ours) {
               String detail = i + 1 < lines.size() && !lines.get(i + 1).startsWith("[") ? " | " + lines.get(i + 1).trim() : "";
               String entry = "log: " + line.replaceFirst("^\\[[^]]*] ", "") + detail;
               if (!this.problems.contains(entry)) {
                  this.problems.add(entry);
               }
            }
         }
      } catch (IOException e) {
         this.note("could not read " + log + ": " + e);
      }
   }

   private void writeReport(Path gameDir) {
      List<String> report = new ArrayList<>();
      report.add("Gemini " + Gemini.VERSION + " game test");
      report.add("problems: " + this.problems.size());
      for (String problem : this.problems) {
         report.add("  PROBLEM " + problem);
      }

      report.add("notes: " + this.notes.size());
      for (String line : this.notes) {
         report.add("  " + line);
      }

      try {
         Files.write(gameDir.resolve("gemini-gametest-report.txt"), report, StandardCharsets.UTF_8);
      } catch (IOException e) {
         Gemini.LOGGER.error("Could not write the game test report", e);
      }

      for (String line : report) {
         Gemini.LOGGER.info("[gametest] {}", line);
      }
   }
}
