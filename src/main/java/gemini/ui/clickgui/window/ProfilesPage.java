package gemini.ui.clickgui.window;

import com.google.gson.JsonObject;
import gemini.config.ConfigManager;
import gemini.config.JsonFiles;
import gemini.config.ProfileBundle;
import gemini.module.Module;
import gemini.module.modules.misc.Presets;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.HudOverlay;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.TextField;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * "Presets & Configs": the saved profiles with their age and which one is active, and every action the
 * {@code .config} command offers (save, load, overwrite, delete, export to a file or the clipboard, import from a
 * file or the clipboard, reset), plus the built-in {@link Presets} bundles. Destructive actions ask once more.
 */
public final class ProfilesPage extends DashboardPage {
   private static final long CONFIRM_MS = 3500L;
   private static final long REFRESH_MS = 1500L;
   private static final int ROW_H = 28;
   private static ProfileList.Sort sort = ProfileList.Sort.RECENT;
   private final TextField saveName = new TextField(48, null);
   private final TextField shareName = new TextField(48, null);
   private List<ProfileList.Entry> entries = List.of();
   private List<String> exports = List.of();
   private long lastRefresh;
   private String confirmKey;
   private long confirmUntil;
   private String selected;

   public ProfilesPage(Host host) {
      super(host);
   }

   @Override
   public String title() {
      return "Presets & Configs";
   }

   @Override
   public String hint() {
      return "Profiles live in config/gemini_profiles  ·  exports in config/gemini_exports";
   }

   @Override
   public void opened() {
      this.lastRefresh = 0L;
      this.confirmKey = null;
   }

   @Override
   protected TextField[] fields() {
      return new TextField[]{this.saveName, this.shareName};
   }

   @Override
   protected void fieldSubmitted(TextField field) {
      field.blur();
      if (field == this.saveName) {
         this.saveAs(this.saveName.text());
      } else {
         this.exportFile();
      }
   }

   private static ConfigManager config() {
      return gemini() == null ? null : gemini().config();
   }

   private void refresh(long now) {
      ConfigManager config = config();
      if (config == null) {
         this.entries = List.of();
         this.exports = List.of();
      } else {
         this.entries = ProfileList.build(config.profiles(), config::profileModified, config.activeProfile(), sort);
         this.exports = config.exports();
      }

      this.lastRefresh = now;
   }

   /** Re-reads the folders shortly: profile writes finish on the IO thread a moment after the call returns. */
   private void refreshSoon() {
      this.lastRefresh = Util.getMillis() - REFRESH_MS + 250L;
   }

   private boolean confirmed(String key) {
      long now = Util.getMillis();
      if (key.equals(this.confirmKey) && now < this.confirmUntil) {
         this.confirmKey = null;
         return true;
      }

      this.confirmKey = key;
      this.confirmUntil = now + CONFIRM_MS;
      return false;
   }

   private boolean confirming(String key) {
      return key.equals(this.confirmKey) && Util.getMillis() < this.confirmUntil;
   }

   private void report(String title, String body, HudOverlay.Severity severity) {
      this.host.notice(title + (body == null || body.isEmpty() ? "" : ": " + body));
      HudOverlay.toast(title, body == null ? "" : body, severity);
   }

   // ---- actions --------------------------------------------------------------------------------------------

