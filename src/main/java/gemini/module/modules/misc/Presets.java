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
      new ModeSetting("Preset", "Which group of modules to turn on", "General", "General", "Performance", "PvP", "Explore", "Build", "Travel")
   );
   private final BooleanSetting disableOthers = this.addSetting(new BooleanSetting("Disable Others", "First turn off the modules the other presets use that this one does not", false));

   /** The modules each preset turns on, by preset name. */
   private static final Map<String, List<String>> PACKS = new LinkedHashMap<>();

   static {
      PACKS.put("General", List.of("Sprint", "HUD", "Target HUD", "Velocity", "Auto Armor", "Auto Eat", "ESP", "Nametags", "Anti Lag"));
      PACKS.put("Performance", List.of("Anti Lag", "Performance", "Background FPS", "Particles", "HUD", "FPS Graph", "Anti Lag Status"));
      PACKS.put("PvP", List.of("Kill Aura", "Velocity", "Criticals", "Auto Totem", "Auto Armor", "Sprint", "Target HUD", "Hole ESP", "Hit Effect"));
      PACKS.put("Explore", List.of("Fullbright", "X-Ray", "ESP", "Storage ESP", "Waypoints", "Position", "Zoom"));
      PACKS.put("Build", List.of("Scaffold", "Fast Place", "Auto Tool", "Safe Walk", "Sprint"));
      PACKS.put("Travel", List.of("Flight", "Speed", "Jesus", "No Fall", "Sprint", "Step"));
   }

   public Presets() {
      super(
         "Presets", "Turns on a ready-made group of modules for PvP, performance, exploring, building or travel.", Category.CLIENT
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
