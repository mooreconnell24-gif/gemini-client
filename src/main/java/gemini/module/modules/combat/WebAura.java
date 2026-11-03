package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.PlaceSettings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;

public final class WebAura extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 1, 2, 4.5);
   private final NumberSetting range = this.addSetting(new NumberSetting("Enemy Range", "Target distance for cobwebs", 4.5, 2.0, 6.0, 0.5));

   public WebAura() {
      super("WebAura", "Place cobwebs at enemy feet to stall movement.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null && this.placer.ready()) {
         LivingEntity enemy = CombatHelpers.nearestEnemy(client, client.player, this.range.get(), true);
         if (enemy != null) {
            this.placer
               .place(
                  client,
                  List.of(enemy.blockPosition(), enemy.blockPosition().above()),
                  stack -> stack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.COBWEB,
                  false
               );
         }
      }
   }
}
