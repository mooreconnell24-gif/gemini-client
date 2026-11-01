package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

public final class CameraModules {
   private CameraModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new CameraModules.Freecam());
      sink.accept(new CameraModules.FreeLook());
      sink.accept(new CameraModules.CameraTweaks());
      sink.accept(new CameraModules.Fov());
      sink.accept(new CameraModules.UnfocusedCpu());
      sink.accept(new CameraModules.ParticleBlocker());
      sink.accept(new CameraModules.BetterTooltips());
   }

   public static final class BetterTooltips extends Module {
      private final BooleanSetting durability = this.addSetting(new BooleanSetting("Durability", "Exact remaining uses and percentage", true));
      private final BooleanSetting food = this.addSetting(new BooleanSetting("Food", "Hunger and saturation values", true));
      private final BooleanSetting storage = this.addSetting(new BooleanSetting("Storage", "Item totals inside containers", true));
      private final BooleanSetting itemId = this.addSetting(new BooleanSetting("Item Id", "The registry id of the item", false));
      private final NumberSetting storageLines = this.addSetting(
         new NumberSetting("Storage Lines", "Most item types listed", 6.0, 1.0, 27.0, 1.0).showIf(this.storage::get)
      );

      public BetterTooltips() {
         super("Better Tooltips", "Add durability, food and container details to item tooltips.", Category.RENDER);
         ItemTooltipCallback.EVENT.register((ItemTooltipCallback)(stack, context, flag, lines) -> {
            if (this.isEnabled()) {
               this.decorate(stack, lines);
            }
         });
      }

      private void decorate(ItemStack stack, List<Component> lines) {
         if (this.durability.get() && stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            lines.add(
               Component.literal(String.format(Locale.ROOT, "Durability: %d / %d (%d%%)", left, max, Math.round(100.0F * left / Math.max(1, max))))
                  .withStyle(ChatFormatting.GRAY)
            );
         }

         FoodProperties properties = (FoodProperties)stack.get(DataComponents.FOOD);
         if (this.food.get() && properties != null) {
            lines.add(
               Component.literal(String.format(Locale.ROOT, "Hunger: +%d   Saturation: +%.1f", properties.nutrition(), properties.saturation()))
                  .withStyle(ChatFormatting.GOLD)
            );
         }

         ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         if (this.storage.get() && contents != null) {
            Map<String, Integer> totals = new LinkedHashMap<>();
            contents.nonEmptyItemCopyStream().forEach(item -> totals.merge(item.getHoverName().getString(), item.getCount(), Integer::sum));
            int shown = 0;

            for (Entry<String, Integer> entry : totals.entrySet()) {
               if (shown++ >= this.storageLines.intValue()) {
                  lines.add(Component.literal("  and " + (totals.size() - this.storageLines.intValue()) + " more...").withStyle(ChatFormatting.DARK_GRAY));
                  break;
               }

               lines.add(Component.literal("  " + entry.getValue() + "x " + entry.getKey()).withStyle(ChatFormatting.AQUA));
            }
         }

         if (this.itemId.get()) {
            lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
         }
      }
   }

   public static final class CameraTweaks extends Module {
      public static CameraModules.CameraTweaks instance;
      private final BooleanSetting clip = this.addSetting(new BooleanSetting("Clip Through Blocks", "Third-person camera ignores walls", true));
      private final BooleanSetting custom = this.addSetting(new BooleanSetting("Custom Distance", "Override how far the third-person camera sits", true));
      private final NumberSetting distance = this.addSetting(
         new NumberSetting("Distance", "Third-person camera distance in blocks", 8.0, 1.0, 40.0, 0.5).showIf(this.custom::get)
      );

      public CameraTweaks() {
         super("Camera Tweaks", "Move the third-person camera further out and let it pass through walls.", Category.RENDER);
         instance = this;
      }

      public float distanceOr(float vanilla) {
         return this.custom.get() ? this.distance.floatValue() : vanilla;
      }

      public boolean clips() {
         return this.clip.get();
      }
   }

   public static final class Fov extends Module {
      public static CameraModules.Fov instance;
      private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view in degrees", 100.0, 30.0, 160.0, 1.0));
      private final BooleanSetting keepEffects = this.addSetting(new BooleanSetting("Keep Effects", "Still widen the view while sprinting or hasted", false));

      public Fov() {
         super("FOV", "Set a field of view beyond the vanilla slider.", Category.RENDER);
         instance = this;
      }

      public float apply(float vanilla) {
         if (this.keepEffects.get()) {
            float base = ((Integer)Minecraft.getInstance().options.fov().get()).floatValue();
            return base <= 0.0F ? vanilla : vanilla * this.fov.floatValue() / base;
         } else {
            return this.fov.floatValue();
         }
      }
   }

   public static final class FreeLook extends Module {
      public static CameraModules.FreeLook instance;
      private float yaw;
      private float pitch;
      private boolean ready;

      public FreeLook() {
         super("Free Look", "Look around freely while your character keeps facing forward. Bind it to a held key.", Category.RENDER);
         instance = this;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.ready = true;
         }
      }

      @Override
      public void onDisable() {
         this.ready = false;
      }

      public boolean active() {
         return this.isEnabled() && this.ready && (CameraModules.Freecam.instance == null || !CameraModules.Freecam.instance.active());
      }

      public float yaw() {
         return this.yaw;
      }

      public float pitch() {
         return this.pitch;
      }

      public boolean consumeTurn(Entity entity, double dx, double dy) {
         if (this.active() && entity == Minecraft.getInstance().player) {
            this.yaw += (float)dx * 0.15F;
            this.pitch = Mth.clamp(this.pitch + (float)dy * 0.15F, -90.0F, 90.0F);
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class Freecam extends Module {
      public static CameraModules.Freecam instance;
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Camera speed in blocks per tick", 1.0, 0.1, 5.0, 0.1));
      private final NumberSetting boost = this.addSetting(
         new NumberSetting("Sprint Boost", "Speed multiplier while the sprint key is held", 2.5, 1.0, 6.0, 0.5)
      );
      private final BooleanSetting freeze = this.addSetting(new BooleanSetting("Freeze Player", "Ignore movement keys so your character stands still", true));
      private final BooleanSetting stopOnHurt = this.addSetting(new BooleanSetting("Disable On Damage", "Return to your body when you get hit", true));
      private final ClientInput frozen = new ClientInput();
      private ClientInput saved;
      private double x;
      private double y;
      private double z;
      private double oldX;
      private double oldY;
      private double oldZ;
      private float yaw;
      private float pitch;
      private boolean ready;

      public Freecam() {
         super("Freecam", "Fly the camera around while your character stays where it is.", Category.RENDER);
         instance = this;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      protected boolean extraActivation() {
         return Minecraft.getInstance().player != null;
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            Vec3 eye = player.getEyePosition();
            this.x = this.oldX = eye.x;
            this.y = this.oldY = eye.y;
            this.z = this.oldZ = eye.z;
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.ready = true;
         }
      }

      @Override
      public void onDisable() {
         this.ready = false;
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.input == this.frozen) {
            player.input = (ClientInput)(this.saved != null ? this.saved : new KeyboardInput(Minecraft.getInstance().options));
         }

         this.saved = null;
      }

      public boolean active() {
         return this.isEnabled() && this.ready;
      }

      public double x(float partial) {
         return Mth.lerp(partial, this.oldX, this.x);
      }

      public double y(float partial) {
         return Mth.lerp(partial, this.oldY, this.y);
      }

      public double z(float partial) {
         return Mth.lerp(partial, this.oldZ, this.z);
      }

      public float yaw() {
         return this.yaw;
      }

      public float pitch() {
         return this.pitch;
      }

      public boolean consumeTurn(Entity entity, double dx, double dy) {
         if (this.active() && entity == Minecraft.getInstance().player) {
            this.yaw += (float)dx * 0.15F;
            this.pitch = Mth.clamp(this.pitch + (float)dy * 0.15F, -90.0F, 90.0F);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null) {
            if (this.stopOnHurt.get() && player.hurtTime > 0) {
               this.setEnabled(false);
            } else {
               if (this.freeze.get()) {
                  if (player.input != this.frozen) {
                     this.saved = player.input;
                     player.input = this.frozen;
                  }
               } else if (player.input == this.frozen && this.saved != null) {
                  player.input = this.saved;
                  this.saved = null;
               }

               this.oldX = this.x;
               this.oldY = this.y;
               this.oldZ = this.z;
               if (client.gui.screen() == null) {
                  Options options = client.options;
                  double forward = (options.keyUp.isDown() ? 1.0 : 0.0) - (options.keyDown.isDown() ? 1.0 : 0.0);
                  double strafe = (options.keyRight.isDown() ? 1.0 : 0.0) - (options.keyLeft.isDown() ? 1.0 : 0.0);
                  double lift = (options.keyJump.isDown() ? 1.0 : 0.0) - (options.keyShift.isDown() ? 1.0 : 0.0);
                  double length = Math.hypot(forward, strafe);
                  double step = this.speed.get() * (options.keySprint.isDown() ? this.boost.get() : 1.0);
                  if (length > 0.0) {
                     double radians = Math.toRadians(this.yaw);
                     double sin = Math.sin(radians);
                     double cos = Math.cos(radians);
                     double f = forward / length;
                     double s = strafe / length;
                     this.x += (-sin * f - cos * s) * step;
                     this.z += (cos * f - sin * s) * step;
                  }

                  this.y += lift * step;
               }
            }
         }
      }
   }

   public static final class ParticleBlocker extends Module {
      public static CameraModules.ParticleBlocker instance;
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Block everything, or only the listed particle types", "Listed", "Listed", "All")
      );
      private final ListSetting types = this.addSetting(
         new ListSetting(
               "Particles",
               "Particle ids to block, e.g. minecraft:explosion",
               ListSetting.Kind.TEXT,
               "minecraft:explosion",
               "minecraft:explosion_emitter",
               "minecraft:large_smoke"
            )
            .showIf(() -> this.mode.is("Listed"))
      );
      private volatile Set<String> cache;

      public ParticleBlocker() {
         super("Particles", "Hide particles you do not want to see: explosions, smoke, anything.", Category.RENDER);
         instance = this;
         this.types.onChange(() -> this.cache = null);
      }

      public boolean blocks(ParticleOptions options) {
         if (this.mode.is("All")) {
            return true;
         } else {
            Set<String> ids = this.cache;
            if (ids == null) {
               ids = new HashSet<>();

               for (String entry : this.types.get()) {
                  String id = entry.toLowerCase(Locale.ROOT);
                  ids.add(id.indexOf(58) < 0 ? "minecraft:" + id : id);
               }

               this.cache = ids;
            }

            Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
            return key != null && ids.contains(key.toString());
         }
      }
   }

   public static final class UnfocusedCpu extends Module {
      public static CameraModules.UnfocusedCpu instance;
      private final NumberSetting limit = this.addSetting(
         new NumberSetting("Background FPS", "Frame rate cap while the window is unfocused", 15.0, 1.0, 60.0, 1.0)
      );

      public UnfocusedCpu() {
         super("Unfocused CPU", "Save power by limiting the frame rate when you tab away.", Category.RENDER);
         instance = this;
      }

      public int cap(int vanilla) {
         return Minecraft.getInstance().isWindowActive() ? vanilla : Math.min(vanilla, this.limit.intValue());
      }
   }
}
