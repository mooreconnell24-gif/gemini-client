package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public final class FreeLook extends Module {
   public static FreeLook instance;
   private float yaw;
   private float pitch;
   private boolean ready;

   public FreeLook() {
      super("Free Look", "Look around freely while your character keeps facing forward.", Category.RENDER);
      instance = this;
      this.holdByDefault();
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.yaw = player.getYRot();
         this.pitch = player.getXRot();
         this.ready = true;
      }
   }

   @Override
   public void onDisable() {
      this.ready = false;
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
         this.yaw += (float)dx * 0.15F;
         this.pitch = Mth.clamp(this.pitch + (float)dy * 0.15F, -90.0F, 90.0F);
         return true;
      } else {
         return false;
      }
   }
}
