package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class ClickTp extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block you can teleport to", 60.0, 5.0, 200.0, 5.0));
   private final ModeSetting trigger = this.addSetting(
      new ModeSetting("Trigger", "What starts the teleport", "Middle Click", "Middle Click", "Sneak + Right Click")
   );
   private final BooleanSetting steps = this.addSetting(
      new BooleanSetting("Step Packets", "Send the route in short hops so the server accepts long teleports", true)
   );
   private boolean previous;

   public ClickTp() {
      super("Click TP", "Teleport to the block you are looking at.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && client.gui.screen() == null) {
         boolean pressed = this.trigger.is("Middle Click")
            ? client.mouseHandler.isMiddlePressed()
            : client.options.keyUse.isDown() && player.isShiftKeyDown();
         if (pressed && !this.previous) {
            this.teleport(client, player);
         }

         this.previous = pressed;
      } else {
         this.previous = false;
      }
   }

   private void teleport(Minecraft client, LocalPlayer player) {
      Vec3 from = player.getEyePosition();
      Vec3 to = from.add(player.getLookAngle().scale(this.range.get()));
      BlockHitResult hit = client.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
      if (hit.getType() == Type.MISS) {
         CommandManager.feedback("§bClick TP §7no block in range.");
      } else {
         BlockPos above = hit.getBlockPos().relative(hit.getDirection());
         Vec3 destination = null;

         for (int lift = 0; lift < 3 && destination == null; lift++) {
            double y = hit.getDirection() == Direction.UP ? hit.getBlockPos().getY() + 1.0 + lift : above.getY() + lift;
            Vec3 candidate = new Vec3(above.getX() + 0.5, y, above.getZ() + 0.5);
            Vec3 delta = candidate.subtract(player.position());
            if (client.level.noCollision(player, player.getBoundingBox().move(delta))) {
               destination = candidate;
            }
         }

         if (destination == null) {
            CommandManager.feedback("§bClick TP §7there is no room to stand there.");
         } else {
            MoveUtil.teleport(client, player, destination, this.steps.get());
         }
      }
   }
}
