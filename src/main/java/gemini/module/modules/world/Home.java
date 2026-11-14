package gemini.module.modules.world;

import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class Home extends Module {
   public static Home instance;
   private boolean saved;
   private String dimension = "minecraft:overworld";
   private double x;
   private double y;
   private double z;
   private boolean hasBack;
   private String backDimension = "minecraft:overworld";
   private double backX;
   private double backY;
   private double backZ;

   public Home() {
      super("Home", "Save a spot in this world and teleport back to it.", Category.WORLD);
      instance = this;
      this.addSetting(new ActionSetting("Set Home", "Save where you are standing", this::setHere));
      this.addSetting(new ActionSetting("Go Home", "Teleport to the saved spot", this::go));
      this.addSetting(new ActionSetting("Back", "Return to where you were before the last teleport", this::back));
      this.addSetting(new ActionSetting("Set Spawn Here", "Make where you stand your respawn point", this::setSpawn));
   }

   public static Home get() {
      return instance;
   }

   public void setHere() {
      LocalPlayer player = player();
      if (player != null) {
         this.dimension = player.level().dimension().identifier().toString();
         this.x = player.getX();
         this.y = player.getY();
         this.z = player.getZ();
         this.saved = true;
         this.save();
         CommandManager.feedback(String.format(Locale.ROOT, "§bHome §7set to §f%.1f %.1f %.1f", this.x, this.y, this.z));
         if (!this.isSavedEnabled()) {
            this.setEnabled(true, false);
         }
      }
   }

   public void setSpawn() {
      LocalPlayer player = player();
      if (player != null && SpCommands.run("spawnpoint @s ~ ~ ~")) {
         CommandManager.feedback(
            String.format(Locale.ROOT, "§bHome §7spawn point set to §f%d %d %d", player.getBlockX(), player.getBlockY(), player.getBlockZ())
         );
      }
   }

   public void go() {
      LocalPlayer player = player();
      if (player != null) {
         if (!this.saved) {
            CommandManager.feedback("§cNo home yet. Use §f.sethome §cor the Set Home button.");
         } else {
            this.remember(player);
            if (SpCommands.teleport(this.dimension, this.x, this.y, this.z)) {
               CommandManager.feedback("§bHome §7teleporting.");
            }
         }
      }
   }

   public void back() {
      if (player() != null) {
         if (!this.hasBack) {
            CommandManager.feedback("§cNowhere to go back to.");
         } else if (SpCommands.teleport(this.backDimension, this.backX, this.backY, this.backZ)) {
            this.hasBack = false;
            CommandManager.feedback("§bHome §7back.");
         }
      }
   }

   private void remember(LocalPlayer player) {
      this.backDimension = player.level().dimension().identifier().toString();
      this.backX = player.getX();
      this.backY = player.getY();
      this.backZ = player.getZ();
      this.hasBack = true;
   }

   private static LocalPlayer player() {
      Minecraft client = Minecraft.getInstance();
      if (SingleplayerGate.allowed() && client.player != null) {
         return client.player;
      } else {
         CommandManager.feedback(SingleplayerGate.denialMessage());
         return null;
      }
   }

   @Override
   public String info() {
      return this.saved ? String.format(Locale.ROOT, "%.0f %.0f %.0f", this.x, this.y, this.z) : "unset";
   }

   @Override
   public void writeExtra(JsonObject object) {
      object.addProperty("homeSet", this.saved);
      object.addProperty("homeDim", this.dimension);
      object.addProperty("homeX", this.x);
      object.addProperty("homeY", this.y);
      object.addProperty("homeZ", this.z);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.saved = object.has("homeSet") && object.get("homeSet").getAsBoolean();
      if (object.has("homeDim")) {
         this.dimension = object.get("homeDim").getAsString();
      }

      if (object.has("homeX")) {
         this.x = object.get("homeX").getAsDouble();
         this.y = object.get("homeY").getAsDouble();
         this.z = object.get("homeZ").getAsDouble();
      }
   }
}
