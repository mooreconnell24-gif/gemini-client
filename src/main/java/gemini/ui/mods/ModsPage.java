package gemini.ui.mods;

import gemini.mods.DependencyPlan;
import gemini.mods.InstalledMods;
import gemini.mods.ModBrowser;
import gemini.mods.ModInstaller;
import gemini.mods.Modrinth;
import gemini.mods.ModrinthClient;
import gemini.mods.ModrinthUrls;
import gemini.mods.ProjectType;
import gemini.mods.SafeFiles;
import gemini.mods.TextExcerpt;
import gemini.mods.UpdateCheck;
import gemini.mods.VersionPicker;
import gemini.ui.Glass;
import gemini.ui.clickgui.controls.TextField;
import gemini.ui.clickgui.window.DashboardPage;
import gemini.ui.font.UiFont;
import gemini.util.ClientThread;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The "Mods" page of the window menu: search Modrinth for Fabric mods (and resource packs, and shader packs when Iris
 * is running) made for the running game version, look at one, and install it with its required dependencies; plus an
 * Installed tab for the mods folder with update checks, Disable and Remove.
 *
 * <p>Fabric sets mods up once, when the game starts, so a downloaded mod is put in the mods folder and loads the next
 * time Minecraft starts; the page says so next to the Install button. Resource packs can be turned on straight away.
 * All network and file work happens on worker threads; results come back through {@link ClientThread}.
 */
public final class ModsPage extends DashboardPage {
   private static final int ROW = 30;
   private static final int ICON = 24;
   private static final int INSTALLED_ROW = 20;
   private static final long SEARCH_DELAY_MS = 350L;
   private static final long CONFIRM_MS = 4000L;
   private static Tab tab = Tab.BROWSE;
   private static ProjectType type = ProjectType.MOD;
   private static ModrinthUrls.Sort sort = ModrinthUrls.Sort.RELEVANCE;
   private static String rememberedQuery = "";
   private final TextField search = new TextField(64, null);
   private ModBrowser browser;
   private IconCache icons;
   // browse
   private final List<Modrinth.Hit> hits = new ArrayList<>();
   private int totalHits;
   private boolean hasMore;
   private boolean loading;
   private String listError;
   private int searchSerial;
   private String searchedFor;
   private ProjectType searchedType;
   private ModrinthUrls.Sort searchedSort;
   private long searchDue;
   private String lastText = "";
   private boolean sortOpen;
   private Modrinth.Hit selected;
   private Detail detail;
   private int detailSerial;
   private boolean confirmApply;
   private String confirmNote;
   private float browseScroll;
   // installed
   private List<InstalledMods.Entry> entries = List.of();
   private boolean scanning;
   private String installedError;
   private Map<Path, UpdateCheck.Update> updates = Map.of();
   private boolean checkingUpdates;
   private String updateStatus;
   private Path confirmPath;
   private String confirmAction;
   private long confirmUntil;
   private float installedScroll;

   private enum Tab {
      BROWSE,
      INSTALLED
   }

   /** Everything the detail pane shows for one project. */
   private static final class Detail {
      final Modrinth.Hit hit;
      final ProjectType type;
      boolean loading = true;
      String error;
      Modrinth.Project project;
      Modrinth.Version version;
      DependencyPlan plan;
      boolean alreadyInstalled;
      Path existingPack;
      List<String> excerpt = List.of();

      Detail(Modrinth.Hit hit, ProjectType type) {
         this.hit = hit;
         this.type = type;
      }
   }

   public ModsPage(Host host) {
      super(host);
   }

   @Override
   public String title() {
      return "Mods";
   }

   @Override
   public String hint() {
      return tab == Tab.BROWSE ? "Type to search Modrinth  ·  Enter searches now" : "Remove moves files to mods/.gemini-trash, nothing is deleted";
   }

   @Override
   public void opened() {
      if (this.browser == null) {
         this.browser = ModBrowser.get();
         this.icons = new IconCache(this.browser.client());
         this.search.setText(rememberedQuery);
         this.lastText = rememberedQuery;
      }

      if (type == ProjectType.SHADER && !ModBrowser.irisLoaded()) {
         type = ProjectType.MOD;
      }

      if (tab == Tab.BROWSE) {
         if (this.hits.isEmpty() && !this.loading) {
            this.runSearch();
         }

         this.search.focus(this.search.text(), false);
      } else {
         this.rescan();
      }
   }

   @Override
   protected TextField[] fields() {
      return new TextField[]{this.search};
   }

   /** On the Browse tab typing always goes to the search box, even after clicking a result. */
   @Override
   public void blurExcept(Object owner) {
      super.blurExcept(owner);
      if (tab == Tab.BROWSE && !this.search.focused()) {
         this.search.focus(this.search.text(), false);
      }
   }

   /** Tab still switches the menu's pages while the search box has the keyboard. */
   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
      if (event.key() == gemini.input.Keys.TAB) {
         return false;
      }

