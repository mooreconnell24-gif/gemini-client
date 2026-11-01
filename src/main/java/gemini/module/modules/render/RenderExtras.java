package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.HudContext;
import gemini.util.Holes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class RenderExtras {
   private RenderExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new RenderExtras.ItemEsp());
      sink.accept(new RenderExtras.ProjectileEsp());
      sink.accept(new RenderExtras.TunnelEsp());
      sink.accept(new RenderExtras.VoidEsp());
      sink.accept(new RenderExtras.Animations());
      sink.accept(new RenderExtras.HoleEsp());
      sink.accept(new RenderExtras.JumpEffect());
      sink.accept(new RenderExtras.DamageParticles());
      sink.accept(new RenderExtras.ChinaHat());
      sink.accept(new RenderExtras.LogoffSpot());
      sink.accept(new RenderExtras.TntTimer());
      sink.accept(new RenderExtras.BedPlates());
      sink.accept(new RenderExtras.CrosshairPlus());
      sink.accept(new RenderExtras.EntityCulling());
      sink.accept(new RenderExtras.PearlTracker());
      sink.accept(new RenderExtras.BreakIndicators());
      sink.accept(new RenderExtras.HitEffect());
      sink.accept(new RenderExtras.SoundLocator());
      sink.accept(new RenderExtras.PlayerTrails());
      sink.accept(new RenderExtras.BlockCounter());
   }

   public static final class Animations extends Module {
      private static RenderExtras.Animations instance;
      private final NumberSetting swingDuration = this.addSetting(
         new NumberSetting("Swing Duration", "Ticks a swing takes (vanilla is 6, less with haste)", 6.0, 1.0, 20.0, 1.0)
      );
      private final BooleanSetting othersToo = this.addSetting(new BooleanSetting("Other Players", "Also apply to other players' swings", false));

      public Animations() {
         super("Animations", "Speed up or slow down arm swing animations.", Category.RENDER);
         instance = this;
      }

      public static int swingDuration(Entity entity) {
         RenderExtras.Animations module = instance;
         if (module != null && module.isEnabled()) {
            LocalPlayer self = Minecraft.getInstance().player;
            return entity != self && (!module.othersToo.get() || !(entity instanceof Player)) ? -1 : module.swingDuration.intValue();
         } else {
            return -1;
         }
      }

      @Override
      public String info() {
         return this.swingDuration.intValue() + "t";
      }
   }

   public static final class BedPlates extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Scan radius for beds", 24.0, 8.0, 48.0, 4.0));
      private final List<BlockPos> beds = new ArrayList<>();
      private int wait;

      public BedPlates() {
         super("BedPlates", "Display floating 3D plates above nearby beds showing their defense cover.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.beds.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null && this.wait-- <= 0) {
            this.wait = 25;
            this.beds.clear();
            int r = this.radius.intValue();
            BlockPos c = client.player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, 6, r))) {
               if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
                  this.beds.add(pos.immutable());
                  if (this.beds.size() >= 12) {
                     break;
                  }
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null) {
            for (BlockPos bed : this.beds) {
               String cover = client.level.getBlockState(bed.above()).getBlock().getName().getString();
               Render3D.block(bed, -16722689, true, true, 1.5F, 40, true);
               Render3D.text("Bed [" + cover + "]", Vec3.atCenterOf(bed).add(0.0, 1.15, 0.0), -1, 0.8F, true);
            }
         }
      }
   }

   public static final class BlockCounter extends Module {
      public BlockCounter() {
         super("BlockCounter", "Display a sleek live block counter pill near your crosshair when holding building blocks.", Category.RENDER);
      }

      @Override
      public void onRenderHud(HudContext hud) {
         LocalPlayer player = hud.client.player;
         if (player != null) {
            int totalBlocks = 0;
            ItemStack iconStack = ItemStack.EMPTY;

            for (int i = 0; i < 9; i++) {
               ItemStack s = player.getInventory().getItem(i);
               if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
                  totalBlocks += s.getCount();
                  if (iconStack.isEmpty()) {
                     iconStack = s;
                  }
               }
            }

            if (player.getMainHandItem().getItem() instanceof BlockItem) {
               iconStack = player.getMainHandItem();
            }

            if (totalBlocks > 0 && !iconStack.isEmpty()) {
               String text = totalBlocks + " blocks";
               int boxW = 24 + hud.font.width(text) + 8;
               int boxH = 20;
               int x = hud.width / 2 - boxW / 2;
               int y = hud.height / 2 + 16;
               hud.graphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 1140897023);
               hud.graphics.fill(x, y, x + boxW, y + boxH, -871559145);
               hud.graphics.fill(x, y + boxH - 2, x + boxW, y + boxH, totalBlocks > 32 ? hud.accent2 : -43691);
               hud.graphics.item(iconStack, x + 4, y + 2);
               hud.graphics.text(hud.font, text, x + 23, y + 6, totalBlocks > 32 ? -1 : -43691, hud.shadow);
            }
         }
      }
   }

   public static final class BreakIndicators extends Module {
      private final BooleanSetting percentage = this.addSetting(new BooleanSetting("Percentage", "Show mining completion percentage in 3D", true));
      private BlockPos miningPos;
      private float progress;

      public BreakIndicators() {
         super("BreakIndicators", "Render a dynamic 3D progress box and completion percentage on the block being mined.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.miningPos = null;
         this.progress = 0.0F;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null && client.gameMode != null) {
            if (client.gameMode.isDestroying() && client.hitResult instanceof BlockHitResult bhr && bhr.getType() == Type.BLOCK) {
               BlockPos pos = bhr.getBlockPos();
               BlockState state = client.level.getBlockState(pos);
               if (state.isAir()) {
                  this.miningPos = null;
                  this.progress = 0.0F;
                  return;
               }

               if (!pos.equals(this.miningPos)) {
                  this.miningPos = pos.immutable();
                  this.progress = 0.0F;
               }

               this.progress = Math.min(1.0F, this.progress + state.getDestroyProgress(client.player, client.level, pos));
            } else {
               this.miningPos = null;
               this.progress = 0.0F;
            }
         } else {
            this.miningPos = null;
            this.progress = 0.0F;
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         if (this.miningPos != null && this.progress > 0.01F) {
            float p = Mth.clamp(this.progress, 0.05F, 1.0F);
            int color = ColorSetting.hueColor(p * 0.33F, 0.85F, 1.0F);
            double half = 0.5 * p;
            Vec3 c = Vec3.atCenterOf(this.miningPos);
            AABB box = new AABB(c.x - half, c.y - half, c.z - half, c.x + half, c.y + half, c.z + half);
            Render3D.boxGlow(box, color, 1.8F, 55, true);
            if (this.percentage.get()) {
               Render3D.text(Math.round(p * 100.0F) + "%", c.add(0.0, 0.65, 0.0), color, 0.85F, true);
            }
         }
      }
   }

   public static final class ChinaHat extends Module {
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Hat accent hue", 205));
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Hat brim radius", 0.65, 0.3, 1.2, 0.05));

      public ChinaHat() {
         super("ChinaHat", "Render a sleek 3D polygonal cone halo above your character's head.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null && !client.options.getCameraType().isFirstPerson()) {
            Vec3 base = player.getPosition(partialTick).add(0.0, player.getBbHeight() + 0.12, 0.0);
            Vec3 tip = base.add(0.0, 0.32, 0.0);
            int argb = this.color.argb();
            double r = this.radius.get();
            int segments = 16;

            for (int i = 0; i < segments; i++) {
               double a1 = i * Math.PI * 2.0 / segments;
               double a2 = (i + 1) * Math.PI * 2.0 / segments;
               Vec3 p1 = base.add(Math.cos(a1) * r, 0.0, Math.sin(a1) * r);
               Vec3 p2 = base.add(Math.cos(a2) * r, 0.0, Math.sin(a2) * r);
               Render3D.line(p1, p2, argb, 1.8F, false);
               Render3D.line(tip, p1, Render3D.alpha(argb, 150), 1.2F, false);
            }
         }
      }
   }

   public static final class CrosshairPlus extends Module {
      private final NumberSetting gap = this.addSetting(new NumberSetting("Gap", "Center gap in pixels", 3.0, 1.0, 10.0, 1.0));
      private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Arm length in pixels", 5.0, 2.0, 14.0, 1.0));
      private final BooleanSetting dynamic = this.addSetting(new BooleanSetting("Dynamic", "Expand when moving or swinging", true));

      public CrosshairPlus() {
         super("Crosshair", "Render a dynamic neon crosshair that reacts to movement and attack cooldown.", Category.RENDER);
      }

      @Override
      public void onRenderHud(HudContext hud) {
         LocalPlayer player = hud.client.player;
         if (player != null) {
            int cx = hud.width / 2;
            int cy = hud.height / 2;
            double speed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
            int g = this.gap.intValue() + (this.dynamic.get() ? Mth.floor(speed * 14.0) : 0);
            int len = this.length.intValue();
            hud.graphics.fill(cx - g - len, cy, cx - g, cy + 1, hud.accent2);
            hud.graphics.fill(cx + g + 1, cy, cx + g + 1 + len, cy + 1, hud.accent2);
            hud.graphics.fill(cx, cy - g - len, cx + 1, cy - g, hud.accent);
            hud.graphics.fill(cx, cy + g + 1, cx + 1, cy + g + 1 + len, hud.accent);
         }
      }
   }

   public static final class DamageParticles extends Module {
      private final Map<Integer, Float> lastHealth = new HashMap<>();
      private final ArrayDeque<RenderExtras.DamageParticles.Popup> popups = new ArrayDeque<>();

      public DamageParticles() {
         super("DamageParticles", "Display floating 3D damage numbers whenever entities lose or gain health.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.lastHealth.clear();
         this.popups.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity != client.player && entity instanceof LivingEntity living && entity.distanceToSqr(client.player) <= 400.0) {
                  float hp = living.getHealth() + living.getAbsorptionAmount();
                  Float prev = this.lastHealth.put(living.getId(), hp);
                  if (prev != null && Math.abs(prev - hp) >= 0.5F) {
                     float diff = hp - prev;
                     String text = String.format(Locale.ROOT, "%s%.1f", diff < 0.0F ? "-" : "+", Math.abs(diff));
                     int color = diff < 0.0F ? -1548963 : -11086174;
                     this.popups
                        .addLast(
                           new RenderExtras.DamageParticles.Popup(
                              text, living.position().add(0.0, living.getBbHeight() + 0.35, 0.0), color, System.currentTimeMillis()
                           )
                        );
                     if (this.popups.size() > 16) {
                        this.popups.removeFirst();
                     }
                  }
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         long now = System.currentTimeMillis();
         Iterator<RenderExtras.DamageParticles.Popup> it = this.popups.iterator();

         while (it.hasNext()) {
            RenderExtras.DamageParticles.Popup p = it.next();
            float age = (float)(now - p.createdAt) / 1100.0F;
            if (age >= 1.0F) {
               it.remove();
            } else {
               Render3D.text(p.text, p.pos.add(0.0, age * 0.65, 0.0), p.color, 0.9F, true);
            }
         }
      }

      private record Popup(String text, Vec3 pos, int color, long createdAt) {
      }
   }

   public static final class EntityCulling extends Module {
      public static RenderExtras.EntityCulling instance;
      private final BooleanSetting wallCull = this.addSetting(
         new BooleanSetting("Cull Behind Walls", "Hide vanilla nametags for entities obstructed by solid blocks", true)
      );
      private final NumberSetting maxDistance = this.addSetting(
         new NumberSetting("Nametag Distance", "Maximum distance in blocks to render vanilla nametags", 28.0, 6.0, 64.0, 2.0)
      );
      private final BooleanSetting hideArmorStands = this.addSetting(
         new BooleanSetting("Hide Armor Stand Tags", "Suppress hologram and armor stand nametag clutter in hubs", true)
      );

      public EntityCulling() {
         super("EntityCulling", "Skip rendering nametags and labels for occluded or distant entities to boost FPS.", Category.RENDER);
         instance = this;
      }

      @Override
      public String info() {
         return this.maxDistance.intValue() + "m";
      }

      public boolean shouldCullNametag(Entity entity, double distSq) {
         if (!this.isEnabled()) {
            return false;
         } else if (this.hideArmorStands.get() && entity instanceof ArmorStand) {
            return true;
         } else {
            double maxD = this.maxDistance.get();
            if (distSq > maxD * maxD) {
               return true;
            } else {
               if (this.wallCull.get()) {
                  LocalPlayer self = Minecraft.getInstance().player;
                  if (self != null && entity != self && !self.hasLineOfSight(entity)) {
                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   public static final class HitEffect extends Module {
      private final ModeSetting style = this.addSetting(
         new ModeSetting("Style", "3D hit burst visual", "Energy Burst", "Energy Burst", "Shockwave", "Lightning")
      );
      private final BooleanSetting hitmarker = this.addSetting(new BooleanSetting("Crosshair Hitmarker", "Flash a crisp X hitmarker on your crosshair", true));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Hit effect hue", 195));
      private final ArrayDeque<RenderExtras.HitEffect.HitBurst> bursts = new ArrayDeque<>();
      private long lastHitMs;

      public HitEffect() {
         super("HitEffect", "Render 3D neon impact bursts and a crosshair hitmarker when striking enemies.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.bursts.clear();
      }

      @Override
      public void onAttack(Entity target) {
         if (target instanceof LivingEntity living) {
            this.lastHitMs = System.currentTimeMillis();
            this.bursts.addLast(new RenderExtras.HitEffect.HitBurst(living.position().add(0.0, living.getBbHeight() * 0.55, 0.0), this.lastHitMs));

            while (this.bursts.size() > 10) {
               this.bursts.removeFirst();
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         long now = System.currentTimeMillis();
         int baseColor = this.color.argb();
         String mode = this.style.get();
         Iterator<RenderExtras.HitEffect.HitBurst> it = this.bursts.iterator();

         while (it.hasNext()) {
            RenderExtras.HitEffect.HitBurst burst = it.next();
            float t = (float)(now - burst.timestamp) / 550.0F;
            if (t >= 1.0F) {
               it.remove();
            } else {
               int alpha = Math.round(235.0F * (1.0F - t));
               int c = Render3D.alpha(baseColor, alpha);
               if (mode.equals("Lightning")) {
                  Vec3 top = burst.center.add(0.0, 3.8 * (1.0F - t * 0.3F), 0.0);
                  Vec3 mid1 = burst.center.add(0.28, 2.4, -0.22);
                  Vec3 mid2 = burst.center.add(-0.24, 1.2, 0.25);
                  Render3D.lineArgb(top, mid1, c, 2.4F, true);
                  Render3D.lineArgb(mid1, mid2, c, 2.4F, true);
                  Render3D.lineArgb(mid2, burst.center, c, 2.4F, true);
               } else if (mode.equals("Shockwave")) {
                  double r = 0.25 + t * 1.45;
                  int segs = 18;

                  for (int i = 0; i < segs; i++) {
                     double a1 = i * Math.PI * 2.0 / segs;
                     double a2 = (i + 1) * Math.PI * 2.0 / segs;
                     Render3D.lineArgb(
                        new Vec3(burst.center.x + Math.cos(a1) * r, burst.center.y, burst.center.z + Math.sin(a1) * r),
                        new Vec3(burst.center.x + Math.cos(a2) * r, burst.center.y, burst.center.z + Math.sin(a2) * r),
                        c,
                        2.0F,
                        true
                     );
                  }
               } else {
                  double s = 0.2 + t * 0.75;
                  AABB box = new AABB(burst.center.x - s, burst.center.y - s, burst.center.z - s, burst.center.x + s, burst.center.y + s, burst.center.z + s);
                  Render3D.box(box, baseColor, true, false, 1.8F, alpha / 3, true);
               }
            }
         }
      }

      @Override
      public void onRenderHud(HudContext hud) {
         if (this.hitmarker.get()) {
            long age = System.currentTimeMillis() - this.lastHitMs;
            if (age >= 0L && age < 220L) {
               int cx = hud.width / 2;
               int cy = hud.height / 2;

               for (int d = 3; d <= 7; d++) {
                  hud.graphics.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, hud.accent2);
                  hud.graphics.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, hud.accent2);
                  hud.graphics.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, hud.accent2);
                  hud.graphics.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, hud.accent2);
               }
            }
         }
      }

      private record HitBurst(Vec3 center, long timestamp) {
      }
   }

   public static final class HoleEsp extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal scan radius for safe holes", 8.0, 3.0, 16.0, 1.0));
      private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Render holes through blocks", true));
      private final List<BlockPos> cached = new ArrayList<>();
      private int wait;

      public HoleEsp() {
         super("HoleESP", "Highlight safe Bedrock, Obsidian, and Mixed crystal PvP holes around you.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.cached.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null && this.wait-- <= 0) {
            this.wait = 6;
            this.cached.clear();
            this.cached.addAll(Holes.around(client.level, client.player.blockPosition(), this.radius.intValue(), 3));
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null) {
            for (BlockPos pos : this.cached) {
               Holes.Safety safety = Holes.classify(client.level, pos);
               if (safety != Holes.Safety.NONE) {
                  int color = switch (safety) {
                     case BEDROCK -> -11086174;
                     case OBSIDIAN -> -6467875;
                     default -> -16722689;
                  };
                  AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 0.25, pos.getZ() + 1.0);
                  Render3D.box(box, color, true, true, 1.8F, 55, this.throughWalls.get());
               }
            }
         }
      }
   }

   public static final class ItemEsp extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest item to draw", 64.0, 8.0, 256.0, 4.0));
      private final BooleanSetting boxes = this.addSetting(new BooleanSetting("Boxes", "Draw a box around each item", true));
      private final BooleanSetting names = this.addSetting(new BooleanSetting("Names", "Show item name and count", true));
      private final BooleanSetting tracers = this.addSetting(new BooleanSetting("Tracers", "Draw a line to each item", false));
      private final ModeSetting colors = this.addSetting(
         new ModeSetting("Colors", "Rarity tints by item rarity; Custom uses one color", "Rarity", "Rarity", "Custom")
      );
      private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Box color", 120).showIf(() -> this.colors.is("Custom")));
      private final ListSetting only = this.addSetting(new ListSetting("Only", "If not empty, only these items are shown", ListSetting.Kind.ITEM));
      private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Draw even when the item is hidden", true));

      public ItemEsp() {
         super("Item ESP", "See dropped items through walls with their names and counts.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer self = client.player;
         ClientLevel level = client.level;
         if (self != null && level != null) {
            double limit = this.range.get() * this.range.get();
            Set<Item> filter = this.only.items();
            Vec3 origin = Render3D.crosshair();

            for (Entity entity : level.entitiesForRendering()) {
               if (entity instanceof ItemEntity item && !(entity.distanceToSqr(self) > limit)) {
                  ItemStack stack = item.getItem();
                  if (filter.isEmpty() || filter.contains(stack.getItem())) {
                     int color = this.colors.is("Custom") ? this.custom.argb() : rarity(stack);
                     AABB box = Render3D.interpolatedBox(entity, partialTick);
                     if (Render3D.visible(box)) {
                        if (this.boxes.get()) {
                           Render3D.box(box, color, true, true, 1.0F, 35, this.throughWalls.get());
                        }

                        if (this.names.get()) {
                           String label = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
                           Render3D.text(label, box.getCenter().add(0.0, 0.45, 0.0), color, 0.8F, this.throughWalls.get());
                        }

                        if (this.tracers.get()) {
                           Render3D.line(origin, box.getCenter(), color, 1.0F, this.throughWalls.get());
                        }
                     }
                  }
               }
            }
         }
      }

      private static int rarity(ItemStack stack) {
         return switch (stack.getRarity()) {
            case UNCOMMON -> -171;
            case RARE -> -11141121;
            case EPIC -> -43521;
            default -> -1;
         };
      }
   }

   public static final class JumpEffect extends Module {
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Ring accent hue", 198));
      private final ArrayDeque<RenderExtras.JumpEffect.Ring> rings = new ArrayDeque<>();
      private boolean wasOnGround = true;

      public JumpEffect() {
         super("JumpEffect", "Render expanding neon rings on the ground whenever you jump.", Category.RENDER);
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            if (this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.1) {
               this.rings.addLast(new RenderExtras.JumpEffect.Ring(player.position(), System.currentTimeMillis()));
               if (this.rings.size() > 8) {
                  this.rings.removeFirst();
               }
            }

            this.wasOnGround = player.onGround();
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         long now = System.currentTimeMillis();
         int baseColor = this.color.argb();
         Iterator<RenderExtras.JumpEffect.Ring> it = this.rings.iterator();

         while (it.hasNext()) {
            RenderExtras.JumpEffect.Ring ring = it.next();
            float t = (float)(now - ring.createdAt) / 900.0F;
            if (t >= 1.0F) {
               it.remove();
            } else {
               double r = 0.2 + t * 1.4;
               int alpha = Math.round(220.0F * (1.0F - t));
               int segments = 20;

               for (int i = 0; i < segments; i++) {
                  double a1 = i * Math.PI * 2.0 / segments;
                  double a2 = (i + 1) * Math.PI * 2.0 / segments;
                  Vec3 p1 = new Vec3(ring.center.x + Math.cos(a1) * r, ring.center.y + 0.04, ring.center.z + Math.sin(a1) * r);
                  Vec3 p2 = new Vec3(ring.center.x + Math.cos(a2) * r, ring.center.y + 0.04, ring.center.z + Math.sin(a2) * r);
                  Render3D.lineArgb(p1, p2, Render3D.alpha(baseColor, alpha), 2.0F, false);
               }
            }
         }
      }

      private record Ring(Vec3 center, long createdAt) {
      }
   }

   public static final class LogoffSpot extends Module {
      private final Map<UUID, RenderExtras.LogoffSpot.Spot> activePlayers = new HashMap<>();
      private final List<RenderExtras.LogoffSpot.Spot> loggedSpots = new ArrayList<>();

      public LogoffSpot() {
         super("LogoffSpot", "Mark the last known 3D bounding box and name of players who disconnect nearby.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.activePlayers.clear();
         this.loggedSpots.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            Map<UUID, RenderExtras.LogoffSpot.Spot> current = new HashMap<>();

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity != client.player && entity instanceof Player p) {
                  current.put(p.getUUID(), new RenderExtras.LogoffSpot.Spot(p.getName().getString(), p.getBoundingBox()));
               }
            }

            for (Entry<UUID, RenderExtras.LogoffSpot.Spot> prev : this.activePlayers.entrySet()) {
               if (!current.containsKey(prev.getKey())) {
                  this.loggedSpots.add(prev.getValue());
                  if (this.loggedSpots.size() > 12) {
                     this.loggedSpots.removeFirst();
                  }
               }
            }

            this.activePlayers.clear();
            this.activePlayers.putAll(current);
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         for (RenderExtras.LogoffSpot.Spot spot : this.loggedSpots) {
            Render3D.box(spot.box, -14249, true, true, 1.6F, 45, true);
            Render3D.text(spot.name + " (LOGGED)", spot.box.getCenter().add(0.0, 1.1, 0.0), -14249, 0.85F, true);
         }
      }

      private record Spot(String name, AABB box) {
      }
   }

   public static final class PearlTracker extends Module {
      private final BooleanSetting trajectory = this.addSetting(new BooleanSetting("Flight Path", "Draw predicted landing arc for thrown pearls", true));
      private final BooleanSetting landingBox = this.addSetting(new BooleanSetting("Landing Box", "Highlight predicted impact spot in 3D", true));
      private final BooleanSetting labels = this.addSetting(new BooleanSetting("Owner Label", "Display thrower name and impact countdown", true));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Pearl tracker accent hue", 285));

      public PearlTracker() {
         super("PearlTracker", "Track thrown Ender Pearls in 3D flight with owner labels and predicted landing spots.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            int argb = this.color.argb();

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity instanceof Projectile pearl && entity.getType() == EntityTypes.ENDER_PEARL) {
                  Vec3 pos = pearl.getPosition(partialTick);
                  Vec3 vel = pearl.getDeltaMovement();
                  Entity owner = pearl.getOwner();
                  String ownerName = owner != null ? owner.getName().getString() : "Pearl";
                  Render3D.boxGlow(Render3D.interpolatedBox(pearl, partialTick).inflate(0.08), argb, 1.8F, 70, true);
                  Vec3 simPos = pos;
                  Vec3 simVel = vel;
                  Vec3 landing = null;
                  int ticksToLand = 0;

                  for (int step = 0; step < 120; step++) {
                     Vec3 next = simPos.add(simVel);
                     BlockHitResult hit = client.level.clip(new ClipContext(simPos, next, Block.COLLIDER, Fluid.NONE, pearl));
                     if (hit.getType() != Type.MISS) {
                        landing = hit.getLocation();
                        ticksToLand = step;
                        if (this.trajectory.get()) {
                           Render3D.line(simPos, landing, argb, 1.6F, true);
                        }
                        break;
                     }

                     if (this.trajectory.get()) {
                        Render3D.line(simPos, next, Render3D.alpha(argb, 185), 1.5F, true);
                     }

                     simPos = next;
                     simVel = simVel.scale(0.99).subtract(0.0, 0.03, 0.0);
                  }

                  if (landing != null && this.landingBox.get()) {
                     AABB spot = new AABB(landing.x - 0.3, landing.y - 0.1, landing.z - 0.3, landing.x + 0.3, landing.y + 0.5, landing.z + 0.3);
                     Render3D.boxGlow(spot, argb, 2.0F, 65, true);
                     if (this.labels.get()) {
                        String tag = String.format(Locale.ROOT, "%s (%.1fs)", ownerName, ticksToLand / 20.0F);
                        Render3D.text(tag, landing.add(0.0, 0.75, 0.0), argb, 0.85F, true);
                     }
                  } else if (this.labels.get()) {
                     Render3D.text(ownerName, pos.add(0.0, 0.55, 0.0), argb, 0.85F, true);
                  }
               }
            }
         }
      }
   }

   public static final class PlayerTrails extends Module {
      private final NumberSetting length = this.addSetting(new NumberSetting("Trail Length", "Number of trail segments", 35.0, 10.0, 100.0, 5.0));
      private final BooleanSetting rainbow = this.addSetting(new BooleanSetting("Rainbow", "Cycle neon rainbow colors along the ribbon", true));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Ribbon color", 195).showIf(() -> !this.rainbow.get()));
      private final ArrayDeque<Vec3> samples = new ArrayDeque<>();

      public PlayerTrails() {
         super("PlayerTrails", "Render a smooth dual-ribbon neon trail behind your character.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.samples.clear();
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            this.samples.addLast(player.position());

            while (this.samples.size() > this.length.intValue()) {
               this.samples.removeFirst();
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         if (this.samples.size() >= 2) {
            Vec3 prev = null;
            int idx = 0;
            int total = this.samples.size();
            int base = this.color.argb();

            for (Vec3 pt : this.samples) {
               if (prev != null) {
                  float t = (float)idx / total;
                  int c = this.rainbow.get()
                     ? ColorSetting.hueColor((float)((System.currentTimeMillis() / 18L + idx * 10L) % 360L) / 360.0F, 0.78F, 1.0F)
                     : base;
                  int faded = Render3D.alpha(c, Math.round(210.0F * t));
                  Render3D.lineArgb(prev.add(0.0, 0.12, 0.0), pt.add(0.0, 0.12, 0.0), faded, 2.2F, false);
                  Render3D.lineArgb(prev.add(0.0, 1.05, 0.0), pt.add(0.0, 1.05, 0.0), Render3D.alpha(c, Math.round(135.0F * t)), 1.5F, false);
               }

               prev = pt;
               idx++;
            }
         }
      }
   }

   public static final class ProjectileEsp extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest projectile to draw", 48.0, 8.0, 128.0, 4.0));
      private final BooleanSetting arrows = this.addSetting(new BooleanSetting("Arrows", "Arrows and tridents", true));
      private final BooleanSetting fireballs = this.addSetting(new BooleanSetting("Fireballs", "Ghast and blaze fire", true));
      private final BooleanSetting explosives = this.addSetting(new BooleanSetting("Explosives", "Primed TNT and falling anvils", true));
      private final BooleanSetting other = this.addSetting(new BooleanSetting("Other", "Pearls, potions, snowballs and the rest", true));
      private final BooleanSetting path = this.addSetting(new BooleanSetting("Path", "Draw where the projectile is going", true));
      private final BooleanSetting ownerLines = this.addSetting(new BooleanSetting("Owner Lines", "Draw a line back to whoever fired it", false));

      public ProjectileEsp() {
         super("Projectile ESP", "Highlight incoming arrows, fireballs, TNT and falling blocks.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer self = client.player;
         ClientLevel level = client.level;
         if (self != null && level != null) {
            double limit = this.range.get() * this.range.get();

            for (Entity entity : level.entitiesForRendering()) {
               if (!(entity.distanceToSqr(self) > limit)) {
                  int color = this.classify(entity, self);
                  if (color != 0) {
                     AABB box = Render3D.interpolatedBox(entity, partialTick).inflate(0.1);
                     Render3D.box(box, color, true, true, 1.5F, 60, true);
                     Vec3 velocity = entity.getDeltaMovement();
                     if (this.path.get() && velocity.lengthSqr() > 1.0E-4) {
                        Vec3 start = box.getCenter();
                        Vec3 point = start;
                        Vec3 motion = velocity;

                        for (int step = 0; step < 40; step++) {
                           Vec3 next = point.add(motion);
                           Render3D.line(point, next, Render3D.alpha(color, 160), 1.0F, true);
                           point = next;
                           motion = motion.scale(0.99).add(0.0, entity instanceof Fireball ? 0.0 : -0.05, 0.0);
                           if (!level.getBlockState(BlockPos.containing(next)).isAir()) {
                              break;
                           }
                        }
                     }

                     if (this.ownerLines.get() && entity instanceof Projectile projectile && projectile.getOwner() != null && projectile.getOwner() != self) {
                        Render3D.line(
                           box.getCenter(),
                           projectile.getOwner().getPosition(partialTick).add(0.0, projectile.getOwner().getBbHeight() / 2.0, 0.0),
                           Render3D.alpha(color, 120),
                           1.0F,
                           true
                        );
                     }
                  }
               }
            }
         }
      }

      private int classify(Entity entity, LocalPlayer self) {
         if (entity instanceof AbstractArrow arrow) {
            return this.arrows.get() && arrow.getOwner() != self ? -41892 : 0;
         } else if (entity instanceof Fireball) {
            return this.fireballs.get() ? -26052 : 0;
         } else if (entity instanceof PrimedTnt) {
            return this.explosives.get() ? -50116 : 0;
         } else if (entity instanceof FallingBlockEntity falling) {
            return this.explosives.get() && falling.getBlockState().is(BlockTags.ANVIL) ? -4144960 : 0;
         } else if (!(entity instanceof Projectile projectile)) {
            return 0;
         } else {
            return this.other.get() && projectile.getOwner() != self ? -6641921 : 0;
         }
      }
   }

   public static final class SoundLocator extends Module {
      public static RenderExtras.SoundLocator instance;
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Maximum distance to locate sounds", 48.0, 12.0, 96.0, 4.0));
      private final BooleanSetting onlyImportant = this.addSetting(
         new BooleanSetting("Important Only", "Only show chests, explosions, teleports, totems, and player steps", true)
      );
      private final ArrayDeque<RenderExtras.SoundLocator.SoundPing> pings = new ArrayDeque<>();

      public SoundLocator() {
         super("SoundLocator", "Visualize nearby explosions, chests, teleports, and footsteps in 3D space.", Category.RENDER);
         instance = this;
      }

      @Override
      public void onDisable() {
         this.pings.clear();
      }

      public void recordSound(String path, double x, double y, double z) {
         if (this.isEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
               double maxD = this.range.get();
               double d2 = client.player.distanceToSqr(x, y, z);
               if (!(d2 < 2.25) && !(d2 > maxD * maxD)) {
                  String label = classifySound(path, this.onlyImportant.get());
                  if (label != null) {
                     Vec3 pos = new Vec3(x, y, z);
                     synchronized (this.pings) {
                        for (RenderExtras.SoundLocator.SoundPing existing : this.pings) {
                           if (existing.pos.distanceToSqr(pos) < 4.0 && existing.label.equals(label)) {
                              return;
                           }
                        }

                        this.pings.addLast(new RenderExtras.SoundLocator.SoundPing(label, pos, System.currentTimeMillis()));

                        while (this.pings.size() > 14) {
                           this.pings.removeFirst();
                        }
                     }
                  }
               }
            }
         }
      }

      private static String classifySound(String path, boolean importantOnly) {
         if (path.contains("chest") || path.contains("shulker") || path.contains("barrel")) {
            return "Container";
         } else if (path.contains("explode") || path.contains("tnt")) {
            return "Explosion";
         } else if (path.contains("teleport") || path.contains("chorus") || path.contains("ender_pearl")) {
            return "Teleport";
         } else if (path.contains("totem")) {
            return "Totem Pop";
         } else if (!path.contains("Drink") && !path.contains("drink") && !path.contains("burp")) {
            return importantOnly || !path.contains("step") && !path.contains("swim") ? null : "Movement";
         } else {
            return "Consumable";
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         long now = System.currentTimeMillis();
         synchronized (this.pings) {
            Iterator<RenderExtras.SoundLocator.SoundPing> it = this.pings.iterator();

            while (it.hasNext()) {
               RenderExtras.SoundLocator.SoundPing ping = it.next();
               float t = (float)(now - ping.createdAt) / 1400.0F;
               if (t >= 1.0F) {
                  it.remove();
               } else {
                  int color = !ping.label.equals("Explosion") && !ping.label.equals("Totem Pop") ? -16718337 : -48043;
                  AABB box = new AABB(ping.pos.x - 0.25, ping.pos.y - 0.25, ping.pos.z - 0.25, ping.pos.x + 0.25, ping.pos.y + 0.25, ping.pos.z + 0.25);
                  Render3D.box(box, color, true, true, 1.5F, Math.round(65.0F * (1.0F - t)), true);
                  Render3D.text(ping.label, ping.pos.add(0.0, 0.55, 0.0), color, 0.78F, true);
               }
            }
         }
      }

      private record SoundPing(String label, Vec3 pos, long createdAt) {
      }
   }

   public static final class TntTimer extends Module {
      public TntTimer() {
         super("TNTTimer", "Render a floating fuse countdown in seconds above primed TNT entities.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity instanceof PrimedTnt tnt && entity.distanceToSqr(client.player) <= 2304.0) {
                  float seconds = Math.max(0.0F, (tnt.getFuse() - partialTick) / 20.0F);
                  int color = seconds <= 1.2F ? -1548963 : -14249;
                  Render3D.text(String.format(Locale.ROOT, "%.2fs", seconds), tnt.getPosition(partialTick).add(0.0, 1.25, 0.0), color, 0.9F, true);
               }
            }
         }
      }
   }

   public static final class TunnelEsp extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you to scan", 24.0, 8.0, 48.0, 4.0));
      private final NumberSetting below = this.addSetting(new NumberSetting("Depth", "Blocks below your feet to scan", 12.0, 0.0, 48.0, 2.0));
      private final NumberSetting interval = this.addSetting(new NumberSetting("Scan Interval", "Ticks between scans", 20.0, 5.0, 100.0, 5.0));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 200));
      private final BooleanSetting fill = this.addSetting(new BooleanSetting("Fill", "Fill the highlighted blocks", true));
      private final List<BlockPos> found = new ArrayList<>();
      private int wait;

      public TunnelEsp() {
         super("Tunnel ESP", "Highlight 1x2 tunnels dug by players.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.found.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         Level level = client.level;
         if (player != null && level != null && this.wait-- <= 0) {
            this.wait = this.interval.intValue();
            this.found.clear();
            int r = this.radius.intValue();
            BlockPos center = player.blockPosition();
            MutableBlockPos cursor = new MutableBlockPos();
            int yMin = Math.max(level.getMinY() + 1, center.getY() - this.below.intValue());
            int yMax = Math.min(level.getMaxY() - 2, center.getY() + 6);

            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  for (int y = yMin; y <= yMax; y++) {
                     cursor.set(center.getX() + x, y, center.getZ() + z);
                     if (tunnel(level, cursor)) {
                        this.found.add(cursor.immutable());
                        if (this.found.size() >= 1500) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }

      private static boolean tunnel(Level level, BlockPos pos) {
         if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
         } else if (solid(level, pos.below()) && solid(level, pos.above(2))) {
            boolean northSouth = solid(level, pos.east())
               && solid(level, pos.west())
               && solid(level, pos.above().east())
               && solid(level, pos.above().west())
               && level.getBlockState(pos.north()).isAir()
               && level.getBlockState(pos.south()).isAir();
            boolean eastWest = solid(level, pos.north())
               && solid(level, pos.south())
               && solid(level, pos.above().north())
               && solid(level, pos.above().south())
               && level.getBlockState(pos.east()).isAir()
               && level.getBlockState(pos.west()).isAir();
            return northSouth || eastWest;
         } else {
            return false;
         }
      }

      private static boolean solid(Level level, BlockPos pos) {
         BlockState state = level.getBlockState(pos);
         return !state.isAir() && state.isSolidRender();
      }

      @Override
      public void onRender3D(float partialTick) {
         int argb = this.color.argb();

         for (BlockPos pos : this.found) {
            if (Render3D.visibleBlock(pos.getX(), pos.getY(), pos.getZ())) {
               Render3D.box(
                  new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1), argb, true, this.fill.get(), 1.0F, 30, true
               );
            }
         }
      }

      @Override
      public String info() {
         return this.found.isEmpty() ? null : this.found.size() + " blocks";
      }
   }

   public static final class VoidEsp extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you to scan", 32.0, 8.0, 96.0, 4.0));
      private final NumberSetting interval = this.addSetting(new NumberSetting("Scan Interval", "Ticks between scans", 20.0, 5.0, 100.0, 5.0));
      private final BooleanSetting netherRoof = this.addSetting(new BooleanSetting("Nether Roof", "Also check the top bedrock layer in the Nether", true));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 300));
      private final List<BlockPos> holes = new ArrayList<>();
      private int wait;

      public VoidEsp() {
         super("Void ESP", "Highlight holes in the bedrock that drop into the void.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.holes.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         Level level = client.level;
         if (player != null && level != null && this.wait-- <= 0) {
            this.wait = this.interval.intValue();
            this.holes.clear();
            if (level.dimension() != Level.END) {
               int r = this.radius.intValue();
               BlockPos center = player.blockPosition();
               int floor = level.getMinY();
               MutableBlockPos cursor = new MutableBlockPos();

               for (int x = -r; x <= r; x++) {
                  for (int z = -r; z <= r; z++) {
                     cursor.set(center.getX() + x, floor, center.getZ() + z);
                     if (level.hasChunkAt(cursor)) {
                        if (level.getBlockState(cursor).isAir()) {
                           this.holes.add(cursor.immutable());
                        }

                        if (this.netherRoof.get() && level.dimension() == Level.NETHER) {
                           cursor.setY(127);
                           if (level.getBlockState(cursor).isAir() && !level.getBlockState(cursor.below()).is(Blocks.BEDROCK)) {
                              this.holes.add(cursor.immutable());
                           }
                        }

                        if (this.holes.size() >= 2000) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         int argb = this.color.argb();

         for (BlockPos pos : this.holes) {
            if (Render3D.visibleBlock(pos.getX(), pos.getY(), pos.getZ())) {
               Render3D.block(pos, argb, true, true, 1.0F, 50, true);
            }
         }
      }

      @Override
      public String info() {
         return this.holes.isEmpty() ? null : this.holes.size() + " holes";
      }
   }
}
