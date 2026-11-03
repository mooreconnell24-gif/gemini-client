package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudOverlay;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class Notifier extends Module {
   private final BooleanSetting visualRange = this.addSetting(new BooleanSetting("Visual Range", "Alert when players enter or leave render distance", true));
   private final BooleanSetting totemPops = this.addSetting(new BooleanSetting("Totem Pops", "Alert when a player pops a Totem of Undying", true));
   private final Set<UUID> seenPlayers = new HashSet<>();

   public Notifier() {
      super("Notifier", "Alert on totem pops and visual range changes.", Category.CLIENT);
   }

   @Override
   public void onDisable() {
      this.seenPlayers.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer self = client.player;
      if (this.visualRange.get() && self != null && client.level != null) {
         Set<UUID> current = new HashSet<>();

         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != self && entity instanceof Player p) {
               current.add(p.getUUID());
               if (this.seenPlayers.add(p.getUUID())) {
                  HudOverlay.pushNotification("Visual Range", p.getName().getString() + " entered range", true);
                  CommandManager.feedback("§b[Notifier] §f" + p.getName().getString() + " §7entered visual range.");
               }
            }
         }

         this.seenPlayers.retainAll(current);
      }
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      Minecraft client = Minecraft.getInstance();
      if (this.totemPops.get()
         && client.level != null
         && packet instanceof ClientboundEntityEventPacket evt
         && evt.getEventId() == 35
         && evt.getEntity(client.level) instanceof Player p) {
         HudOverlay.pushNotification("Totem Pop", p.getName().getString() + " popped a totem!", true);
         CommandManager.feedback("§b[Notifier] §f" + p.getName().getString() + " §7popped a §eTotem of Undying§7!");
      }

      return false;
   }
}
