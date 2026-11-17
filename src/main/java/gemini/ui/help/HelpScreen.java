package gemini.ui.help;

import gemini.Gemini;
import gemini.command.CommandHelp;
import gemini.input.GeminiKeys;
import gemini.input.Keys;
import gemini.ui.Glass;
import gemini.ui.GlassButton;
import gemini.ui.GlassWindow;
import gemini.ui.Theme;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.util.VersionBridge;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The in-game guide: What's new (from {@code assets/gemini/changelog.md}), Controls, Commands (from
 * {@link CommandHelp}, the list {@code .help} prints) and About. One window in the shared chrome (see
 * {@link GlassWindow}), keyboard-first: Tab / Left / Right or
 * 1-4 switch tabs, the arrow keys, Page Up / Down, Home / End and the wheel scroll.
 *
 * <p>It opens by itself once after an update (see {@link #maybeShowWhatsNew}) and from the home screen, the pause
 * bar, the module menu, {@code .help} and {@code .changelog}.
 */
public final class HelpScreen extends Screen {
   private static final String CHANGELOG = "/assets/gemini/changelog.md";
   private static List<Markdown.Block> changelogCache;
   private final Screen parent;
   private Tab tab;
   private HelpLayout layout;
   private DocLayout.Result doc = new DocLayout.Result(List.of(), 0);
   private float scroll;
   private float scrollTarget;
   private final GlassWindow window = new GlassWindow();
   private final float[] tabHover = new float[Tab.values().length];
   private GlassButton done;
   private int hoveredTab = -1;

   public enum Tab {
      WHATS_NEW("What's new", "New"),
      CONTROLS("Controls", "Keys"),
      COMMANDS("Commands", "Cmds"),
      ABOUT("About", "About");

      public final String label;
      public final String brief;

      Tab(String label, String brief) {
         this.label = label;
         this.brief = brief;
      }
   }

   public HelpScreen(Tab tab, Screen parent) {
      super(Component.literal("Gemini guide"));
      this.tab = tab == null ? Tab.WHATS_NEW : tab;
      this.parent = parent;
      if (this.tab == Tab.WHATS_NEW) {
         markSeen();
      }
   }

   /** Opens the guide over {@code parent}. */
   public static void open(Tab tab, Screen parent) {
      Minecraft.getInstance().gui.setScreen(new HelpScreen(tab, parent));
   }

   /**
    * Opens "What's new" over {@code parent} if this version's changelog has not been shown yet. Returns whether it
    * did. Called by the home screen when it first appears.
    */
   public static boolean maybeShowWhatsNew(Screen parent) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || gemini.config() == null || !HelpContent.shouldShowWhatsNew(gemini.config().lastSeenVersion(), Gemini.VERSION)) {
         return false;
      }

      open(Tab.WHATS_NEW, parent);
      return true;
   }

   private static void markSeen() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.config() != null) {
         gemini.config().setLastSeenVersion(Gemini.VERSION);
      }
   }

   // ---- content ---------------------------------------------------------------------------------------------

   /** The bundled changelog, parsed once. A missing resource becomes a one-line explanation instead of a crash. */
   static List<Markdown.Block> changelog() {
      if (changelogCache == null) {
         String text;
         try (InputStream in = HelpScreen.class.getResourceAsStream(CHANGELOG)) {
            text = in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
         } catch (IOException e) {
            text = null;
         }

         changelogCache = Markdown.parse(text == null ? "# Gemini " + Gemini.VERSION + "\n\nThe changelog is missing from this build; see the README." : text);
      }

      return changelogCache;
   }

   /**
    * The first {@code count} points of the changelog as plain text, for the home screen's "what's new" card.
    */
   public static List<String> teaser(int count) {
      return HelpContent.teaser(changelog(), count);
   }

   private List<Markdown.Block> blocks() {
      List<Markdown.Block> blocks = new ArrayList<>();
      switch (this.tab) {
         case WHATS_NEW -> blocks.addAll(changelog());
         case CONTROLS -> {
            String menuKey = GeminiKeys.OPEN_GUI.isUnbound() ? "Menu key (unbound)" : GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString();
            for (HelpContent.Section section : HelpContent.controls(menuKey, GeminiKeys.paletteHint())) {
               blocks.add(new Markdown.Heading(2, section.title()));
               List<List<String>> rows = new ArrayList<>();
               for (HelpContent.Control control : section.controls()) {
                  rows.add(List.of(control.input(), control.action()));
               }

               blocks.add(new Markdown.Table(List.of("Input", "Action"), rows));
            }
         }
         case COMMANDS -> {
            blocks.add(new Markdown.Paragraph("Type these in chat. Module names are case-insensitive; " + Markdown.CODE + ".<module>" + Markdown.RESET
               + " toggles a module and " + Markdown.CODE + ".<module> <setting> [value]" + Markdown.RESET + " reads or changes a setting."));
            for (CommandHelp.Group group : CommandHelp.Group.values()) {
               List<List<String>> rows = new ArrayList<>();
               for (CommandHelp.Entry entry : CommandHelp.of(group)) {
                  rows.add(List.of(Markdown.CODE + entry.usage() + Markdown.RESET, entry.description()));
               }

               if (!rows.isEmpty()) {
                  blocks.add(new Markdown.Heading(2, group.label));
                  blocks.add(new Markdown.Table(List.of("Command", "What it does"), rows));
               }
            }

            blocks.add(new Markdown.Paragraph("Module commands: " + CommandHelp.MODULE_COMMANDS));
         }
         case ABOUT -> {
            List<String> paragraphs = HelpContent.about(Gemini.VERSION, VersionBridge.activeVersionLabel());
            blocks.add(new Markdown.Heading(1, Gemini.NAME));
            for (String paragraph : paragraphs) {
               blocks.add(new Markdown.Paragraph(paragraph));
            }
         }
      }

      return blocks;
   }

   private void rebuild() {
      this.doc = DocLayout.layout(this.blocks(), this.layout.contentWidth(), UiFont::width);
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll());
      this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll());
   }

   private float maxScroll() {
      return this.layout == null ? 0.0F : Math.max(0, this.doc.height() - this.layout.contentHeight());
   }

   private void showTab(Tab next) {
      if (next != this.tab) {
         this.tab = next;
         this.scroll = 0.0F;
         this.scrollTarget = 0.0F;
         if (next == Tab.WHATS_NEW) {
            markSeen();
         }

         this.rebuild();
      }
   }

   // ---- widgets ---------------------------------------------------------------------------------------------

   @Override
   protected void init() {
      Tab[] tabs = Tab.values();
      int[] full = new int[tabs.length];
      int[] brief = new int[tabs.length];
      for (int i = 0; i < tabs.length; i++) {
         full[i] = UiFont.width(tabs[i].label);
         brief[i] = UiFont.width(tabs[i].brief);
      }

      this.layout = HelpLayout.compute(this.width, this.height, full, brief);
      HelpLayout l = this.layout;
      int doneWidth = Math.min(64, l.panelWidth() / 3);
      this.done = this.addRenderableWidget(
         new GlassButton(
               l.contentX() + l.contentWidth() + Theme.SPACE_S - doneWidth,
               l.footerY() + Theme.SPACE_L,
               doneWidth,
               Theme.BUTTON_HEIGHT_SMALL,
               CommonComponents.GUI_DONE,
               button -> this.onClose()
            )
            .primary()
      );
      this.rebuild();
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.window.frame();
      this.window.backdrop(g, this.width, this.height, mouseX, mouseY);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      float delta = this.window.delta();
      this.scroll = Glass.approach(this.scroll, this.scrollTarget, 16.0F, delta);
      HelpLayout l = this.layout;
      float a = this.window.open();
      int x1 = l.panelX();
      int y1 = l.panelY();
      int x2 = x1 + l.panelWidth();
      int y2 = y1 + l.panelHeight();
      this.done.setAlpha(a);
      // The version is in the "What's new" heading, so the title is just the name.
      this.window.frame(g, x1, y1, x2, y2);
      UiFont.draw(g, "Guide", l.contentX(), y1 + (HelpLayout.HEADER_HEIGHT - UiFont.capHeight(FontSize.LARGE)) / 2 + 1, Glass.fade(Glass.text(), a), FontSize.LARGE, FontWeight.BOLD, false);
      this.extractTabs(g, mouseX, mouseY, a);
      this.extractDocument(g, a);
      this.window.divider(g, x1 + 1, x2 - 1, l.footerY());
      String hint = this.tab == Tab.COMMANDS ? "Quick search: " + GeminiKeys.paletteHint() : "Tab switches pages";
      int hintRoom = this.done.getX() - Theme.SPACE_L - l.contentX();
      if (hintRoom > 60) {
         Controls.text(g, this.font, UiFont.ellipsize(hint, hintRoom), l.contentX(), UiFont.centerY(this.done.getY(), this.done.getHeight()), Glass.textFaint(), a);
      }

      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   private void extractTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      HelpLayout l = this.layout;
      Tab[] tabs = Tab.values();
      this.hoveredTab = -1;
      for (int i = 0; i < tabs.length; i++) {
         int x = l.tabX()[i];
         int w = l.tabWidth()[i];
         int y = l.tabsY();
         boolean active = tabs[i] == this.tab;
         boolean over = Glass.inside(mouseX, mouseY, x, y, x + w, y + HelpLayout.TAB_HEIGHT);
         if (over) {
            this.hoveredTab = i;
         }

         this.tabHover[i] = Glass.approach(this.tabHover[i], over ? 1.0F : 0.0F, Theme.HOVER_SPEED, this.window.delta());
         this.window.tab(g, x, y, w, l.shortTabs() ? tabs[i].brief : tabs[i].label, active, this.tabHover[i]);
      }
   }

   private void extractDocument(GuiGraphicsExtractor g, float a) {
      HelpLayout l = this.layout;
      int top = l.contentY();
      int bottom = top + l.contentHeight();
      int cx = l.contentX();
      int offset = Math.round(this.scroll);
      g.enableScissor(cx - 2, top, cx + l.contentWidth() + 2, bottom);
      for (DocLayout.Piece piece : this.doc.pieces()) {
         int y = top + piece.y() - offset;
         int h = piece.kind() == DocLayout.Kind.H1 ? Math.round(Theme.LINE * DocLayout.H1_SCALE) : Math.max(Theme.LINE, piece.height());
         if (y + h < top || y > bottom) {
            continue;
         }

         int x = cx + piece.x();
         switch (piece.kind()) {
            case H1 -> {
               UiFont.draw(g, piece.text(), x, y, Glass.fade(Glass.text(), a), FontSize.LARGE, FontWeight.BOLD, false);
            }
            case H2 -> Controls.text(g, this.font, piece.text(), x, y, Glass.lerp(Glass.text(), Glass.accent2(), 0.55F), a);
            case H3, CELL_KEY -> Controls.text(g, this.font, piece.text(), x, y, Glass.text(), a);
            case BODY, BULLET, CELL -> Controls.text(g, this.font, piece.text(), x, y, Glass.textDim(), a);
            case BULLET_MARK -> Controls.text(g, this.font, piece.text(), x, y, Glass.textFaint(), a);
            case TABLE_HEADER -> Controls.text(g, this.font, piece.text(), x, y, Glass.textFaint(), a);
            case RULE -> g.fill(x, y, x + piece.width(), y + 1, Glass.fade(0x18FFFFFF, a));
            case STRIPE -> Glass.roundRect(g, x, y, x + piece.width(), y + piece.height(), Theme.RADIUS_SMALL, Glass.fade(0x0CFFFFFF, a));
         }
      }

      g.disableScissor();
      float max = this.maxScroll();
      if (max > 0.0F) {
         this.window.scrollbar(g, l.panelX() + l.panelWidth() - Theme.SPACE_S - 3, top, bottom, this.scroll, max);
      }
   }

   // ---- input -----------------------------------------------------------------------------------------------

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      if (this.hoveredTab >= 0 && Keys.glfwButton(event.button()) == 0) {
         this.showTab(Tab.values()[this.hoveredTab]);
         return true;
      }

      return super.mouseClicked(event, doubled);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      this.scrollBy((float)-scrollY * Theme.LINE * 3.0F);
      return true;
   }

   private void scrollBy(float amount) {
      this.scrollTarget = Mth.clamp(this.scrollTarget + amount, 0.0F, this.maxScroll());
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      Tab[] tabs = Tab.values();
      int page = Math.max(Theme.LINE, this.layout.contentHeight() - 2 * Theme.LINE);
      if (key == Keys.TAB) {
         this.showTab(tabs[Math.floorMod(this.tab.ordinal() + (event.hasShiftDown() ? -1 : 1), tabs.length)]);
         return true;
      } else if (key == Keys.LEFT || key == Keys.RIGHT) {
         this.showTab(tabs[Math.floorMod(this.tab.ordinal() + (key == Keys.LEFT ? -1 : 1), tabs.length)]);
         return true;
      } else if (key >= 30 && key < 30 + tabs.length) {
         // SDL scancodes 30.. are the digit keys 1..
         this.showTab(tabs[key - 30]);
         return true;
      } else if (key == Keys.DOWN || key == Keys.UP) {
         this.scrollBy(key == Keys.DOWN ? Theme.LINE * 2 : -Theme.LINE * 2);
         return true;
      } else if (key == Keys.PAGE_DOWN || key == Keys.PAGE_UP || key == Keys.SPACE) {
         this.scrollBy(key == Keys.PAGE_UP ? -page : page);
         return true;
      } else if (key == Keys.HOME || key == Keys.END) {
         this.scrollTarget = key == Keys.HOME ? 0.0F : this.maxScroll();
         return true;
      }

      return super.keyPressed(event);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }
}
