package gemini.ui.palette;

import gemini.Gemini;
import gemini.command.CommandHelp;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
import gemini.input.Keys;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.HudOverlay;
import gemini.ui.KeybindReportScreen;
import gemini.ui.SandboxUi;
import gemini.ui.Theme;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.help.HelpScreen;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Quick search: one glass card with a search field over whatever is showing (the game, a menu, the title screen or
 * the pause menu). Type to fuzzy-search modules (and their setting names), pages and commands; Enter toggles a
 * module, opens a page or pre-fills a command in chat; Shift+Enter opens a module in the window dashboard; Tab
 * cycles the result kinds. Before a world is loaded it is configure-only: modules that cannot run say why instead
 * of toggling, and commands explain that they need a world.
 */
public final class PaletteScreen extends Screen {
   private static final int MAX_QUERY = 48;
   private static final int MAX_RESULTS = 200;
   private static final int RECENT_MAX = 8;
   private static List<String> recent = List.of();
   private static PaletteRanking.Kind lastKind = PaletteRanking.Kind.ALL;
   private final Screen parent;
   private final List<PaletteRanking.Candidate> candidates = new ArrayList<>();
   private final Map<Module, Float> knobs = new HashMap<>();
   private List<PaletteRanking.Result> results = List.of();
   private PaletteRanking.Kind kind = lastKind;
   private PaletteLayout layout = PaletteLayout.compute(320, 240);
   private String query = "";
   private int selected;
   private int scroll;
   private float open;
   private long lastFrame;
   private String status;
   private int statusColor = Glass.TEXT_DIM;
   private long statusUntil;
   private int hoverRow = -1;
   private int hoverKind = -1;

   /** Pages and quick actions the palette can open. */
   private enum Action {
      MODULES("Modules", "window menu dashboard modules settings", "Open the window menu on the module list", false),
      PROFILES("Presets & Configs", "profiles configs save load export import share code presets", "Profiles, export and import, share codes, built-in presets", false),
      KEYBINDS("Keybinds", "keys binds conflicts rebind", "Every key bind grouped by key, with conflicts to fix", false),
      BARITONE("Baritone", "bot pathing goto mine follow", "Live pathing status and bot quick starts", false),
      PANELS("Panel menu", "clickgui classic panels", "Open the classic panel ClickGUI", false),
      HUD_EDITOR("HUD layout editor", "hud widgets move drag layout", "Drag the HUD widgets into place (in a world)", true),
      KEYBIND_REPORT("Keybind report", "keys binds conflicts report", "Every bound module by key, shared keys marked", false),
      WHATS_NEW("Guide: What's new", "changelog help news release notes", "What changed in this version", false),
      CONTROLS("Guide: Controls", "help keys controls shortcuts mouse", "Keys and mouse for both menus, the HUD editor and quick search", false),
      COMMANDS("Guide: Commands", "help commands chat", "Every chat command with what it does", false),
      ABOUT("Guide: About", "help about version sandbox", "Version, sandbox mode, where files live", false),
      SHARE_CODE("Copy share code", "share export clipboard code profile", "Copy your current settings as a gemini:... share code", false),
      PANIC("Panic", "disable all off stop", "Switch every module off at once", false);

      final String label;
      final String keywords;
      final String description;
      final boolean needsWorld;

      Action(String label, String keywords, String description, boolean needsWorld) {
         this.label = label;
         this.keywords = keywords;
         this.description = description;
         this.needsWorld = needsWorld;
      }
   }

   public PaletteScreen(Screen parent) {
      super(Component.literal("Quick search"));
      this.parent = parent;
   }

   /** Opens the palette over the current screen (which it returns to). */
   public static void open(Minecraft client) {
      Screen current = client.gui.screen();
      if (!(current instanceof PaletteScreen)) {
         client.gui.setScreen(new PaletteScreen(current));
      }
   }

   // ---- candidates ------------------------------------------------------------------------------------------

   private void collect() {
      this.candidates.clear();
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null) {
         for (Module module : gemini.modules().getModules()) {
            List<String> settings = new ArrayList<>();
            for (Setting<?> setting : module.settings()) {
               settings.add(setting.name());
            }

            this.candidates.add(new PaletteRanking.Candidate(
               PaletteRanking.Kind.MODULE, "module:" + module.getName(), module.getName(), module.aliases(), module.getDescription(), settings, module
            ));
         }
      }

