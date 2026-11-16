package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.ClientState;
import gemini.util.CombatTarget;
import gemini.util.HomingSteering;
import gemini.util.InfoText;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Homing projectiles for the single-player sandbox. The client picks the target; every tick a task on the integrated
 * server thread steers the server copies of your own arrows (and, by setting, tridents, snowballs, eggs and pearls)
 * toward it with {@link HomingSteering}: constant speed, a turn-rate limit, and a look ahead that swerves around
 * blocks and entities (you included). Gravity is switched off while a projectile homes and restored the moment it
 * stops; homing projectiles carry a tag so a leftover one (the world was saved mid-flight) is put back to normal.
 *
 * <p>Threading: {@link #homing}, {@link #finished} and everything in {@code server*} methods are touched only on the
 * server thread, through {@link MinecraftServer#execute}. The client side only sends immutable {@link Request}s.
 */
public final class SillyAimbot extends Module {
   private static final String TAG = "gemini_homing";
   private static final int PATH_STEPS = 30;
   private static final int MAX_PATHS = 6;
   private static final int FINISHED_LIMIT = 512;

   private final BooleanSetting arrows = this.addSetting(new BooleanSetting("Arrows", "Steer arrows and spectral arrows", true));
   private final BooleanSetting tridents = this.addSetting(new BooleanSetting("Tridents", "Steer thrown tridents (until they hit, then loyalty takes over)", true));
   private final BooleanSetting snowballs = this.addSetting(new BooleanSetting("Snowballs", "Steer snowballs", false));
   private final BooleanSetting eggs = this.addSetting(new BooleanSetting("Eggs", "Steer thrown eggs", false));
   private final BooleanSetting pearls = this.addSetting(new BooleanSetting("Ender Pearls", "Steer ender pearls (you teleport to the target)", false));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away a target may be picked, in blocks", 64.0, 8.0, 128.0, 4.0));
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Speed", "Flight speed while homing, in blocks per tick (a full bow shot starts at 3; arrow damage grows with speed)", 2.0, 0.5, 5.0, 0.1)
   );
   private final NumberSetting turnRate = this.addSetting(
      new NumberSetting("Turn Rate", "Most the flight direction may turn per tick, in degrees: low values give wide, smooth curves", 30.0, 5.0, 180.0, 5.0)
   );
   private final NumberSetting lookAhead = this.addSetting(
      new NumberSetting("Look Ahead", "How many blocks ahead to check for blocks and entities in the way before swerving around them", 5.0, 2.0, 12.0, 0.5)
   );
   private final NumberSetting maxTime = this.addSetting(
      new NumberSetting("Max Time", "Seconds a projectile may home before it falls back to normal flight", 8.0, 1.0, 30.0, 0.5)
   );
   private final BooleanSetting retarget = this.addSetting(
      new BooleanSetting("Retarget", "When the target dies, send projectiles still in the air to the next target instead of letting them drop", true)
   );
   private final BooleanSetting showPath = this.addSetting(new BooleanSetting("Show Path", "Draw the predicted path of your homing projectiles", true));
   private final ColorSetting pathColor = this.addSetting(new ColorSetting("Path Color", "Colour of the predicted homing path", 300).showIf(this.showPath::get));
   private final Targeting targeting = Targeting.builder(this).box("Outline", 300).build();

   /** Server thread only: projectile UUID to its homing state. */
   private final Map<UUID, Homing> homing = new HashMap<>();
   /** Server thread only: projectiles that stopped homing and must not start again. */
   private final Set<UUID> finished = new LinkedHashSet<>();
   private volatile int homingCount;
   private final InfoText infoText = new InfoText();

   private final double[][] paths = new double[MAX_PATHS][(PATH_STEPS + 1) * 3];
   private final int[] pathCounts = new int[MAX_PATHS];
   private int pathTotal;

   public SillyAimbot() {
      super("Silly Aimbot", "Your arrows dodge blocks and anything in the way and fly straight to your target (single-player only).", Category.COMBAT);
   }

   @Override
   public String info() {
      int count = this.homingCount;
      return count > 0 ? this.infoText.whole(count, " homing") : "Idle";
   }

   @Override
   public void onEnable() {
      this.pathTotal = 0;
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server != null) {
         UUID owner = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
         server.execute(() -> this.serverRestoreAll(server, owner));
      }
   }

   @Override
   public void onDisable() {
      this.shutdown();
   }

   @Override
   public void onWorldChange() {
      this.shutdown();
   }

   private void shutdown() {
      this.targeting.clear();
      CombatTarget.release(this);
      this.pathTotal = 0;
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (server != null && server.isRunning()) {
         UUID owner = client.player != null ? client.player.getUUID() : null;
         server.execute(() -> this.serverRestoreAll(server, owner));
      } else {
         // No server left to run on: its entities are gone with it, and tagged leftovers are fixed on the next enable.
         this.homingCount = 0;
      }
   }

   private boolean kindEnabled(Entity entity) {
      if (entity instanceof Arrow || entity instanceof SpectralArrow) {
         return this.arrows.get();
      } else if (entity instanceof ThrownTrident) {
         return this.tridents.get();
      } else if (entity instanceof Snowball) {
         return this.snowballs.get();
      } else if (entity instanceof ThrownEgg) {
         return this.eggs.get();
      } else if (entity instanceof ThrownEnderpearl) {
         return this.pearls.get();
      }

      return false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      this.pathTotal = 0;
      MinecraftServer server = client.getSingleplayerServer();
      if (!SingleplayerGate.allowed() || !ClientState.ready(client) || server == null) {
         return;
      }

      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      LivingEntity target = this.targeting.pick(player, level.entitiesForRendering(), this.range.get(), null);
      CombatTarget.report(this, target);
      List<UUID> own = new ArrayList<>();
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof Projectile projectile && projectile.getOwner() == player && this.kindEnabled(entity)) {
            own.add(entity.getUUID());
            if (this.showPath.get() && target != null && entity.isNoGravity() && this.pathTotal < MAX_PATHS) {
               this.predictPath(level, entity, target);
            }
         }
      }

      Request request = new Request(
         player.getUUID(),
         target == null ? null : target.getUUID(),
         own,
         this.speed.get(),
         this.turnRate.get(),
         this.lookAhead.get(),
         (int)Math.round(this.maxTime.get() * 20.0),
         this.retarget.get(),
         this.arrows.get(),
         this.tridents.get(),
         this.snowballs.get(),
         this.eggs.get(),
         this.pearls.get()
      );
      server.execute(() -> this.serverStep(server, request));
   }

   /** Client-side preview: the same steering against the client's blocks, for a few dozen ticks. */
   private void predictPath(ClientLevel level, Entity projectile, LivingEntity target) {
      double[] out = this.paths[this.pathTotal];
      double x = projectile.getX();
      double y = projectile.getY();
      double z = projectile.getZ();
      Vec3 motion = projectile.getDeltaMovement();
      double vx = motion.x;
      double vy = motion.y;
      double vz = motion.z;
      AABB box = target.getBoundingBox();
      double tx = (box.minX + box.maxX) * 0.5;
      double ty = (box.minY + box.maxY) * 0.5;
      double tz = (box.minZ + box.maxZ) * 0.5;
      HomingSteering.Probe probe = (px, py, pz, dx, dy, dz, length) -> clearOfBlocks(level, px, py, pz, dx, dy, dz, length, CollisionContext.empty());
      HomingSteering.Solid solid = (bx, by, bz) -> solidAt(level, bx, by, bz);
      int count = 0;
      out[count * 3] = x;
      out[count * 3 + 1] = y;
      out[count * 3 + 2] = z;
      count++;
      for (int i = 0; i < PATH_STEPS; i++) {
         double[] v = HomingSteering.steer(x, y, z, vx, vy, vz, tx, ty, tz, this.speed.get(), this.turnRate.get(), this.lookAhead.get(), probe, solid);
         x += v[0];
         y += v[1];
         z += v[2];
         vx = v[0];
         vy = v[1];
         vz = v[2];
         out[count * 3] = x;
         out[count * 3 + 1] = y;
         out[count * 3 + 2] = z;
         count++;
         if (box.inflate(0.3).contains(x, y, z)) {
            break;
         }
      }

      this.pathCounts[this.pathTotal] = count;
      this.pathTotal++;
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
      if (!this.showPath.get()) {
         return;
      }

      int color = this.pathColor.argb() | 0xFF000000;
      for (int p = 0; p < this.pathTotal; p++) {
         double[] path = this.paths[p];
         for (int i = 1; i < this.pathCounts[p]; i++) {
            Render3D.line(path[(i - 1) * 3], path[(i - 1) * 3 + 1], path[(i - 1) * 3 + 2], path[i * 3], path[i * 3 + 1], path[i * 3 + 2], color, 2.0F, false);
         }
      }
   }

   private static boolean solidAt(Level level, int x, int y, int z) {
      BlockPos pos = new BlockPos(x, y, z);
      // An unloaded chunk counts as solid: the planner must never make the server load chunks.
      return !level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   /** The straight move is clear of blocks along its centre line and four lines offset by the projectile's radius. */
   private static boolean clearOfBlocks(Level level, double x, double y, double z, double dx, double dy, double dz, double length, CollisionContext context) {
      // Two unit vectors across the direction, for the offset lines.
      double ax = Math.abs(dy) < 0.9 ? 0.0 : 1.0;
      double ay = Math.abs(dy) < 0.9 ? 1.0 : 0.0;
      double ux = dy * 0.0 - dz * ay;
      double uy = dz * ax - dx * 0.0;
      double uz = dx * ay - dy * ax;
      double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
      ux /= ul;
      uy /= ul;
      uz /= ul;
      double wx = dy * uz - dz * uy;
      double wy = dz * ux - dx * uz;
      double wz = dx * uy - dy * ux;
      double r = 0.2;
      for (int i = 0; i < 5; i++) {
         double ox = 0.0;
         double oy = 0.0;
         double oz = 0.0;
         if (i > 0) {
            double sign = i % 2 == 0 ? -r : r;
            boolean first = i <= 2;
            ox = (first ? ux : wx) * sign;
            oy = (first ? uy : wy) * sign;
            oz = (first ? uz : wz) * sign;
         }

         Vec3 from = new Vec3(x + ox, y + oy, z + oz);
         Vec3 to = new Vec3(x + ox + dx * length, y + oy + dy * length, z + oz + dz * length);
         if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)).getType() != HitResult.Type.MISS) {
            return false;
         }
      }

      return true;
   }

   // ---------------------------------------------------------------- server thread

   private void serverStep(MinecraftServer server, Request request) {
      ServerPlayer owner = server.getPlayerList().getPlayer(request.owner());
      if (owner == null || !this.isEnabled()) {
         return;
      }

      ServerLevel level = owner.level();
      for (UUID id : request.projectiles()) {
         if (!this.homing.containsKey(id) && !this.finished.contains(id)) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.entityTags().contains(TAG)) {
               // Left over from an earlier session: put it back to normal.
               entity.setNoGravity(false);
               entity.removeTag(TAG);
               this.markFinished(id);
            } else if (entity instanceof Projectile projectile && this.startable(projectile, owner, request)) {
               Homing state = new Homing(entity.getUUID(), request.target(), entity.isNoGravity());
               if (!state.originalNoGravity) {
                  entity.setNoGravity(true);
                  entity.addTag(TAG);
               }

               this.homing.put(id, state);
            }
         }
      }

      Iterator<Map.Entry<UUID, Homing>> it = this.homing.entrySet().iterator();
      while (it.hasNext()) {
         Homing state = it.next().getValue();
         Entity entity = level.getEntity(state.id);
         if (entity == null || entity.isRemoved()) {
            it.remove();
            this.markFinished(state.id);
         } else if (!this.serverSteer(level, owner, entity, state, request)) {
            this.serverStop(entity, state);
            it.remove();
         }
      }

      this.homingCount = this.homing.size();
   }

   private boolean startable(Projectile projectile, ServerPlayer owner, Request request) {
      return request.target() != null
         && projectile.getOwner() == owner
         && !projectile.isRemoved()
         && request.kind(projectile)
         && projectile.getDeltaMovement().lengthSqr() > 0.04;
   }

   /** One steering step; false when this projectile should stop homing. */
   private boolean serverSteer(ServerLevel level, ServerPlayer owner, Entity projectile, Homing state, Request request) {
      state.ticks++;
      if (state.ticks > request.maxTicks() || !request.kind(projectile)) {
         return false;
      }

      Vec3 motion = projectile.getDeltaMovement();
      if (state.hasLast) {
         // Something other than the steering changed the velocity a lot: it hit, bounced or was deflected.
         double ex = motion.x - state.lastX;
         double ey = motion.y - state.lastY;
         double ez = motion.z - state.lastZ;
         double expected = Math.sqrt(state.lastX * state.lastX + state.lastY * state.lastY + state.lastZ * state.lastZ);
         if (Math.sqrt(ex * ex + ey * ey + ez * ez) > Math.max(0.3, expected * 0.35)) {
            return false;
         }
      }

      LivingEntity target = state.target == null ? null : level.getEntity(state.target) instanceof LivingEntity living ? living : null;
      if (target == null || !target.isAlive() || target.isRemoved() || target == owner) {
         UUID next = request.target();
         if (!request.retarget() || next == null || next.equals(state.target) || next.equals(owner.getUUID())) {
            return false;
         }

         state.target = next;
         target = level.getEntity(next) instanceof LivingEntity living ? living : null;
         if (target == null || !target.isAlive() || target == owner) {
            return false;
         }
      }

      AABB box = target.getBoundingBox();
      double px = projectile.getX();
      double py = projectile.getY() + projectile.getBbHeight() * 0.5;
      double pz = projectile.getZ();
      double tx = (box.minX + box.maxX) * 0.5;
      double ty = (box.minY + box.maxY) * 0.5;
      double tz = (box.minZ + box.maxZ) * 0.5;
      double distance = Math.sqrt((tx - px) * (tx - px) + (ty - py) * (ty - py) + (tz - pz) * (tz - pz));
      // Lead the target by the time the projectile needs to get there.
      double lead = Math.min(20.0, distance / Math.max(0.1, request.speed()));
      Vec3 targetMotion = target.getDeltaMovement();
      tx += targetMotion.x * lead;
      tz += targetMotion.z * lead;
      if (!target.onGround()) {
         ty += targetMotion.y * lead;
      }

      double reach = request.lookAhead();
      AABB around = projectile.getBoundingBox().inflate(reach + 1.0);
      LivingEntity aim = target;
      List<Entity> others = level.getEntities(projectile, around, e -> e != aim && e.isAlive() && (e.isPickable() || e == owner) && !(e instanceof Projectile));
      double radius = Math.max(0.15, projectile.getBbWidth() * 0.5);
      double[] boxes = new double[others.size() * 6];
      for (int i = 0; i < others.size(); i++) {
         AABB b = others.get(i).getBoundingBox();
         boxes[i * 6] = b.minX - radius;
         boxes[i * 6 + 1] = b.minY - radius;
         boxes[i * 6 + 2] = b.minZ - radius;
         boxes[i * 6 + 3] = b.maxX + radius;
         boxes[i * 6 + 4] = b.maxY + radius;
         boxes[i * 6 + 5] = b.maxZ + radius;
      }

      CollisionContext context = CollisionContext.of(projectile);
      HomingSteering.Probe probe = (x, y, z, dx, dy, dz, length) -> {
         for (int i = 0; i < boxes.length; i += 6) {
            boolean inside = x > boxes[i] && y > boxes[i + 1] && z > boxes[i + 2] && x < boxes[i + 3] && y < boxes[i + 4] && z < boxes[i + 5];
            // A box the projectile is already in (it was just fired from there) cannot be avoided, so it is ignored.
            if (!inside && HomingSteering.segmentHitsBox(x, y, z, dx, dy, dz, length, boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3], boxes[i + 4], boxes[i + 5])) {
               return false;
            }
         }

         return clearOfBlocks(level, x, y, z, dx, dy, dz, length, context);
      };
      HomingSteering.Solid solid = (x, y, z) -> solidAt(level, x, y, z);
      double[] v = HomingSteering.steer(px, py, pz, motion.x, motion.y, motion.z, tx, ty, tz, request.speed(), request.turnRate(), reach, probe, solid);
      projectile.setDeltaMovement(v[0], v[1], v[2]);
      projectile.needsSync = true;
      state.hasLast = true;
      state.lastX = v[0];
      state.lastY = v[1];
      state.lastZ = v[2];
      return true;
   }

   /** Ends homing: gravity back to what it was, tag removed, never homes again. */
   private void serverStop(Entity entity, Homing state) {
      if (!state.originalNoGravity) {
         entity.setNoGravity(false);
      }

      entity.removeTag(TAG);
      this.markFinished(state.id);
   }

   private void markFinished(UUID id) {
      this.finished.add(id);
      if (this.finished.size() > FINISHED_LIMIT) {
         Iterator<UUID> it = this.finished.iterator();
         it.next();
         it.remove();
      }
   }

   /**
    * Restores every projectile this module changed: the ones it is homing, and any tagged one in the worlds (around
    * the owner when known), so nothing stays weightless after disable, a world change or a save mid-flight.
    */
   private void serverRestoreAll(MinecraftServer server, UUID ownerId) {
      for (Homing state : this.homing.values()) {
         for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(state.id);
            if (entity != null) {
               this.serverStop(entity, state);
               break;
            }
         }
      }

      this.homing.clear();
      this.finished.clear();
      ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
      if (owner != null) {
         for (Entity entity : owner.level().getEntities(owner, owner.getBoundingBox().inflate(256.0), e -> e instanceof Projectile && e.entityTags().contains(TAG))) {
            entity.setNoGravity(false);
            entity.removeTag(TAG);
         }
      }

      this.homingCount = 0;
   }

   /** Server thread only. */
   private static final class Homing {
      final UUID id;
      final boolean originalNoGravity;
      UUID target;
      int ticks;
      boolean hasLast;
      double lastX;
      double lastY;
      double lastZ;

      Homing(UUID id, UUID target, boolean originalNoGravity) {
         this.id = id;
         this.target = target;
         this.originalNoGravity = originalNoGravity;
      }
   }

   /** A snapshot of the client's choices for one server step; immutable, so it is safe to hand across threads. */
   private record Request(
      UUID owner,
      UUID target,
      List<UUID> projectiles,
      double speed,
      double turnRate,
      double lookAhead,
      int maxTicks,
      boolean retarget,
      boolean arrows,
      boolean tridents,
      boolean snowballs,
      boolean eggs,
      boolean pearls
   ) {
      boolean kind(Entity entity) {
         if (entity instanceof Arrow || entity instanceof SpectralArrow) {
            return this.arrows;
         } else if (entity instanceof ThrownTrident) {
            return this.tridents;
         } else if (entity instanceof Snowball) {
            return this.snowballs;
         } else if (entity instanceof ThrownEgg) {
            return this.eggs;
         } else if (entity instanceof ThrownEnderpearl) {
            return this.pearls;
         }

         return false;
      }
   }
}
