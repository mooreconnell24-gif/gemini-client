package gemini.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import gemini.mixin.ModelPartAccessor;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.Glass;
import gemini.util.Targets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Entities through walls, with one colour while they are in plain sight and another while terrain hides them.
 *
 * <p>How each part is drawn, exactly:
 * <ul>
 * <li><b>Visible colour</b>: the real, textured model of a living entity is tinted with it (the tint argument of the
 * model submit in {@code LivingEntityRenderer.submit}). Items and crystals are not tinted; they have no model tint.</li>
 * <li><b>Occluded or not</b> is decided per entity, 20 times a second, by two line-of-sight rays from the camera (to
 * the eyes and the centre). It is a whole-entity decision, not per pixel: an entity half behind a wall counts as
 * visible while either ray is clear.</li>
 * <li><b>Flat</b>: the model's posed cubes are drawn as flat filled boxes. Visible: drawn depth-tested in the visible
 * colour (over the tinted model). Occluded: drawn on top of everything in the occluded colour.</li>
 * <li><b>Wireframe</b>: the same cubes as edges only, visible colour depth-tested or occluded colour on top.</li>
 * <li><b>Textured</b>: the real textured model cannot pass the depth test, so the occluded pass is Minecraft's own
 * entity outline (the glowing-effect shader) in the occluded colour, which is drawn through walls.</li>
 * <li><b>Pulse</b>: Flat whose fill opacity and tint strength breathe over time.</li>
 * </ul>
 * Items and crystals have no posed cube model reachable here, so for them Flat, Wireframe and Pulse use the
 * bounding box instead; Textured uses the outline for every entity kind.
 */
