package gemini.ui;

import gemini.Gemini;
import gemini.command.Suggestions;
import gemini.input.GeminiKeys;
import gemini.input.KeybindConflicts;
import gemini.input.KeybindReport;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The keybind report as a scrollable glass card: every bound module grouped by key, with keys that are shared
 * (between modules, or with a vanilla control) marked. It shows what {@code .binds} prints to chat.
 */
public final class KeybindReportScreen extends Screen {
   private static final int ROW_HEIGHT = 11;
   private final Screen parent;
   private final List<Row> rows = new ArrayList<>();
   private String summary = "";
   private boolean clean = true;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int keyColumn;
   private float scroll;
   private float scrollTarget;
   private long lastFrame;

   private record Row(String key, String text, int color) {
   }

   public KeybindReportScreen(Screen parent) {
      super(Component.translatable("gemini.keybinds.title"));
      this.parent = parent;
   }

   /** Opens the report over {@code parent} and announces the headline (bound modules, shared keys) as a toast. */
   public static void open(Minecraft client, Screen parent) {
      KeybindReportScreen screen = new KeybindReportScreen(parent);
      client.gui.setScreen(screen);
      SandboxUi.notify(client, "Keybind report", screen.summary, screen.clean ? HudOverlay.Severity.SUCCESS : HudOverlay.Severity.WARNING);
   }

   @Override
   protected void init() {
      this.panelWidth = Math.min(360, this.width - 16);
      this.panelHeight = Math.min(230, this.height - 16);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
      this.buildRows();
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll());
      this.scroll = this.scrollTarget;
      this.addRenderableWidget(
         new GlassButton(this.panelX + this.panelWidth - 68, this.panelY + this.panelHeight - 22, 60, 16, CommonComponents.GUI_DONE, button -> this.onClose())
      );
   }

   private void buildRows() {
      this.rows.clear();
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || gemini.modules() == null) {
         this.summary = "Gemini is not loaded.";
         return;
      }

      ModuleManager modules = gemini.modules();
      Map<Integer, List<String>> conflicts = KeybindConflicts.find(KeybindReport.snapshot(modules));
      Map<Integer, List<String>> modulesByKey = new TreeMap<>();
      int bound = 0;
      for (Module module : modules.getModules()) {
         if (module.isBindable() && KeybindConflicts.isBound(module.getKeybind())) {
            modulesByKey.computeIfAbsent(module.getKeybind(), key -> new ArrayList<>()).add(module.getName());
            bound++;
         }
      }

      int shared = 0;
      this.keyColumn = 30;
      for (Integer key : modulesByKey.keySet()) {
         this.keyColumn = Math.max(this.keyColumn, UiFont.width(GeminiKeys.displayName(key)) + 10);
      }

      this.keyColumn = Math.min(this.keyColumn, this.panelWidth / 3);
      int textWidth = this.panelWidth - 16 - this.keyColumn - 6;
      for (Map.Entry<Integer, List<String>> entry : modulesByKey.entrySet()) {
         List<String> clash = conflicts.get(entry.getKey());
         this.wrap(GeminiKeys.displayName(entry.getKey()), String.join(", ", entry.getValue()), textWidth, clash == null ? Glass.text() : Glass.WARN);
         if (clash != null) {
            shared++;
            List<String> outside = clash.stream().filter(name -> !entry.getValue().contains(name)).toList();
            this.wrap("", outside.isEmpty() ? "shared: one press runs all of them" : "shared with " + Suggestions.humanList(outside), textWidth, Glass.WARN);
         }
      }

      this.clean = shared == 0;
      if (bound == 0) {
         this.summary = "No modules have a keybind yet.";
         this.wrap("", "Middle-click a module in the menu or use .bind <module> <key>.", this.panelWidth - 16, Glass.textDim());
      } else {
         this.summary = bound + (bound == 1 ? " module bound, " : " modules bound, ") + (shared == 0 ? "no conflicts" : shared + (shared == 1 ? " shared key" : " shared keys"));
      }
   }

   /** Adds {@code text} as one or more rows no wider than {@code maxWidth}, breaking at spaces; only the first row carries the key. */
   private void wrap(String key, String text, int maxWidth, int color) {
      String rest = text;
      String label = key;
      while (UiFont.width(rest) > maxWidth) {
         int cut = -1;
         for (int i = rest.indexOf(' '); i >= 0; i = rest.indexOf(' ', i + 1)) {
            if (UiFont.width(rest.substring(0, i)) > maxWidth) {
               break;
            }

            cut = i;
         }

         if (cut <= 0) {
            break;
         }

         this.rows.add(new Row(label, rest.substring(0, cut), color));
         label = "";
         rest = rest.substring(cut + 1);
      }

      this.rows.add(new Row(label, rest, color));
   }

   private int listTop() {
      return this.panelY + 34;
   }

   private int listBottom() {
      return this.panelY + this.panelHeight - 28;
   }

   private float maxScroll() {
      return Math.max(0, this.rows.size() * ROW_HEIGHT - (this.listBottom() - this.listTop()));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.scroll = Glass.approach(this.scroll, this.scrollTarget, 16.0F, delta);
      int x1 = this.panelX;
      int y1 = this.panelY;
      int x2 = x1 + this.panelWidth;
      int y2 = y1 + this.panelHeight;
      Glass.panel(g, x1, y1, x2, y2, 1.0F, 0.25F);
      UiFont.draw(g, this.title, x1 + 8, y1 + 8, Glass.text(), false);
      UiFont.draw(g, this.summary, x1 + 8, y1 + 20, this.clean ? Glass.OK : Glass.WARN, false);
      Glass.gradientBar(g, x1 + 8, y1 + 31, x2 - 8, y1 + 32, Glass.withAlpha(Glass.accent(), 160), Glass.withAlpha(Glass.accent2(), 40));
      int top = this.listTop();
      int bottom = this.listBottom();
      g.enableScissor(x1 + 4, top, x2 - 4, bottom);
      int first = Math.max(0, (int)(this.scroll / ROW_HEIGHT));
      for (int i = first; i < this.rows.size(); i++) {
         int y = top + i * ROW_HEIGHT - Math.round(this.scroll);
         if (y >= bottom) {
            break;
         }

         Row row = this.rows.get(i);
         if (!row.key().isEmpty()) {
            UiFont.draw(g, row.key(), x1 + 8, y + 1, Glass.accent2(), false);
         }

         UiFont.draw(g, row.text(), x1 + 8 + this.keyColumn, y + 1, row.color(), false);
      }

      g.disableScissor();
      float max = this.maxScroll();
      if (max > 0.0F) {
         int track = bottom - top;
         int thumb = Math.max(12, Math.round(track * (track / (track + max))));
         int thumbY = top + Math.round((track - thumb) * (this.scroll / max));
         g.fill(x2 - 5, thumbY, x2 - 3, thumbY + thumb, Glass.withAlpha(Glass.accent(), 170));
      }

      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      this.scrollTarget = Mth.clamp(this.scrollTarget - (float)scrollY * ROW_HEIGHT * 3.0F, 0.0F, this.maxScroll());
      return true;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      // Opened from the pause menu: the game stays paused while reading.
      return true;
   }
}
