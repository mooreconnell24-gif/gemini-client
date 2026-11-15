package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudOverlay;
import gemini.util.TotemPops;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Notifier extends Module {
   private final BooleanSetting visualRange = this.addSetting(new BooleanSetting("Visual Range", "Alert when players enter or leave render distance", true));
   private final BooleanSetting totemPops = this.addSetting(new BooleanSetting("Totem Pops", "Alert when a player pops a Totem of Undying", true));
   private final Set<UUID> seenPlayers = new HashSet<>();
   private final Set<UUID> current = new HashSet<>();

   public Notifier() {
      super("Notifier", "Alert on totem pops and visual range changes.", Category.CLIENT);
      // Pops come from the shared tracker, so the count shown here matches Nametags and TargetHUD.
      TotemPops.addListener(new TotemPops.Listener() {
         @Override
         public void onPop(LivingEntity entity, int count) {
            Notifier.this.popped(entity, count);
         }

         @Override
         public void onDeath(LivingEntity entity, int pops) {
         }
      });
   }

   @Override
   public void onDisable() {
      this.seenPlayers.clear();
      this.current.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer self = client.player;
      if (this.visualRange.get() && self != null && client.level != null) {
         Set<UUID> now = this.current;
         now.clear();
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != self && entity instanceof Player p) {
               now.add(p.getUUID());
               if (this.seenPlayers.add(p.getUUID())) {
                  HudOverlay.toast("Visual Range", p.getName().getString() + " entered range", HudOverlay.Severity.INFO);
                  CommandManager.feedback("§b[Notifier] §f" + p.getName().getString() + " §7entered visual range.");
               }
            }
         }

         this.seenPlayers.retainAll(now);
      }
   }

   private void popped(LivingEntity entity, int count) {
      if (this.isEnabled() && this.totemPops.get() && entity instanceof Player p) {
         String name = p.getName().getString();
         String times = count == 1 ? "once" : count + " times";
         HudOverlay.toast("Totem Pop", name + " popped a totem (" + times + ")", HudOverlay.Severity.WARNING);
         CommandManager.feedback("§b[Notifier] §f" + name + " §7popped a §eTotem of Undying§7 (" + times + ").");
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.seenPlayers.clear();
      this.current.clear();
   }
}
