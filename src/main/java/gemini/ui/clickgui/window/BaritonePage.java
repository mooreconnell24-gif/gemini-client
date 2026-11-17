package gemini.ui.clickgui.window;

import gemini.module.Module;
import gemini.module.modules.bot.AutoExplore;
import gemini.module.modules.bot.AutoGoto;
import gemini.module.modules.bot.AutoHighway;
import gemini.module.modules.bot.AutoMineBot;
import gemini.module.modules.bot.AutoTunnel;
import gemini.module.modules.bot.FollowBot;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.clickgui.controls.Widgets;
import gemini.ui.UiMath;
import gemini.ui.clickgui.ListEditorScreen;
import gemini.ui.clickgui.controls.TextField;
import gemini.ui.font.UiFont;
import gemini.util.BaritoneBridge;
import gemini.util.BuiltInBaritone;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * Live Baritone control centre: the engine in use, the running task, its goal, path progress and an ETA from the
 * measured walking speed, with Pause/Resume and Cancel, and quick starts for the bot modules through their own
 * toggles and settings. Without a world (the menu can be opened from the title screen) it says so and every
 * action is greyed out with the reason.
 */
public final class BaritonePage extends DashboardPage {
   /** The pause reason the {@code #pause} command uses, so {@code #resume} and this page undo each other. */
   private static final String PAUSE_REASON = "command";
   private static final int STATUS_H = 88;
   private final TextField gotoX = new TextField(9, BaritonePage::numeric);
   private final TextField gotoY = new TextField(5, BaritonePage::numeric);
   private final TextField gotoZ = new TextField(9, BaritonePage::numeric);
   private double speed = Double.NaN;
   private double lastX = Double.NaN;
   private double lastZ = Double.NaN;
   private long lastSample;

   public BaritonePage(Host host) {
      super(host);
   }

   private static boolean numeric(int c) {
      return c >= '0' && c <= '9' || c == '-' || c == '+' || c == '.' || c == '~';
   }

   @Override
   public String title() {
      return "Baritone";
   }

   @Override
   public String hint() {
      return "Live status of the pathing engine  ·  bot modules start through their own switches";
   }

   @Override
   protected TextField[] fields() {
      return new TextField[]{this.gotoX, this.gotoY, this.gotoZ};
   }

   @Override
   protected void fieldSubmitted(TextField field) {
      field.blur();
      this.startGoto();
   }

   @Override
   public void opened() {
      AutoGoto module = module(AutoGoto.class);
      if (module != null && this.gotoX.text().isEmpty() && this.gotoZ.text().isEmpty()) {
         this.gotoX.setText(intText(setting(module, "Target X")));
         this.gotoY.setText(intText(setting(module, "Target Y")));
         this.gotoZ.setText(intText(setting(module, "Target Z")));
      }
   }

   private static String intText(Setting<?> setting) {
      return setting instanceof NumberSetting number ? Integer.toString(number.intValue()) : "";
   }

   private static <T extends Module> T module(Class<T> type) {
      return gemini() == null ? null : gemini().modules().byClass(type).orElse(null);
   }

   private static Setting<?> setting(Module module, String name) {
      if (module == null) {
         return null;
      }

      for (Setting<?> setting : module.settings()) {
         if (setting.name().equals(name)) {
            return setting;
         }
      }

      return null;
   }

