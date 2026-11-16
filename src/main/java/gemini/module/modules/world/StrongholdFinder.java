package gemini.module.modules.world;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.InfoText;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;

/**
 * Reads where thrown Eyes of Ender fly. Each eye's heading is taken from where it was first seen to where it is
 * {@link #SAMPLE_TICKS} ticks later (the client is told its position, not its target), reported once per eye, and
 * the last two throws are crossed with {@link StrongholdMath#intersect} to estimate the stronghold's position.
 */
public final class StrongholdFinder extends Module {
   /** Ticks an eye is watched before its heading is read: long enough to travel a few blocks, before it slows. */
   private static final int SAMPLE_TICKS = 12;
   /** Horizontal blocks an eye must have moved for its heading to mean anything. */
   private static final double MIN_TRAVEL = 1.5;
   private final BooleanSetting pin = this.addSetting(
      new BooleanSetting("Pin Estimate", "Add a waypoint at the estimated stronghold once two throws cross", true)
   );
   /** Eyes being watched: entity id to {x, z, first tick seen}. */
   private final Int2ObjectOpenHashMap<double[]> watching = new Int2ObjectOpenHashMap<>();
   /** Eyes already reported, so one eye is never reported twice. */
   private final IntOpenHashSet reported = new IntOpenHashSet();
   private final InfoText infoText = new InfoText();
   /** The last two throws: {x, z, dx, dz}; [1] is the newest. */
   private double[] previous;
   private double[] latest;
   private int estimates;
   private int ticks;

   public StrongholdFinder() {
      super(
         "StrongholdFinder",
         "Shows the direction of each eye of ender you throw and, after two throws from different spots, estimates where the stronghold is.",
         Category.WORLD
      );
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   /** Throws from another dimension or world do not cross with ones made here. */
   @Override
   public void onWorldChange() {
      this.forget();
   }

   private void forget() {
      this.watching.clear();
      this.reported.clear();
      this.previous = null;
      this.latest = null;
      this.estimates = 0;
   }

   @Override
   public String info() {
      return this.latest == null ? null : this.infoText.whole(this.previous == null ? 1 : 2, " throws");
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.player == null) {
         return;
      }

      this.ticks++;
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof EyeOfEnder eye && !this.reported.contains(eye.getId())) {
            double[] start = this.watching.get(eye.getId());
            if (start == null) {
               this.watching.put(eye.getId(), new double[]{eye.getX(), eye.getZ(), this.ticks});
            } else if (this.ticks - start[2] >= SAMPLE_TICKS) {
               double dx = eye.getX() - start[0];
               double dz = eye.getZ() - start[1];
               if (Math.hypot(dx, dz) >= MIN_TRAVEL) {
                  this.reported.add(eye.getId());
                  this.watching.remove(eye.getId());
                  this.record(start[0], start[1], dx, dz);
               }
            }
         }
      }

      // Drop eyes that shattered or dropped before they were read.
      if (!this.watching.isEmpty() && this.ticks % 20 == 0) {
         this.watching.keySet().removeIf(id -> level.getEntity(id) == null);
      }

      if (this.reported.size() > 256) {
         this.reported.clear();
      }
   }

   private void record(double x, double z, double dx, double dz) {
      double heading = StrongholdMath.yaw(dx, dz);
      CommandManager.feedback(
         String.format(Locale.ROOT, "§b[StrongholdFinder] §7Eye heading §f%.1f° §7from §f%d, %d", heading, (int)Math.floor(x), (int)Math.floor(z))
      );
      this.previous = this.latest;
      this.latest = new double[]{x, z, dx, dz};
      if (this.previous == null) {
         CommandManager.feedback("§b[StrongholdFinder] §7Walk a few hundred blocks to the side and throw another eye to cross the lines.");
         return;
      }

      double[] a = this.previous;
      double[] b = this.latest;
      double[] hit = StrongholdMath.intersect(a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3]);
      if (hit == null) {
         CommandManager.feedback("§b[StrongholdFinder] §7These two throws point the same way; throw again from further to the side.");
         return;
      }

      int sx = (int)Math.floor(hit[0]);
      int sz = (int)Math.floor(hit[1]);
      int distance = (int)Math.round(Math.hypot(hit[0] - b[0], hit[1] - b[1]));
      this.estimates++;
      CommandManager.feedback(String.format(Locale.ROOT, "§b[StrongholdFinder] §7Stronghold estimate: §f%d, %d §7(%d blocks away)", sx, sz, distance));
      Minecraft client = Minecraft.getInstance();
      if (this.pin.get() && Gemini.getInstance() != null && Gemini.getInstance().modules() != null && client.player != null) {
         String name = "Stronghold" + (this.estimates > 1 ? " " + this.estimates : "");
         int y = client.player.getBlockY();
         Gemini.getInstance().modules().byClass(WaypointsModule.class).ifPresent(waypoints -> waypoints.pin(name, sx, y, sz));
      }
   }
}
