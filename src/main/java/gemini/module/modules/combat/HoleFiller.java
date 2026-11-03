package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Holes;
import gemini.util.PlaceSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public final class HoleFiller extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.8);
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Hole scan radius", 5.0, 2.0, 6.0, 1.0));
   private final BooleanSetting smart = this.addSetting(new BooleanSetting("Smart Only", "Only fill holes close to an enemy", true));

   public HoleFiller() {
      super("HoleFiller", "Fill nearby safe holes before enemies can enter them.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null && this.placer.ready()) {
         LocalPlayer player = client.player;
         LivingEntity enemy = CombatHelpers.nearestEnemy(client, player, 8.0, true);
         if (this.smart.get() && enemy == null) {
            return;
         }

         List<BlockPos> holes = Holes.around(client.level, player.blockPosition(), this.radius.intValue(), 2);
         List<BlockPos> targets = new ArrayList<>();

         for (BlockPos hole : holes) {
            if (!hole.equals(player.blockPosition()) && (enemy == null || enemy.blockPosition().distSqr(hole) <= 9.0)) {
               targets.add(hole);
            }
         }

         this.placer.place(client, targets, CombatHelpers.obsidianOrResistant());
      }
   }
}
