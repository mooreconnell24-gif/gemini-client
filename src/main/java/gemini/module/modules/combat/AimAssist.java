package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class AimAssist extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view cone", 90.0, 15.0, 180.0, 5.0));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Smooth Speed", "Camera assist speed in deg/tick", 14.0, 2.0, 45.0, 1.0));
   private final BooleanSetting weaponOnly = this.addSetting(new BooleanSetting("Weapon Only", "Only assist while holding a weapon", true));
   private final Targeting targeting = Targeting.builder(this).build();
   private final Predicate<LivingEntity> inFov = this::inFov;
   private LocalPlayer scanPlayer;

   public AimAssist() {
      super("AimAssist", "Smoothly guide your crosshair toward nearby targets.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (this.weaponOnly.get() && !AutoWeapon.isWeapon(player.getMainHandItem())) {
            this.targeting.clear();
            return;
         }

         this.scanPlayer = player;
         LivingEntity target = this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), this.inFov);
         if (target != null) {
            float[] look = Rotations.face(player, target);
            RotationManager.request(player, look[0], look[1], this.speed.floatValue(), false, ActionPriority.WORLD_UTILITY, 2.0F);
         }
      } else {
         this.targeting.clear();
      }
   }

   private boolean inFov(LivingEntity living) {
      LocalPlayer player = this.scanPlayer;
      return player != null && Targets.inFov(player, living, this.fov.get());
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   @Override
   public void onDisable() {
      this.targeting.clear();
      this.scanPlayer = null;
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }
}
