package gemini.module.modules;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.VersionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class AntiVoidModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Teleport returns you to the last ground you stood on; Bounce pushes you back up", "Teleport", "Teleport", "Bounce")
   );
   private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "Blocks below the world floor to start rescuing", 2.0, 0.0, 20.0, 1.0));
   private final NumberSetting boost = this.addSetting(
      new NumberSetting("Boost", "Upward speed of the rescue", 0.7, 0.2, 2.0, 0.1).showIf(() -> this.mode.is("Bounce"))
   );
   private boolean haveSafe;
   private String safeDimension;
   private double safeX;
   private double safeY;
   private double safeZ;
   private int cooldown;

   public AntiVoidModule() {
      super("AntiVoid", "Catch yourself before falling into the void.", Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.haveSafe = false;
      this.cooldown = 0;
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null || player.getAbilities().flying || player.isSpectator()) {
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
      }

      int minY = VersionBridge.minWorldY(client.level);
      if (player.onGround() && !player.isInWater() && !player.isInLava() && player.getY() >= minY) {
         this.haveSafe = true;
         this.safeDimension = player.level().dimension().identifier().toString();
         this.safeX = player.getX();
         this.safeY = player.getY();
         this.safeZ = player.getZ();
      }

      double floor = minY - this.depth.get();
      if (player.getY() >= floor + 2.0) {
         return;
      }

      String dimension = player.level().dimension().identifier().toString();
      if (this.mode.is("Teleport") && this.haveSafe && dimension.equals(this.safeDimension)) {
         if (this.cooldown <= 0 && SoloModules.teleport(this.safeDimension, this.safeX, this.safeY + 0.05, this.safeZ)) {
            player.setDeltaMovement(Vec3.ZERO);
            AttributeBoost.resetFall(player);
            this.cooldown = 20;
            CommandManager.feedback("§bAntiVoid §7caught you and returned you to solid ground.");
         }
      } else {
         Vec3 motion = player.getDeltaMovement();
         if (motion.y < this.boost.get()) {
            player.setDeltaMovement(motion.x, this.boost.get(), motion.z);
         }
      }

      AttributeBoost.resetFall(player);
   }
}
