package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ItemEsp extends Module {
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
      super("Item ESP", "Highlight dropped items with names and stack counts.", Category.RENDER);
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