public final class Chams extends Module {
   /** The flags {@link Marked} carries: whether Chams handles the entity, and whether it is hidden behind terrain. */
   public static final int APPLY = 1;
   public static final int OCCLUDED = 2;
   /** Most entities checked for line of sight per tick; the rest keep their last result. */
   private static final int RAYS_PER_TICK = 96;
   /** Cubes are grown by this many model pixels so the flat boxes do not z-fight with the textured model. */
   private static final float INFLATE = 0.3F;
   public static Chams instance;
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "How entities are drawn (see the module description)", "Flat", "Flat", "Textured", "Wireframe", "Pulse"));
   private final ColorSetting visibleColor = this.addSetting(new ColorSetting("Visible Color", "Tint and overlay while the entity is in plain sight", 190));
   private final ColorSetting occludedColor = this.addSetting(new ColorSetting("Occluded Color", "Silhouette while terrain hides the entity", 300));
   private final NumberSetting tint = this.addSetting(new NumberSetting("Tint Strength", "How strongly the visible colour tints the real model (0 = untouched)", 0.6, 0.0, 1.0, 0.05));
   private final NumberSetting fillOpacity = this.addSetting(
      new NumberSetting("Fill Opacity", "Opacity of the flat boxes", 110.0, 10.0, 255.0, 5.0).showIf(() -> this.mode.is("Flat") || this.mode.is("Pulse"))
   );
   private final NumberSetting lineWidth = this.addSetting(new NumberSetting("Line Width", "Edge thickness in Wireframe", 1.5, 0.5, 5.0, 0.5).showIf(() -> this.mode.is("Wireframe")));
   private final NumberSetting pulseSpeed = this.addSetting(new NumberSetting("Pulse Period", "Seconds per pulse", 1.6, 0.3, 5.0, 0.1).showIf(() -> this.mode.is("Pulse")));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest entity drawn", 64.0, 8.0, 256.0, 4.0));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Other players (and the Fake Player dummy)", true));
   private final BooleanSetting hostiles = this.addSetting(new BooleanSetting("Hostiles", "Hostile mobs", true));
   private final BooleanSetting passives = this.addSetting(new BooleanSetting("Passives", "Animals and other passive mobs", false));
   private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Dropped items (bounding box / outline)", false));
   private final BooleanSetting crystals = this.addSetting(new BooleanSetting("Crystals", "End crystals (bounding box / outline)", true));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Self", "Yourself in third person", false));
   /** Ids of entities whose line of sight was blocked at the last check; written and read on the client thread. */
   private final IntOpenHashSet occluded = new IntOpenHashSet();
   private final Vector3f scratch = new Vector3f();
   private final Vec3[] corners = new Vec3[8];
   private int rayCursor;

   public Chams() {
      super(
         "Chams",
         "See entities through walls in two colours. Flat/Wireframe draw the posed model cubes (on top of walls when hidden), Textured tints the real model and uses the glow outline through walls, Pulse breathes. Hidden-or-not is a per-entity line-of-sight test.",
         Category.RENDER
      );
      instance = this;
   }

   /** Implemented on every entity render state by a mixin so the renderer knows what Chams decided for it. */
   public interface Marked {
      int geminiChamsFlags();

      void geminiSetChamsFlags(int flags);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onDisable() {
      this.occluded.clear();
   }

   @Override
   public void onWorldChange() {
      this.occluded.clear();
   }

   public boolean matches(Entity entity) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || entity == null || entity.isRemoved()) {
         return false;
      } else if (entity.distanceToSqr(player) > this.range.get() * this.range.get()) {
         return false;
      } else if (entity == player) {
         return this.self.get();
      } else if (entity instanceof ItemEntity) {
         return this.items.get();
      } else if (entity instanceof EndCrystal) {
         return this.crystals.get();
      } else if (entity instanceof Player) {
         return this.players.get() && !entity.isSpectator();
      } else if (entity instanceof LivingEntity living && living.isAlive()) {
         return Targets.isHostile(living) ? this.hostiles.get() : this.passives.get() && Targets.isPassive(living);
      } else {
         return false;
      }
   }

   /** What a render state extracted for this entity should carry; 0 when Chams leaves it alone. */
   public int flagsFor(Entity entity) {
      if (!this.isEnabled() || !this.matches(entity)) {
         return 0;
      }

      return APPLY | (this.occluded.contains(entity.getId()) ? OCCLUDED : 0);
   }

   /** Outline colour for the Textured mode's occluded pass, or 0 for none. Vanilla outline colours are RGB only. */
   public int outlineFor(int flags) {
      if ((flags & OCCLUDED) == 0 || !this.mode.is("Textured")) {
         return 0;
      }

      int rgb = this.occludedColor.argb() & 0xFFFFFF;
      return rgb == 0 ? 1 : rgb;
   }

   private float pulse() {
      return this.mode.is("Pulse") ? 0.35F + 0.65F * Glass.pulse((long)(this.pulseSpeed.get() * 1000.0)) : 1.0F;
   }

   /** The model tint with the visible colour blended in. */
   public int tint(int original, int flags) {
      float strength = this.tint.floatValue() * this.pulse();
      if ((flags & APPLY) == 0 || strength <= 0.0F) {
         return original;
      }

      int target = Glass.lerp(0xFFFFFFFF, this.visibleColor.argb() | 0xFF000000, Math.min(1.0F, strength));
      return ARGB.multiply(original, target);
   }

   /**
    * Submits the posed cubes of a living entity's model as gizmo geometry: on top of everything in the occluded
    * colour when the entity is hidden, depth-tested in the visible colour otherwise. The pose stack is the
    * entity's (camera-relative, as the level renderer passes it); it is left as it was found.
    */
   public void submitCubes(Model<?> model, Object state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int flags) {
      if ((flags & APPLY) == 0 || this.mode.is("Textured") || camera == null || camera.pos == null) {
         return;
      }

      boolean hidden = (flags & OCCLUDED) != 0;
      boolean wire = this.mode.is("Wireframe");
      int base = hidden ? this.occludedColor.argb() : this.visibleColor.argb();
      int color = wire ? ARGB.color(255, base & 0xFFFFFF) : ARGB.color(Math.round(this.fillOpacity.floatValue() * this.pulse()), base & 0xFFFFFF);
      try {
         @SuppressWarnings("unchecked")
         Model<Object> posed = (Model<Object>)model;
         posed.setupAnim(state);
         DrawableGizmoPrimitives primitives = new DrawableGizmoPrimitives();
         poseStack.pushPose();
         try {
            this.visit(posed.root(), poseStack, camera.pos, primitives, color, wire);
         } finally {
            poseStack.popPose();
         }

         if (!primitives.isEmpty()) {
            primitives.submit(collector, camera, hidden);
         }
      } catch (RuntimeException | LinkageError ignored) {
         // A model with an unusual part tree is simply drawn without the overlay.
      }
   }

   private void visit(ModelPart part, PoseStack poseStack, Vec3 camera, DrawableGizmoPrimitives out, int color, boolean wire) {
      if (!part.visible) {
         return;
      }

      ModelPartAccessor access = (ModelPartAccessor)(Object)part;
      List<ModelPart.Cube> cubes = access.geminiCubes();
      Map<String, ModelPart> children = access.geminiChildren();
      if (cubes.isEmpty() && children.isEmpty()) {
         return;
      }

      poseStack.pushPose();
      part.translateAndRotate(poseStack);
      if (!part.skipDraw) {
         Matrix4f matrix = poseStack.last().pose();
         for (int i = 0; i < cubes.size(); i++) {
            this.cube(cubes.get(i), matrix, camera, out, color, wire);
         }
      }

      for (ModelPart child : children.values()) {
         this.visit(child, poseStack, camera, out, color, wire);
      }

      poseStack.popPose();
   }

   private void cube(ModelPart.Cube cube, Matrix4f matrix, Vec3 camera, DrawableGizmoPrimitives out, int color, boolean wire) {
      float x0 = (cube.minX - INFLATE) / 16.0F;
      float y0 = (cube.minY - INFLATE) / 16.0F;
      float z0 = (cube.minZ - INFLATE) / 16.0F;
      float x1 = (cube.maxX + INFLATE) / 16.0F;
      float y1 = (cube.maxY + INFLATE) / 16.0F;
      float z1 = (cube.maxZ + INFLATE) / 16.0F;
      for (int i = 0; i < 8; i++) {
         matrix.transformPosition((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1, this.scratch);
         this.corners[i] = new Vec3(camera.x + this.scratch.x, camera.y + this.scratch.y, camera.z + this.scratch.z);
      }

      Vec3[] c = this.corners;
      if (wire) {
         float width = this.lineWidth.floatValue();
         // The twelve edges: corners differing in exactly one bit.
         for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
               if ((i & bit) == 0) {
                  out.addLine(c[i], c[i | bit], color, width);
               }
            }
         }
      } else {
         out.addQuad(c[0], c[1], c[3], c[2], color); // z min
         out.addQuad(c[4], c[6], c[7], c[5], color); // z max
         out.addQuad(c[0], c[4], c[5], c[1], color); // y min
         out.addQuad(c[2], c[3], c[7], c[6], color); // y max
         out.addQuad(c[0], c[2], c[6], c[4], color); // x min
         out.addQuad(c[1], c[5], c[7], c[3], color); // x max
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null) {
         this.occluded.clear();
         return;
      }

      Camera camera = Render3D.camera();
      Vec3 eye = camera == null ? player.getEyePosition() : camera.position();
      int checked = 0;
      int seen = 0;
      // A rotating window over the entity list so a crowded area spreads its rays over a few ticks.
      for (Entity entity : client.level.entitiesForRendering()) {
         if (!this.matches(entity)) {
            continue;
         }

         if (seen++ < this.rayCursor || checked >= RAYS_PER_TICK) {
            continue;
         }

         checked++;
         if (entity == player || this.clear(client, player, eye, entity.getEyePosition()) || this.clear(client, player, eye, entity.getBoundingBox().getCenter())) {
            this.occluded.remove(entity.getId());
         } else {
            this.occluded.add(entity.getId());
         }
      }

      this.rayCursor = checked >= RAYS_PER_TICK ? this.rayCursor + checked : 0;
      if (this.occluded.size() > 4096) {
         this.occluded.clear();
      }
   }

   private boolean clear(Minecraft client, LocalPlayer player, Vec3 from, Vec3 to) {
      return client.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
   }

   /** Items and crystals: a bounding box stands in for the model in the cube modes. */
   @Override
   public void onRender3D(float partialTick) {
      if (this.mode.is("Textured")) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.player == null) {
         return;
      }

      boolean wire = this.mode.is("Wireframe");
      int fill = Math.round(this.fillOpacity.floatValue() * this.pulse());
      for (Entity entity : client.level.entitiesForRendering()) {
         if (!(entity instanceof ItemEntity) && !(entity instanceof EndCrystal) || !this.matches(entity)) {
            continue;
         }

         AABB box = Render3D.interpolatedBox(entity, partialTick);
         boolean hidden = this.occluded.contains(entity.getId());
         int color = hidden ? this.occludedColor.argb() : this.visibleColor.argb();
         Render3D.box(box, color, wire, !wire, this.lineWidth.floatValue(), fill, hidden);
      }
   }
}