   /** Why nothing here can run right now, or null when the bot can be used. */
   private static String blockedReason() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null) {
         return "The bot needs a world: load a single-player world first";
      }

      return SingleplayerGate.allowed() ? null : plain(SingleplayerGate.denialMessage());
   }

   private List<Module> botModules() {
      List<Module> list = new ArrayList<>();
      for (Class<? extends Module> type : List.of(AutoGoto.class, AutoMineBot.class, AutoExplore.class, FollowBot.class, AutoTunnel.class, AutoHighway.class)) {
         Module module = module(type);
         if (module != null) {
            list.add(module);
         }
      }

      return list;
   }

   private String owner() {
      if (gemini() == null) {
         return null;
      }

      List<String> enabled = new ArrayList<>();
      for (String name : BotTasks.PATHING_MODULES) {
         gemini().modules().getByName(name).filter(Module::isEnabled).ifPresent(m -> enabled.add(m.getName()));
      }

      return BotTasks.owner(BaritoneBridge.taskLabel(), enabled);
   }

   // ---- actions --------------------------------------------------------------------------------------------

   private void toggleBot(Module module) {
      if (module.isEnabled()) {
         module.setEnabled(false, false);
         this.host.notice(module.getName() + " stopped");
      } else if (!module.canActivate()) {
         this.host.notice(plain(module.blockedReason()));
      } else {
         module.setEnabled(true, false);
         this.host.notice(module.isEnabled() ? module.getName() + " started" : module.getName() + " did not start (see chat)");
      }

      save();
   }

   private void pauseOrResume() {
      if (BaritoneBridge.isPaused()) {
         BaritoneBridge.resume(PAUSE_REASON);
         BaritoneBridge.resume("manual");
         this.host.notice(BaritoneBridge.isPaused() ? "Still paused: " + BaritoneBridge.pauseReason() : "Baritone resumed");
      } else {
         BaritoneBridge.pause(PAUSE_REASON);
         this.host.notice("Baritone paused");
      }
   }

   /**
    * Stops the engine and switches off every bot module, so none of them restarts a task. Auto Highway walks and
    * builds without Baritone, so it is switched off here too rather than left running behind a "cancelled" notice.
    */
   private void cancel() {
      for (Module module : this.botModules()) {
         if (module.isEnabled()) {
            module.setEnabled(false, false);
         }
      }

      BaritoneBridge.stopAll();
      this.host.notice("All bot tasks cancelled");
      save();
   }

   private OptionalDouble coordinate(TextField field, double here) {
      String text = field.text().trim();
      if (text.startsWith("~")) {
         // relative to the player, like vanilla commands: "~" or "~-20"
         OptionalDouble offset = text.length() == 1 ? OptionalDouble.of(0.0) : UiMath.parseNumber(text.substring(1));
         return offset.isPresent() ? OptionalDouble.of(here + offset.getAsDouble()) : OptionalDouble.empty();
      }

      return UiMath.parseNumber(text);
   }

   private void startGoto() {
      AutoGoto module = module(AutoGoto.class);
      String blocked = blockedReason();
      if (module == null || blocked != null) {
         this.host.notice(blocked == null ? "Auto Goto is not available" : blocked);
         return;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      OptionalDouble x = this.coordinate(this.gotoX, player.getX());
      OptionalDouble y = this.gotoY.text().isBlank() ? OptionalDouble.of(player.getY()) : this.coordinate(this.gotoY, player.getY());
      OptionalDouble z = this.coordinate(this.gotoZ, player.getZ());
      boolean bad = false;
      for (Object[] pair : new Object[][]{{x, this.gotoX}, {y, this.gotoY}, {z, this.gotoZ}}) {
         if (((OptionalDouble)pair[0]).isEmpty()) {
            ((TextField)pair[1]).flashError();
            bad = true;
         }
      }

      if (bad) {
         this.host.notice("Type whole-number coordinates (~ means your position)");
         return;
      }

      BlockPos target = BlockPos.containing(x.getAsDouble(), y.getAsDouble(), z.getAsDouble());
      // The module's own settings hold the destination; starting the module paths there and claims the task for it.
      if (setting(module, "Target X") instanceof NumberSetting sx) {
         sx.set((double)target.getX());
      }

      if (setting(module, "Target Y") instanceof NumberSetting sy) {
         sy.set((double)target.getY());
      }

      if (setting(module, "Target Z") instanceof NumberSetting sz) {
         sz.set((double)target.getZ());
      }

      if (module.isEnabled()) {
         // Restart so the running task is replaced by one owned by the module (a bare path request would make it let go).
         module.setEnabled(false, false);
      }

      module.setEnabled(true, false);
      if (!module.isEnabled()) {
         this.host.notice(plain(module.blockedReason()));
         return;
      }

      this.host.notice("Auto Goto heading to " + target.getX() + " " + target.getY() + " " + target.getZ());
      save();
   }

   private void fillHere() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         this.host.notice("The bot needs a world: load a single-player world first");
         return;
      }

      BlockPos pos = player.blockPosition();
      this.gotoX.setText(Integer.toString(pos.getX()));
      this.gotoY.setText(Integer.toString(pos.getY()));
      this.gotoZ.setText(Integer.toString(pos.getZ()));
   }

   // ---- drawing --------------------------------------------------------------------------------------------

   private void sampleSpeed(LocalPlayer player, long now) {
      if (player == null) {
         this.speed = Double.NaN;
         this.lastX = Double.NaN;
         return;
      }

      if (Double.isNaN(this.lastX)) {
         this.lastX = player.getX();
         this.lastZ = player.getZ();
         this.lastSample = now;
         return;
      }

      long elapsed = now - this.lastSample;
      if (elapsed >= 250L) {
         double dx = player.getX() - this.lastX;
         double dz = player.getZ() - this.lastZ;
         double sample = Math.sqrt(dx * dx + dz * dz) / (elapsed / 1000.0);
         this.speed = BotTasks.smooth(this.speed, sample, 0.35);
         this.lastX = player.getX();
         this.lastZ = player.getZ();
         this.lastSample = now;
      }
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
      long now = Util.getMillis();
      LocalPlayer player = Minecraft.getInstance().player;
      this.sampleSpeed(player, now);
      String blocked = blockedReason();
      this.heading(g, x, y);
      int cx = x + 18 + this.headingWidth();
      this.chip(g, cx, y + 7, BaritoneBridge.engineName(), Glass.withAlpha(BaritoneBridge.externalPresent() ? Glass.OK : Glass.INFO, 110), Glass.text());
      String task = BaritoneBridge.taskLabel();
      String state = blocked != null ? "No world" : (BaritoneBridge.isPaused() ? "Paused" : (task == null ? "Idle" : task));
      int stateColor = blocked != null ? Glass.textFaint() : (BaritoneBridge.isPaused() ? Glass.WARN : (task == null ? Glass.textDim() : Glass.OK));
      int sw = UiFont.width(state) + 8;
      this.chip(g, x + w - 10 - sw, y + 7, state, Glass.withAlpha(stateColor, 90), Glass.text());
      int top = y + 26;
      int bottom = y + h - 4;
      int inner = w - 24;
      List<Module> bots = this.botModules();
      int contentH = STATUS_H + 8 + (blocked != null ? 34 : 0);
      for (Module bot : bots) {
         contentH += this.botHeight(bot) + 4;
      }

      this.viewport(x, top, w, bottom, contentH + 4);
      g.enableScissor(x, top, x + w, bottom);
      int cy = top + 2 - Math.round(this.scroll);
      if (blocked != null) {
         this.card(g, x + 10, cy, x + 10 + inner, cy + 30, Glass.WARN, 0.5F);
         this.textFit(g, blocked, x + 18, cy + 6, inner - 16, Glass.WARN);
         this.textFit(g, "Settings below can still be changed; starting, pausing and cancelling need a world.", x + 18, cy + 18, inner - 16, Glass.textFaint());
         cy += 34;
      }

      this.drawStatus(g, x + 10, cy, inner, mouseX, mouseY, blocked, player);
      cy += STATUS_H + 8;
      String owner = this.owner();
      for (Module bot : bots) {
         int bh = this.botHeight(bot);
         if (cy + bh >= top && cy <= bottom) {
            this.drawBot(g, bot, x + 10, cy, inner, bh, mouseX, mouseY, blocked, bot.getName().equals(owner));
         }

         cy += bh + 4;
      }

      g.disableScissor();
      this.scrollbar(g, x + w - 5, contentH + 4);
   }

   private void drawStatus(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, String blocked, LocalPlayer player) {
      String task = BaritoneBridge.taskLabel();
      this.card(g, x, y, x + w, y + STATUS_H, task != null && blocked == null ? 0.25F : 0.0F);
      int lx = x + 8;
      int vx = x + 64;
      int vw = w - 72;
      this.text(g, "Task", lx, y + 7, Glass.textFaint());
      String owner = this.owner();
      String taskText = task == null ? "Idle" : task + (owner != null ? "  ·  by " + owner : "  ·  started by a command");
      this.textFit(g, taskText, vx, y + 7, vw, task == null ? Glass.textDim() : Glass.text());
      this.text(g, "Goal", lx, y + 19, Glass.textFaint());
      BlockPos goal = BuiltInBaritone.goal();
      String external = BaritoneBridge.externalGoal();
      String goalText;
      double distance = -1.0;
      if (goal != null) {
         goalText = goal.getX() + ", " + goal.getY() + ", " + goal.getZ();
         if (player != null) {
            distance = Math.sqrt(goal.distToCenterSqr(player.getX(), player.getY(), player.getZ()));
            goalText = goalText + "  ·  " + Math.round(distance) + " blocks away";
         }
      } else if (external != null) {
         goalText = external;
      } else {
         goalText = "none";
      }

      this.textFit(g, goalText, vx, y + 19, vw, goal == null && external == null ? Glass.textDim() : Glass.text());
      this.text(g, "Path", lx, y + 31, Glass.textFaint());
      List<BlockPos> path = BuiltInBaritone.path();
      int index = Math.min(BuiltInBaritone.pathIndex(), path.size());
      float progress = BotTasks.progress(index, path.size());
      int barRight = x + w - 110;
      Glass.bar(g, vx, y + 33, barRight, y + 37, progress, this.c(Widgets.trackOff()), this.c(Glass.accent()), this.c(Glass.accent2()));
      String nodes = path.isEmpty() ? (BaritoneBridge.externalPathing() ? "Baritone path" : "no path") : index + " / " + path.size() + " nodes";
      this.textFit(g, nodes, barRight + 6, y + 31, x + w - barRight - 14, Glass.textDim());
      this.text(g, "ETA", lx, y + 43, Glass.textFaint());
      String eta;
      if (task == null || distance < 0.0) {
         eta = "--";
      } else {
         eta = BotTasks.formatDuration(BotTasks.etaSeconds(distance, this.speed));
      }

      String speedText = Double.isNaN(this.speed) || player == null ? "" : String.format(java.util.Locale.ROOT, "  ·  %.1f blocks/s", this.speed);
      this.textFit(g, eta + speedText, vx, y + 43, vw, Glass.text());
      this.text(g, "Engine", lx, y + 55, Glass.textFaint());
      String paused = BaritoneBridge.isPaused() ? "  ·  paused: " + BaritoneBridge.pauseReason() : "";
      this.textFit(g, BaritoneBridge.engineDetail() + paused, vx, y + 55, vw, BaritoneBridge.isPaused() ? Glass.WARN : Glass.textDim());
      int by = y + STATUS_H - 19;
      String pause = BaritoneBridge.isPaused() ? "Resume" : "Pause";
      int bx = lx;
      bx += this.button(g, bx, by, this.buttonWidth(pause) + 8, 14, pause, mouseX, mouseY, BaritoneBridge.isPaused() ? TONE_OK : TONE_ACCENT, blocked,
         BaritoneBridge.isPaused() ? "Let the bot carry on (same as #resume)" : "Hold the bot where it is (same as #pause)", true, (b, mx, my, d) -> this.pauseOrResume()) + 4;
      this.button(g, bx, by, this.buttonWidth("Cancel") + 8, 14, "Cancel", mouseX, mouseY, TONE_DANGER, blocked,
         "Stop every task and switch off the bot module driving it", true, (b, mx, my, d) -> this.cancel());
      String summary = plain(BaritoneBridge.statusSummary());
      this.textFit(g, summary, bx + this.buttonWidth("Cancel") + 16, by + 3, x + w - (bx + this.buttonWidth("Cancel") + 24), Glass.textFaint());
   }

   private int botHeight(Module bot) {
      int body;
      if (bot instanceof AutoGoto || bot instanceof AutoMineBot || bot instanceof FollowBot) {
         body = 18;
      } else if (bot instanceof AutoTunnel) {
         body = this.controlHeight(setting(bot, "Length"));
      } else if (bot instanceof AutoHighway) {
         body = this.controlHeight(setting(bot, "Width")) + 16;
      } else {
         body = 0;
      }

      return 30 + body + (body > 0 ? 4 : 0);
   }

   private int controlHeight(Setting<?> setting) {
      return setting == null || this.control(setting) == null ? 0 : this.control(setting).height();
   }

   private void drawBot(GuiGraphicsExtractor g, Module bot, int x, int y, int w, int h, int mouseX, int mouseY, String blocked, boolean owns) {
      boolean on = bot.isEnabled();
      this.card(g, x, y, x + w, y + h, on ? 0.3F : 0.0F);
      String action = bot instanceof AutoGoto ? (on ? "Stop" : "Go") : (on ? "Stop" : "Start");
      int bw = this.buttonWidth("Start") + 6;
      int bx = x + w - 8 - bw;
      Click click = bot instanceof AutoGoto && !on ? (b, mx, my, d) -> this.startGoto() : (b, mx, my, d) -> this.toggleBot(bot);
      this.button(g, bx, y + 6, bw, 14, action, mouseX, mouseY, on ? TONE_DANGER : TONE_OK, on ? null : (blocked != null ? blocked : (bot.canActivate() ? null : plain(bot.blockedReason()))),
         on ? "Switch " + bot.getName() + " off" : "Switch " + bot.getName() + " on", true, click);
      int tx = x + 8;
      Glass.roundRect(g, tx, y + 9, tx + 5, y + 14, 2, this.c(on ? Glass.OK : Glass.surface(3)));
      tx += 10;
      this.text(g, bot.getName(), tx, y + 7, on ? Glass.text() : Glass.textDim());
      tx += UiFont.width(bot.getName()) + 6;
      if (owns) {
         tx += this.chip(g, tx, y + 5, "owns the task", Glass.withAlpha(Glass.accent(), 140), Glass.text()) + 4;
      } else if (on) {
         String info = plain(bot.info());
         if (info != null && !info.isEmpty()) {
            tx += this.chip(g, tx, y + 5, info, Glass.withAlpha(Glass.surface(3), 255), Glass.textDim()) + 4;
         }
      }

      this.textFit(g, bot.getDescription(), x + 18, y + 18, bx - x - 24, Glass.textFaint());
      int by = y + 30;
      if (bot instanceof AutoGoto) {
         this.drawGotoFields(g, x + 8, by, w - 16, mouseX, mouseY, blocked);
      } else if (bot instanceof AutoMineBot) {
         Setting<?> blocks = setting(bot, "Target Blocks");
         if (blocks instanceof ListSetting list) {
            this.text(g, list.size() + (list.size() == 1 ? " block to mine" : " blocks to mine"), x + 10, by + 3, Glass.textDim());
            String edit = "Edit list";
            int ew = this.buttonWidth(edit);
            this.button(g, x + w - 8 - ew, by, ew, 14, edit, mouseX, mouseY, TONE_NEUTRAL, null, "Choose which ores and blocks the bot mines", true,
               (b, mx, my, d) -> Minecraft.getInstance().gui.setScreen(new ListEditorScreen(this.host.screen(), bot, list)));
         }
      } else if (bot instanceof FollowBot) {
         Setting<?> target = setting(bot, "Target");
         if (target instanceof ModeSetting mode) {
            this.text(g, "Follow", x + 10, by + 3, Glass.textDim());
            String label = "<  " + mode.get() + "  >";
            int mw = this.buttonWidth(label);
            this.button(g, x + w - 8 - mw, by, mw, 14, label, mouseX, mouseY, TONE_ACCENT, null, "Which entities to follow (right-click goes back)", true, (b, mx, my, d) -> {
               if (b == 1) {
                  mode.cycleBack();
               } else {
                  mode.cycle();
               }

               save();
            });
         }
      } else if (bot instanceof AutoTunnel) {
         Setting<?> length = setting(bot, "Length");
         if (length != null) {
            this.drawControl(g, length, x + 2, by, w - 4, mouseX, mouseY, true);
         }
      } else if (bot instanceof AutoHighway) {
         Setting<?> width = setting(bot, "Width");
         int used = width == null ? 0 : this.drawControl(g, width, x + 2, by, w - 4, mouseX, mouseY, true);
         if (setting(bot, "Guard Rails") instanceof BooleanSetting rails) {
            int ry = by + used + 2;
            this.text(g, rails.name(), x + 10, ry + 1, Glass.textDim());
            this.toggle(g, x + w - 8 - 20, ry, rails.get());
            this.host.hit(x + 4, ry - 2, w - 8, 14, this.viewTop, this.viewBottom, null, (b, mx, my, d) -> {
               rails.toggle();
               save();
            });
            if (inside(mouseX, mouseY, x + 4, ry - 2, w - 8, 14)) {
               this.host.tip(rails.description());
            }
         }
      }
   }

   private void drawGotoFields(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, String blocked) {
      String here = "Here";
      int hw = this.buttonWidth(here);
      int labelW = UiFont.width("X") + 4;
      int room = w - hw - 6;
      int fieldW = Math.max(30, (room - labelW * 3 - 12) / 3);
      int fx = x + 2;
      TextField[] fields = {this.gotoX, this.gotoY, this.gotoZ};
      String[] labels = {"X", "Y", "Z"};
      for (int i = 0; i < 3; i++) {
         this.text(g, labels[i], fx, y + 3, Glass.textFaint());
         fx += labelW;
         this.field(g, fields[i], fx, y, fieldW, i == 1 ? "feet" : "0", mouseX, mouseY, true);
         fx += fieldW + 6;
      }

      this.button(g, x + w - hw, y, hw, 13, here, mouseX, mouseY, TONE_NEUTRAL, blocked == null ? null : blocked,
         "Fill in where you are standing (~ in a field means your position too)", true, (b, mx, my, d) -> this.fillHere());
   }
}
