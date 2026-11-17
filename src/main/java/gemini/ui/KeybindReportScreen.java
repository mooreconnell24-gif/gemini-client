package gemini.ui;

import gemini.Gemini;
import gemini.command.Suggestions;
import gemini.input.GeminiKeys;
import gemini.input.KeybindConflicts;
import gemini.input.KeybindReport;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
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

/**
 * The keybind report as one scrollable window in the shared chrome (see {@link GlassWindow}): every bound module grouped by key, with keys that are shared
 * (between modules, or with a vanilla control) marked. It shows what {@code .binds} prints to chat.
 */
public final class KeybindReportScreen extends Screen {
   private static final int ROW_HEIGHT = 15;
   /** Height of the chip a key name sits on, and its padding left and right of the name. */
   private static final int CHIP = 12;
   private static final int CHIP_PAD = 5;
   private final Screen parent;
   private final GlassWindow window = new GlassWindow();
   private GlassButton done;
   private int pad;
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
      int[] panel = ScreenBounds.window(this.width, this.height, 380, 260);
      this.panelX = panel[0];
      this.panelY = panel[1];
      this.panelWidth = panel[2];
      this.panelHeight = panel[3];
      this.pad = ScreenBounds.pad(this.panelWidth);
      this.buildRows();
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll());
      this.scroll = this.scrollTarget;
      this.done = this.addRenderableWidget(
         new GlassButton(
               this.panelX + this.panelWidth - this.pad - 64,
               this.panelY + this.panelHeight - GlassWindow.FOOTER + Theme.SPACE_L,
               64,
               Theme.BUTTON_HEIGHT_SMALL,
               CommonComponents.GUI_DONE,
               button -> this.onClose()
            )
            .primary()
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
         this.keyColumn = Math.max(this.keyColumn, UiFont.width(GeminiKeys.displayName(key), FontSize.BODY, FontWeight.MEDIUM) + 2 * CHIP_PAD + 10);
      }

      this.keyColumn = Math.min(this.keyColumn, this.panelWidth / 3);
      int textWidth = this.panelWidth - 2 * this.pad - this.keyColumn - 8;
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
         this.keyColumn = 0;
         this.wrap("", "Middle-click a module in the menu or use .bind <module> <key>.", this.panelWidth - 2 * this.pad - 8, Glass.textDim());
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
      return this.panelY + GlassWindow.HEADER;
   }

   private int listBottom() {
      return this.panelY + this.panelHeight - GlassWindow.FOOTER - Theme.SPACE_S;
   }

   private float maxScroll() {
      return Math.max(0, this.rows.size() * ROW_HEIGHT - (this.listBottom() - this.listTop()));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      float a = this.window.open();
      this.scroll = Glass.approach(this.scroll, this.scrollTarget, 16.0F, this.window.delta());
      int x1 = this.panelX;
      int y1 = this.panelY;
      int x2 = x1 + this.panelWidth;
      int y2 = y1 + this.panelHeight;
      int left = x1 + this.pad;
      this.done.setAlpha(a);
      this.window.frame(g, x1, y1, x2, y2);
      this.window.header(g, left - GlassWindow.PAD, y1, x2 - this.pad + GlassWindow.PAD, this.title.getString(), this.summary, this.clean ? Glass.textDim() : Glass.WARN);
      this.window.divider(g, x1 + 1, x2 - 1, y2 - GlassWindow.FOOTER);
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
         int textY = UiFont.centerY(y, ROW_HEIGHT);
         if (!row.key().isEmpty()) {
            // The key on a small chip, like a key cap.
            String key = UiFont.ellipsize(row.key(), this.keyColumn - 2 * CHIP_PAD - 6, FontSize.BODY, FontWeight.MEDIUM);
            int chipWidth = Math.max(CHIP + 2, UiFont.width(key, FontSize.BODY, FontWeight.MEDIUM) + 2 * CHIP_PAD);
            int chipY = y + (ROW_HEIGHT - CHIP) / 2;
            Glass.roundRect(g, left, chipY, left + chipWidth, chipY + CHIP, Theme.RADIUS, Glass.fade(Glass.surface(3), a));
            Glass.roundOutline(g, left, chipY, left + chipWidth, chipY + CHIP, Theme.RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, 20), a));
            UiFont.drawCentered(g, key, left + chipWidth / 2, UiFont.centerY(chipY, CHIP), Glass.fade(Glass.text(), a), FontSize.BODY, FontWeight.MEDIUM, false);
         }

         UiFont.draw(g, row.text(), left + this.keyColumn, textY, Glass.fade(row.color(), a), false);
      }

      g.disableScissor();
      this.window.scrollbar(g, x2 - Theme.SPACE_S - 3, top, bottom, this.scroll, this.maxScroll());

      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.window.frame();
      this.window.backdrop(g, this.width, this.height, mouseX, mouseY);
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