      for (Action action : Action.values()) {
         this.candidates.add(new PaletteRanking.Candidate(
            PaletteRanking.Kind.PAGE, "page:" + action.name(), action.label, List.of(action.keywords.split(" ")), action.description, null, action
         ));
      }

      for (CommandHelp.Entry entry : CommandHelp.ENTRIES) {
         this.candidates.add(new PaletteRanking.Candidate(
            PaletteRanking.Kind.COMMAND, "cmd:" + entry.usage(), entry.usage(), List.of(entry.word()), entry.description(), null, entry
         ));
      }
   }

   private void refresh() {
      this.results = PaletteRanking.rank(this.query, this.candidates, this.kind, recent, MAX_RESULTS);
      this.selected = Mth.clamp(this.selected, 0, Math.max(0, this.results.size() - 1));
      this.keepSelectionVisible();
   }

   private void keepSelectionVisible() {
      int rows = this.layout.rows();
      if (this.selected < this.scroll) {
         this.scroll = this.selected;
      } else if (this.selected >= this.scroll + rows) {
         this.scroll = this.selected - rows + 1;
      }

      this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.results.size() - rows));
   }

   // ---- lifecycle -------------------------------------------------------------------------------------------

   @Override
   protected void init() {
      this.minecraft.textInputManager().startTextInput(this);
      this.layout = PaletteLayout.compute(this.width, this.height);
      if (this.candidates.isEmpty()) {
         this.collect();
      }

      this.refresh();
   }

   @Override
   public void removed() {
      this.minecraft.textInputManager().stopTextInput(this);
      lastKind = this.kind;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   /** Over the game it does not pause (it is a quick action); opened from the pause menu, the game stays paused. */
   @Override
   public boolean isPauseScreen() {
      return this.parent != null && this.parent.isPauseScreen();
   }

   // ---- actions ---------------------------------------------------------------------------------------------

   private void say(String text, int color) {
      this.status = text == null ? null : text.replaceAll("§.", "");
      this.statusColor = color;
      this.statusUntil = Util.getMillis() + 3200L;
   }

   private void activate(PaletteRanking.Result result, boolean shift) {
      PaletteRanking.Candidate candidate = result.candidate();
      recent = PaletteRanking.remember(recent, candidate.key(), RECENT_MAX);
      Object payload = candidate.payload();
      if (payload instanceof Module module) {
         if (shift || module instanceof ClickGuiModule) {
            this.minecraft.gui.setScreen(WindowGuiScreen.forModule(module, this.parent));
         } else {
            this.toggle(module);
         }
      } else if (payload instanceof Action action) {
         this.run(action);
      } else if (payload instanceof CommandHelp.Entry entry) {
         this.command(entry);
      }
   }

   private void toggle(Module module) {
      if (!module.isSavedEnabled() && !module.canActivate()) {
         // Same rule as both menus: refused by Module.canActivate(), and the palette says why.
         this.say(module.blockedReason() + "  ·  Shift+Enter configures it", Glass.WARN);
         return;
      }

      module.toggle();
      saveConfig();
      this.say(module.getName() + (module.isSavedEnabled() ? " on" : " off"), module.isSavedEnabled() ? Glass.OK : Glass.TEXT_DIM);
   }

   private void run(Action action) {
      Minecraft client = this.minecraft;
      if (action.needsWorld && (client.player == null || !SingleplayerGate.allowed())) {
         this.say(action.label + " needs a single-player world. " + SingleplayerGate.denialMessage(), Glass.WARN);
         return;
      }

      switch (action) {
         case MODULES -> client.gui.setScreen(new WindowGuiScreen(WindowGuiScreen.Page.MODULES, this.parent));
         case PROFILES -> client.gui.setScreen(new WindowGuiScreen(WindowGuiScreen.Page.PROFILES, this.parent));
         case KEYBINDS -> client.gui.setScreen(new WindowGuiScreen(WindowGuiScreen.Page.KEYBINDS, this.parent));
         case BARITONE -> client.gui.setScreen(new WindowGuiScreen(WindowGuiScreen.Page.BARITONE, this.parent));
         case PANELS -> client.gui.setScreen(new ClickGuiScreen());
         case HUD_EDITOR -> client.gui.setScreen(ClickGuiScreen.hudEditor());
         case KEYBIND_REPORT -> KeybindReportScreen.open(client, this.parent);
         case WHATS_NEW -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, this.parent);
         case CONTROLS -> HelpScreen.open(HelpScreen.Tab.CONTROLS, this.parent);
         case COMMANDS -> HelpScreen.open(HelpScreen.Tab.COMMANDS, this.parent);
         case ABOUT -> HelpScreen.open(HelpScreen.Tab.ABOUT, this.parent);
         case SHARE_CODE -> this.copyShareCode();
         case PANIC -> {
            Gemini gemini = Gemini.getInstance();
            if (gemini != null) {
               ModuleManager modules = gemini.modules();
               int before = modules.getEnabledModules().size();
               modules.panic();
               int stopped = Math.max(0, before - modules.getEnabledModules().size());
               this.say(stopped == 0 ? "No modules were running" : stopped + (stopped == 1 ? " module switched off" : " modules switched off"), Glass.WARN);
            }
         }
      }
   }

   private void copyShareCode() {
      Gemini gemini = Gemini.getInstance();
      ConfigManager config = gemini == null ? null : gemini.config();
      if (config == null) {
         return;
      }

      String name = config.activeProfile() == null ? "my-settings" : config.activeProfile();
      String code = config.shareCode(name);
      Controls.setClipboard(code);
      this.say("Share code for " + name + " copied (" + code.length() + " characters)", Glass.OK);
      SandboxUi.notify(this.minecraft, "Share code copied", name + " · paste it in Presets & Configs or .config import code", HudOverlay.Severity.SUCCESS);
   }

   private void command(CommandHelp.Entry entry) {
      Minecraft client = this.minecraft;
      if (client.player == null || client.getConnection() == null) {
         this.say("Chat commands need a world: " + entry.usage(), Glass.WARN);
         return;
      }

      client.gui.setScreen(new ChatScreen(CommandHelp.prefill(entry.usage()), false));
   }

   private static void saveConfig() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.config() != null) {
         gemini.config().save();
      }
   }

   // ---- input -----------------------------------------------------------------------------------------------

   @Override
   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      if (key == Keys.ESCAPE) {
         if (!this.query.isEmpty()) {
            this.query = "";
            this.selected = 0;
            this.refresh();
         } else {
            this.onClose();
         }

         return true;
      } else if (Keys.isEnter(key)) {
         if (this.selected < this.results.size()) {
            this.activate(this.results.get(this.selected), event.hasShiftDown());
         }

         return true;
      } else if (key == Keys.TAB) {
         this.kind = this.kind.cycle(event.hasShiftDown() ? -1 : 1);
         this.selected = 0;
         this.scroll = 0;
         this.refresh();
         return true;
      } else if (key == Keys.DOWN || key == Keys.UP) {
         if (!this.results.isEmpty()) {
            this.selected = Math.floorMod(this.selected + (key == Keys.DOWN ? 1 : -1), this.results.size());
            this.keepSelectionVisible();
         }

         return true;
      } else if (key == Keys.PAGE_DOWN || key == Keys.PAGE_UP) {
         if (!this.results.isEmpty()) {
            int step = key == Keys.PAGE_DOWN ? this.layout.rows() : -this.layout.rows();
            this.selected = Mth.clamp(this.selected + step, 0, this.results.size() - 1);
            this.keepSelectionVisible();
         }

         return true;
      } else if (key == Keys.BACKSPACE) {
         if (!this.query.isEmpty()) {
            this.query = event.hasControlDown() ? "" : this.query.substring(0, this.query.length() - 1);
            this.selected = 0;
            this.refresh();
         }

         return true;
      } else if (event.isPaste()) {
         String pasted = Controls.clipboard().replaceAll("[\\r\\n\\t]+", " ").strip();
         this.query = (this.query + pasted).substring(0, Math.min(MAX_QUERY, this.query.length() + pasted.length()));
         this.selected = 0;
         this.refresh();
         return true;
      } else if (GeminiKeys.opensPalette(event)) {
         this.onClose();
         return true;
      }

      return super.keyPressed(event);
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      if (event.isAllowedChatCharacter() && this.query.length() < MAX_QUERY) {
         this.query = this.query + event.codepointAsString();
         this.selected = 0;
         this.scroll = 0;
         this.refresh();
         return true;
      }

      return super.charTyped(event);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      int button = Keys.glfwButton(event.button());
      if (this.hoverKind >= 0 && button == 0) {
         this.kind = PaletteRanking.Kind.values()[this.hoverKind];
         this.selected = 0;
         this.scroll = 0;
         this.refresh();
         return true;
      }

      if (this.hoverRow >= 0 && this.hoverRow < this.results.size() && (button == 0 || button == 1)) {
         this.selected = this.hoverRow;
         // Right-click is Shift+Enter: open the module in the dashboard instead of toggling it.
         this.activate(this.results.get(this.hoverRow), button == 1 || event.hasShiftDown());
         return true;
      }

      PaletteLayout l = this.layout;
      if (!Glass.inside(event.x(), event.y(), l.x(), l.y(), l.x() + l.width(), l.y() + l.height())) {
         this.onClose();
         return true;
      }

      return super.mouseClicked(event, doubled);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      if (!this.results.isEmpty()) {
         this.selected = Mth.clamp(this.selected - (int)Math.signum(scrollY), 0, this.results.size() - 1);
         this.keepSelectionVisible();
      }

      return true;
   }

   // ---- drawing ---------------------------------------------------------------------------------------------

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.open = Glass.approach(this.open, 1.0F, 16.0F, delta);
      float a = this.open;
      PaletteLayout l = this.layout;
      // Slides down a few pixels as it fades in.
      int lift = Math.round((1.0F - Glass.easeOutCubic(this.open)) * 6.0F);
      int x1 = l.x();
      int x2 = l.x() + l.width();
      g.pose().pushMatrix();
      g.pose().translate(0.0F, -lift);
      Glass.panel(g, x1, l.y(), x2, l.y() + l.height(), Theme.RADIUS_LARGE, Glass.surface(0), Glass.accent(), a, 0.35F);
      this.extractField(g, x1 + Theme.SPACE_L, l.fieldY(), l.width() - 2 * Theme.SPACE_L, a, now);
      this.extractKinds(g, x1 + Theme.SPACE_L, l.kindsY(), mouseX, mouseY + lift, a);
      this.extractRows(g, x1 + Theme.SPACE_S, l.listY(), l.width() - 2 * Theme.SPACE_S, mouseX, mouseY + lift, a, delta);
      this.extractFooter(g, x1 + Theme.SPACE_L, l.footerY(), l.width() - 2 * Theme.SPACE_L, a, now);
      g.pose().popMatrix();
      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   private void extractField(GuiGraphicsExtractor g, int x, int y, int w, float a, long now) {
      int accent = Glass.accent();
      Glass.roundRect(g, x, y, x + w, y + PaletteLayout.FIELD_HEIGHT, Theme.RADIUS, Glass.fade(Glass.withAlpha(Glass.surface(2), 255), a));
      Glass.roundOutline(g, x, y, x + w, y + PaletteLayout.FIELD_HEIGHT, Theme.RADIUS, Glass.fade(Glass.withAlpha(accent, 170), a));
      // A small magnifier: ring plus handle.
      int mx = x + 6;
      int my = y + 5;
      Glass.roundOutline(g, mx, my, mx + 7, my + 7, 3, Glass.fade(Glass.accent2(), a));
      g.fill(mx + 6, my + 6, mx + 8, my + 8, Glass.fade(Glass.accent2(), a));
      int textX = x + 18;
      int room = w - 18 - Theme.SPACE_M;
      String right = SingleplayerGate.allowed() && this.minecraft.player != null ? null : "configure only";
      if (right != null) {
         int rw = this.font.width(right) + 8;
         int rx = x + w - rw - 4;
         Glass.roundRect(g, rx, y + 4, rx + rw, y + 14, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(Glass.WARN, 56), a));
         Controls.text(g, this.font, right, rx + 4, y + 5, Glass.WARN, a);
         room -= rw + Theme.SPACE_S;
      }

      boolean blink = now / 450L % 2L == 0L;
      if (this.query.isEmpty()) {
         Controls.text(g, this.font, Controls.fit(this.font, "Search modules, settings, commands and pages...", room), textX, y + 5, Glass.TEXT_FAINT, a);
         if (blink) {
            g.fill(textX - 1, y + 4, textX, y + 14, Glass.fade(Glass.TEXT, a));
         }
      } else {
         // Keep the end of a long query (where the caret is) in view.
         String shown = this.query;
         while (shown.length() > 1 && this.font.width(shown) > room - 4) {
            shown = shown.substring(1);
         }

         Controls.text(g, this.font, shown, textX, y + 5, Glass.TEXT, a);
         if (blink) {
            int cx = textX + this.font.width(shown) + 1;
            g.fill(cx, y + 4, cx + 1, y + 14, Glass.fade(Glass.TEXT, a));
         }
      }
   }

   private void extractKinds(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float a) {
      PaletteRanking.Kind[] kinds = PaletteRanking.Kind.values();
      this.hoverKind = -1;
      int cursor = x;
      for (int i = 0; i < kinds.length; i++) {
         String label = kinds[i].label;
         int w = this.font.width(label) + 10;
         boolean active = kinds[i] == this.kind;
         boolean over = Glass.inside(mouseX, mouseY, cursor, y, cursor + w, y + PaletteLayout.KINDS_HEIGHT);
         if (over) {
            this.hoverKind = i;
         }

         int fill = active ? Glass.withAlpha(Glass.accent(), 130) : Glass.withAlpha(Glass.surface(over ? 3 : 1), 220);
         Glass.roundRect(g, cursor, y, cursor + w, y + PaletteLayout.KINDS_HEIGHT, Theme.RADIUS_SMALL, Glass.fade(fill, a));
         Controls.text(g, this.font, label, cursor + 5, y + 2, active ? Glass.TEXT : Theme.labelColor(true, over ? 1.0F : 0.0F), a);
         cursor += w + Theme.SPACE_XS + 1;
      }

      String count = this.results.size() >= MAX_RESULTS ? MAX_RESULTS + "+" : Integer.toString(this.results.size());
      String hint = count + (this.results.size() == 1 ? " result" : " results") + "  ·  Tab";
      int hw = this.font.width(hint);
      int right = this.layout.x() + this.layout.width() - Theme.SPACE_L;
      if (cursor + Theme.SPACE_M + hw <= right) {
         Controls.text(g, this.font, hint, right - hw, y + 2, Glass.TEXT_FAINT, a);
      }
   }

   private void extractRows(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float a, float delta) {
      this.hoverRow = -1;
      int rows = this.layout.rows();
      if (this.results.isEmpty()) {
         String empty = this.query.isEmpty() ? "Nothing here." : "No " + (this.kind == PaletteRanking.Kind.ALL ? "results" : this.kind.label.toLowerCase()) + " for \"" + this.query + "\"";
         String text = Controls.fit(this.font, empty, w - 16);
         Controls.text(g, this.font, text, x + (w - this.font.width(text)) / 2, y + PaletteLayout.ROW_HEIGHT, Glass.TEXT_FAINT, a);
         if (this.kind != PaletteRanking.Kind.ALL) {
            String tip = "Tab shows all kinds";
            Controls.text(g, this.font, tip, x + (w - this.font.width(tip)) / 2, y + PaletteLayout.ROW_HEIGHT + Theme.LINE + 2, Glass.TEXT_FAINT, a * 0.8F);
         }

         return;
      }

      for (int i = 0; i < rows && this.scroll + i < this.results.size(); i++) {
         int index = this.scroll + i;
         int ry = y + i * PaletteLayout.ROW_HEIGHT;
         boolean over = Glass.inside(mouseX, mouseY, x, ry, x + w, ry + PaletteLayout.ROW_HEIGHT);
         if (over) {
            this.hoverRow = index;
         }

         this.extractRow(g, this.results.get(index), x, ry, w, index == this.selected, over, a, delta);
      }

      if (this.results.size() > rows) {
         int track = rows * PaletteLayout.ROW_HEIGHT;
         int thumb = Math.max(10, track * rows / this.results.size());
         int thumbY = y + (track - thumb) * this.scroll / Math.max(1, this.results.size() - rows);
         int sx = x + w + 1;
         Glass.roundRect(g, sx, thumbY, sx + 2, thumbY + thumb, 1, Glass.fade(Glass.withAlpha(Glass.accent(), 170), a));
      }
   }

   private void extractRow(GuiGraphicsExtractor g, PaletteRanking.Result result, int x, int y, int w, boolean selected, boolean over, float a, float delta) {
      PaletteRanking.Candidate candidate = result.candidate();
      int h = PaletteLayout.ROW_HEIGHT - 2;
      if (selected || over) {
         Glass.roundRect(g, x, y, x + w, y + h, Theme.RADIUS, Glass.fade(selected ? Glass.withAlpha(Glass.accent(), 60) : 0x10FFFFFF, a));
         if (selected) {
            g.fill(x, y + 4, x + 2, y + h - 4, Glass.fade(Glass.accent2(), a));
         }
      }

      String badge;
      int badgeColor;
      switch (candidate.kind()) {
         case MODULE -> {
            badge = "MOD";
            badgeColor = ((Module)candidate.payload()).getCategory().accent();
         }
         case PAGE -> {
            badge = "PAGE";
            badgeColor = Glass.INFO;
         }
         default -> {
            badge = "CMD";
            badgeColor = Glass.WARN;
         }
      }

      int textY = y + (h - Theme.FONT_HEIGHT) / 2;
      int bw = this.font.width("PAGE") + 6;
      Glass.roundRect(g, x + 6, textY - 1, x + 6 + bw, textY + 9, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(badgeColor, 50), a));
      String b = badge;
      Controls.text(g, this.font, b, x + 6 + (bw - this.font.width(b)) / 2, textY, Glass.lerp(badgeColor, 0xFFFFFFFF, 0.3F), a);
      int left = x + 6 + bw + Theme.SPACE_M;
      int right = x + w - Theme.SPACE_M;
      Object payload = candidate.payload();
      boolean dim = false;
      if (payload instanceof Module module) {
         boolean blocked = !(module instanceof ClickGuiModule) && !module.canActivate();
         dim = blocked;
         if (!(module instanceof ClickGuiModule)) {
            float target = (blocked ? module.isSavedEnabled() : module.isEnabled()) ? 1.0F : 0.0F;
            float knob = Glass.approach(this.knobs.getOrDefault(module, target), target, 12.0F, delta);
            this.knobs.put(module, knob);
            Glass.toggle(g, right - 18, y + (h - 10) / 2, knob, blocked ? 0xFF59606E : Glass.accent(), a * (blocked ? 0.6F : 1.0F));
            right -= 18 + Theme.SPACE_M;
         }

         int key = module.getKeybind();
         if (key > 0) {
            String label = GeminiKeys.displayName(key);
            int kw = this.font.width(label) + 6;
            if (right - kw > left + 40) {
               Glass.roundRect(g, right - kw, textY - 1, right, textY + 9, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(Glass.surface(3), 255), a));
               Controls.text(g, this.font, label, right - kw + 3, textY, Glass.TEXT_DIM, a);
               right -= kw + Theme.SPACE_S;
            }
         }
      }

      String name = candidate.name();
      String extra = result.setting() != null ? "setting: " + result.setting() : null;
      if (extra == null && payload instanceof Module module) {
         extra = module.getCategory().displayName();
      }

      int nameRoom = right - left;
      int nameWidth = Math.min(this.font.width(name), extra == null ? nameRoom : Math.max(nameRoom * 3 / 5, nameRoom - this.font.width(extra) - Theme.SPACE_M));
      String shownName = Controls.fit(this.font, name, nameWidth);
      int color = dim ? Glass.TEXT_DIM : (selected ? Glass.TEXT : Theme.labelColor(true, over ? 1.0F : 0.4F));
      Controls.text(g, this.font, shownName, left, textY, color, a);
      if (extra != null) {
         int ex = left + this.font.width(shownName) + Theme.SPACE_M;
         String shownExtra = Controls.fit(this.font, extra, right - ex);
         Controls.text(g, this.font, shownExtra, ex, textY, result.setting() != null ? Glass.accent2() : Glass.TEXT_FAINT, a);
      }
   }

   private void extractFooter(GuiGraphicsExtractor g, int x, int y, int w, float a, long now) {
      Glass.gradientBar(g, x, y - 2, x + w, y - 1, Glass.fade(Glass.withAlpha(Glass.accent(), 90), a), Glass.fade(Glass.withAlpha(Glass.accent2(), 0), a));
      String line1;
      int color1 = Glass.TEXT_DIM;
      if (this.status != null && now < this.statusUntil) {
         line1 = this.status;
         color1 = this.statusColor;
      } else if (this.selected < this.results.size()) {
         PaletteRanking.Candidate c = this.results.get(this.selected).candidate();
         line1 = c.description() == null ? "" : c.description();
      } else {
         line1 = "";
      }

      Controls.text(g, this.font, Controls.fit(this.font, line1, w), x, y + 2, color1, a);
      String keys = this.keysHint();
      Controls.text(g, this.font, Controls.fit(this.font, keys, w), x, y + 2 + Theme.LINE, Glass.TEXT_FAINT, a);
   }

   private String keysHint() {
      if (this.selected >= this.results.size()) {
         return "Tab: kinds  ·  Esc: close";
      }

      Object payload = this.results.get(this.selected).candidate().payload();
      if (payload instanceof Module module) {
         return module instanceof ClickGuiModule ? "Enter: open in the window menu  ·  Esc: close" : "Enter: toggle  ·  Shift+Enter: settings  ·  Tab: kinds  ·  Esc: close";
      } else if (payload instanceof CommandHelp.Entry) {
         return "Enter: type it in chat  ·  Tab: kinds  ·  Esc: close";
      }

      return "Enter: open  ·  Tab: kinds  ·  Esc: close";
   }
}
