package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;

public final class EntityOwner extends Module {
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
