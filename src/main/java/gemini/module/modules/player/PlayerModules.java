package gemini.module.modules.player;

import com.mojang.authlib.GameProfile;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

public final class PlayerModules {
   private PlayerModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new PlayerModules.AutoDrop());
      sink.accept(new PlayerModules.AutoMount());
      sink.accept(new PlayerModules.ChestSwap());
      sink.accept(new PlayerModules.FakePlayer());
      sink.accept(new PlayerModules.LiquidInteract());
      sink.accept(new PlayerModules.MiddleClickExtra());
      sink.accept(new PlayerModules.NoInteract());
      sink.accept(new PlayerModules.Rotation());
      sink.accept(new PlayerModules.PotionSpoof());
      sink.accept(new PlayerModules.ExpThrower());
   }

   private static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null && client.level != null && client.gameMode != null && !player.isSpectator() && !player.isDeadOrDying();
   }

   private static boolean useHotbarItem(Minecraft client, LocalPlayer player, Item item) {
      if (player.getOffhandItem().is(item)) {
         client.gameMode.useItem(player, InteractionHand.OFF_HAND);
         return true;
      } else {
         int slot = Inventories.findHotbar(player, stack -> stack.is(item));
         if (slot < 0) {
            return false;
         } else {
            int previous = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.getInventory().setSelectedSlot(previous);
            return true;
         }
      }
   }

   public static final class AutoDrop extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Blacklist drops the listed items, Whitelist drops everything else", "Blacklist", "Blacklist", "Whitelist")
      );
      private final ListSetting items = this.addSetting(
         new ListSetting(
            "Items", "Items the mode applies to", ListSetting.Kind.ITEM, "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "string", "bone"
         )
      );
      private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Include Hotbar", "Also drop from the hotbar", false));
      private final BooleanSetting skipNamed = this.addSetting(new BooleanSetting("Skip Named", "Never throw items you renamed on an anvil", true));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between drops", 2.0, 0.0, 20.0, 1.0));
      private final NumberSetting perTick = this.addSetting(new NumberSetting("Per Tick", "How many stacks to throw in one burst", 1.0, 1.0, 9.0, 1.0));
      private int cooldown;

      public AutoDrop() {
         super("Auto Drop", "Throw unwanted items out of your inventory.", Category.PLAYER);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (PlayerModules.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else {
               boolean blacklist = this.mode.is("Blacklist");
               int thrown = 0;

               for (int index = this.hotbar.get() ? 0 : 9; index < 36; index++) {
                  ItemStack stack = player.getInventory().getItem(index);
                  if (!stack.isEmpty() && this.items.items().contains(stack.getItem()) == blacklist && (!this.skipNamed.get() || stack.getCustomName() == null)
                     )
                   {
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(index), 1, ContainerInput.THROW, player);
                     if (++thrown >= this.perTick.intValue()) {
                        this.cooldown = this.delay.intValue();
                        return;
                     }
                  }
               }

               if (thrown > 0) {
                  this.cooldown = this.delay.intValue();
               }
            }
         }
      }
   }

   public static final class AutoMount extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How close a mount must be", 4.0, 1.5, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between attempts", 10.0, 2.0, 40.0, 1.0));
      private final BooleanSetting boats = this.addSetting(new BooleanSetting("Boats", "Mount boats and rafts", true));
      private final BooleanSetting minecarts = this.addSetting(new BooleanSetting("Minecarts", "Mount minecarts", true));
      private final ListSetting animals = this.addSetting(
         new ListSetting(
            "Animals", "Rideable animals to mount", ListSetting.Kind.ENTITY, "horse", "donkey", "mule", "pig", "strider", "camel", "skeleton_horse", "llama"
         )
      );
      private int cooldown;

      public AutoMount() {
         super("Auto Mount", "Climb into nearby boats, minecarts and rideable animals.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (PlayerModules.ready(client) && !player.isPassenger() && client.gui.screen() == null) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else {
               double limit = this.range.get() * this.range.get();
               Entity best = null;
               double bestDistance = limit;

               for (Entity entity : client.level.entitiesForRendering()) {
                  if (entity.isAlive() && entity.getPassengers().isEmpty() && this.mountable(entity)) {
                     double distance = player.distanceToSqr(entity);
                     if (distance < bestDistance) {
                        bestDistance = distance;
                        best = entity;
                     }
                  }
               }

               if (best != null) {
                  client.gameMode.interact(player, best, new EntityHitResult(best), InteractionHand.MAIN_HAND);
                  this.cooldown = this.delay.intValue();
               }
            }
         }
      }

      private boolean mountable(Entity entity) {
         if (entity instanceof AbstractBoat) {
            return this.boats.get();
         } else {
            return entity instanceof AbstractMinecart ? this.minecarts.get() : this.animals.entityTypes().contains(entity.getType());
         }
      }
   }

   public static final class ChestSwap extends Module {
      private boolean done;

      public ChestSwap() {
         super("Chest Swap", "Swap your chestplate and elytra (bind a key to this).", Category.PLAYER);
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      protected boolean hasListOption() {
         return false;
      }

      @Override
      public boolean showInArrayList() {
         return false;
      }

      @Override
      public void onEnable() {
         this.done = false;
      }

      @Override
      public void onTick() {
         if (!this.done) {
            this.done = true;
            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (PlayerModules.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
               this.swap(client, player);
            }

            this.setEnabled(false, false);
         }
      }

      private void swap(Minecraft client, LocalPlayer player) {
         boolean elytraOn = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
         int index = Inventories.find(player, stack -> elytraOn ? stack.is(ItemTags.CHEST_ARMOR) : stack.is(Items.ELYTRA), false);
         if (index < 0) {
            CommandManager.feedback("§bChest Swap §7no " + (elytraOn ? "chestplate" : "elytra") + " in your inventory.");
         } else {
            int container = player.inventoryMenu.containerId;
            int chestSlot = 6;
            if (index < 9) {
               client.gameMode.handleContainerInput(container, chestSlot, index, ContainerInput.SWAP, player);
            } else {
               int menuSlot = Inventories.menuSlot(index);
               client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
               client.gameMode.handleContainerInput(container, chestSlot, 0, ContainerInput.PICKUP, player);
               if (!player.inventoryMenu.getCarried().isEmpty()) {
                  client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
               }
            }
         }
      }
   }

   public static final class ExpThrower extends Module {
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bottles", 4.0, 1.0, 20.0, 1.0));
      private final BooleanSetting onlyDamaged = this.addSetting(new BooleanSetting("Only When Damaged", "Stop once no enchanted gear needs repairing", true));
      private final BooleanSetting lookDown = this.addSetting(new BooleanSetting("Look Down", "Face the ground so the XP lands at your feet", true));
      private int cooldown;

      public ExpThrower() {
         super("EXP Thrower", "Throw experience bottles to mend your gear.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (PlayerModules.ready(client) && client.gui.screen() == null && (!this.onlyDamaged.get() || needsRepair(player))) {
            if (this.lookDown.get() && Math.abs(player.getXRot() - 90.0F) > 0.5F) {
               player.setXRot(90.0F);
            } else if (this.cooldown > 0) {
               this.cooldown--;
            } else if (PlayerModules.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE)) {
               this.cooldown = this.delay.intValue();
            }
         }
      }

      private static boolean needsRepair(LocalPlayer player) {
         for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
         }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isDamaged() && stack.isEnchanted()) {
               return true;
            }
         }

         return false;
      }
   }

   public static final class FakePlayer extends Module {
      private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Starting health of the dummy", 20.0, 1.0, 40.0, 1.0));
      private final BooleanSetting copyGear = this.addSetting(new BooleanSetting("Copy Gear", "Wear the same armor and items as you", true));
      private final BooleanSetting glow = this.addSetting(new BooleanSetting("Glowing", "Give the dummy a glowing outline", false));
      private static int nextClientId = -1000000;
      private RemotePlayer dummy;

      public FakePlayer() {
         super("Fake Player", "Spawn a dummy player next to you to test combat modules.", Category.PLAYER);
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         RemotePlayer target = this.dummy;
         if (target == null) {
            return false;
         } else {
            return packet instanceof ServerboundAttackPacket attack
               ? attack.entityId() == target.getId()
               : packet instanceof ServerboundInteractPacket interact && interact.entityId() == target.getId();
         }
      }

      @Override
      public void onEnable() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         if (player != null && level != null) {
            this.dummy = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), "Fake_Player"));
            this.dummy.setId(nextClientId--);
            this.dummy.snapTo(player.position().add(player.getLookAngle().multiply(2.0, 0.0, 2.0)), player.getYRot() + 180.0F, 0.0F);
            this.dummy.setYHeadRot(this.dummy.getYRot());
            this.dummy.setHealth(this.health.floatValue());
            if (this.copyGear.get()) {
               for (EquipmentSlot slot : EquipmentSlot.values()) {
                  this.dummy.setItemSlot(slot, player.getItemBySlot(slot).copy());
               }
            }

            level.addEntity(this.dummy);
            CommandManager.feedback("§bFake Player §7spawned. Hits are simulated on your side only.");
         }
      }

      @Override
      public void onDisable() {
         ClientLevel level = Minecraft.getInstance().level;
         if (this.dummy != null && level != null) {
            level.removeEntity(this.dummy.getId(), RemovalReason.DISCARDED);
         }

         this.dummy = null;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.dummy != null) {
            if (this.dummy.level() == client.level && !this.dummy.isRemoved()) {
               this.dummy.setGlowingTag(this.glow.get());
            } else {
               this.dummy = null;
               this.setEnabled(false, false);
            }
         }
      }

      @Override
      public void onAttack(Entity target) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (this.dummy != null && target == this.dummy && player != null) {
            float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + player.getAttackStrengthScale(0.5F) * 0.8F);
            float remaining = this.dummy.getHealth() - damage;
            this.dummy.animateHurt(player.getYRot());
            this.dummy.hurtTime = 10;
            this.dummy.hurtDuration = 10;
            if (remaining <= 0.0F) {
               client.particleEngine.createTrackingEmitter(this.dummy, ParticleTypes.TOTEM_OF_UNDYING, 30);
               this.dummy.setHealth(this.health.floatValue());
            } else {
               this.dummy.setHealth(remaining);
            }
         }
      }

      @Override
      public String info() {
         return this.dummy == null ? null : String.format("%.0f", this.dummy.getHealth());
      }
   }

   public static final class LiquidInteract extends Module {
      public static PlayerModules.LiquidInteract instance;

      public LiquidInteract() {
         super("Liquid Interact", "Target liquids with your crosshair so you can interact with them.", Category.PLAYER);
         instance = this;
      }

      public static boolean active() {
         PlayerModules.LiquidInteract module = instance;
         return module != null && module.isEnabled();
      }
   }

   public static final class MiddleClickExtra extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Action", "What a middle click does", "Ender Pearl", "Ender Pearl", "Firework", "XP Bottle", "Golden Apple")
      );
      private boolean previous;

      public MiddleClickExtra() {
         super("Middle Click Extra", "Give the middle mouse button a useful job.", Category.PLAYER);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (PlayerModules.ready(client) && client.gui.screen() == null) {
            boolean pressed = client.mouseHandler.isMiddlePressed();
            if (pressed && !this.previous) {
               this.act(client, player);
            }

            this.previous = pressed;
         } else {
            this.previous = false;
         }
      }

      private void act(Minecraft client, LocalPlayer player) {
         String var3 = this.mode.get();
         switch (var3) {
            case "Golden Apple":
               PlayerModules.useHotbarItem(client, player, Items.GOLDEN_APPLE);
               break;
            case "Firework":
               PlayerModules.useHotbarItem(client, player, Items.FIREWORK_ROCKET);
               break;
            case "XP Bottle":
               PlayerModules.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE);
               break;
            default:
               PlayerModules.useHotbarItem(client, player, Items.ENDER_PEARL);
         }
      }
   }

   public static final class NoInteract extends Module {
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

   public static final class PotionSpoof extends Module {
      private final Map<Holder<MobEffect>, BooleanSetting> effects = new LinkedHashMap<>();
      private final NumberSetting level = this.addSetting(new NumberSetting("Level", "Strength of the faked effects", 1.0, 1.0, 5.0, 1.0));

      public PotionSpoof() {
         super("Potion Spoof", "Apply helpful potion effects on your client.", Category.PLAYER);
         this.add("Night Vision", "See in the dark", MobEffects.NIGHT_VISION, true);
         this.add("Water Breathing", "Show the underwater breathing effect", MobEffects.WATER_BREATHING, false);
         this.add("Fire Resistance", "Show the fire resistance effect", MobEffects.FIRE_RESISTANCE, false);
         this.add("Conduit Power", "Clear underwater vision", MobEffects.CONDUIT_POWER, false);
         this.add("Dolphins Grace", "Swim quickly", MobEffects.DOLPHINS_GRACE, false);
         this.add("Haste", "Mine faster on the client", MobEffects.HASTE, false);
         this.add("Speed", "Move faster on the client", MobEffects.SPEED, false);
         this.add("Jump Boost", "Jump higher on the client", MobEffects.JUMP_BOOST, false);
         this.add("Slow Falling", "Fall slowly on the client", MobEffects.SLOW_FALLING, false);
      }

      private void add(String name, String description, Holder<MobEffect> effect, boolean enabled) {
         this.effects.put(effect, this.addSetting(new BooleanSetting(name, description, enabled)));
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            int amplifier = this.level.intValue() - 1;

            for (Entry<Holder<MobEffect>, BooleanSetting> entry : this.effects.entrySet()) {
               MobEffectInstance current = player.getEffect(entry.getKey());
               boolean ours = current != null && !current.isVisible();
               if (entry.getValue().get()) {
                  if (current == null || ours && (current.getDuration() < 200 || current.getAmplifier() != amplifier)) {
                     player.addEffect(new MobEffectInstance(entry.getKey(), 600, amplifier, false, false, false));
                  }
               } else if (ours) {
                  player.removeEffectNoUpdate(entry.getKey());
               }
            }
         }
      }

      @Override
      public void onDisable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            List<Holder<MobEffect>> mine = new ArrayList<>();

            for (Holder<MobEffect> effect : this.effects.keySet()) {
               MobEffectInstance current = player.getEffect(effect);
               if (current != null && !current.isVisible()) {
                  mine.add(effect);
               }
            }

            mine.forEach(player::removeEffectNoUpdate);
         }
      }
   }

   public static final class Rotation extends Module {
      private final ModeSetting yawMode = this.addSetting(
         new ModeSetting("Yaw", "None leaves it alone, Snap rounds to a step, Fixed holds an angle", "Snap", "None", "Snap", "Fixed")
      );
      private final NumberSetting step = this.addSetting(
         new NumberSetting("Snap Step", "Degrees between snap angles", 45.0, 5.0, 90.0, 5.0).showIf(() -> this.yawMode.is("Snap"))
      );
      private final NumberSetting yawValue = this.addSetting(
         new NumberSetting("Yaw Angle", "Angle to hold", 0.0, -180.0, 180.0, 5.0).showIf(() -> this.yawMode.is("Fixed"))
      );
      private final ModeSetting pitchMode = this.addSetting(new ModeSetting("Pitch", "None leaves it alone, Fixed holds an angle", "None", "None", "Fixed"));
      private final NumberSetting pitchValue = this.addSetting(
         new NumberSetting("Pitch Angle", "Angle to hold", 0.0, -90.0, 90.0, 5.0).showIf(() -> this.pitchMode.is("Fixed"))
      );

      public Rotation() {
         super("Rotation", "Snap your yaw to fixed steps or lock your view angles.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (PlayerModules.ready(client) && client.gui.screen() == null) {
            float yaw = player.getYRot();
            if (this.yawMode.is("Snap")) {
               yaw = Math.round(yaw / this.step.floatValue()) * this.step.floatValue();
            } else if (this.yawMode.is("Fixed")) {
               yaw = this.yawValue.floatValue();
            }

            player.setYRot(yaw);
            player.yHeadRot = yaw;
            if (this.pitchMode.is("Fixed")) {
               player.setXRot(this.pitchValue.floatValue());
            }
         }
      }
   }
}
