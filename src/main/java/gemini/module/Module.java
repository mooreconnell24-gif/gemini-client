package gemini.module;

import com.google.gson.JsonObject;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.config.ConfigManager;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import gemini.ui.HudContext;
import gemini.ui.HudOverlay;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public abstract class Module {
   private final String name;
   private final String description;
   private final Category category;
   private final List<Setting<?>> settings = new ArrayList<>();
   private ModeSetting bindMode = new ModeSetting(
      "Bind Mode", "Toggle flips the module; Hold keeps it on only while the key is down", "Toggle", "Toggle", "Hold"
   );
   private final BooleanSetting listed = new BooleanSetting("Show In List", "Show this module in the HUD module list", true);
   private List<Setting<?>> exposedSettings;
   private int keybind;
   private boolean savedEnabled;
   private boolean enabled;
   private ConfigManager configManager;

   protected Module(String name, String description, Category category) {
      this(name, description, category, -1);
   }

   protected Module(String name, String description, Category category, int keybind) {
      this.name = name;
      this.description = description;
      this.category = category;
      this.keybind = keybind == 0 ? -1 : keybind;
   }

   /** For modules meant to be held (Zoom, Freelook...): their key bind defaults to Hold instead of Toggle. */
   protected final void holdByDefault() {
      this.bindMode = new ModeSetting(
         "Bind Mode", "Toggle flips the module; Hold keeps it on only while the key is down", "Hold", "Toggle", "Hold"
      );
      this.exposedSettings = null;
   }

   public final <T extends Setting<?>> T addSetting(T setting) {
      this.settings.add(setting);
      this.exposedSettings = null;
      return setting;
   }

   void attachConfig(ConfigManager configManager) {
      this.configManager = configManager;
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public Category getCategory() {
      return this.category;
   }

   public List<Setting<?>> settings() {
      if (this.exposedSettings == null) {
         List<Setting<?>> all = new ArrayList<>(this.settings);
         if (this.hasBindOptions()) {
            all.add(this.bindMode);
         }

         if (this.hasListOption()) {
            all.add(this.listed);
         }

         this.exposedSettings = Collections.unmodifiableList(all);
      }

      return this.exposedSettings;
   }

   public int getKeybind() {
      return this.keybind;
   }

   public void setKeybind(int keybind) {
      this.keybind = keybind == 0 ? -1 : keybind;
      this.save();
   }

   public boolean isHoldBind() {
      return this.bindMode.is("Hold");
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isSavedEnabled() {
      return this.savedEnabled;
   }

   public boolean showInArrayList() {
      return true;
   }

   public boolean isListed() {
      return this.showInArrayList() && this.listed.get();
   }

   public boolean isBindable() {
      return this.hasBindOptions();
   }

   protected boolean hasBindOptions() {
      return true;
   }

   protected boolean hasListOption() {
      return this.showInArrayList();
   }

   public boolean selfTestExempt() {
      return false;
   }

   public final boolean canActivate() {
      return this.settingsOnly() ? true : SingleplayerGate.allowed() && this.extraActivation();
   }

   protected boolean extraActivation() {
      return true;
   }

   protected boolean settingsOnly() {
      return false;
   }

   public String blockedReason() {
      return SingleplayerGate.allowed() ? "§b" + this.name + " §7can't be turned on right now." : SingleplayerGate.denialMessage();
   }

   public final void toggle() {
      this.setEnabled(!this.savedEnabled, true);
   }

   public final void setEnabled(boolean enabled) {
      this.setEnabled(enabled, true);
   }

   public final void setEnabled(boolean enabled, boolean notify) {
      this.applyEnabled(enabled, notify, true);
   }

   public final void setHeld(boolean held) {
      this.applyEnabled(held, false, false);
   }

   private void applyEnabled(boolean enabled, boolean notify, boolean persist) {
      if (enabled && !this.canActivate()) {
         if (notify) {
            CommandManager.feedback(this.blockedReason());
         }
      } else if (this.savedEnabled != enabled || this.enabled != enabled) {
         this.savedEnabled = enabled;
         this.applyRuntimeState();
         if (notify && Minecraft.getInstance().player != null && this.isListed()) {
            HudOverlay.pushNotification(this.name, this.savedEnabled ? "Module enabled" : "Module disabled", this.savedEnabled);
            CommandManager.feedback("§b" + this.name + " §7" + (this.savedEnabled ? "§aenabled" : "§cdisabled"));
         }

         if (persist) {
            this.save();
         }
      }
   }

   public final void loadState(boolean enabled, int keybind) {
      this.keybind = keybind == 0 ? -1 : keybind;
      this.savedEnabled = enabled && this.restoresEnabledState();
      this.applyRuntimeState();
   }

   protected boolean restoresEnabledState() {
      return true;
   }

   public final void applyRuntimeState() {
      boolean shouldRun = this.savedEnabled && this.canActivate();
      if (this.enabled != shouldRun) {
         this.enabled = shouldRun;
         ModuleManager.invalidateActive();

         try {
            if (shouldRun) {
               this.onEnable();
            } else {
               this.onDisable();
            }
         } catch (RuntimeException | LinkageError var3) {
            Gemini.LOGGER.error("Module {} failed during {}", new Object[]{this.name, shouldRun ? "enable" : "disable", var3});
            ModuleManager.recordFailure(this, shouldRun ? "onEnable" : "onDisable", var3);
            if (shouldRun) {
               this.abandonStart();
            }
         }
      }
   }

   private void abandonStart() {
      this.enabled = false;
      this.savedEnabled = false;
      ModuleManager.invalidateActive();

      try {
         this.onDisable();
      } catch (RuntimeException | LinkageError var2) {
      }

      CommandManager.feedback("§c" + this.name + " §7failed to start and was switched off. The log has the details.");
   }

   public final void forceDisableRuntime() {
      if (this.enabled) {
         this.enabled = false;
         ModuleManager.invalidateActive();

         try {
            this.onDisable();
         } catch (RuntimeException var2) {
            Gemini.LOGGER.error("Module {} failed during disable", this.name, var2);
         }
      }
   }

   protected void save() {
      if (this.configManager != null) {
         this.configManager.save();
      }
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void onTick() {
   }

   public void onRender3D(float partialTick) {
   }

   public void onRenderHud(HudContext hud) {
   }

   public boolean onSendPacket(Packet<?> packet) {
      return false;
   }

   public boolean onReceivePacket(Packet<?> packet) {
      return false;
   }

   public void onAttack(Entity target) {
   }

   public void readExtra(JsonObject object) {
   }

   public void writeExtra(JsonObject object) {
   }

   public boolean onCommand(String[] args) {
      return false;
   }

   public String info() {
      return null;
   }

   public boolean wantsBaritonePause() {
      return false;
   }
}
