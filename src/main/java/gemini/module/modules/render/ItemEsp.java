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
import net.minecraft.world.phys.Vec3;

public final class ItemEsp extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest item shown, in blocks", 64.0, 8.0, 256.0, 4.0));
   private final BooleanSetting boxes = this.addSetting(new BooleanSetting("Boxes", "Draw a box around each item", true));
   private final BooleanSetting names = this.addSetting(new BooleanSetting("Names", "Show item name and count", true));
   private final BooleanSetting tracers = this.addSetting(new BooleanSetting("Tracers", "Draw a line to each item", false));
   private final ModeSetting colors = this.addSetting(
      new ModeSetting("Colors", "Rarity colours items by rarity; Custom uses one colour", "Rarity", "Rarity", "Custom")
   );
   private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Box colour", 120).showIf(() -> this.colors.is("Custom")));
   private final ListSetting only = this.addSetting(new ListSetting("Only", "If not empty, only these items are shown", ListSetting.Kind.ITEM));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Draw items hidden behind blocks too", true));

   private final LabelCache labels = new LabelCache();

   public ItemEsp() {
      super("Item ESP", "Highlights dropped items and shows their names and stack sizes.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.labels.clear();
   }

   @Override
   public void onWorldChange() {
      this.labels.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer self = client.player;
      ClientLevel level = client.level;
      if (self != null && level != null) {
         this.labels.beginFrame();
         double limit = this.range.get() * this.range.get();
         Set<Item> filter = this.only.items();
         boolean walls = this.throughWalls.get();
         Vec3 origin = null;

         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && !(entity.distanceToSqr(self) > limit)) {
               ItemStack stack = item.getItem();
               if ((filter.isEmpty() || filter.contains(stack.getItem())) && Render3D.entityVisible(entity, partialTick, 0.0)) {
                  int color = this.colors.is("Custom") ? this.custom.argb() : rarity(stack);
                  if (this.boxes.get()) {
                     Render3D.entityBox(entity, partialTick, 0.0, color, true, true, 1.0F, 35, walls);
                  }

                  double x = Render3D.entityX(entity, partialTick);
                  double centerY = Render3D.entityY(entity, partialTick) + entity.getBbHeight() / 2.0;
                  double z = Render3D.entityZ(entity, partialTick);
                  if (this.names.get()) {
                     Render3D.text(this.label(item, stack), x, centerY + 0.45, z, color, 0.8F, walls);
                  }

                  if (this.tracers.get()) {
                     if (origin == null) {
                        origin = Render3D.crosshair();
                     }

                     Render3D.line(origin.x, origin.y, origin.z, x, centerY, z, color, 1.0F, walls);
                  }
               }
            }
         }
      }
   }

   /** "Name xN", rebuilt only when the stack or its count changes. */
   private String label(ItemEntity item, ItemStack stack) {
      int count = stack.getCount();
      String cached = this.labels.get(item.getId(), stack, count);
      if (cached != null) {
         return cached;
      }

      String name = stack.getHoverName().getString();
      return this.labels.put(item.getId(), stack, count, count > 1 ? name + " x" + count : name);
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
