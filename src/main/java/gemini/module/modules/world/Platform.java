package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;

public final class Platform extends Module {
   private final ModeSetting block = this.addSetting(
      new ModeSetting(
         "Block",
         "What the platform is made of",
         "Glass",
         "Glass",
         "Stone",
         "Smooth Stone",
         "Oak Planks",
         "Obsidian",
         "Sea Lantern",
         "Glowstone",
         "Barrier",
         "Bedrock",
         "Tinted Glass",
         "White Concrete"
      )
   );
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks from you to the edge", 3.0, 1.0, 12.0, 1.0));
   private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "How many blocks below your feet", 1.0, 1.0, 5.0, 1.0));
   private final ModeSetting onToggle = this.addSetting(new ModeSetting("On Toggle", "What switching the module on does", "Place", "Place", "Remove"));

   public Platform() {
      super("Platform", "Put a platform under your feet, or remove it, with one click or key.", Category.WORLD);
      this.addSetting(new ActionSetting("Place", "Build the platform under you now", this::place));
      this.addSetting(new ActionSetting("Remove", "Remove a platform of this block under you", this::remove));
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
   public void onEnable() {
      if (this.onToggle.is("Remove")) {
         this.remove();
      } else {
         this.place();
      }

      this.setEnabled(false, false);
   }

   private String id() {
      return "minecraft:" + this.block.get().toLowerCase(Locale.ROOT).replace(' ', '_');
   }

   private String area() {
      int reach = this.radius.intValue();
      int below = this.depth.intValue();
      return String.format(Locale.ROOT, "~%d ~%d ~%d ~%d ~%d ~%d", -reach, -below, -reach, reach, -below, reach);
   }

   private void place() {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.run("fill " + this.area() + " " + this.id())) {
         CommandManager.feedback("§bPlatform §7placed §f" + this.block.get() + " §7under you.");
      }
   }

   private void remove() {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.run("fill " + this.area() + " minecraft:air replace " + this.id())) {
         CommandManager.feedback("§bPlatform §7removed the §f" + this.block.get() + " §7under you.");
      }
   }

   @Override
   public String info() {
      return this.block.get();
   }
}
