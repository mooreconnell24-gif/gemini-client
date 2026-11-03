package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ItemMagnet extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you that drops are pulled from", 8.0, 2.0, 24.0, 1.0));

   public ItemMagnet() {
      super("Item Magnet", "Pull your own dropped items toward you.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      IntegratedServer server = client.getSingleplayerServer();
      LocalPlayer player = client.player;
      if (SingleplayerGate.allowed() && server != null && player != null) {
         UUID id = player.getUUID();
         double reach = this.radius.get();
         server.execute(() -> pull(server, id, reach));
      }
   }

   private static void pull(IntegratedServer server, UUID id, double reach) {
      ServerPlayer serverPlayer = server.getPlayerList().getPlayer(id);
      if (serverPlayer != null) {
         AABB area = serverPlayer.getBoundingBox().inflate(reach);

         for (ItemEntity item : serverPlayer.level().getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive() && !entity.isRemoved())) {
            Vec3 delta = serverPlayer.position().add(0.0, 0.6, 0.0).subtract(item.position());
            double distance = delta.length();
            if (!(distance < 0.8) && !(distance > reach)) {
               item.setDeltaMovement(delta.scale(Math.min(0.45, 1.4 / distance)));
               item.setPickUpDelay(0);
            }
         }
      }
   }

   @Override
   public String info() {
      return this.radius.intValue() + "m";
   }
}
