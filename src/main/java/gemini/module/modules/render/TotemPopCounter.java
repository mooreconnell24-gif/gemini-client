package gemini.module.modules.render;

import java.util.List;
import java.util.ArrayList;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.HudOverlay;
import gemini.util.InfoText;
import gemini.util.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Announces Totem of Undying pops and keeps the per-entity counts that Nametags and TargetHUD show.
 *
 * <p>The counting itself is {@link TotemPops}, which runs whether or not this module is on; this module decides
 * how the counts are announced and, while it is on, when they are reset.
 */
public final class TotemPopCounter extends Module {
   private final BooleanSetting chat = this.addSetting(new BooleanSetting("Chat", "Write each pop in chat", true));
   private final BooleanSetting toast = this.addSetting(new BooleanSetting("Toast", "Show each pop as a notification", true));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Self", "Also announce your own pops", false));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players Only", "Only announce players (mobs are still counted)", false));
   private final BooleanSetting announceDeath = this.addSetting(new BooleanSetting("Announce Death", "Say \"X died after N pops\" when someone who popped dies", true));
   private final ModeSetting reset = this.addSetting(
      new ModeSetting("Reset", "When an entity's count goes back to zero", "On Death", "On Death", "When Gone", "On World Change")
   );

   private final InfoText infoText = new InfoText();

   public TotemPopCounter() {
      super("Totem Pop Counter", "Counts totem pops for each entity, announces them and shows the counts on Nametags and TargetHUD.", Category.RENDER);
      TotemPops.setPolicy(this::policy);
      TotemPops.addListener(new TotemPops.Listener() {
         @Override
         public void onPop(LivingEntity entity, int count) {
            TotemPopCounter.this.popped(entity, count);
         }

         @Override
         public void onDeath(LivingEntity entity, int pops) {
            TotemPopCounter.this.died(entity, pops);
         }
      });
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Notifier");
      return names;
   }

   private int policy() {
      if (!this.isEnabled()) {
         return TotemPops.RESET_ON_DEATH;
      }

      return switch (this.reset.get()) {
         case "When Gone" -> TotemPops.RESET_WHEN_GONE;
         case "On World Change" -> TotemPops.RESET_ON_WORLD_CHANGE;
         default -> TotemPops.RESET_ON_DEATH;
      };
   }

   /** Pops recorded for the entity since its count was last reset. */
   public static int pops(Entity entity) {
      return TotemPops.count(entity);
   }

   /** Whether this module is on and would announce a pop or death of {@code entity} itself. */
   public boolean announces(LivingEntity entity) {
      if (!this.isEnabled()) {
         return false;
      }

      if (entity == Minecraft.getInstance().player) {
         return this.self.get();
      }

      return !this.players.get() || entity instanceof Player;
   }

   private void popped(LivingEntity entity, int count) {
      if (!this.announces(entity)) {
         return;
      }

      String name = entity.getName().getString();
      String times = amount(count, "totem");
      if (this.toast.get()) {
         HudOverlay.toast("Pop: " + name, name + " has popped " + times, entity == Minecraft.getInstance().player ? HudOverlay.Severity.ERROR : HudOverlay.Severity.WARNING);
      }

      if (this.chat.get()) {
         CommandManager.feedback("§b[Pops] §f" + name + " §7popped §e" + times + "§7.");
      }
   }

   private void died(LivingEntity entity, int pops) {
      if (!this.announceDeath.get() || !this.announces(entity) || pops <= 0) {
         return;
      }

      String name = entity.getName().getString();
      String after = amount(pops, "pop");
      if (this.toast.get()) {
         HudOverlay.toast("Pop: " + name, name + " died after " + after, HudOverlay.Severity.SUCCESS);
      }

      if (this.chat.get()) {
         CommandManager.feedback("§b[Pops] §f" + name + " §7died after §e" + after + "§7.");
      }
   }

   /** "1 totem", "3 totems": a count with its noun, plural unless the count is one. */
   static String amount(int count, String noun) {
      return count + " " + (count == 1 ? noun : noun + "s");
   }

   @Override
   public String info() {
      int tracked = TotemPops.tracked();
      return tracked == 0 ? null : this.infoText.whole(tracked, "");
   }
}
