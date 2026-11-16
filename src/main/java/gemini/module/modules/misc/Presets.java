package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Presets extends Module {
   private final ModeSetting pack = this.addSetting(
      new ModeSetting("Preset", "Which bundle of modules to enable", "LiquidBounce", "LiquidBounce", "Performance", "PvP", "Explore", "Build", "Travel")
   );
   private final BooleanSetting disableOthers = this.addSetting(new BooleanSetting("Disable Others", "First turn off the modules the other presets use that this one does not", false));

   /** The modules each preset turns on, by preset name. */
   private static final Map<String, List<String>> PACKS = new LinkedHashMap<>();

   static {
      PACKS.put("LiquidBounce", List.of("Sprint", "HUD", "TargetHUD", "Velocity", "AutoArmor", "InventoryCleaner", "ESP", "Nametags", "AntiLag"));
      PACKS.put("Performance", List.of("AntiLag", "EntityCulling", "Unfocused CPU", "Particles", "HUD", "FPS Graph", "AntiLag Status"));
      PACKS.put("PvP", List.of("KillAura", "Velocity", "Criticals", "AutoTotem", "AutoArmor", "Sprint", "TargetHUD", "HoleESP", "HitEffect"));
      PACKS.put("Explore", List.of("Fullbright", "Xray", "ESP", "StorageESP", "Waypoints", "Coordinates", "Zoom"));
      PACKS.put("Build", List.of("Scaffold", "FastPlace", "AutoTool", "SafeWalk", "Sprint"));
      PACKS.put("Travel", List.of("Flight", "Speed", "Jesus", "NoFall", "Sprint", "Step"));
   }

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

   /**
    * The modules "Disable Others" turns off for {@code chosen}: every module of the other presets that the chosen
    * one does not use itself. The HUD is never among them; it is a settings page, not something to switch off.
    */
   static Set<String> othersOf(String chosen) {
      List<String> keep = PACKS.getOrDefault(chosen, List.of());
      Set<String> off = new LinkedHashSet<>();
      for (Map.Entry<String, List<String>> entry : PACKS.entrySet()) {
         if (!entry.getKey().equals(chosen)) {
            for (String name : entry.getValue()) {
               if (!keep.contains(name) && !name.equals("HUD")) {
                  off.add(name);
               }
            }
         }
      }

      return off;
   }

   @Override
   public void onEnable() {
      Gemini client = Gemini.getInstance();
      if (client == null) {
         this.setEnabled(false, false);
      } else {
         String chosen = this.pack.get();
         if (this.disableOthers.get()) {
            for (String name : othersOf(chosen)) {
               client.modules().getByName(name).ifPresent(module -> module.setEnabled(false, false));
            }
         }

         for (String name : PACKS.getOrDefault(chosen, List.of())) {
            client.modules().getByName(name).ifPresent(module -> module.setEnabled(true, false));
         }

         CommandManager.feedback("§bPresets §7applied §f" + chosen + "§7.");
         this.setEnabled(false, false);
      }
   }
}
