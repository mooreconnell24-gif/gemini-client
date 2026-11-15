package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves a fading, floating ghost where an entity popped a Totem of Undying.
 *
 * <p>A humanoid ghost is six boxes (head, body, arms, legs) posed from the entity's body and head yaw, pitch and
 * walk cycle at the moment of the pop; anything else is one box the size of its hit box turned to its body yaw.
 * Each box's eight corners are worked out once, when the ghost is spawned, relative to the ghost's centre; a frame
 * only scales those offsets, lifts them and hands them to {@link Render3D}. Ghosts live in a fixed pool that is
 * reused. (The vanilla gizmo calls themselves take {@link Vec3} corners, which are the
 * only objects made per frame.)
 */
public final class PopChams extends Module {
   private static final int POOL = 32;
   /** Boxes of the humanoid model in model pixels: x0, y0, z0, x1, y1, z1, then the part (see {@link #pose}). */
   private static final float[][] HUMANOID = {
      {-4, 0, -2, 0, 12, 2, 1},
      {0, 0, -2, 4, 12, 2, 2},
      {-4, 12, -2, 4, 24, 2, 0},
      {-8, 12, -2, -4, 24, 2, 3},
      {4, 12, -2, 8, 24, 2, 4},
      {-4, 24, -4, 4, 32, 4, 5}
   };
   /** The 12 edges of a box as pairs of corner indices (corner bit 0 = x, bit 1 = y, bit 2 = z). */
   private static final int[] EDGES = {0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7};
   /** The six faces as corner indices in winding order. */
   private static final int[] FACES = {0, 1, 3, 2, 4, 5, 7, 6, 0, 1, 5, 4, 2, 3, 7, 6, 0, 2, 6, 4, 1, 3, 7, 5};
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "How the ghost is drawn", "Both", "Fill", "Wireframe", "Both"));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Ghost colour; its alpha sets the fill strength", 275));
   private final NumberSetting fadeTime = this.addSetting(new NumberSetting("Fade Time", "Seconds until a ghost has faded away", 1.6, 0.3, 6.0, 0.1));
   private final NumberSetting floatSpeed = this.addSetting(new NumberSetting("Float Speed", "Blocks per second the ghost rises", 0.6, 0.0, 3.0, 0.05));
   private final NumberSetting scaleGrowth = this.addSetting(new NumberSetting("Scale Growth", "How much larger the ghost grows per second", 0.15, 0.0, 1.5, 0.05));
   private final NumberSetting lineWidth = this.addSetting(new NumberSetting("Line Width", "Wireframe thickness", 1.5, 1.0, 4.0, 0.5));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Self", "Also leave a ghost when you pop", false));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Draw ghosts on top of blocks", false));
   private final NumberSetting maxGhosts = this.addSetting(new NumberSetting("Max Ghosts", "Most ghosts shown at once; the oldest makes way", 12.0, 1.0, POOL, 1.0));
   private final Ghost[] ghosts = new Ghost[POOL];

   /** One ghost; reused from the pool. */
   private static final class Ghost {
      boolean live;
      long spawnedAt;
      double x;
      double y;
      double z;
      /** Centre the ghost grows from, above (x, y, z). */
      double centerY;
      int boxes;
      /** Corner offsets from (x, centerY, z): boxes * 8 corners * 3 coordinates. */
      final float[] corners = new float[HUMANOID.length * 24];
   }

   public PopChams() {
      super("Pop Chams", "Leaves a fading, floating ghost of an entity at the spot and pose where it popped a totem.", Category.RENDER);
      for (int i = 0; i < POOL; i++) {
         this.ghosts[i] = new Ghost();
      }

      TotemPops.addListener(new TotemPops.Listener() {
         @Override
         public void onPop(LivingEntity entity, int count) {
            PopChams.this.spawn(entity);
         }

         @Override
         public void onDeath(LivingEntity entity, int pops) {
         }
      });
   }

   @Override
   public void onDisable() {
      this.clear();
   }

   @Override
   public void onWorldChange() {
      this.clear();
   }

   private void clear() {
      for (Ghost ghost : this.ghosts) {
         ghost.live = false;
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   private void spawn(LivingEntity entity) {
      if (!this.isEnabled() || entity == Minecraft.getInstance().player && !this.self.get()) {
         return;
      }

      long now = Util.getMillis();
      int limit = Math.min(POOL, this.maxGhosts.intValue());
      Ghost slot = null;
      Ghost oldest = null;
      int live = 0;
      for (int i = 0; i < POOL; i++) {
         Ghost ghost = this.ghosts[i];
         if (ghost.live) {
            live++;
            if (oldest == null || ghost.spawnedAt < oldest.spawnedAt) {
               oldest = ghost;
            }
         } else if (slot == null) {
            slot = ghost;
         }
      }

      if (live >= limit || slot == null) {
         slot = oldest;
      }

      if (slot == null) {
         return;
      }

      slot.live = true;
      slot.spawnedAt = now;
      slot.x = entity.getX();
      slot.y = entity.getY();
      slot.z = entity.getZ();
      float width = Math.max(0.1F, entity.getBbWidth());
      float height = Math.max(0.1F, entity.getBbHeight());
      slot.centerY = slot.y + height / 2.0F;
      float bodyYaw = entity.yBodyRot * Mth.DEG_TO_RAD;
      boolean humanoid = width <= 0.75F && height >= 1.3F && height <= 2.3F;
      if (humanoid) {
         slot.boxes = HUMANOID.length;
         // 32 model pixels make the full height; the width follows the hit box so slim mobs stay slim.
         float unitY = height / 32.0F;
         float unitXZ = width / 0.6F * 0.0375F;
         float swingPos = entity.walkAnimation.position();
         float swingAmount = Math.min(1.0F, entity.walkAnimation.speed());
         float legSwing = Mth.cos(swingPos * 0.6662F) * 1.4F * swingAmount;
         float armSwing = Mth.cos(swingPos * 0.6662F + Mth.PI) * 1.0F * swingAmount;
         float headYaw = Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot) * Mth.DEG_TO_RAD;
         float headPitch = entity.getXRot() * Mth.DEG_TO_RAD;
         for (int box = 0; box < HUMANOID.length; box++) {
            this.buildBox(slot, box, HUMANOID[box], unitXZ, unitY, height, bodyYaw, legSwing, armSwing, headYaw, headPitch);
         }
      } else {
         slot.boxes = 1;
         float hw = width / 2.0F;
         float[] bounds = {-hw, 0.0F, -hw, hw, height, hw, 0};
         this.buildBox(slot, 0, bounds, 1.0F, 1.0F, height, bodyYaw, 0.0F, 0.0F, 0.0F, 0.0F);
      }
   }

   /** Works out one box's eight corners (posed and turned to the body yaw) as offsets from the ghost's centre. */
   private void buildBox(Ghost ghost, int box, float[] b, float unitXZ, float unitY, float height, float bodyYaw, float legSwing, float armSwing, float headYaw, float headPitch) {
      int part = (int)b[6];
      float sin = Mth.sin(bodyYaw);
      float cos = Mth.cos(bodyYaw);
      for (int corner = 0; corner < 8; corner++) {
         float px = ((corner & 1) == 0 ? b[0] : b[3]) * unitXZ;
         float py = ((corner & 2) == 0 ? b[1] : b[4]) * unitY;
         float pz = ((corner & 4) == 0 ? b[2] : b[5]) * unitXZ;
         float pivot;
         float angle;
         switch (part) {
            case 1, 2 -> {
               pivot = 12.0F * unitY;
               angle = part == 1 ? legSwing : -legSwing;
            }
            case 3, 4 -> {
               pivot = 22.0F * unitY;
               angle = part == 3 ? armSwing : -armSwing;
            }
            case 5 -> {
               pivot = 24.0F * unitY;
               angle = -headPitch;
            }
            default -> {
               pivot = 0.0F;
               angle = 0.0F;
            }
         }

         if (part == 5 && headYaw != 0.0F) {
            // The head turns on its neck before it nods.
            float hs = Mth.sin(headYaw);
            float hc = Mth.cos(headYaw);
            float rx = px * hc - pz * hs;
            pz = px * hs + pz * hc;
            px = rx;
         }

         if (angle != 0.0F) {
            float dy = py - pivot;
            float as = Mth.sin(angle);
            float ac = Mth.cos(angle);
            py = pivot + dy * ac - pz * as;
            pz = dy * as + pz * ac;
         }

         // Model space: +z forward, +x to the right. Minecraft faces (-sin yaw, cos yaw) and its right is (-cos, -sin).
         float wx = -px * cos - pz * sin;
         float wz = -px * sin + pz * cos;
         int at = box * 24 + corner * 3;
         ghost.corners[at] = wx;
         ghost.corners[at + 1] = py - height / 2.0F;
         ghost.corners[at + 2] = wz;
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      long now = Util.getMillis();
      float life = this.fadeTime.floatValue();
      boolean fill = !this.mode.is("Wireframe");
      boolean lines = !this.mode.is("Fill");
      int base = this.color.argb();
      float baseAlpha = (base >>> 24) / 255.0F;
      boolean walls = this.throughWalls.get();
      float width = this.lineWidth.floatValue();
      for (int g = 0; g < POOL; g++) {
         Ghost ghost = this.ghosts[g];
         if (!ghost.live) {
            continue;
         }

         float age = (now - ghost.spawnedAt) / 1000.0F;
         if (age >= life) {
            ghost.live = false;
            continue;
         }

         float fade = 1.0F - age / life;
         fade *= fade;
         float scale = 1.0F + this.scaleGrowth.floatValue() * age;
         double cy = ghost.centerY + this.floatSpeed.floatValue() * age;
         if (Render3D.behindCamera(ghost.x, cy, ghost.z, 3.0 * scale)) {
            continue;
         }

         int lineColor = (Math.round(255.0F * fade) << 24) | base & 0xFFFFFF;
         int fillColor = (Math.round(255.0F * fade * Math.max(0.12F, baseAlpha) * 0.55F) << 24) | base & 0xFFFFFF;
         for (int box = 0; box < ghost.boxes; box++) {
            this.drawBox(ghost, box, scale, cy, lines ? lineColor : 0, fill ? fillColor : 0, width, walls);
         }
      }
   }

   private void drawBox(Ghost ghost, int box, float scale, double cy, int lineColor, int fillColor, float width, boolean walls) {
      Vec3 c0 = this.corner(ghost, box, 0, scale, cy);
      Vec3 c1 = this.corner(ghost, box, 1, scale, cy);
      Vec3 c2 = this.corner(ghost, box, 2, scale, cy);
      Vec3 c3 = this.corner(ghost, box, 3, scale, cy);
      Vec3 c4 = this.corner(ghost, box, 4, scale, cy);
      Vec3 c5 = this.corner(ghost, box, 5, scale, cy);
      Vec3 c6 = this.corner(ghost, box, 6, scale, cy);
      Vec3 c7 = this.corner(ghost, box, 7, scale, cy);
      if (fillColor >>> 24 != 0) {
         for (int f = 0; f < FACES.length; f += 4) {
            Render3D.quad(pick(FACES[f], c0, c1, c2, c3, c4, c5, c6, c7), pick(FACES[f + 1], c0, c1, c2, c3, c4, c5, c6, c7),
               pick(FACES[f + 2], c0, c1, c2, c3, c4, c5, c6, c7), pick(FACES[f + 3], c0, c1, c2, c3, c4, c5, c6, c7), fillColor, walls);
         }
      }

      if (lineColor >>> 24 != 0) {
         for (int e = 0; e < EDGES.length; e += 2) {
            Render3D.lineArgb(pick(EDGES[e], c0, c1, c2, c3, c4, c5, c6, c7), pick(EDGES[e + 1], c0, c1, c2, c3, c4, c5, c6, c7), lineColor, width, walls);
         }
      }
   }

   private Vec3 corner(Ghost ghost, int box, int corner, float scale, double cy) {
      int at = box * 24 + corner * 3;
      return new Vec3(ghost.x + ghost.corners[at] * scale, cy + ghost.corners[at + 1] * scale, ghost.z + ghost.corners[at + 2] * scale);
   }

   private static Vec3 pick(int index, Vec3 c0, Vec3 c1, Vec3 c2, Vec3 c3, Vec3 c4, Vec3 c5, Vec3 c6, Vec3 c7) {
      return switch (index) {
         case 0 -> c0;
         case 1 -> c1;
         case 2 -> c2;
         case 3 -> c3;
         case 4 -> c4;
         case 5 -> c5;
         case 6 -> c6;
         default -> c7;
      };
   }
}
