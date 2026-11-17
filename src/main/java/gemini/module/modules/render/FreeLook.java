package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public final class FreeLook extends Module {
   public static FreeLook instance;
   private final BooleanSetting thirdPerson = this.addSetting(
      new BooleanSetting("Third Person", "Switch to the third-person camera while looking around, and back afterwards", true)
   );
   private final NumberSetting sensitivity = this.addSetting(
      new NumberSetting("Sensitivity", "How fast the camera turns compared with normal looking", 1.0, 0.2, 2.0, 0.1)
   );
   private float yaw;
   private float pitch;
   private boolean ready;
   /** The camera the player had before this module changed it; null when it did not. */
   private CameraType previousCamera;

   public FreeLook() {
      super("Free Look", "Lets you look around while your character keeps facing and walking the same way.", Category.RENDER);
      instance = this;
      this.holdByDefault();
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         this.yaw = player.getYRot();
         this.pitch = player.getXRot();
         this.ready = true;
         if (this.thirdPerson.get() && client.options.getCameraType() == CameraType.FIRST_PERSON) {
            this.previousCamera = CameraType.FIRST_PERSON;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         }
      }
   }

   @Override
   public void onDisable() {
      this.ready = false;
      if (this.previousCamera != null) {
         Minecraft client = Minecraft.getInstance();
         // Only put the camera back if it is still the one this module chose; the player may have pressed F5 since.
         if (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            client.options.setCameraType(this.previousCamera);
         }

         this.previousCamera = null;
      }
   }

   public boolean active() {
      return this.isEnabled() && this.ready && (Freecam.instance == null || !Freecam.instance.active());
   }

   public float yaw() {
      return this.yaw;
   }

   public float pitch() {
      return this.pitch;
   }

   public boolean consumeTurn(Entity entity, double dx, double dy) {
      if (this.active() && entity == Minecraft.getInstance().player) {
         float scale = 0.15F * this.sensitivity.floatValue();
         this.yaw += (float)dx * scale;
         this.pitch = Mth.clamp(this.pitch + (float)dy * scale, -90.0F, 90.0F);
         return true;
      } else {
         return false;
      }
   }
}
