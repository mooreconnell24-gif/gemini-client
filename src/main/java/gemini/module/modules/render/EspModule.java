package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EspModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Glow + Box draws the glowing outline and boxes; 3D Box draws plain boxes; GlowBox draws boxes with a soft edge; Shader Glow draws only the vanilla glowing outline", "Glow + Box", "Glow + Box", "3D Box", "GlowBox", "Shader Glow")
   );
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Highlight other players", true));
   private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Highlight hostile mobs", true));
   private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Highlight animals and other passive mobs", false));
   private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Highlight dropped items", false));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest entity highlighted, in blocks", 64.0, 8.0, 144.0, 4.0));
   private final ModeSetting colorMode = this.addSetting(new ModeSetting("Colors", "How highlights are coloured", "Type", "Type", "Health", "Custom"));
   private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Highlight colour", 168).showIf(() -> this.colorMode.is("Custom")));
   private final BooleanSetting healthBar = this.addSetting(new BooleanSetting("3D Health Bar", "Draw a vertical health bar beside living entities", true));
   private final BooleanSetting tags = this.addSetting(new BooleanSetting("Tags", "Also list highlighted entities on the HUD", false));
   private final NumberSetting tagCount = this.addSetting(
      new NumberSetting("Max Tags", "Most entries in the HUD list", 8.0, 1.0, 20.0, 1.0).showIf(this.tags::get)
   );

   public EspModule() {
      super("ESP", "Highlights entities through walls.", Category.RENDER);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   public boolean tags() {
      return this.tags.get();
   }

   public int maxTags() {
      return this.tagCount.intValue();
   }

   public boolean matchesTarget(Entity entity) {
      if (!entity.level().isClientSide()) {
         return false;
      } else {
         LocalPlayer self = Minecraft.getInstance().player;
         if (self != null && entity != self) {
            if (entity.distanceToSqr(self) > this.range.get() * this.range.get()) {
               return false;
            } else {
               return entity instanceof ItemEntity
                  ? this.items.get()
                  : Targets.isCandidate(self, entity, this.players.get(), this.hostile.get(), this.passive.get(), true);
            }
         } else {
            return false;
         }
      }
   }

   public boolean shouldGlow(Entity entity) {
      return (this.mode.is("Shader Glow") || this.mode.is("Glow + Box")) && this.matchesTarget(entity);
   }

   @Override
   public void onRender3D(float partialTick) {
      if (!this.mode.is("Shader Glow") || this.healthBar.get()) {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            boolean drawBox = !this.mode.is("Shader Glow");
            boolean glowBox = this.mode.is("GlowBox") || this.mode.is("Glow + Box");

            for (Entity entity : client.level.entitiesForRendering()) {
               if (this.matchesTarget(entity)) {
                  // Culled without building the interpolated box; nothing is allocated for off-screen entities.
                  if (Render3D.entityVisible(entity, partialTick, 0.0)) {
                     int argb = 0xFF000000 | this.color(entity);
                     if (drawBox) {
                        if (glowBox) {
                           Render3D.entityBoxGlow(entity, partialTick, 0.0, argb, 1.6F, 42, true);
                        } else {
                           Render3D.entityBox(entity, partialTick, 0.0, argb, true, true, 1.5F, 35, true);
                        }
                     }

                     if (this.healthBar.get() && entity instanceof LivingEntity living) {
                        float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
                        int hpColor = ColorSetting.hueColor(ratio * 0.33F, 0.85F, 1.0F);
                        AABB bounds = entity.getBoundingBox();
                        double x = Render3D.entityX(entity, partialTick) - entity.getX() + bounds.minX - 0.12;
                        double y = Render3D.entityY(entity, partialTick) - entity.getY() + bounds.minY;
                        double z = Render3D.entityZ(entity, partialTick) - entity.getZ() + bounds.minZ;
                        double height = bounds.maxY - bounds.minY;
                        Render3D.line(x, y, z, x, y + height, z, -2012212194, 2.5F, true);
                        Render3D.line(x, y, z, x, y + height * ratio, z, hpColor, 2.5F, true);
                     }
                  }
               }
            }
         }
      }
   }

   public int color(Entity entity) {
      if (this.colorMode.is("Custom")) {
         return this.custom.argb() & 16777215;
      } else if (this.colorMode.is("Health") && entity instanceof LivingEntity living) {
         float ratio = Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
         return ColorSetting.hueColor(ratio * 0.33F, 0.85F, 1.0F) & 16777215;
      } else if (entity instanceof Player) {
         return 16735354;
      } else if (entity instanceof ItemEntity) {
         return 6087167;
      } else {
         return entity instanceof LivingEntity living && Targets.isHostile(living) ? 16753212 : 7143306;
      }
   }
}
