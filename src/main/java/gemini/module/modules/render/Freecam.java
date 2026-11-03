package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Freecam extends Module {
   public static Freecam instance;
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Camera speed in blocks per tick", 1.0, 0.1, 5.0, 0.1));
   private final NumberSetting boost = this.addSetting(
      new NumberSetting("Sprint Boost", "Speed multiplier while the sprint key is held", 2.5, 1.0, 6.0, 0.5)
   );
   private final BooleanSetting freeze = this.addSetting(new BooleanSetting("Freeze Player", "Ignore movement keys so your character stands still", true));
   private final BooleanSetting stopOnHurt = this.addSetting(new BooleanSetting("Disable On Damage", "Return to your body when you get hit", true));
   private final ClientInput frozen = new ClientInput();
   private ClientInput saved;
   private double x;
   private double y;
   private double z;
   private double oldX;
   private double oldY;
   private double oldZ;
   private float yaw;
   private float pitch;
   private boolean ready;

   public Freecam() {
      super("Freecam", "Fly the camera around while your character stays put.", Category.RENDER);
      instance = this;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   protected boolean extraActivation() {
      return Minecraft.getInstance().player != null;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Vec3 eye = player.getEyePosition();
         this.x = this.oldX = eye.x;
         this.y = this.oldY = eye.y;
         this.z = this.oldZ = eye.z;
         this.yaw = player.getYRot();
         this.pitch = player.getXRot();
         this.ready = true;
      }
   }

   @Override
   public void onDisable() {
      this.ready = false;
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.input == this.frozen) {
         player.input = (ClientInput)(this.saved != null ? this.saved : new KeyboardInput(Minecraft.getInstance().options));
      }

      this.saved = null;
   }

   public boolean active() {
      return this.isEnabled() && this.ready;
   }

   public double x(float partial) {
      return Mth.lerp(partial, this.oldX, this.x);
   }

   public double y(float partial) {
      return Mth.lerp(partial, this.oldY, this.y);
   }

   public double z(float partial) {
      return Mth.lerp(partial, this.oldZ, this.z);
   }

   public float yaw() {
      return this.yaw;
   }

   public float pitch() {
      return this.pitch;
   }

   public boolean consumeTurn(Entity entity, double dx, double dy) {
      if (this.active() && entity == Minecraft.getInstance().player) {
         this.yaw += (float)dx * 0.15F;
         this.pitch = Mth.clamp(this.pitch + (float)dy * 0.15F, -90.0F, 90.0F);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (this.stopOnHurt.get() && player.hurtTime > 0) {
            this.setEnabled(false);
         } else {
            if (this.freeze.get()) {
               if (player.input != this.frozen) {
                  this.saved = player.input;
                  player.input = this.frozen;
               }
            } else if (player.input == this.frozen && this.saved != null) {
               player.input = this.saved;
               this.saved = null;
            }

            this.oldX = this.x;
            this.oldY = this.y;
            this.oldZ = this.z;
            if (client.gui.screen() == null) {
               Options options = client.options;
               double forward = (options.keyUp.isDown() ? 1.0 : 0.0) - (options.keyDown.isDown() ? 1.0 : 0.0);
               double strafe = (options.keyRight.isDown() ? 1.0 : 0.0) - (options.keyLeft.isDown() ? 1.0 : 0.0);
               double lift = (options.keyJump.isDown() ? 1.0 : 0.0) - (options.keyShift.isDown() ? 1.0 : 0.0);
               double length = Math.hypot(forward, strafe);
               double step = this.speed.get() * (options.keySprint.isDown() ? this.boost.get() : 1.0);
               if (length > 0.0) {
                  double radians = Math.toRadians(this.yaw);
                  double sin = Math.sin(radians);
                  double cos = Math.cos(radians);
                  double f = forward / length;
                  double s = strafe / length;
                  this.x += (-sin * f - cos * s) * step;
                  this.z += (cos * f - sin * s) * step;
               }

               this.y += lift * step;
            }
         }
      }
   }
}
