package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class Teams extends Module {
   public static Teams instance;
   private final BooleanSetting scoreboardTeam = this.addSetting(new BooleanSetting("Scoreboard Team", "Check vanilla scoreboard team", true));
   private final BooleanSetting nameColor = this.addSetting(new BooleanSetting("Name Prefix", "Check matching display name color", true));

   public Teams() {
      super("Teams", "Skip teammates by scoreboard team or name color.", Category.COMBAT);
      instance = this;
   }

   public boolean isTeammate(LocalPlayer self, Entity other) {
      if (other instanceof Player p) {
         if (this.scoreboardTeam.get() && self.getTeam() != null && self.getTeam().isAlliedTo(p.getTeam())) {
            return true;
         } else {
            return this.nameColor.get() && self.getDisplayName() != null && p.getDisplayName() != null
               ? self.getDisplayName().getStyle().getColor() != null
                  && self.getDisplayName().getStyle().getColor().equals(p.getDisplayName().getStyle().getColor())
               : false;
         }
      } else {
         return false;
      }
   }
}
