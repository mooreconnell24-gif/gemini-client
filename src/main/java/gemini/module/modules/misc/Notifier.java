package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.render.TotemPopCounter;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudOverlay;
import gemini.util.TotemPops;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Notifier extends Module {
   private final BooleanSetting visualRange = this.addSetting(
      new BooleanSetting("Visual Range", "Alert when players enter or leave render distance", true).onChange(this::forgetRange)
   );
   private final BooleanSetting totemPops = this.addSetting(
      new BooleanSetting("Totem Pops", "Alert when a player pops a Totem of Undying (left to Totem Pop Counter while that announces it)", true)
   );
   /** Players in range at the last tick, with the name to report when they leave. */
   private final Map<UUID, String> seenPlayers = new HashMap<>();
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
      this.forgetRange();
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.forgetRange();
   }

   private void forgetRange() {
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
               UUID id = p.getUUID();
               now.add(id);
               if (!this.seenPlayers.containsKey(id)) {
                  String name = p.getName().getString();
                  this.seenPlayers.put(id, name);
                  HudOverlay.toast("Visual Range", name + " entered range", HudOverlay.Severity.INFO);
                  CommandManager.feedback("§b[Notifier] §f" + name + " §7entered visual range.");
               }
            }
         }

         Iterator<Map.Entry<UUID, String>> it = this.seenPlayers.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<UUID, String> seen = it.next();
            if (!now.contains(seen.getKey())) {
               it.remove();
               HudOverlay.toast("Visual Range", seen.getValue() + " left range", HudOverlay.Severity.INFO);
               CommandManager.feedback("§b[Notifier] §f" + seen.getValue() + " §7left visual range.");
            }
         }
      }
   }

   private void popped(LivingEntity entity, int count) {
      if (this.isEnabled() && this.totemPops.get() && entity instanceof Player p && !counterAnnounces(entity)) {
         String name = p.getName().getString();
         String times = count == 1 ? "once" : count + " times";
         HudOverlay.toast("Totem Pop", name + " popped a totem (" + times + ")", HudOverlay.Severity.WARNING);
         CommandManager.feedback("§b[Notifier] §f" + name + " §7popped a §eTotem of Undying§7 (" + times + ").");
      }
   }

   /** True when Totem Pop Counter already announces this pop, so it is not announced twice. */
   private static boolean counterAnnounces(LivingEntity entity) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || gemini.modules() == null) {
         return false;
      }

      TotemPopCounter counter = gemini.modules().byClass(TotemPopCounter.class).orElse(null);
      return counter != null && counter.announces(entity);
   }
}
