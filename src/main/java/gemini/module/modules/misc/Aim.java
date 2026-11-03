package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * How module rotations look and feel. Always on (it only holds settings): spring-smoothed turns for modules that
 * move the camera, and a marker in the world showing where silent aim is pointing, since silent rotations never
 * move the camera.
 */
public final class Aim extends Module {
   public static Aim instance;
   private final BooleanSetting spring = this.addSetting(
      new BooleanSetting("Spring Turns", "Turns from modules accelerate and settle like a hand on a mouse instead of moving at one fixed rate", true)
   );
   private final NumberSetting smoothness = this.addSetting(
      new NumberSetting("Snappiness", "How quickly a spring turn reaches its target", 0.6, 0.1, 1.0, 0.05).showIf(this.spring::get)
   );
   private final BooleanSetting marker = this.addSetting(
      new BooleanSetting("Show Silent Aim", "Draw a marker in the world where silent rotations are pointing", true)
   );
   private final ColorSetting color = this.addSetting(new ColorSetting("Marker Color", "Colour of the aim marker", 196).showIf(this.marker::get));
   private final NumberSetting distance = this.addSetting(
      new NumberSetting("Marker Distance", "How far in front of your eyes the marker floats", 2.5, 1.0, 6.0, 0.5).showIf(this.marker::get)
   );
   private long lastFrame;

   public Aim() {
      super("Aim", "Smooth module turns and show where silent aim points.", Category.CLIENT);
      instance = this;
      this.loadState(true, this.getKeybind());
   }

   @Override
   protected boolean settingsOnly() {
      return true;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   protected boolean hasBindOptions() {
      return false;
   }

   public boolean springTurns() {
      return this.spring.get();
   }

   public float smoothness() {
      return this.smoothness.floatValue();
   }

   @Override
   public void onRender3D(float partialTick) {
      if (!this.marker.get()) {
         return;
      }

      long now = Util.getMillis();
      float seconds = this.lastFrame == 0L ? 0.05F : Mth.clamp((now - this.lastFrame) / 1000.0F, 0.0F, 0.1F);
      this.lastFrame = now;
      float[] aim = RotationManager.shownAim(seconds);
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (aim == null || player == null) {
         return;
      }

      float yawRad = -aim[0] * (float)(Math.PI / 180.0);
      float pitchRad = aim[1] * (float)(Math.PI / 180.0);
      float cosPitch = Mth.cos(pitchRad);
      Vec3 look = new Vec3(Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
      Vec3 at = player.getEyePosition(partialTick).add(look.scale(this.distance.get()));
      Render3D.point(at, this.color.argb(), 5.0F, true);
   }
}
