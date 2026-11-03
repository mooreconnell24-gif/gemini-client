package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class Trajectories extends Module {
   private final BooleanSetting bows = this.addSetting(new BooleanSetting("Bows", "Bows and loaded crossbows", true));
   private final BooleanSetting tridents = this.addSetting(new BooleanSetting("Tridents", "Thrown tridents", true));
   private final BooleanSetting throwables = this.addSetting(new BooleanSetting("Throwables", "Pearls, snowballs, eggs, potions and XP bottles", true));
   private final NumberSetting steps = this.addSetting(new NumberSetting("Max Steps", "How many ticks of flight to simulate", 150.0, 20.0, 400.0, 10.0));
   private final BooleanSetting landing = this.addSetting(new BooleanSetting("Landing Marker", "Box where the projectile ends", true));
   private final BooleanSetting entities = this.addSetting(
      new BooleanSetting("Hit Entities", "Stop trajectory arc at entities and highlight the target", true)
   );
   private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the path", 2.0, 0.5, 5.0, 0.5));
   private final ColorSetting pathColor = this.addSetting(new ColorSetting("Path Color", "Color of the flight path", 190));
   private final ColorSetting hitColor = this.addSetting(new ColorSetting("Hit Color", "Color of the landing marker", 0));

   public Trajectories() {
      super("Trajectories", "Predict where arrows, pearls and other projectiles will land.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null && !player.isSpectator()) {
         Trajectories.Launch launch = this.launchOf(player, player.getMainHandItem());
         if (launch == null) {
            launch = this.launchOf(player, player.getOffhandItem());
         }

         if (launch != null) {
            this.simulate(client, player, level, launch, partialTick);
         }
      }
   }

   private Trajectories.Launch launchOf(LocalPlayer player, ItemStack stack) {
      if (stack.isEmpty()) {
         return null;
      } else if (stack.getItem() instanceof BowItem) {
         if (this.bows.get() && player.isUsingItem() && player.getUseItem() == stack) {
            float power = BowItem.getPowerForTime(player.getTicksUsingItem());
            return power < 0.1F ? null : new Trajectories.Launch(power * 3.0, 0.05, 0.0, true);
         } else {
            return null;
         }
      } else if (stack.getItem() instanceof CrossbowItem) {
         return this.bows.get() && CrossbowItem.isCharged(stack) ? new Trajectories.Launch(3.15, 0.05, 0.0, true) : null;
      } else if (!stack.is(Items.TRIDENT)) {
         if (!this.throwables.get()) {
            return null;
         } else if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
            return new Trajectories.Launch(1.5, 0.03, 0.0, false);
         } else if (!stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
            return stack.is(Items.EXPERIENCE_BOTTLE) ? new Trajectories.Launch(0.7, 0.07, -20.0, false) : null;
         } else {
            return new Trajectories.Launch(0.5, 0.05, -20.0, false);
         }
      } else {
         return this.tridents.get() && player.isUsingItem() && player.getUseItem() == stack
            ? new Trajectories.Launch(2.5, 0.05, 0.0, true)
            : null;
      }
   }

   private void simulate(Minecraft client, LocalPlayer player, ClientLevel level, Trajectories.Launch launch, float partialTick) {
      double yaw = Math.toRadians(player.getYRot());
      double pitch = Math.toRadians(player.getXRot());
      double offset = Math.toRadians(player.getXRot() + launch.pitchOffset());
      Vec3 direction = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(offset), Math.cos(yaw) * Math.cos(pitch)).normalize();
      Vec3 own = player.getKnownMovement();
      Vec3 velocity = direction.scale(launch.speed()).add(own.x, player.onGround() ? 0.0 : own.y, own.z);
      Vec3 position = player.getEyePosition(partialTick).subtract(0.0, 0.1, 0.0);
      List<Entity> candidates = this.entities.get()
         ? level.getEntities((Entity)null, player.getBoundingBox().inflate(80.0), entity -> entity != player && entity.isPickable() && !entity.isSpectator())
         : List.of();
      List<Vec3> path = new ArrayList<>();
      path.add(position);
      Entity struck = null;
      Vec3 end = null;
      int limit = this.steps.intValue();

      for (int i = 0; i < limit; i++) {
         Vec3 next = position.add(velocity);
         BlockHitResult block = level.clip(new ClipContext(position, next, Block.COLLIDER, Fluid.NONE, player));
         Vec3 segmentEnd = block.getType() == Type.MISS ? next : block.getLocation();
         double best = Double.MAX_VALUE;

         for (Entity candidate : candidates) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3).clip(position, segmentEnd);
            if (hit.isPresent()) {
               double distance = hit.get().distanceToSqr(position);
               if (distance < best) {
                  best = distance;
                  struck = candidate;
                  segmentEnd = hit.get();
               }
            }
         }

         path.add(segmentEnd);
         if (struck != null || block.getType() != Type.MISS) {
            end = segmentEnd;
            break;
         }

         position = next;
         boolean water = level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER);
         double drag = water ? (launch.arrow() ? 0.6 : 0.8) : 0.99;
         velocity = velocity.scale(drag).subtract(0.0, launch.gravity(), 0.0);
      }

      int path1 = this.pathColor.argb();
      float lineWidth = this.width.floatValue();

      for (int i = 1; i < path.size(); i++) {
         Render3D.line(path.get(i - 1), path.get(i), path1, lineWidth, true);
      }

      if (end != null && this.landing.get()) {
         int hit = this.hitColor.argb();
         AABB marker = new AABB(end.x - 0.2, end.y - 0.2, end.z - 0.2, end.x + 0.2, end.y + 0.2, end.z + 0.2);
         Render3D.box(marker, hit, true, true, 2.0F, 90, true);
         if (struck != null) {
            Render3D.box(Render3D.interpolatedBox(struck, partialTick), hit, true, true, 2.0F, 40, true);
         }
      }
   }

   private record Launch(double speed, double gravity, double pitchOffset, boolean arrow) {
   }
}
