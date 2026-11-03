package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BedAura extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Bed placement and detonation reach", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bed actions", 3.0, 1.0, 15.0, 1.0));
   private int wait;

   public BedAura() {
      super("BedAura", "Place and detonate beds near enemies in Nether or End.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null && client.level.dimension() != Level.OVERWORLD && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         LivingEntity enemy = CombatHelpers.nearestEnemy(client, player, this.range.get(), true);
         if (enemy != null) {
            BlockPos pos = enemy.blockPosition().above();
            if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
               RotationManager.request(player, RotationManager.toward(player, pos), 90.0F, true, 8);
               CombatHelpers.click(client, player, pos, Direction.UP);
            } else {
               BlockPlacer.place(client, pos, stack -> stack.is(ItemTags.BEDS), true, true, true, false);
            }
         }
      }
   }
}
