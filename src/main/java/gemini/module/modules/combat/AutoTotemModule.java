package gemini.module.modules.combat;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.Damage;
import gemini.util.Inventories;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Owns the offhand. With "Item" on Totem it simply keeps a totem there. With any other item it keeps that item
 * there while you are safe and swaps a totem in the moment you are in danger: low health, a fall that would kill,
 * or gliding with an elytra. Other modules ask {@link gemini.util.OffhandClaim} before touching the offhand.
 */
public final class AutoTotemModule extends Module {
   private final ModeSetting item = this.addSetting(
      new ModeSetting(
         "Item",
         "What your offhand holds while you are safe; Totem always holds a totem, Nothing leaves the offhand alone until you are in danger",
         "Totem",
         "Totem",
         "Nothing",
         "Golden Apple",
         "Enchanted Golden Apple",
         "End Crystal",
         "Shield"
      )
   );
   private final NumberSetting health = this.addSetting(
      new NumberSetting("Health", "Switch to a totem at or below this health, absorption included", 10.0, 1.0, 20.0, 1.0).showIf(this::swapsOnDanger)
   );
   private final BooleanSetting falling = this.addSetting(
      new BooleanSetting("Lethal Falls", "Switch to a totem while you are falling far enough to die", true).showIf(this::swapsOnDanger)
   );
   private final BooleanSetting gliding = this.addSetting(
      new BooleanSetting("While Gliding", "Switch to a totem while you fly with an elytra", true).showIf(this::swapsOnDanger)
   );
   private final BooleanSetting swordGapple = this.addSetting(
      new BooleanSetting("Sword Gapple", "Hold a golden apple while you hold the use button with a sword in your hand, so you can eat without switching slots", false)
         .showIf(this::swapsOnDanger)
   );
   private final BooleanSetting replace = this.addSetting(new BooleanSetting("Replace Offhand", "Swap even when the offhand already holds another item", true));
   private final BooleanSetting hotbarOnly = this.addSetting(new BooleanSetting("Hotbar Only", "Only take items from the hotbar", false));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between offhand swaps", 2.0, 0.0, 20.0, 1.0));
   private int cooldown;

   public AutoTotemModule() {
      super("Auto Totem", "Keeps a totem of undying in your offhand, or another item that gives way to a totem when you are in danger.", Category.COMBAT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Offhand");
      return names;
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public String info() {
      return this.item.get();
   }

   private boolean swapsOnDanger() {
      return !this.item.is("Totem");
   }

   /** Whether a preferred item other than a totem is kept in the offhand. */
   public boolean keepsItem() {
      return !this.item.is("Totem") && !this.item.is("Nothing");
   }

   private boolean inDanger(LocalPlayer player) {
      if (this.item.is("Totem") || Damage.health(player) <= this.health.floatValue()) {
         return true;
      } else if (this.gliding.get() && player.isFallFlying()) {
         return true;
      } else {
         // Fall damage is the distance fallen minus three blocks; this is the unarmoured worst case.
         return this.falling.get() && !player.isFallFlying() && !player.isInWater() && player.fallDistance - 3.0 >= Damage.health(player);
      }
   }

   /**
    * Whether the offhand is reserved for a totem right now: the player is in danger and a totem is there or can be
    * moved there. With no totem left the reservation is dropped, so the preferred item is used and other modules
    * (Auto Mend and the rest) are not locked out by a module that has nothing to put there.
    */
   public boolean wantsTotem(LocalPlayer player) {
      if (player == null || !this.inDanger(player)) {
         return false;
      }

      ItemStack offhand = player.getOffhandItem();
      return offhand.is(Items.TOTEM_OF_UNDYING) || (this.replace.get() || offhand.isEmpty()) && this.find(player, Items.TOTEM_OF_UNDYING) >= 0;
   }

   @Override
   public void onEnable() {
      this.cooldown = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client) || player.isSpectator() || player.isDeadOrDying() || !Inventories.inventoryOpenOrClosed(player)) {
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      Item wanted = this.wantedItem(client, player);
      ItemStack offhand = player.getOffhandItem();
      if (wanted == null || offhand.is(wanted) || !this.replace.get() && !offhand.isEmpty()) {
         return;
      }

      int index = this.find(player, wanted);
      if (index < 0 && (wanted == Items.ENCHANTED_GOLDEN_APPLE || wanted == Items.GOLDEN_APPLE)) {
         // Either kind of golden apple will do when the chosen one has run out.
         Item other = wanted == Items.GOLDEN_APPLE ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
         if (offhand.is(other)) {
            return;
         }

         index = this.find(player, other);
      }

      if (index >= 0) {
         this.cooldown = this.delay.intValue();
         Inventories.swapToOffhand(client, player, index);
      }
   }

   private int find(LocalPlayer player, Item wanted) {
      return Inventories.find(player, stack -> stack.is(wanted), this.hotbarOnly.get());
   }

   /** The item the offhand should hold this tick, or null to leave it as it is. */
   private Item wantedItem(Minecraft client, LocalPlayer player) {
      if (this.wantsTotem(player)) {
         return Items.TOTEM_OF_UNDYING;
      } else if (this.item.is("Totem")) {
         // In danger by definition, but no totem is left: nothing else is put there.
         return null;
      } else if (this.swordGapple.get() && client.options.keyUse.isDown() && player.getMainHandItem().is(ItemTags.SWORDS)) {
         return Items.ENCHANTED_GOLDEN_APPLE;
      }

      return switch (this.item.get()) {
         case "Golden Apple" -> Items.GOLDEN_APPLE;
         case "Enchanted Golden Apple" -> Items.ENCHANTED_GOLDEN_APPLE;
         case "End Crystal" -> Items.END_CRYSTAL;
         case "Shield" -> Items.SHIELD;
         default -> null;
      };
   }
}
