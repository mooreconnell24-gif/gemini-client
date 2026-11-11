package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.BlockPlacer;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Burrow extends Module {
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward in packets", true));

   public Burrow() {
      super("Burrow", "Lag-place obsidian or ender chest inside your feet.", Category.COMBAT);
   }

   @Override
   public boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientPacketListener conn = client.getConnection();
      if (ClientState.ready(client) && conn != null && player.onGround()) {
         BlockPos feet = player.blockPosition();
         Predicate<ItemStack> burrowBlock = CombatHelpers.obsidianOrResistant().or(s -> s.is(Items.ANVIL) || s.is(Items.CHIPPED_ANVIL));
         int slot = Inventories.findHotbar(player, burrowBlock);
         if (slot >= 0) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            conn.send(new Pos(x, y + 0.41999998688698, z, true, false));
            conn.send(new Pos(x, y + 0.7531999805212, z, true, false));
            conn.send(new Pos(x, y + 1.00133597911214, z, true, false));
            conn.send(new Pos(x, y + 1.16610926093821, z, true, false));
            if (this.rotate.get()) {
               RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, true, ActionPriority.COMBAT, 5.0F);
            }

            BlockPlacer.place(client, feet, burrowBlock, true, true, true, false);
            conn.send(new Pos(x, y + 2.34, z, false, false));
         }
      }

      this.setEnabled(false, false);
   }
}
