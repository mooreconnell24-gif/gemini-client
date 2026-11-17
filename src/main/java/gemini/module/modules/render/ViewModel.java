package gemini.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gemini.module.Category;
import net.minecraft.world.entity.Entity;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Moves, turns and scales the first-person items. The offsets are applied right after vanilla's base item-arm
 * placement ({@code applyItemArmTransform}), so they are in screen-aligned space and every later animation (swing,
 * eat, bow pull) happens around the moved item. X and the Y/Z rotations are mirrored for the left arm, so a
 * positive X moves either item outward.
 *
 * <p>The eat and swing scales blend the pose before and after vanilla's eat/drink and swing transforms: 0 skips
 * the motion, 1 is vanilla, values in between shorten it. "Swing Duration" changes how long a swing
 * lasts and "Swing Motion" how far it moves; the two compose. The bare-hand punch is not
 * scaled; only item swings are.
 */
public final class ViewModel extends Module {
   public static ViewModel instance;
   private final List<Setting<?>> resettable = new ArrayList<>();
   private final NumberSetting mainX = this.number("Main X", "Main-hand item: outward (+) / inward (-)", -1.0, 1.0);
   private final NumberSetting mainY = this.number("Main Y", "Main-hand item: up (+) / down (-)", -1.0, 1.0);
   private final NumberSetting mainZ = this.number("Main Z", "Main-hand item: toward you (+) / away (-)", -1.0, 1.0);
   private final NumberSetting mainScale = this.scale("Main Scale", "Main-hand item size");
   private final NumberSetting mainRotX = this.angle("Main Rot X", "Main-hand item pitch");
   private final NumberSetting mainRotY = this.angle("Main Rot Y", "Main-hand item yaw");
   private final NumberSetting mainRotZ = this.angle("Main Rot Z", "Main-hand item roll");
   private final NumberSetting offX = this.number("Off X", "Off-hand item: outward (+) / inward (-)", -1.0, 1.0);
   private final NumberSetting offY = this.number("Off Y", "Off-hand item: up (+) / down (-)", -1.0, 1.0);
   private final NumberSetting offZ = this.number("Off Z", "Off-hand item: toward you (+) / away (-)", -1.0, 1.0);
   private final NumberSetting offScale = this.scale("Off Scale", "Off-hand item size");
   private final NumberSetting offRotX = this.angle("Off Rot X", "Off-hand item pitch");
   private final NumberSetting offRotY = this.angle("Off Rot Y", "Off-hand item yaw");
   private final NumberSetting offRotZ = this.angle("Off Rot Z", "Off-hand item roll");
   private final NumberSetting eatScale = this.track(
      this.addSetting(new NumberSetting("Eat Motion", "Eating and drinking bob: 0 = still, 1 = vanilla", 1.0, 0.0, 1.0, 0.05))
   );
   private final NumberSetting swingScale = this.track(
      this.addSetting(new NumberSetting("Swing Motion", "Item swing movement: 0 = no swing, 1 = vanilla", 1.0, 0.0, 1.0, 0.05))
   );
   private final NumberSetting swingDuration = this.track(
      this.addSetting(new NumberSetting("Swing Duration", "Ticks your arm swing takes; 6 leaves it to the game (which shortens it with Haste)", 6.0, 1.0, 20.0, 1.0))
   );
   private final Matrix4f savedPose = new Matrix4f();
   private final Matrix3f savedNormal = new Matrix3f();

   public ViewModel() {
      super("View Model", "Moves, rotates and scales the items in your hands in first person, and changes the eating and swing animations.", Category.RENDER);
      this.addSetting(new ActionSetting("Reset", "Put every view model setting back to vanilla", this::resetAll));
      instance = this;
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Animations");
      return names;
   }

   /** The length of {@code entity}'s arm swing in ticks, or -1 to leave it to the game. Only your own swing changes. */
   public static int swingDuration(Entity entity) {
      ViewModel module = instance;
      if (module == null || !module.isEnabled() || entity != Minecraft.getInstance().player) {
         return -1;
      }

      int ticks = module.swingDuration.intValue();
      return ticks == 6 ? -1 : ticks;
   }

   private NumberSetting track(NumberSetting setting) {
      this.resettable.add(setting);
      return setting;
   }

   private NumberSetting number(String name, String description, double min, double max) {
      return this.track(this.addSetting(new NumberSetting(name, description, 0.0, min, max, 0.02)));
   }

   private NumberSetting scale(String name, String description) {
      return this.track(this.addSetting(new NumberSetting(name, description, 1.0, 0.1, 2.0, 0.05)));
   }

   private NumberSetting angle(String name, String description) {
      return this.track(this.addSetting(new NumberSetting(name, description, 0.0, -180.0, 180.0, 5.0)));
   }

   private void resetAll() {
      for (Setting<?> setting : this.resettable) {
         setting.reset();
      }

      this.save();
   }

   private static boolean isMain(HumanoidArm arm) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? arm == HumanoidArm.RIGHT : arm == player.getMainArm();
   }

   /** Called at the end of vanilla's item-arm placement for either hand. */
   public static void applyArm(PoseStack pose, HumanoidArm arm) {
      ViewModel module = instance;
      if (module == null || !module.isEnabled() || pose == null) {
         return;
      }

      boolean main = isMain(arm);
      float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
      float x = (main ? module.mainX : module.offX).floatValue();
      float y = (main ? module.mainY : module.offY).floatValue();
      float z = (main ? module.mainZ : module.offZ).floatValue();
      float rx = (main ? module.mainRotX : module.offRotX).floatValue();
      float ry = (main ? module.mainRotY : module.offRotY).floatValue();
      float rz = (main ? module.mainRotZ : module.offRotZ).floatValue();
      float scale = (main ? module.mainScale : module.offScale).floatValue();
      pose.translate(x * side, y, z);
      if (rx != 0.0F) {
         pose.rotateDegrees(Axis.XP, rx);
      }

      if (ry != 0.0F) {
         pose.rotateDegrees(Axis.YP, ry * side);
      }

      if (rz != 0.0F) {
         pose.rotateDegrees(Axis.ZP, rz * side);
      }

      if (scale != 1.0F) {
         pose.scale(scale, scale, scale);
      }
   }

   /** How much of vanilla's eat/drink motion to keep; 1 when the module is off. */
   public static float eatFactor() {
      ViewModel module = instance;
      return module != null && module.isEnabled() ? module.eatScale.floatValue() : 1.0F;
   }

   /** How much of vanilla's item swing motion to keep; 1 when the module is off. */
   public static float swingFactor() {
      ViewModel module = instance;
      return module != null && module.isEnabled() ? module.swingScale.floatValue() : 1.0F;
   }

   /**
    * Runs a vanilla pose transform at a fraction of its strength: the pose before and after are blended. 0 skips
    * the transform, 1 runs it unchanged.
    */
   public static void scaled(PoseStack pose, float factor, Runnable transform) {
      ViewModel module = instance;
      if (module == null || factor >= 0.999F) {
         transform.run();
      } else if (factor > 0.001F) {
         PoseStack.Pose last = pose.last();
         module.savedPose.set(last.pose());
         module.savedNormal.set(last.normal());
         transform.run();
         // lerp(other, t) moves this toward other by t: keep 'factor' of the change.
         last.pose().lerp(module.savedPose, 1.0F - factor);
         last.normal().lerp(module.savedNormal, 1.0F - factor);
      }
   }

   @Override
   public String info() {
      return this.swingScale.get() < 1.0 || this.eatScale.get() < 1.0 ? "Damped" : null;
   }
}
