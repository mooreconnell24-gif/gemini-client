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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class Tracers extends Module {
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
            : RenderHelpers.typeColor(entity);
         case "Custom" -> this.custom.argb();
         default -> RenderHelpers.typeColor(entity);
      };
   }
}
