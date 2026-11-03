package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BuiltInBaritone;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Spawns a chosen mob a few blocks in front of you with /summon: a sparring partner on demand. */
public final class Summoner extends Module {
   private final ModeSetting mob = this.addSetting(
      new ModeSetting(
         "Mob",
         "What to summon",
         "Zombie",
         "Zombie",
         "Skeleton",
         "Creeper",
         "Spider",
         "Enderman",
         "Witch",
         "Blaze",
         "Piglin",
         "Villager",
         "Iron Golem",
         "Wolf",
         "Horse",
         "Cow",
         "Pig",
         "Sheep",
         "Chicken",
         "Armor Stand",
         "Lightning"
      )
   );
   private final NumberSetting count = this.addSetting(new NumberSetting("Count", "How many to summon at once", 1.0, 1.0, 16.0, 1.0));
   private final NumberSetting distance = this.addSetting(new NumberSetting("Distance", "Blocks in front of you", 4.0, 2.0, 24.0, 1.0));

   public Summoner() {
      super("Summoner", "Summon a chosen mob in front of you to practise against.", Category.WORLD);
      this.addSetting(new ActionSetting("Summon", "Summon the selected mob now", this::summon));
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
      this.summon();
      this.setEnabled(false, false);
   }

   private void summon() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         return;
      } else if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
         return;
      }

      double yawRad = Math.toRadians(player.getYRot());
      double x = player.getX() - Math.sin(yawRad) * this.distance.get();
      double z = player.getZ() + Math.cos(yawRad) * this.distance.get();
      int y = BuiltInBaritone.findSurfaceY(client.level, Mth.floor(x), Mth.floor(z), player.getBlockY());
      String id = this.mob.get().toLowerCase(Locale.ROOT).replace(' ', '_');
      if ("lightning".equals(id)) {
         id = "lightning_bolt";
      }

      int n = this.count.intValue();
      for (int i = 0; i < n; i++) {
         if (!SpCommands.run(String.format(Locale.ROOT, "summon minecraft:%s %.2f %d %.2f", id, x, y, z))) {
            CommandManager.feedback(SingleplayerGate.denialMessage());
            return;
         }
      }

      CommandManager.feedback("§bSummoner §7summoned §f" + n + " × " + this.mob.get() + "§7.");
   }

   @Override
   public String info() {
      return this.mob.get();
   }
}