   private void saveAs(String raw) {
      ConfigManager config = config();
      String id = ConfigManager.profileId(raw);
      if (config == null || id == null) {
         this.host.notice("Type a profile name with letters or digits first");
         this.saveName.flashError();
         return;
      }

      boolean exists = config.profiles().contains(id);
      if (exists && !this.confirmed("save:" + id)) {
         this.host.notice("A profile named " + id + " exists. Click Save again to overwrite it");
         return;
      }

      if (config.saveProfile(id)) {
         this.saveName.setText("");
         this.selected = id;
         this.report("Profile saved", id, HudOverlay.Severity.SUCCESS);
      } else {
         this.report("Profile not saved", id, HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private void load(String name) {
      ConfigManager config = config();
      if (config != null && config.loadProfile(name)) {
         this.report("Profile loaded", name, HudOverlay.Severity.SUCCESS);
      } else {
         this.report("Could not load profile", name, HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private void overwrite(String name) {
      if (!this.confirmed("overwrite:" + name)) {
         this.host.notice("Click again to replace " + name + " with your current settings");
         return;
      }

      ConfigManager config = config();
      if (config != null && config.saveProfile(name)) {
         this.report("Profile overwritten", name, HudOverlay.Severity.SUCCESS);
      } else {
         this.report("Could not overwrite", name, HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private void delete(String name) {
      if (!this.confirmed("delete:" + name)) {
         this.host.notice("Click Delete again to remove " + name + " for good");
         return;
      }

      ConfigManager config = config();
      if (config != null && config.deleteProfile(name)) {
         this.report("Profile deleted", name, HudOverlay.Severity.WARNING);
      } else {
         this.report("Could not delete", name, HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private String shareId() {
      String id = ConfigManager.profileId(this.shareName.text());
      if (id == null) {
         this.shareName.flashError();
         this.host.notice("Type a name for the export first");
      }

      return id;
   }

   private void exportFile() {
      ConfigManager config = config();
      String id = this.shareId();
      if (config == null || id == null) {
         return;
      }

      Path file = config.exportProfile(id);
      if (file == null) {
         this.report("Export failed", id, HudOverlay.Severity.ERROR);
      } else {
         this.report("Exported", config.exportDir().getFileName() + "/" + file.getFileName(), HudOverlay.Severity.SUCCESS);
      }

      this.refreshSoon();
   }

   private void exportClipboard() {
      ConfigManager config = config();
      String id = this.shareId();
      if (config != null && id != null) {
         Controls.setClipboard(JsonFiles.GSON.toJson(config.exportBundle(id)));
         this.report("Copied to clipboard", "import it with .config import <name> clipboard", HudOverlay.Severity.SUCCESS);
      }
   }

   private void importClipboard() {
      ConfigManager config = config();
      String id = this.shareId();
      if (config == null || id == null) {
         return;
      }

      String text = Controls.clipboard();
      if (text.isBlank()) {
         this.report("Import failed", "the clipboard is empty", HudOverlay.Severity.ERROR);
         return;
      }

      try {
         JsonObject document = JsonFiles.parse(text);
         int modules = config.importProfile(id, document);
         this.selected = id;
         this.report("Imported " + id, modules + " modules; press Load to apply", HudOverlay.Severity.SUCCESS);
      } catch (ProfileBundle.FormatException e) {
         this.report("Import failed", e.getMessage(), HudOverlay.Severity.ERROR);
      } catch (RuntimeException e) {
         this.report("Import failed", "the clipboard does not hold a Gemini profile", HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private void importFile(String name) {
      ConfigManager config = config();
      if (config == null) {
         return;
      }

      try {
         int modules = config.importProfileFile(name);
         this.selected = name;
         this.report("Imported " + name, modules + " modules; press Load to apply", HudOverlay.Severity.SUCCESS);
      } catch (ProfileBundle.FormatException e) {
         this.report("Import failed", e.getMessage(), HudOverlay.Severity.ERROR);
      }

      this.refreshSoon();
   }

   private void resetAll() {
      if (!this.confirmed("reset")) {
         this.host.notice("Click again to put every setting of every module back to its default");
         return;
      }

      ConfigManager config = config();
      if (config != null) {
         config.resetAllSettings();
         this.report("Settings reset", "every setting is back to its default", HudOverlay.Severity.WARNING);
      }
   }

   private static Presets presets() {
      return gemini() == null ? null : gemini().modules().byClass(Presets.class).orElse(null);
   }

   private static Setting<?> setting(Module module, String name) {
      for (Setting<?> setting : module.settings()) {
         if (setting.name().equals(name)) {
            return setting;
         }
      }

      return null;
   }

   private void applyPreset(Presets presets, ModeSetting pack, String mode) {
      if (!presets.canActivate()) {
         this.host.notice(plain(presets.blockedReason()));
         return;
      }

      pack.set(mode);
      presets.setEnabled(true, false);
      this.report("Preset applied", mode, HudOverlay.Severity.SUCCESS);
      save();
   }

   // ---- drawing --------------------------------------------------------------------------------------------

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
      long now = Util.getMillis();
      if (now - this.lastRefresh >= REFRESH_MS) {
         this.refresh(now);
      }

      ConfigManager config = config();
      String active = config == null ? null : config.activeProfile();
      if (this.shareName.text().isEmpty() && !this.shareName.focused()) {
         this.shareName.setText(active == null ? "my-settings" : active);
      }

      this.text(g, this.title(), x + 10, y + 9, Glass.TEXT);
      String status = active == null ? "No profile loaded or saved yet" : "Active profile: " + active;
      int statusW = this.font().width(status);
      this.text(g, status, x + w - 10 - statusW, y + 9, active == null ? Glass.TEXT_FAINT : Glass.accent2());
      int top = y + 24;
      int bottom = y + h - 4;
      int inner = w - 24;
      int contentH = this.contentHeight(inner);
      this.viewport(x, top, w, bottom, contentH);
      g.enableScissor(x, top, x + w, bottom);
      int cy = top + 2 - Math.round(this.scroll);
      int cx = x + 10;
      cy = this.drawSave(g, cx, cy, inner, mouseX, mouseY, config);
      cy = this.drawProfiles(g, cx, cy + 8, inner, mouseX, mouseY, now);
      cy = this.drawShare(g, cx, cy + 8, inner, mouseX, mouseY);
      cy = this.drawPresets(g, cx, cy + 8, inner, mouseX, mouseY);
      this.drawReset(g, cx, cy + 8, inner, mouseX, mouseY);
      g.disableScissor();
      this.scrollbar(g, x + w - 5, contentH);
   }

   private int contentHeight(int inner) {
      int save = 44;
      int profiles = 22 + Math.max(1, this.entries.size()) * (ROW_H + 3);
      int share = 62 + (this.exports.isEmpty() ? 0 : 14 + this.exports.size() * 16);
      Presets presets = presets();
      int presetRows = 0;
      if (presets != null && setting(presets, "Preset") instanceof ModeSetting pack) {
         presetRows = this.presetLines(pack, inner - 16);
      }

      int preset = 40 + presetRows * 18;
      int reset = 40;
      return save + 8 + profiles + 8 + share + 8 + preset + 8 + reset + 6;
   }

   private void section(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String note) {
      this.card(g, x, y, x + w, y + h, 0.0F);
      this.text(g, title, x + 8, y + 6, Glass.TEXT);
      if (note != null) {
         this.textFit(g, note, x + 14 + this.font().width(title), y + 6, w - 22 - this.font().width(title), Glass.TEXT_FAINT);
      }
   }

   private int drawSave(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, ConfigManager config) {
      this.section(g, x, y, w, 44, "Save current settings", "as a new profile");
      String label = "Save";
      int bw = this.buttonWidth(label) + 6;
      int fy = y + 22;
      this.field(g, this.saveName, x + 8, fy, w - 24 - bw, "Profile name...", mouseX, mouseY, true);
      String id = ConfigManager.profileId(this.saveName.text());
      boolean exists = id != null && this.entries.stream().anyMatch(e -> e.name().equals(id));
      boolean confirm = id != null && this.confirming("save:" + id);
      this.button(
         g, x + w - 8 - bw, fy, bw, 13, confirm ? "Replace?" : label, mouseX, mouseY, confirm ? TONE_DANGER : TONE_ACCENT,
         config == null ? "Gemini is not loaded" : null,
         id == null ? "Type a name first" : (exists ? "Replaces the saved profile " + id : "Saves as " + id), true,
         (b, mx, my, d) -> this.saveAs(this.saveName.text())
      );
      return y + 44;
   }

   private int drawProfiles(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, long now) {
      int h = 22 + Math.max(1, this.entries.size()) * (ROW_H + 3);
      this.section(g, x, y, w, h, "Saved profiles", this.entries.size() + (this.entries.size() == 1 ? " profile" : " profiles"));
      String sortLabel = "Sort: " + sort.label;
      int sw = this.buttonWidth(sortLabel);
      this.button(g, x + w - 8 - sw, y + 4, sw, 12, sortLabel, mouseX, mouseY, TONE_NEUTRAL, null, "Newest first or alphabetical", true, (b, mx, my, d) -> {
         sort = sort.next();
         this.refresh(Util.getMillis());
      });
      int ry = y + 20;
      if (this.entries.isEmpty()) {
         this.text(g, "No saved profiles yet: name one above and press Save.", x + 10, ry + 9, Glass.TEXT_FAINT);
         return y + h;
      }

      for (ProfileList.Entry entry : this.entries) {
         this.drawProfileRow(g, entry, x + 4, ry, w - 8, mouseX, mouseY, now);
         ry += ROW_H + 3;
      }

      return y + h;
   }

   private void drawProfileRow(GuiGraphicsExtractor g, ProfileList.Entry entry, int x, int y, int w, int mouseX, int mouseY, long now) {
      String name = entry.name();
      boolean over = inside(mouseX, mouseY, x, y, w, ROW_H) && mouseY >= this.viewTop && mouseY < this.viewBottom;
      boolean picked = name.equals(this.selected);
      int fill = entry.active() ? Glass.withAlpha(Glass.accent(), 46) : Glass.withAlpha(Glass.surface(2), over || picked ? 255 : 200);
      Glass.roundRect(g, x, y, x + w, y + ROW_H, 3, this.c(fill));
      if (entry.active()) {
         g.fill(x, y + 5, x + 2, y + ROW_H - 5, this.c(Glass.accent2()));
      }

      this.host.hit(x, y, w, ROW_H, this.viewTop, this.viewBottom, null, (b, mx, my, d) -> {
         this.selected = name;
         if (d) {
            this.load(name);
         }
      });
      String[] labels = {"Load", this.confirming("overwrite:" + name) ? "Sure?" : "Overwrite", this.confirming("delete:" + name) ? "Sure?" : "Delete"};
      int bx = x + w - 6;
      int[] widths = new int[3];
      int total = 0;
      for (int i = 0; i < 3; i++) {
         widths[i] = this.buttonWidth(labels[i]);
         total += widths[i] + 3;
      }

      bx -= total;
      int by = y + (ROW_H - 13) / 2;
      bx += this.button(g, bx, by, widths[0], 13, labels[0], mouseX, mouseY, TONE_ACCENT, null, "Apply this profile's settings now (double-click the row does the same)", true, (b, mx, my, d) -> this.load(name)) + 3;
      bx += this.button(
         g, bx, by, widths[1], 13, labels[1], mouseX, mouseY, this.confirming("overwrite:" + name) ? TONE_DANGER : TONE_NEUTRAL, null,
         "Replace this profile with your current settings", true, (b, mx, my, d) -> this.overwrite(name)
      ) + 3;
      this.button(g, bx, by, widths[2], 13, labels[2], mouseX, mouseY, TONE_DANGER, null, "Delete this profile (asks once more)", true, (b, mx, my, d) -> this.delete(name));
      int textRight = x + w - 10 - total;
      int tx = x + 8;
      if (entry.active()) {
         tx += this.chip(g, tx, y + 3, "ACTIVE", Glass.withAlpha(Glass.accent(), 150), Glass.TEXT) + 4;
      }

      this.textFit(g, name, tx, y + 5, textRight - tx, Glass.TEXT);
      this.textFit(g, "modified " + ProfileList.ago(System.currentTimeMillis(), entry.modified()), x + 8, y + 17, textRight - x - 8, Glass.TEXT_FAINT);
      if (over && !inside(mouseX, mouseY, x + w - 6 - total, y, total, ROW_H)) {
         this.host.tip("Double-click to load  ·  " + name + ".json");
      }
   }

   private int drawShare(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
      int h = 62 + (this.exports.isEmpty() ? 0 : 14 + this.exports.size() * 16);
      this.section(g, x, y, w, h, "Export & import", "share your settings as a file or text");
      int fy = y + 22;
      this.text(g, "Name", x + 8, fy + 3, Glass.TEXT_DIM);
      this.field(g, this.shareName, x + 38, fy, w - 46, "Export or import name...", mouseX, mouseY, true);
      int by = y + 41;
      int bx = x + 8;
      String[] labels = {"Export to file", "Copy to clipboard", "Import clipboard"};
      bx += this.button(g, bx, by, this.buttonWidth(labels[0]), 14, labels[0], mouseX, mouseY, TONE_ACCENT, null,
         "Writes your current settings to gemini_exports/<name>.json (no waypoints, home or panel layout)", true, (b, mx, my, d) -> this.exportFile()) + 4;
      bx += this.button(g, bx, by, this.buttonWidth(labels[1]), 14, labels[1], mouseX, mouseY, TONE_NEUTRAL, null,
         "Copies your current settings as text to paste anywhere", true, (b, mx, my, d) -> this.exportClipboard()) + 4;
      this.button(g, bx, by, this.buttonWidth(labels[2]), 14, labels[2], mouseX, mouseY, TONE_NEUTRAL, null,
         "Stores a profile copied from someone else under the name above; Load applies it", true, (b, mx, my, d) -> this.importClipboard());
      if (!this.exports.isEmpty()) {
         int ry = y + 62;
         this.text(g, "Files in gemini_exports", x + 8, ry, Glass.TEXT_FAINT);
         ry += 12;
         for (String file : this.exports) {
            this.textFit(g, file + ".json", x + 14, ry + 3, w - 90, Glass.TEXT_DIM);
            String label = "Import";
            int bw = this.buttonWidth(label);
            this.button(g, x + w - 8 - bw, ry, bw, 13, label, mouseX, mouseY, TONE_NEUTRAL, null, "Store this file as the profile " + file, true,
               (b, mx, my, d) -> this.importFile(file));
            ry += 16;
         }
      }

      return y + h;
   }

   /** How many rows the preset buttons wrap onto at this width. */
   private int presetLines(ModeSetting pack, int width) {
      int lines = 1;
      int used = 0;
      for (String mode : pack.modes()) {
         int bw = this.buttonWidth(mode) + 4;
         if (used > 0 && used + bw > width) {
            lines++;
            used = 0;
         }

         used += bw;
      }

      return lines;
   }

   private int drawPresets(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
      Presets presets = presets();
      if (presets == null || !(setting(presets, "Preset") instanceof ModeSetting pack)) {
         this.section(g, x, y, w, 40, "Built-in presets", "not available");
         return y + 40;
      }

      int lines = this.presetLines(pack, w - 16);
      int h = 40 + lines * 18;
      this.section(g, x, y, w, h, "Built-in presets", "one-click module bundles");
      String blocked = presets.canActivate() ? null : plain(presets.blockedReason());
      if (setting(presets, "Disable Others") instanceof BooleanSetting others) {
         String label = "Turn others off";
         int lw = this.font().width(label);
         int sx = x + w - 8 - 18;
         this.text(g, label, sx - 6 - lw, y + 6, Glass.TEXT_DIM);
         Glass.toggle(g, sx, y + 5, others.get() ? 1.0F : 0.0F, Glass.accent(), this.host.fade());
         this.host.hit(sx - 6 - lw, y + 3, lw + 24, 14, this.viewTop, this.viewBottom, null, (b, mx, my, d) -> {
            others.toggle();
            save();
         });
         if (inside(mouseX, mouseY, sx - 6 - lw, y + 3, lw + 24, 14)) {
            this.host.tip(others.description());
         }
      }

      int bx = x + 8;
      int by = y + 20;
      for (String mode : pack.modes()) {
         int bw = this.buttonWidth(mode);
         if (bx > x + 8 && bx + bw > x + w - 8) {
            bx = x + 8;
            by += 18;
         }

         boolean current = pack.is(mode);
         bx += this.button(g, bx, by, bw, 14, mode, mouseX, mouseY, current ? TONE_ACCENT : TONE_NEUTRAL, blocked, "Switch on the " + mode + " bundle of modules", true,
            (b, mx, my, d) -> this.applyPreset(presets, pack, mode)) + 4;
      }

      String note = blocked == null ? "Presets switch modules on; your profiles are untouched." : "Needs a single-player world: " + blocked;
      this.textFit(g, note, x + 8, by + 20, w - 16, blocked == null ? Glass.TEXT_FAINT : Glass.WARN);
      return y + h;
   }

   private void drawReset(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
      this.section(g, x, y, w, 40, "Reset", null);
      this.textFit(g, "Every setting of every module back to its default.", x + 8, y + 22, w - 110, Glass.TEXT_FAINT);
      boolean confirm = this.confirming("reset");
      String label = confirm ? "Click to confirm" : "Reset all";
      int bw = this.buttonWidth(label);
      this.button(g, x + w - 8 - bw, y + 18, bw, 14, label, mouseX, mouseY, TONE_DANGER, null, "Same as .config reset (asks once more)", true, (b, mx, my, d) -> this.resetAll());
   }
}