      return super.keyPressed(event);
   }

   @Override
   protected void fieldSubmitted(TextField field) {
      this.runSearch();
   }

   @Override
   protected void fieldKey(TextField field, net.minecraft.client.input.KeyEvent event) {
      if (event.key() == gemini.input.Keys.ESCAPE) {
         field.blur();
      } else if (gemini.input.Keys.isEnter(event.key())) {
         this.runSearch();
      } else {
         field.keyPressed(event);
      }
   }

   // ---- rendering --------------------------------------------------------------------------------------------

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
      if (this.browser == null) {
         this.opened();
      }

      long now = Util.getMillis();
      int tx = x + 10;
      tx += this.tabLabel(g, "Browse", tx, y + 9, tab == Tab.BROWSE, mouseX, mouseY, Tab.BROWSE) + 12;
      int pending = this.browser.pendingRestart();
      this.tabLabel(g, pending > 0 ? "Installed (" + pending + " new)" : "Installed", tx, y + 9, tab == Tab.INSTALLED, mouseX, mouseY, Tab.INSTALLED);
      if (tab == Tab.BROWSE) {
         this.renderBrowse(g, x, y, w, h, mouseX, mouseY, now);
      } else {
         this.renderInstalled(g, x, y, w, h, mouseX, mouseY, now);
      }
   }

   private int tabLabel(GuiGraphicsExtractor g, String label, int x, int y, boolean active, int mouseX, int mouseY, Tab target) {
      int tw = UiFont.width(label);
      boolean over = inside(mouseX, mouseY, x - 2, y - 3, tw + 4, 14);
      this.text(g, label, x, y, active ? Glass.text() : (over ? Glass.lerp(Glass.textDim(), Glass.text(), 0.5F) : Glass.textDim()));
      if (active) {
         g.fill(x, y + 11, x + tw, y + 12, this.c(Glass.accent()));
      }

      this.host.hit(x - 2, y - 3, tw + 4, 14, null, (b, mx, my, d) -> this.switchTab(target));
      return tw;
   }

   private void switchTab(Tab target) {
      if (tab == target) {
         return;
      }

      if (tab == Tab.BROWSE) {
         this.browseScroll = this.scrollTarget;
      } else {
         this.installedScroll = this.scrollTarget;
      }

      tab = target;
      this.sortOpen = false;
      this.scroll = this.scrollTarget = target == Tab.BROWSE ? this.browseScroll : this.installedScroll;
      if (target == Tab.INSTALLED) {
         this.search.blur();
         this.rescan();
      } else if (this.hits.isEmpty()) {
         this.runSearch();
      }
   }

   // ---- browse -----------------------------------------------------------------------------------------------

   private void renderBrowse(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
      // type switch, right-aligned on the tab row
      List<ProjectType> types = new ArrayList<>(List.of(ProjectType.MOD, ProjectType.RESOURCE_PACK));
      if (ModBrowser.irisLoaded()) {
         types.add(ProjectType.SHADER);
      }

      int rx = x + w - 10;
      for (int i = types.size() - 1; i >= 0; i--) {
         ProjectType t = types.get(i);
         int tw = UiFont.width(t.label);
         rx -= tw;
         boolean active = t == type;
         boolean over = inside(mouseX, mouseY, rx - 3, y + 6, tw + 6, 14);
         this.text(g, t.label, rx, y + 9, active ? Glass.text() : (over ? Glass.lerp(Glass.textDim(), Glass.text(), 0.5F) : Glass.textFaint()));
         if (active) {
            g.fill(rx, y + 20, rx + tw, y + 21, this.c(Glass.accent()));
         }

         this.host.hit(rx - 3, y + 6, tw + 6, 14, null, (b, mx, my, d) -> {
            if (type != t) {
               type = t;
               this.runSearch();
            }
         });
         rx -= 12;
      }

      // search field and sort
      int rowY = y + 26;
      String sortLabel = sort.label;
      int sortW = UiFont.width(sortLabel) + 18;
      int fieldW = w - 20 - sortW - 6;
      String placeholder = "Search " + type.label.toLowerCase(Locale.ROOT) + " for " + this.browser.gameVersion() + "...";
      this.field(g, this.search, x + 10, rowY, fieldW, placeholder, mouseX, mouseY, false);
      int sx = x + 10 + fieldW + 6;
      boolean overSort = inside(mouseX, mouseY, sx, rowY, sortW, 13);
      Glass.roundRect(g, sx, rowY, sx + sortW, rowY + 13, gemini.ui.Theme.RADIUS_SMALL, this.c(overSort || this.sortOpen ? Glass.surface(3) : Glass.surface(2)));
      this.text(g, sortLabel, sx + 5, rowY + 3, overSort ? Glass.text() : Glass.textDim());
      caret(g, sx + sortW - 10, rowY + 5, this.c(Glass.textDim()));
      this.host.hit(sx, rowY, sortW, 13, null, (b, mx, my, d) -> this.sortOpen = !this.sortOpen);
      if (overSort) {
         this.host.tip("Order of the results");
      }

      String text = this.search.text();
      if (!text.equals(this.lastText)) {
         this.lastText = text;
         this.searchDue = now + SEARCH_DELAY_MS;
      }

      if (this.searchDue > 0L && now >= this.searchDue) {
         this.runSearch();
      }

      int top = rowY + 20;
      int bottom = y + h - 4;
      int listW = Math.max(150, Math.round((w - 30) * 0.5F));
      int listX = x + 10;
      this.renderList(g, listX, top, listW, bottom, mouseX, mouseY);
      int detailX = listX + listW + 14;
      this.renderDetail(g, detailX, top, x + w - 10 - detailX, bottom, mouseX, mouseY);
      if (this.sortOpen) {
         this.renderSortMenu(g, x, y, w, h, sx, rowY + 15, sortW, mouseX, mouseY);
      }
   }

   private void renderSortMenu(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx0, int my0, int width, int mouseX, int mouseY) {
      // clicks anywhere else close the menu
      this.host.hit(x, y, w, h, null, (b, mx, my, d) -> this.sortOpen = false);
      int itemW = width;
      for (ModrinthUrls.Sort s : ModrinthUrls.Sort.values()) {
         itemW = Math.max(itemW, UiFont.width(s.label) + 12);
      }

      int left = mx0 + width - itemW;
      int hgt = ModrinthUrls.Sort.values().length * 14 + 4;
      Glass.roundRect(g, left, my0, left + itemW, my0 + hgt, gemini.ui.Theme.RADIUS_SMALL, this.c(Glass.surface(3)));
      Glass.roundOutline(g, left, my0, left + itemW, my0 + hgt, gemini.ui.Theme.RADIUS_SMALL, this.c(0xFFFFFFFF, 24));
      int iy = my0 + 2;
      for (ModrinthUrls.Sort s : ModrinthUrls.Sort.values()) {
         boolean over = inside(mouseX, mouseY, left, iy, itemW, 14);
         if (over) {
            g.fill(left + 1, iy, left + itemW - 1, iy + 14, this.c(0xFFFFFFFF, 14));
         }

         this.text(g, s.label, left + 6, iy + 3, s == sort ? Glass.text() : Glass.textDim());
         this.host.hit(left, iy, itemW, 14, null, (b, mx, my, d) -> {
            this.sortOpen = false;
            if (sort != s) {
               sort = s;
               this.runSearch();
            }
         });
         iy += 14;
      }
   }

   private void renderList(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mouseX, int mouseY) {
      int contentH = this.hits.size() * ROW + (this.hasMore || this.loading ? ROW : 0);
      this.viewport(x, top, w + 4, bottom, contentH);
      if (this.hits.isEmpty()) {
         String message = this.loading ? "Searching Modrinth..." : (this.listError != null ? this.listError : "Nothing found for " + this.browser.gameVersion() + ".");
         int color = this.listError != null && !this.loading ? Glass.WARN : Glass.textFaint();
         this.textFit(g, message, x + 4, top + 12, w - 8, color);
         if (this.listError != null && !this.loading) {
            int bw = this.buttonWidth("Try again");
            this.button(g, x + 4, top + 28, bw, 14, "Try again", mouseX, mouseY, TONE_NEUTRAL, null, "Search again", (b, mx, my, d) -> this.runSearch());
         }

         return;
      }

      g.enableScissor(x, top, x + w, bottom);
      int ry = top - Math.round(this.scroll);
      for (Modrinth.Hit hit : this.hits) {
         if (ry + ROW >= top && ry <= bottom) {
            this.drawHit(g, hit, x, ry, w, mouseX, mouseY);
         }

         ry += ROW;
      }

      if (this.hasMore || this.loading) {
         if (ry <= bottom + ROW) {
            String more = this.listError != null ? this.listError : "Loading more...";
            this.textFit(g, more, x + 6, ry + 10, w - 12, this.listError != null ? Glass.WARN : Glass.textFaint());
            if (!this.loading && this.listError == null && this.hasMore) {
               this.loadMore();
            }
         }
      }

      g.disableScissor();
      this.scrollbar(g, x + w + 2, contentH);
   }

   private void drawHit(GuiGraphicsExtractor g, Modrinth.Hit hit, int x, int y, int w, int mouseX, int mouseY) {
      boolean over = inside(mouseX, mouseY, x, y, w, ROW - 2) && mouseY >= this.viewTop && mouseY < this.viewBottom;
      boolean chosen = this.selected != null && this.selected.projectId().equals(hit.projectId());
      if (chosen || over) {
         Glass.roundRect(g, x, y, x + w, y + ROW - 2, gemini.ui.Theme.RADIUS_SMALL, this.c(0xFFFFFFFF, chosen ? 18 : 9));
      }

      this.drawIcon(g, hit.iconUrl(), hit.title(), x + 3, y + 2, ICON);
      int textX = x + ICON + 9;
      String downloads = compact(hit.downloads());
      int dw = UiFont.width(downloads);
      this.text(g, downloads, x + w - 4 - dw, y + 4, Glass.textFaint());
      int titleRoom = x + w - 10 - dw - textX;
      String title = gemini.ui.clickgui.controls.Controls.fit(this.font(), hit.title(), titleRoom);
      this.text(g, title, textX, y + 4, Glass.text());
      int afterTitle = textX + UiFont.width(title) + 5;
      if (!hit.author().isEmpty() && afterTitle < x + w - 14 - dw) {
         this.textFit(g, hit.author(), afterTitle, y + 4, x + w - 10 - dw - afterTitle, Glass.textFaint());
      }

      this.textFit(g, hit.description(), textX, y + 15, x + w - 4 - textX, Glass.textDim());
      this.host.hit(x, y, w, ROW - 2, this.viewTop, this.viewBottom, null, (b, mx, my, d) -> this.select(hit));
      if (over) {
         this.host.tip(String.format(java.util.Locale.ROOT, "%,d downloads", hit.downloads()));
      }
   }

   private void drawIcon(GuiGraphicsExtractor g, String url, String title, int x, int y, int size) {
      Identifier icon = this.icons.get(url);
      if (icon != null) {
         int white = this.c(0xFFFFFFFF);
         g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, size, size, size, size, white);
         return;
      }

      Glass.roundRect(g, x, y, x + size, y + size, gemini.ui.Theme.RADIUS_SMALL, this.c(Glass.surface(3)));
      String letter = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
      this.text(g, letter, x + (size - UiFont.width(letter)) / 2, y + (size - 8) / 2, Glass.textFaint());
   }

   private void renderDetail(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mouseX, int mouseY) {
      if (w < 60) {
         return;
      }

      g.fill(x - 7, top, x - 6, bottom, this.c(0xFFFFFFFF, 14));
      Detail d = this.detail;
      if (d == null) {
         this.textFit(g, "Pick something from the list.", x, top + 12, w, Glass.textFaint());
         this.drawRestartLine(g, x, bottom - 10, w);
         return;
      }

      Modrinth.Hit hit = d.hit;
      this.drawIcon(g, hit.iconUrl(), hit.title(), x, top, 32);
      this.textFit(g, hit.title(), x + 40, top + 4, w - 40, Glass.text());
      String by = (hit.author().isEmpty() ? "" : "by " + hit.author() + "  ·  ") + compact(hit.downloads()) + " downloads";
      this.textFit(g, by, x + 40, top + 17, w - 40, Glass.textFaint());
      int ly = top + 40;
      for (String line : wrap(hit.description(), w, 2)) {
         this.text(g, line, x, ly, Glass.textDim());
         ly += 11;
      }

      ly += 6;
      ly = this.drawInstallSection(g, d, x, ly, w, mouseX, mouseY);
      ly += 8;
      int restartY = d.type.needsRestart() ? bottom - 2 - this.restartLines(w).size() * 10 : bottom;
      for (String paragraph : d.excerpt) {
         for (String line : wrap(paragraph, w, 6)) {
            if (ly + 9 > restartY - 4) {
               break;
            }

            this.text(g, line, x, ly, Glass.textFaint());
            ly += 10;
         }

         ly += 4;
      }

      if (d.type.needsRestart()) {
         this.drawRestartLine(g, x, bottom - 10, w);
      }
   }

   /** The one line that says when mods take effect; shown wherever a mod can be installed. */
   private void drawRestartLine(GuiGraphicsExtractor g, int x, int y, int w) {
      if (type.needsRestart() || tab == Tab.INSTALLED) {
         List<String> lines = this.restartLines(w);
         int ly = y + 8 - lines.size() * 10;
         for (String line : lines) {
            this.text(g, line, x, ly, Glass.textDim());
            ly += 10;
         }
      }
   }

   private List<String> restartLines(int w) {
      return wrap(I18n.get("gemini.mods.restart"), w, 2);
   }

   private int drawInstallSection(GuiGraphicsExtractor g, Detail d, int x, int y, int w, int mouseX, int mouseY) {
      if (d.loading) {
         this.text(g, "Checking versions...", x, y, Glass.textFaint());
         return y + 12;
      }

      if (d.error != null) {
         for (String line : wrap(d.error, w, 3)) {
            this.text(g, line, x, y, Glass.WARN);
            y += 11;
         }

         return y;
      }

      String gv = this.browser.gameVersion();
      if (d.version == null) {
         String loader = d.type == ProjectType.MOD ? "Fabric " : "";
         this.textFit(g, "No " + loader + "version for " + gv + " yet.", x, y, w, Glass.WARN);
         return y + 12;
      }

      this.textFit(g, "For " + gv + ": " + d.version.versionNumber() + (d.version.versionType().equals("release") ? "" : " (" + d.version.versionType() + ")"), x, y, w, Glass.textDim());
      y += 12;
      DependencyPlan plan = d.plan;
      if (plan != null) {
         List<String> added = plan.addedNames();
         if (!added.isEmpty()) {
            for (String line : wrap("Also installs " + ModInstaller.joinNames(added), w, 2)) {
               this.text(g, line, x, y, Glass.textDim());
               y += 11;
            }
         }

         if (!plan.alreadyInstalled().isEmpty()) {
            this.textFit(g, "Uses " + ModInstaller.joinNames(plan.alreadyInstalled()) + " (installed)", x, y, w, Glass.textFaint());
            y += 11;
         }

         for (String problem : plan.problems()) {
            for (String line : wrap(problem, w, 2)) {
               this.text(g, line, x, y, Glass.WARN);
               y += 11;
            }
         }
      }

      y += 3;
      ModBrowser.Job job = this.browser.job(d.hit.projectId());
      int bw = Math.min(w, 110);
      if (job != null && !job.done) {
         Glass.bar(g, x, y + 5, x + bw, y + 8, job.progress, this.c(Glass.surface(3)), this.c(Glass.accent()), this.c(Glass.accent()));
         this.textFit(g, job.message + "  " + Math.round(job.progress * 100.0F) + "%", x, y + 12, w, Glass.textDim());
         return y + 24;
      }

      if (job != null && job.failed) {
         for (String line : wrap(job.message, w, 3)) {
            this.text(g, line, x, y, Glass.ERROR);
            y += 11;
         }

         y += 2;
      }

      boolean done = job != null && job.done && !job.failed;
      Path packFile = done ? job.mainFile() : d.existingPack;
      if (d.type == ProjectType.MOD && (done || d.alreadyInstalled)) {
         String state = done ? "Installed" : "Already installed";
         this.text(g, state, x, y + 3, Glass.OK);
         return y + 16;
      }

      if (d.type == ProjectType.SHADER && (done || packFile != null)) {
         for (String line : wrap("In your shaderpacks folder. Pick it under Video Settings > Shader Packs.", w, 3)) {
            this.text(g, line, x, y, Glass.OK);
            y += 11;
         }

         return y;
      }

      if (d.type == ProjectType.RESOURCE_PACK && packFile != null) {
         return this.drawApply(g, d, packFile, x, y, w, mouseX, mouseY);
      }

      String reason = plan == null ? "Still checking dependencies" : (plan.ok() ? null : "Can't install: " + (plan.problems().isEmpty() ? "nothing to install" : plan.problems().get(0)));
      String label = plan != null && plan.installs().size() > 1 ? "Install (" + plan.installs().size() + " files)" : "Install";
      bw = Math.min(w, this.buttonWidth(label) + 10);
      String tip = d.type.needsRestart() ? "Downloads into the mods folder; loads next time you start Minecraft" : "Downloads into the " + d.type.folder + " folder";
      this.button(g, x, y, bw, 16, label, mouseX, mouseY, TONE_ACCENT, reason, tip, (b, mx, my, dd) -> this.install(d));
      return y + 20;
   }

   private int drawApply(GuiGraphicsExtractor g, Detail d, Path pack, int x, int y, int w, int mouseX, int mouseY) {
      if (PackState.applied(pack)) {
         this.text(g, "Applied", x, y + 3, Glass.OK);
         return y + 16;
      }

      if (!this.confirmApply) {
         this.text(g, "Downloaded", x, y + 3, Glass.OK);
         int lw = UiFont.width("Downloaded") + 10;
         this.button(g, x + lw, y, this.buttonWidth("Apply now"), 16, "Apply now", mouseX, mouseY, TONE_ACCENT, null,
            "Turn the pack on now; you stay where you are", (b, mx, my, dd) -> {
               String id = PackApplier.find(pack);
               this.confirmNote = id == null || PackApplier.compatible(id) ? null : "It was made for a different game version, so parts may look wrong.";
               this.confirmApply = true;
            });
         return y + 20;
      }

      for (String line : wrap("Turn on " + d.hit.title() + "? Textures and sounds reload, which takes a few seconds.", w, 3)) {
         this.text(g, line, x, y, Glass.textDim());
         y += 11;
      }

      if (this.confirmNote != null) {
         for (String line : wrap(this.confirmNote, w, 2)) {
            this.text(g, line, x, y, Glass.WARN);
            y += 11;
         }
      }

      y += 2;
      int aw = this.buttonWidth("Apply");
      this.button(g, x, y, aw, 16, "Apply", mouseX, mouseY, TONE_ACCENT, null, "Select the pack and reload resources", (b, mx, my, dd) -> {
         this.confirmApply = false;
         String error = PackApplier.apply(pack);
         if (error == null) {
            PackState.markApplied(pack);
            this.host.notice(d.hit.title() + " is on. Change the order in Options > Resource Packs.");
         } else {
            this.host.notice(error);
         }
      });
      this.button(g, x + aw + 4, y, this.buttonWidth("Not now"), 16, "Not now", mouseX, mouseY, TONE_NEUTRAL, null, "Keep the pack downloaded but off",
         (b, mx, my, dd) -> this.confirmApply = false);
      return y + 20;
   }

   // ---- browse actions ---------------------------------------------------------------------------------------

   private void runSearch() {
      this.searchDue = 0L;
      String query = this.search.text().strip();
      rememberedQuery = this.search.text();
      if (query.equals(this.searchedFor) && type == this.searchedType && sort == this.searchedSort && !this.hits.isEmpty() && this.listError == null) {
         return;
      }

      this.searchedFor = query;
      this.searchedType = type;
      this.searchedSort = sort;
      this.hits.clear();
      this.hasMore = false;
      this.listError = null;
      this.scroll = this.scrollTarget = 0.0F;
      this.fetch(0);
   }

   private void loadMore() {
      this.fetch(this.hits.size());
   }

   private void fetch(int offset) {
      int serial = ++this.searchSerial;
      this.loading = true;
      ProjectType forType = type;
      this.browser.client().search(this.searchedFor, forType, sort, offset, this.browser.gameVersion()).whenComplete((page, error) -> ClientThread.run(() -> {
         if (serial != this.searchSerial) {
            return;
         }

         this.loading = false;
         if (error != null) {
            this.listError = message(error);
            this.hasMore = false;
            return;
         }

         this.listError = null;
         java.util.Set<String> seen = new HashSet<>();
         for (Modrinth.Hit hit : this.hits) {
            seen.add(hit.projectId());
         }

         for (Modrinth.Hit hit : page.hits()) {
            if (seen.add(hit.projectId())) {
               this.hits.add(hit);
            }
         }

         this.totalHits = page.totalHits();
         this.hasMore = page.hasMore();
         if (offset == 0 && !this.hits.isEmpty() && (this.selected == null || this.detail == null || this.detail.type != forType)) {
            this.select(this.hits.get(0));
         }
      }));
   }

   private void select(Modrinth.Hit hit) {
      this.sortOpen = false;
      this.confirmApply = false;
      if (this.selected != null && this.selected.projectId().equals(hit.projectId()) && this.detail != null && this.detail.type == type) {
         return;
      }

      this.selected = hit;
      Detail d = new Detail(hit, type);
      this.detail = d;
      int serial = ++this.detailSerial;
      ModBrowser browser = this.browser;
      ModrinthClient client = browser.client();
      String gv = browser.gameVersion();
      ProjectType forType = type;
      CompletableFuture<ModBrowser.FolderIndex> index = forType == ProjectType.MOD ? browser.folderIndex().handle((idx, e) -> idx) : CompletableFuture.completedFuture(null);
      index.thenCompose(idx -> client.async(() -> {
         Modrinth.Project project = client.projectNow(hit.projectId());
         List<Modrinth.Version> versions = client.versionsNow(hit.projectId(), forType.loader, gv);
         Modrinth.Version best = VersionPicker.best(versions, forType.loader, gv).orElse(null);
         DependencyPlan plan = null;
         if (best != null) {
            plan = DependencyPlan.resolve(best, hit.title(), forType, gv, client.lookup(forType.loader, gv), browser.installed(idx));
         }

         boolean installed = forType == ProjectType.MOD && browser.installed(idx).has(project);
         Path pack = null;
         if (best != null && forType != ProjectType.MOD) {
            String name = SafeFiles.sanitizeFilename(best.primaryFile().filename(), forType.extension);
            Path candidate = name == null ? null : browser.installer().folder(forType).resolve(name);
            if (candidate != null && Files.isRegularFile(candidate) && SafeFiles.sameHash(best.primaryFile().sha512(), SafeFiles.sha512(candidate))) {
               pack = candidate;
            }
         }

         List<String> excerpt = new ArrayList<>(TextExcerpt.paragraphs(project.body(), 700));
         if (!excerpt.isEmpty() && excerpt.get(0).equalsIgnoreCase(hit.title())) {
            excerpt.remove(0);
         }
         Modrinth.Version chosen = best;
         DependencyPlan finalPlan = plan;
         Path finalPack = pack;
         ClientThread.run(() -> {
            if (serial == this.detailSerial) {
               d.project = project;
               d.version = chosen;
               d.plan = finalPlan;
               d.alreadyInstalled = installed;
               d.existingPack = finalPack;
               d.excerpt = excerpt;
               d.loading = false;
            }
         });
         return null;
      })).exceptionally(error -> {
         ClientThread.run(() -> {
            if (serial == this.detailSerial) {
               d.error = message(error);
               d.loading = false;
            }
         });
         return null;
      });
   }

   private void install(Detail d) {
      if (d.plan == null || !d.plan.ok()) {
         return;
      }

      this.browser.install(d.hit.projectId(), d.hit.title(), d.type, d.plan, null);
      if (d.type == ProjectType.MOD) {
         this.host.notice("Installing " + d.hit.title() + ". " + I18n.get("gemini.mods.restart") + ".");
      }
   }

   // ---- installed --------------------------------------------------------------------------------------------

   private void rescan() {
      this.browser.folderChanged(); // files may have been added or removed outside the browser
      this.scanning = true;
      this.installedError = null;
      ModBrowser browser = this.browser;
      browser.client().async(browser::scanMods).whenComplete((list, error) -> ClientThread.run(() -> {
         this.scanning = false;
         if (error != null) {
            this.installedError = "Could not read the mods folder: " + message(error);
         } else {
            this.entries = list;
         }
      }));
   }

   private void checkUpdates() {
      this.checkingUpdates = true;
      this.browser.folderChanged();
      this.updateStatus = null;
      ModBrowser browser = this.browser;
      String gv = browser.gameVersion();
      browser.folderIndex().thenCompose(index -> browser.client().async(() -> {
         List<String> hashes = new ArrayList<>();
         for (Map.Entry<Path, String> entry : index.hashes().entrySet()) {
            if (!entry.getKey().getFileName().toString().endsWith(InstalledMods.DISABLED_SUFFIX)) {
               hashes.add(entry.getValue());
            }
         }

         Map<String, UpdateCheck.Update> byHash = UpdateCheck.find(index.versions(), browser.client().updatesNow(hashes, "fabric", gv));
         Map<Path, UpdateCheck.Update> byPath = new HashMap<>();
         for (Map.Entry<Path, String> entry : index.hashes().entrySet()) {
            UpdateCheck.Update update = byHash.get(entry.getValue());
            if (update != null) {
               byPath.put(entry.getKey().toAbsolutePath().normalize(), update);
            }
         }

         return byPath;
      })).whenComplete((found, error) -> ClientThread.run(() -> {
         this.checkingUpdates = false;
         if (error != null) {
            this.updateStatus = message(error);
         } else {
            this.updates = found;
            this.updateStatus = found.isEmpty() ? "Everything from Modrinth is up to date" : found.size() + (found.size() == 1 ? " update" : " updates") + " for " + gv;
         }
      }));
   }

   private void renderInstalled(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
      int rowY = y + 26;
      String label = this.checkingUpdates ? "Checking..." : "Check for updates";
      int bw = this.buttonWidth(label);
      this.button(g, x + w - 10 - bw, rowY, bw, 14, label, mouseX, mouseY, TONE_NEUTRAL, this.checkingUpdates ? "Already checking" : null,
         "Ask Modrinth for newer " + this.browser.gameVersion() + " versions of the mods in your folder", (b, mx, my, d) -> this.checkUpdates());
      String summary = this.updateStatus != null ? this.updateStatus : this.entries.size() + (this.entries.size() == 1 ? " file" : " files") + " in mods";
      this.textFit(g, summary, x + 10, rowY + 3, w - 30 - bw, this.updateStatus != null && !this.updates.isEmpty() ? Glass.accent2() : Glass.textFaint());
      int top = rowY + 20;
      int bottom = y + h - 16;
      int contentH = this.entries.size() * INSTALLED_ROW;
      this.viewport(x + 10, top, w - 16, bottom, contentH);
      if (this.installedError != null) {
         this.textFit(g, this.installedError, x + 10, top + 8, w - 20, Glass.WARN);
      } else if (this.entries.isEmpty()) {
         this.textFit(g, this.scanning ? "Reading the mods folder..." : "Your mods folder is empty.", x + 10, top + 8, w - 20, Glass.textFaint());
      } else {
         if (this.confirmPath != null && now > this.confirmUntil) {
            this.confirmPath = null;
         }

         g.enableScissor(x + 10, top, x + w - 6, bottom);
         int ry = top - Math.round(this.scroll);
         for (InstalledMods.Entry entry : this.entries) {
            if (ry + INSTALLED_ROW >= top && ry <= bottom) {
               this.drawEntry(g, entry, x + 10, ry, w - 22, mouseX, mouseY);
            }

            ry += INSTALLED_ROW;
         }

         g.disableScissor();
         this.scrollbar(g, x + w - 9, contentH);
      }

      this.textFit(g, I18n.get("gemini.mods.restart"), x + 10, y + h - 12, w - 20, Glass.textDim());
   }

   private void drawEntry(GuiGraphicsExtractor g, InstalledMods.Entry entry, int x, int y, int w, int mouseX, int mouseY) {
      boolean over = inside(mouseX, mouseY, x, y, w, INSTALLED_ROW - 1) && mouseY >= this.viewTop && mouseY < this.viewBottom;
      if (over) {
         Glass.roundRect(g, x, y, x + w, y + INSTALLED_ROW - 1, gemini.ui.Theme.RADIUS_SMALL, this.c(0xFFFFFFFF, 9));
      }

      // buttons from the right: Remove, Disable/Enable, Update
      int right = x + w - 2;
      int by = y + 3;
      String essential = entry.essential() ? entry.name() + " is needed for Gemini to start, so it can't be changed here" : null;
      boolean confirmingRemove = entry.path().equals(this.confirmPath) && "remove".equals(this.confirmAction);
      String removeLabel = confirmingRemove ? "Confirm" : "Remove";
      int rw = this.buttonWidth(removeLabel);
      right -= rw;
      this.button(g, right, by, rw, 13, removeLabel, mouseX, mouseY, confirmingRemove ? TONE_DANGER : TONE_NEUTRAL, essential,
         "Move to mods/.gemini-trash (takes effect next start)", true, (b, mx, my, d) -> this.remove(entry));
      boolean confirmingDisable = entry.path().equals(this.confirmPath) && "disable".equals(this.confirmAction);
      String toggle = entry.disabled() ? "Enable" : (confirmingDisable ? "Confirm" : "Disable");
      int tw = this.buttonWidth(toggle);
      right -= tw + 4;
      this.button(g, right, by, tw, 13, toggle, mouseX, mouseY, confirmingDisable ? TONE_DANGER : TONE_NEUTRAL, essential,
         entry.disabled() ? "Load it again from the next start" : "Keep it in the folder but don't load it from the next start", true,
         (b, mx, my, d) -> this.toggle(entry));
      UpdateCheck.Update update = this.updates.get(entry.path().toAbsolutePath().normalize());
      ModBrowser.Job job = this.browser.job("update:" + entry.path());
      if (job != null && !job.done) {
         String progress = Math.round(job.progress * 100.0F) + "%";
         int pw = UiFont.width(progress);
         right -= pw + 6;
         this.text(g, progress, right, y + 6, Glass.textDim());
      } else if (update != null && !entry.disabled() && !entry.essential()) {
         String upd = "Update to " + update.latest().versionNumber();
         int uw = Math.min(this.buttonWidth(upd), 120);
         right -= uw + 4;
         this.button(g, right, by, uw, 13, upd, mouseX, mouseY, TONE_ACCENT, null,
            "Download " + update.latest().versionNumber() + " and move this version to the trash (takes effect next start)", true,
            (b, mx, my, d) -> this.update(entry, update));
      }

      String state = entry.state().label;
      int stateColor = switch (entry.state()) {
         case NEXT_START -> Glass.accent2();
         case OFF_NEXT_START -> Glass.WARN;
         default -> Glass.textFaint();
      };
      if (job != null && job.done && job.failed) {
         state = "Update failed";
         stateColor = Glass.ERROR;
      }

      int sw = UiFont.width(state);
      right -= sw + 10;
      this.text(g, state, right, y + 6, stateColor);
      int nameRoom = right - 8 - (x + 6);
      String name = gemini.ui.clickgui.controls.Controls.fit(this.font(), entry.name(), nameRoom);
      this.text(g, name, x + 6, y + 6, entry.disabled() ? Glass.textDim() : Glass.text());
      int vx = x + 6 + UiFont.width(name) + 6;
      if (!entry.version().isEmpty() && vx < right - 20) {
         this.textFit(g, entry.version(), vx, y + 6, right - 8 - vx, Glass.textFaint());
      }

      if (over && this.host != null && mouseX < right) {
         this.host.tip(entry.fileName() + (job != null && job.failed ? "  ·  " + job.message : ""));
      }
   }

   private void toggle(InstalledMods.Entry entry) {
      try {
         if (entry.disabled()) {
            this.browser.installer().enable(entry.path());
            this.host.notice(entry.name() + " loads again from the next start");
         } else {
            List<String> needs = ModBrowser.dependents(entry.modId());
            boolean confirmed = entry.path().equals(this.confirmPath) && "disable".equals(this.confirmAction);
            if (!needs.isEmpty() && !confirmed) {
               this.confirm(entry, "disable");
               this.host.notice(ModInstaller.joinNames(needs) + " need" + (needs.size() == 1 ? "s " : " ") + entry.name() + ". Click Confirm to disable it anyway.");
               return;
            }

            this.browser.installer().disable(entry.path());
            this.host.notice(entry.name() + " is disabled from the next start");
         }
      } catch (IOException e) {
         this.host.notice(e.getMessage());
      }

      this.confirmPath = null;
      this.browser.folderChanged();
      this.rescan();
   }

   private void remove(InstalledMods.Entry entry) {
      boolean confirmed = entry.path().equals(this.confirmPath) && "remove".equals(this.confirmAction);
      if (!confirmed) {
         List<String> needs = ModBrowser.dependents(entry.modId());
         this.confirm(entry, "remove");
         this.host.notice(needs.isEmpty()
            ? "Click Confirm to move " + entry.fileName() + " to the trash"
            : ModInstaller.joinNames(needs) + " need" + (needs.size() == 1 ? "s " : " ") + entry.name() + ". Click Confirm to remove it anyway.");
         return;
      }

      try {
         this.browser.installer().trash(entry.path());
         this.host.notice(entry.name() + " moved to mods/" + InstalledMods.TRASH + "; gone after the next start");
         this.updates = new HashMap<>(this.updates);
         this.updates.remove(entry.path().toAbsolutePath().normalize());
      } catch (IOException e) {
         this.host.notice(e.getMessage());
      }

      this.confirmPath = null;
      this.browser.folderChanged();
      this.rescan();
   }

   private void confirm(InstalledMods.Entry entry, String action) {
      this.confirmPath = entry.path();
      this.confirmAction = action;
      this.confirmUntil = Util.getMillis() + CONFIRM_MS;
   }

   private void update(InstalledMods.Entry entry, UpdateCheck.Update update) {
      ModBrowser browser = this.browser;
      String gv = browser.gameVersion();
      String key = "update:" + entry.path();
      this.host.notice("Updating " + entry.name() + ". " + I18n.get("gemini.mods.restart") + ".");
      browser.folderIndex().handle((idx, e) -> idx).thenCompose(idx -> browser.client().async(() -> DependencyPlan.resolve(
         update.latest(), entry.name(), ProjectType.MOD, gv, browser.client().lookup("fabric", gv), browser.installed(idx)
      ))).whenComplete((plan, error) -> ClientThread.run(() -> {
         if (error != null) {
            this.host.notice(message(error));
         } else if (!plan.ok()) {
            this.host.notice("Can't update " + entry.name() + ": " + (plan.problems().isEmpty() ? "nothing to install" : plan.problems().get(0)));
         } else {
            ModBrowser.Job job = browser.install(key, entry.name(), ProjectType.MOD, plan, entry.path());
            this.updateStatus = null;
            this.updates = new HashMap<>(this.updates);
            this.updates.remove(entry.path().toAbsolutePath().normalize());
            this.watch(job);
         }
      }));
   }

   /** Rescans the folder once {@code job} is done. */
   private void watch(ModBrowser.Job job) {
      this.browser.client().async(() -> {
         while (!job.done) {
            try {
               Thread.sleep(150L);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return null;
            }
         }

         ClientThread.run(() -> {
            if (job.failed) {
               this.host.notice(job.message);
            }

            this.rescan();
         });
         return null;
      });
   }

   // ---- helpers ----------------------------------------------------------------------------------------------

   private static String message(Throwable error) {
      Throwable cause = error;
      while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
         cause = cause.getCause();
      }

      if (cause instanceof ModrinthClient.Failure failure && failure.kind == ModrinthClient.Failure.Kind.NOT_FOUND) {
         return "Not found on Modrinth.";
      }

      String text = cause.getMessage();
      return text == null || text.isBlank() ? "Something went wrong (" + cause.getClass().getSimpleName() + ")" : text;
   }

   static String compact(long count) {
      if (count >= 1_000_000L) {
         return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
      } else if (count >= 1_000L) {
         return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
      }

      return Long.toString(count);
   }

   private List<String> wrap(String text, int width, int maxLines) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isBlank()) {
         return lines;
      }

      StringBuilder line = new StringBuilder();
      for (String word : text.strip().split("\\s+")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (UiFont.width(candidate) <= width || line.isEmpty()) {
            line.setLength(0);
            line.append(candidate);
         } else {
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (lines.size() == maxLines) {
               break;
            }
         }
      }

      if (lines.size() < maxLines && !line.isEmpty()) {
         lines.add(line.toString());
      } else if (lines.size() == maxLines) {
         String last = lines.get(maxLines - 1);
         lines.set(maxLines - 1, gemini.ui.clickgui.controls.Controls.fit(this.font(), last + " ...", width));
      }

      for (int i = 0; i < lines.size(); i++) {
         lines.set(i, gemini.ui.clickgui.controls.Controls.fit(this.font(), lines.get(i), width));
      }

      return lines;
   }

   private static void caret(GuiGraphicsExtractor g, int x, int y, int color) {
      g.fill(x, y, x + 5, y + 1, color);
      g.fill(x + 1, y + 1, x + 4, y + 2, color);
      g.fill(x + 2, y + 2, x + 3, y + 3, color);
   }

   /** Which downloaded packs were applied this session (so the button does not offer it again). */
   private static final class PackState {
      private static final java.util.Set<Path> APPLIED = new HashSet<>();

      static boolean applied(Path pack) {
         return APPLIED.contains(pack.toAbsolutePath().normalize());
      }

      static void markApplied(Path pack) {
         APPLIED.add(pack.toAbsolutePath().normalize());
      }
   }
}
