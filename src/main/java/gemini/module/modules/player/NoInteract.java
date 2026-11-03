package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;

public final class NoInteract extends Module {
   private final ListSetting blocks = this.addSetting(
      new ListSetting(
         "Blocks",
         "Blocks you cannot use",
         ListSetting.Kind.BLOCK,
         "chest",
         "trapped_chest",
         "barrel",
         "ender_chest",
         "furnace",
         "blast_furnace",
         "smoker",
         "crafting_table",
         "anvil",
         "hopper",
         "dispenser",
         "dropper"
      )
   );
   private final ListSetting entities = this.addSetting(
      new ListSetting("Entities", "Mobs you cannot interact with", ListSetting.Kind.ENTITY, "villager", "wandering_trader")
   );
   private final BooleanSetting sneakBypass = this.addSetting(new BooleanSetting("Sneak Bypass", "Holding sneak lets the click through", true));

   public NoInteract() {
      super("No Interact", "Stop accidental clicks on containers and villagers.", Category.PLAYER);
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, level, hand, hit) -> (InteractionResult)(
               level.isClientSide()
                     && this.isEnabled()
                     && (!this.sneakBypass.get() || !player.isShiftKeyDown())
                     && this.blocks.blocks().contains(level.getBlockState(hit.getBlockPos()).getBlock())
                  ? InteractionResult.FAIL
                  : InteractionResult.PASS
            )
         );
      UseEntityCallback.EVENT
         .register(
            (UseEntityCallback)(player, level, hand, entity, hit) -> (InteractionResult)(
               level.isClientSide()
                     && this.isEnabled()
                     && (!this.sneakBypass.get() || !player.isShiftKeyDown())
                     && this.entities.entityTypes().contains(entity.getType())
                  ? InteractionResult.FAIL
                  : InteractionResult.PASS
            )
         );
   }
}
