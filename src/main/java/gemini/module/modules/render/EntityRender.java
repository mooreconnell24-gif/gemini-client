package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Targets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class EntityRender {
   private EntityRender() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new EntityRender.Tracers());
      sink.accept(new EntityRender.Nametags());
      sink.accept(new EntityRender.Trajectories());
      sink.accept(new EntityRender.Breadcrumbs());
      sink.accept(new EntityRender.EntityOwner());
   }

   static int typeColor(Entity entity) {
      if (entity instanceof Player) {
         return 16735354;
      } else if (entity instanceof ItemEntity) {
         return 16765788;
      } else {
         return entity instanceof LivingEntity living && Targets.isHostile(living) ? 16753212 : 7143306;
      }
   }

   public static final class Breadcrumbs extends Module {
      private final NumberSetting maxPoints = this.addSetting(new NumberSetting("Max Points", "How long the trail can get", 400.0, 20.0, 2000.0, 20.0));
      private final NumberSetting spacing = this.addSetting(new NumberSetting("Spacing", "Minimum distance between trail points", 0.6, 0.2, 4.0, 0.1));
      private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the trail", 2.0, 0.5, 5.0, 0.5));
      private final BooleanSetting fade = this.addSetting(new BooleanSetting("Fade", "Older parts of the trail fade out", true));
      private final BooleanSetting rainbow = this.addSetting(new BooleanSetting("Rainbow", "Color the trail by age instead of one color", false));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Trail color", 190).showIf(() -> !this.rainbow.get()));
      private final ArrayDeque<Vec3> points = new ArrayDeque<>();
      private ClientLevel lastLevel;

      public Breadcrumbs() {
         super("Breadcrumbs", "Draw the path you have walked.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.points.clear();
         this.lastLevel = null;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null) {
            if (client.level != this.lastLevel) {
               this.points.clear();
               this.lastLevel = client.level;
            }

            Vec3 here = client.player.position().add(0.0, 0.1, 0.0);
            Vec3 last = this.points.peekLast();
            double gap = this.spacing.get();
            if (last == null || last.distanceToSqr(here) >= gap * gap) {
               this.points.addLast(here);

               while (this.points.size() > this.maxPoints.intValue()) {
                  this.points.removeFirst();
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         int size = this.points.size();
         if (size >= 2) {
            Vec3 previous = null;
            int index = 0;
            int base = this.color.argb();

            for (Vec3 point : this.points) {
               if (previous != null) {
                  float age = (float)index / size;
                  int argb = this.rainbow.get() ? ColorSetting.hueColor(age, 0.8F, 1.0F) : base;
                  if (this.fade.get()) {
                     argb = Render3D.alpha(argb, 40 + (int)(215.0F * age));
                  }

                  Render3D.lineArgb(previous, point, argb, this.width.floatValue(), false);
               }

               previous = point;
               index++;
            }
         }
      }
   }

   public static final class EntityOwner extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Longest label distance", 32.0, 4.0, 96.0, 2.0));
      private final BooleanSetting yours = this.addSetting(new BooleanSetting("Show Your Own", "Also label pets that belong to you", false));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Label color", 45));
      private final Map<UUID, String> names = new HashMap<>();

      public EntityOwner() {
         super("Entity Owner", "Show who owns tamed pets and horses.", Category.RENDER);
      }

      @Override
      public void onDisable() {
         this.names.clear();
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer me = client.player;
         ClientLevel level = client.level;
         if (me != null && level != null) {
            double limit = this.range.get() * this.range.get();
            int argb = this.color.argb();

            for (Entity entity : level.entitiesForRendering()) {
               if (entity instanceof OwnableEntity ownable && !(entity.distanceToSqr(me) > limit)) {
                  EntityReference<LivingEntity> reference = ownable.getOwnerReference();
                  if (reference != null) {
                     UUID id = reference.getUUID();
                     if (this.yours.get() || !id.equals(me.getUUID())) {
                        String owner = this.names.computeIfAbsent(id, key -> resolve(client, key));
                        Render3D.text("Owner: " + owner, entity.getPosition(partialTick).add(0.0, entity.getBbHeight() + 0.45, 0.0), argb, 0.38F, true);
                     }
                  }
               }
            }
         }
      }

      private static String resolve(Minecraft client, UUID id) {
         if (client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(id);
            if (info != null) {
               return info.getProfile().name();
            }
         }

         String raw = id.toString();
         return raw.substring(0, 8) + "...";
      }
   }

   public static final class Nametags extends Module {
      public static EntityRender.Nametags instance;
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Label other players", true));
      private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Label hostile and passive mobs", false));
      private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Label dropped items with their stack size", false));
      private final BooleanSetting self = this.addSetting(new BooleanSetting("Yourself", "Label yourself in third person and Freecam", false));
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Longest label distance", 64.0, 8.0, 128.0, 4.0));
      private final BooleanSetting health = this.addSetting(new BooleanSetting("Health", "Show hit points", true));
      private final BooleanSetting ping = this.addSetting(new BooleanSetting("Ping", "Show latency from the tab list", true));
      private final BooleanSetting gamemode = this.addSetting(new BooleanSetting("Gamemode", "Show the game mode letter", false));
      private final BooleanSetting distance = this.addSetting(new BooleanSetting("Distance", "Show distance in blocks", false));
      private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Label size", 1.25, 0.4, 3.0, 0.05));
      private final BooleanSetting constantSize = this.addSetting(new BooleanSetting("Constant Size", "Grow labels with distance so they stay readable", true));
      private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Keep labels visible behind blocks", true));
      private final BooleanSetting hideVanilla = this.addSetting(new BooleanSetting("Hide Vanilla", "Hide the built-in name tags of labelled players", true));

      public Nametags() {
         super("Nametags", "Readable labels with health and ping above players.", Category.RENDER);
         instance = this;
      }

      public boolean replacesVanilla(Entity entity) {
         return this.isEnabled() && this.hideVanilla.get() && entity instanceof Player && this.players.get() && entity != Minecraft.getInstance().player;
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer me = client.player;
         ClientLevel level = client.level;
         if (me != null && level != null) {
            double limit = this.range.get() * this.range.get();
            boolean showSelf = this.self.get() && client.gameRenderer.mainCamera().isDetached();

            for (Entity entity : level.entitiesForRendering()) {
               if (entity != me || showSelf) {
                  double distSq = entity.distanceToSqr(me);
                  if (!(distSq > limit)) {
                     String label = this.label(client, entity, Math.sqrt(distSq));
                     if (label != null) {
                        Vec3 at = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() + 0.5, 0.0);
                        float size = 0.32F * this.scale.floatValue();
                        if (this.constantSize.get()) {
                           size *= (float)Math.max(1.0, Math.sqrt(distSq) / 5.0);
                        }

                        int color = entity instanceof LivingEntity living && this.health.get() && !(entity instanceof Player)
                           ? ColorSetting.hueColor(Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F) * 0.33F, 0.55F, 1.0F)
                           : EntityRender.typeColor(entity);
                        Render3D.text(label, at, color, size, this.throughWalls.get());
                     }
                  }
               }
            }
         }
      }

      private String label(Minecraft client, Entity entity, double blocks) {
         StringBuilder text = new StringBuilder();
         if (entity instanceof Player player) {
            if (!this.players.get() || player.isSpectator() && player != client.player) {
               return null;
            }

            text.append(player.getName().getString());
            PlayerInfo info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player.getUUID());
            if (this.ping.get() && info != null && info.getLatency() > 0) {
               text.append(' ').append(info.getLatency()).append("ms");
            }

            if (this.gamemode.get() && info != null) {
               text.append(" [").append(modeLetter(info.getGameMode())).append(']');
            }
         } else {
            if (entity instanceof ItemEntity item) {
               if (!this.items.get()) {
                  return null;
               }

               ItemStack stack = item.getItem();
               text.append(stack.getHoverName().getString());
               if (stack.getCount() > 1) {
                  text.append(" x").append(stack.getCount());
               }

               return text.toString();
            }

            if (!(entity instanceof LivingEntity)) {
               return null;
            }

            if (!this.mobs.get()) {
               return null;
            }

            text.append(entity.getName().getString());
         }

         if (this.health.get() && entity instanceof LivingEntity living) {
            float hp = living.getHealth() + living.getAbsorptionAmount();
            text.append(' ').append(String.format(Locale.ROOT, "%.1f", hp));
         }

         if (this.distance.get()) {
            text.append(' ').append((int)blocks).append('m');
         }

         return text.toString();
      }

      private static char modeLetter(GameType type) {
         return switch (type) {
            case SURVIVAL -> 'S';
            case CREATIVE -> 'C';
            case ADVENTURE -> 'A';
            case SPECTATOR -> 'V';
            default -> throw new MatchException(null, null);
         };
      }
   }

   public static final class Tracers extends Module {
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Draw lines to other players", true));
      private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Draw lines to hostile mobs", true));
      private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Draw lines to animals and villagers", false));
      private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Draw lines to dropped items", false));
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Longest distance that gets a line", 128.0, 8.0, 256.0, 4.0));
      private final ModeSetting target = this.addSetting(new ModeSetting("Target", "Which part of the entity the line ends on", "Body", "Feet", "Body", "Eyes"));
      private final ModeSetting colors = this.addSetting(new ModeSetting("Colors", "How lines are colored", "Type", "Type", "Distance", "Health", "Custom"));
      private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Line color", 190).showIf(() -> this.colors.is("Custom")));
      private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the lines", 1.5, 0.5, 5.0, 0.5));

      public Tracers() {
         super("Tracers", "Draw lines from your crosshair to entities.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer self = client.player;
         ClientLevel level = client.level;
         if (self != null && level != null) {
            Vec3 origin = Render3D.crosshair();
            double limit = this.range.get() * this.range.get();

            for (Entity entity : level.entitiesForRendering()) {
               if (entity != self && !(entity.distanceToSqr(self) > limit) && this.accepts(entity)) {
                  Vec3 at = entity.getPosition(partialTick);
                  String var13 = this.target.get();

                  double y = switch (var13) {
                     case "Feet" -> at.y;
                     case "Eyes" -> at.y + entity.getEyeHeight();
                     default -> at.y + entity.getBbHeight() / 2.0;
                  };
                  Render3D.line(origin, new Vec3(at.x, y, at.z), this.color(self, entity), this.width.floatValue(), true);
               }
            }
         }
      }

      private boolean accepts(Entity entity) {
         if (!(entity instanceof Player player)) {
            if (entity instanceof ItemEntity) {
               return this.items.get();
            } else {
               return entity instanceof LivingEntity living ? Targets.isHostile(living) ? this.hostile.get() : this.passive.get() : false;
            }
         } else {
            return this.players.get() && !player.isSpectator();
         }
      }

      private int color(LocalPlayer self, Entity entity) {
         String var3 = this.colors.get();

         return switch (var3) {
            case "Distance" -> ColorSetting.hueColor(
               Mth.clamp((float)(Math.sqrt(entity.distanceToSqr(self)) / this.range.get()), 0.0F, 1.0F) * 0.33F, 0.85F, 1.0F
            );
            case "Health" -> entity instanceof LivingEntity living
               ? ColorSetting.hueColor(Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F) * 0.33F, 0.85F, 1.0F)
               : EntityRender.typeColor(entity);
            case "Custom" -> this.custom.argb();
            default -> EntityRender.typeColor(entity);
         };
      }
   }

   public static final class Trajectories extends Module {
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
         super("Trajectories", "Predict where arrows, pearls and other projectiles.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         if (player != null && level != null && !player.isSpectator()) {
            EntityRender.Trajectories.Launch launch = this.launchOf(player, player.getMainHandItem());
            if (launch == null) {
               launch = this.launchOf(player, player.getOffhandItem());
            }

            if (launch != null) {
               this.simulate(client, player, level, launch, partialTick);
            }
         }
      }

      private EntityRender.Trajectories.Launch launchOf(LocalPlayer player, ItemStack stack) {
         if (stack.isEmpty()) {
            return null;
         } else if (stack.getItem() instanceof BowItem) {
            if (this.bows.get() && player.isUsingItem() && player.getUseItem() == stack) {
               float power = BowItem.getPowerForTime(player.getTicksUsingItem());
               return power < 0.1F ? null : new EntityRender.Trajectories.Launch(power * 3.0, 0.05, 0.0, true);
            } else {
               return null;
            }
         } else if (stack.getItem() instanceof CrossbowItem) {
            return this.bows.get() && CrossbowItem.isCharged(stack) ? new EntityRender.Trajectories.Launch(3.15, 0.05, 0.0, true) : null;
         } else if (!stack.is(Items.TRIDENT)) {
            if (!this.throwables.get()) {
               return null;
            } else if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
               return new EntityRender.Trajectories.Launch(1.5, 0.03, 0.0, false);
            } else if (!stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
               return stack.is(Items.EXPERIENCE_BOTTLE) ? new EntityRender.Trajectories.Launch(0.7, 0.07, -20.0, false) : null;
            } else {
               return new EntityRender.Trajectories.Launch(0.5, 0.05, -20.0, false);
            }
         } else {
            return this.tridents.get() && player.isUsingItem() && player.getUseItem() == stack
               ? new EntityRender.Trajectories.Launch(2.5, 0.05, 0.0, true)
               : null;
         }
      }

      private void simulate(Minecraft client, LocalPlayer player, ClientLevel level, EntityRender.Trajectories.Launch launch, float partialTick) {
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
}
