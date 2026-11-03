package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;

public final class InventoryMoveModule extends Module {
   private final BooleanSetting sprint = this.addSetting(new BooleanSetting("Sprint", "Keep sprinting while a menu is open", true));
   private final BooleanSetting jump = this.addSetting(new BooleanSetting("Jump", "Allow jumping with the jump key", true));
   private final BooleanSetting sneak = this.addSetting(new BooleanSetting("Sneak", "Allow sneaking with the sneak key", false));
   private boolean driving;

   public InventoryMoveModule() {
      super("InventoryMove", "Walk and jump while inventories are open.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      this.release(Minecraft.getInstance());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Screen screen = client.gui.screen();
      if (player != null && this.applies(screen)) {
         // Feed the physical key state into the normal key mappings so vanilla movement (speed effects,
         // soul sand, sneaking, collisions) does the work instead of hand-made velocity.
         this.driving = true;
         mirror(client.options.keyUp);
         mirror(client.options.keyDown);
         mirror(client.options.keyLeft);
         mirror(client.options.keyRight);
         client.options.keyJump.setDown(this.jump.get() && MoveUtil.physicalKeyDown(client.options.keyJump));
         client.options.keyShift.setDown(this.sneak.get() && MoveUtil.physicalKeyDown(client.options.keyShift));
         client.options.keySprint.setDown(this.sprint.get() && MoveUtil.physicalKeyDown(client.options.keySprint));
         if (this.sprint.get() && MoveUtil.physicalKeyDown(client.options.keyUp) && !player.isSprinting() && player.getFoodData().getFoodLevel() > 6) {
            player.setSprinting(true);
         }
      } else if (this.driving) {
         this.release(client);
      }
   }

   private boolean applies(Screen screen) {
      if (!(screen instanceof AbstractContainerScreen)) {
         return false;
      } else {
         // Screens with a text box would turn typing into walking.
         String name = screen.getClass().getSimpleName();
         return !name.contains("Creative") && !name.contains("Anvil") && !name.contains("Sign") && !name.contains("Book");
      }
   }

   private void release(Minecraft client) {
      if (this.driving && client.options != null) {
         this.driving = false;
         mirror(client.options.keyUp);
         mirror(client.options.keyDown);
         mirror(client.options.keyLeft);
         mirror(client.options.keyRight);
         mirror(client.options.keyJump);
         mirror(client.options.keyShift);
         mirror(client.options.keySprint);
      }
   }

   private static void mirror(KeyMapping mapping) {
      mapping.setDown(MoveUtil.physicalKeyDown(mapping));
   }
}
