package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;

public final class Presets extends Module {
   private final ModeSetting pack = this.addSetting(
      new ModeSetting("Preset", "Which bundle of modules to enable", "LiquidBounce", "LiquidBounce", "Performance", "PvP", "Explore", "Build", "Travel")
   );
   private final BooleanSetting disableOthers = this.addSetting(new BooleanSetting("Disable Others", "Turn off the other presets' modules first", false));

   public Presets() {
      super(
         "Presets", "One-click module bundles for PvP, FPS, and travel.", Category.CLIENT
      );
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public String info() {
      return this.pack.get();
   }

   @Override
   public void onEnable() {
      Gemini client = Gemini.getInstance();
      if (client == null) {
         this.setEnabled(false, false);
      } else {
         if (this.disableOthers.get()) {
            toggle(
               client,
               false,
               "Fullbright",
               "Xray",
               "ESP",
               "StorageESP",
               "Waypoints",
               "Flight",
               "Speed",
               "Jesus",
               "Scaffold",
               "Nuker",
               "AutoTool",
               "FastPlace",
               "KillAura",
               "Velocity",
               "TargetHUD"
            );
         }

         String var2 = this.pack.get();
         switch (var2) {
            case "LiquidBounce":
               toggle(client, true, "Sprint", "HUD", "TargetHUD", "Velocity", "AutoArmor", "InventoryCleaner", "ESP", "Nametags", "AntiLag");
               break;
            case "Performance":
               toggle(client, true, "AntiLag", "EntityCulling", "Unfocused CPU", "Particles", "HUD", "FPS Graph", "AntiLag Status");
               break;
            case "PvP":
               toggle(client, true, "KillAura", "Velocity", "Criticals", "AutoTotem", "AutoArmor", "Sprint", "TargetHUD", "HoleESP", "HitEffect");
               break;
            case "Build":
               toggle(client, true, "Scaffold", "FastPlace", "AutoTool", "SafeWalk", "Sprint");
               break;
            case "Travel":
               toggle(client, true, "Flight", "Speed", "Jesus", "NoFall", "Sprint", "Step");
               break;
            default:
               toggle(client, true, "Fullbright", "Xray", "ESP", "StorageESP", "Waypoints", "Coordinates", "Zoom");
         }

         CommandManager.feedback("§bPresets §7applied §f" + this.pack.get() + "§7.");
         this.setEnabled(false, false);
      }
   }

   private static void toggle(Gemini gemini, boolean on, String... names) {
      for (String name : names) {
         gemini.modules().getByName(name).ifPresent(module -> module.setEnabled(on, false));
      }
   }
}
