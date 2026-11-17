package gemini.ui.clickgui.window;

import gemini.input.GeminiKeys;
import gemini.input.KeybindReport;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.font.UiFont;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Visual keybind conflict resolver: every bound module grouped by key ({@link KeybindGroups} over
 * {@link KeybindReport#snapshot}), keys shared by several modules or with a vanilla control flagged, and one-click
 * fixes per module: rebind (the screen's key capture: Esc cancels, Delete clears), clear, or switch Bind Mode.
 */
public final class KeybindsPage extends DashboardPage {
   private static final long REBUILD_MS = 400L;
   private static final int HEAD_H = 18;
   private static final int ROW_H = 18;
   private static boolean onlyConflicts;
   private List<KeybindGroups.Group> groups = List.of();
   private int conflicts;
   private int bound;
   private long lastBuild;

   public KeybindsPage(Host host) {
      super(host);
   }

   @Override
   public String title() {
      return "Keybinds";
   }

   @Override
   public String hint() {
      return "Rebind captures the next key  ·  Esc cancels  ·  Delete clears";
   }

   @Override
   public void opened() {
      this.lastBuild = 0L;
   }

   private void rebuild(long now) {
      if (gemini() == null || gemini().modules() == null) {
         this.groups = List.of();
         this.conflicts = 0;
         this.bound = 0;
      } else {
         List<KeybindGroups.Group> all = KeybindGroups.build(KeybindReport.snapshot(gemini().modules()), KeybindReport.CONTROLS_SUFFIX, false);
         this.conflicts = KeybindGroups.conflictCount(all);
         this.bound = KeybindGroups.moduleCount(all);
         this.groups = onlyConflicts ? all.stream().filter(KeybindGroups.Group::conflict).toList() : all;
      }

      this.lastBuild = now;
   }

   private static Module module(String name) {
      return gemini() == null ? null : gemini().modules().getByName(name).orElse(null);
   }

   private static ModeSetting bindMode(Module module) {
      for (Setting<?> setting : module.settings()) {
         if (setting instanceof ModeSetting mode && "Bind Mode".equals(setting.name())) {
            return mode;
         }
      }

      return null;
   }

   private static int groupHeight(KeybindGroups.Group group) {
      return HEAD_H + group.modules().size() * ROW_H + (group.controls().isEmpty() ? 0 : 12) + 6;
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
      long now = Util.getMillis();
      if (now - this.lastBuild >= REBUILD_MS) {
         this.rebuild(now);
      }

      this.heading(g, x, y);
      String summary = this.conflicts == 0
         ? "No conflicts"
         : this.conflicts + (this.conflicts == 1 ? " conflict" : " conflicts");
      int sx = x + 18 + this.headingWidth();
      int sw = this.chip(g, sx, y + 7, summary, Glass.withAlpha(this.conflicts == 0 ? Glass.OK : Glass.ERROR, 120), Glass.text());
      String filter = onlyConflicts ? "Only conflicts: on" : "Only conflicts: off";
      int fw = this.buttonWidth(filter);
      this.textFit(g, this.bound + " bound modules", sx + sw + 6, y + 9, x + w - 16 - fw - (sx + sw + 6), Glass.textFaint());
      this.button(g, x + w - 10 - fw, y + 6, fw, 14, filter, mouseX, mouseY, onlyConflicts ? TONE_ACCENT : TONE_NEUTRAL, null,
         "Hide keys that only one module uses", (b, mx, my, d) -> {
            onlyConflicts = !onlyConflicts;
            this.rebuild(Util.getMillis());
            this.scrollTarget = 0.0F;
         });
      int top = y + 26;
      int bottom = y + h - 4;
      int contentH = 4;
      for (KeybindGroups.Group group : this.groups) {
         contentH += groupHeight(group) + 4;
      }

      this.viewport(x, top, w, bottom, contentH);
      if (this.groups.isEmpty()) {
         String empty = onlyConflicts && this.bound > 0
            ? "No conflicts: every key does one thing."
            : "No module has a key bind yet. Middle-click a module card to bind it.";
         this.text(g, empty, x + (w - UiFont.width(empty)) / 2, top + 24, Glass.textFaint());
         return;
      }

      g.enableScissor(x, top, x + w, bottom);
      int gy = top + 2 - Math.round(this.scroll);
      for (KeybindGroups.Group group : this.groups) {
         int gh = groupHeight(group);
         if (gy + gh >= top && gy <= bottom) {
            this.drawGroup(g, group, x + 10, gy, w - 24, gh, mouseX, mouseY);
         }

         gy += gh + 4;
      }

      g.disableScissor();
      this.scrollbar(g, x + w - 5, contentH);
   }

   private void drawGroup(GuiGraphicsExtractor g, KeybindGroups.Group group, int x, int y, int w, int h, int mouseX, int mouseY) {
      int border = group.modules().size() > 1 ? Glass.ERROR : (group.vanillaClash() ? Glass.WARN : Glass.accent());
      this.card(g, x, y, x + w, y + h, border, group.conflict() ? 0.6F : 0.0F);
      String keyName = GeminiKeys.displayName(group.key());
      int kx = x + 6 + this.chip(g, x + 6, y + 4, keyName, Glass.withAlpha(Glass.surface(3), 255), Glass.text()) + 6;
      String state;
      int stateColor;
      if (group.modules().size() > 1) {
         state = group.modules().size() + " modules share this key";
         stateColor = Glass.ERROR;
      } else if (group.vanillaClash()) {
         state = "also a vanilla control";
         stateColor = Glass.WARN;
      } else {
         state = "no conflict";
         stateColor = Glass.textFaint();
      }

      this.textFit(g, state, kx, y + 6, x + w - kx - 6, stateColor);
      int ry = y + HEAD_H;
      for (String name : group.modules()) {
         this.drawModuleRow(g, name, group, x + 4, ry, w - 8, mouseX, mouseY);
         ry += ROW_H;
      }

      if (!group.controls().isEmpty()) {
         this.textFit(g, "Controls: " + String.join(", ", group.controls()), x + 10, ry + 2, w - 20, Glass.WARN);
      }
   }

   private void drawModuleRow(GuiGraphicsExtractor g, String name, KeybindGroups.Group group, int x, int y, int w, int mouseX, int mouseY) {
      Module module = module(name);
      boolean over = inside(mouseX, mouseY, x, y, w, ROW_H - 1) && mouseY >= this.viewTop && mouseY < this.viewBottom;
      if (over) {
         Glass.roundRect(g, x, y, x + w, y + ROW_H - 1, 3, this.c(Glass.text(), 10));
      }

      boolean capturing = module != null && this.host.bindingModule() == module;
      if (module == null) {
         this.textFit(g, name, x + 6, y + 5, w - 12, Glass.textFaint());
         return;
      }

      ModeSetting mode = bindMode(module);
      String modeLabel = mode == null ? null : mode.get();
      String rebind = capturing ? "Press a key..." : "Rebind";
      int[] widths = {this.buttonWidth(rebind), this.buttonWidth("Clear"), modeLabel == null ? 0 : this.buttonWidth("Toggle")};
      int total = widths[0] + widths[1] + widths[2] + 8;
      int bx = x + w - 4 - total;
      int by = y + 2;
      bx += this.button(g, bx, by, widths[0], 13, rebind, mouseX, mouseY, capturing ? TONE_ACCENT : TONE_NEUTRAL, null,
         "Capture a new key for " + name, true, (b, mx, my, d) -> this.host.beginBind(module)) + 4;
      bx += this.button(g, bx, by, widths[1], 13, "Clear", mouseX, mouseY, TONE_DANGER, null, "Remove " + name + "'s key bind", true, (b, mx, my, d) -> {
         module.setKeybind(-1);
         this.host.notice(name + " unbound");
         this.lastBuild = 0L;
      }) + 4;
      if (mode != null) {
         this.button(g, bx, by, widths[2], 13, modeLabel, mouseX, mouseY, mode.is("Hold") ? TONE_ACCENT : TONE_NEUTRAL, null,
            "Bind Mode: Toggle flips the module, Hold keeps it on only while the key is down", true, (b, mx, my, d) -> {
               mode.cycle();
               save();
               this.host.notice(name + " now uses " + mode.get() + " mode");
            });
      }

      int dot = module.isEnabled() ? Glass.OK : Glass.textFaint();
      Glass.roundRect(g, x + 6, y + 6, x + 10, y + 10, 2, this.c(dot));
      int nameRight = x + w - 8 - total;
      this.textFit(g, name, x + 14, y + 5, nameRight - x - 14, group.conflict() ? Glass.text() : Glass.textDim());
      if (over && !inside(mouseX, mouseY, x + w - 4 - total, y, total, ROW_H)) {
         this.host.tip(module.getCategory().displayName() + "  ·  " + module.getDescription());
      }
   }
}
