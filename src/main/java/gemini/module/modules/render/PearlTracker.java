package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import gemini.util.InfoText;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

/**
 * Ender pearls in flight, their predicted arc and where they land. The arc is worked out once a tick (a pearl only
 * moves once a tick) and kept per pearl; a frame only draws the stored points.
 */
public final class PearlTracker extends Module {
   private final BooleanSetting trajectory = this.addSetting(new BooleanSetting("Flight Path", "Draw the predicted flight path of thrown pearls", true));
   private final BooleanSetting landingBox = this.addSetting(new BooleanSetting("Landing Box", "Highlight where each pearl will land", true));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Owner Label", "Show who threw it and how long until it lands", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Path and box colour", 285));
   private final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
   private int tickStamp;

   public PearlTracker() {
      super("PearlTracker", "Shows where thrown ender pearls will land and who threw them.", Category.RENDER);
   }

   /** One pearl's predicted flight. */
   private static final class Track {
      static final int STEPS = 120;
      final double[] points = new double[(STEPS + 1) * 3];
      int count;
      boolean landed;
      int ticksToLand;
      String owner = "";
      int labelTicks = Integer.MIN_VALUE;
      String label = "";
      int seen;
   }

   @Override
   public void onDisable() {
      this.tracks.clear();
   }

   @Override
   public void onWorldChange() {
      this.tracks.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.player == null) {
         this.tracks.clear();
         return;
      }

      int stamp = ++this.tickStamp;
      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity instanceof Projectile pearl && entity.getType() == EntityTypes.ENDER_PEARL) {
            Track track = this.tracks.get(pearl.getId());
            if (track == null) {
               track = new Track();
               this.tracks.put(pearl.getId(), track);
            }

            track.seen = stamp;
            this.simulate(client, pearl, track);
            Entity owner = pearl.getOwner();
            String ownerName = owner != null ? owner.getName().getString() : "Pearl";
            int ticks = track.landed ? track.ticksToLand : -1;
            if (ticks != track.labelTicks || !ownerName.equals(track.owner)) {
               track.owner = ownerName;
               track.labelTicks = ticks;
               track.label = ticks >= 0 ? ownerName + " (" + InfoText.format(ticks / 20.0, 1) + "s)" : ownerName;
            }
         }
      }

      if (this.tracks.size() > 0) {
         this.tracks.values().removeIf(track -> track.seen != stamp);
      }
   }

   private void simulate(Minecraft client, Projectile pearl, Track track) {
      Vec3 simPos = pearl.position();
      Vec3 simVel = pearl.getDeltaMovement();
      double[] points = track.points;
      points[0] = simPos.x;
      points[1] = simPos.y;
      points[2] = simPos.z;
      int count = 1;
      track.landed = false;
      track.ticksToLand = -1;
      for (int step = 0; step < Track.STEPS; step++) {
         Vec3 next = simPos.add(simVel);
         BlockHitResult hit = client.level.clip(new ClipContext(simPos, next, Block.COLLIDER, Fluid.NONE, pearl));
         boolean struck = hit.getType() != Type.MISS;
         Vec3 end = struck ? hit.getLocation() : next;
         points[count * 3] = end.x;
         points[count * 3 + 1] = end.y;
         points[count * 3 + 2] = end.z;
         count++;
         if (struck) {
            track.landed = true;
            track.ticksToLand = step;
            break;
         }

         simPos = next;
         simVel = simVel.scale(0.99).subtract(0.0, 0.03, 0.0);
      }

      track.count = count;
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.player == null || this.tracks.isEmpty()) {
         return;
      }

      int argb = this.color.argb();
      int faint = Render3D.alpha(argb, 185);
      boolean path = this.trajectory.get();
      boolean showLabels = this.labels.get();
      for (Entity entity : client.level.entitiesForRendering()) {
         if (!(entity instanceof Projectile) || entity.getType() != EntityTypes.ENDER_PEARL) {
            continue;
         }

         Track track = this.tracks.get(entity.getId());
         if (track == null) {
            continue;
         }

         Render3D.entityBoxGlow(entity, partialTick, 0.08, argb, 1.8F, 70, true);
         double[] p = track.points;
         if (path) {
            for (int i = 1; i < track.count; i++) {
               // The segment that ends on the impact point is drawn solid, the rest of the arc a little fainter.
               boolean last = track.landed && i == track.count - 1;
               int from = (i - 1) * 3;
               int to = i * 3;
               Render3D.line(p[from], p[from + 1], p[from + 2], p[to], p[to + 1], p[to + 2], last ? argb : faint, last ? 1.6F : 1.5F, true);
            }
         }

         if (track.landed && this.landingBox.get()) {
            int at = (track.count - 1) * 3;
            double lx = p[at];
            double ly = p[at + 1];
            double lz = p[at + 2];
            Render3D.boxGlow(lx - 0.3, ly - 0.1, lz - 0.3, lx + 0.3, ly + 0.5, lz + 0.3, argb, 2.0F, 65, true);
            if (showLabels) {
               Render3D.text(track.label, lx, ly + 0.75, lz, argb, 0.85F, true);
            }
         } else if (showLabels) {
            Render3D.text(
               track.owner,
               Render3D.entityX(entity, partialTick),
               Render3D.entityY(entity, partialTick) + 0.55,
               Render3D.entityZ(entity, partialTick),
               argb,
               0.85F,
               true
            );
         }
      }
   }
}
