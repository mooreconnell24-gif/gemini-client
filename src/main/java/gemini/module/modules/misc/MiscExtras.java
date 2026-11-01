package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.util.Damage;
import gemini.util.RotationManager;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class MiscExtras {
   private MiscExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new MiscExtras.DeathCoords());
      sink.accept(new MiscExtras.Presets());
      sink.accept(new MiscExtras.Spammer());
      sink.accept(new MiscExtras.AutoGG());
      sink.accept(new MiscExtras.Notifier());
      sink.accept(new MiscExtras.Teams());
      sink.accept(new MiscExtras.Derp());
      sink.accept(new MiscExtras.AutoLeave());
      sink.accept(new MiscExtras.ResetVL());
      sink.accept(new MiscExtras.FakeLag());
   }

   static String randomSuffix(Random random) {
      StringBuilder builder = new StringBuilder(" [");

      for (int i = 0; i < 4; i++) {
         builder.append((char)(97 + random.nextInt(26)));
      }

      return builder.append(']').toString();
   }

   public static final class AutoGG extends Module {
      private final ListSetting phrase = this.addSetting(new ListSetting("Message", "GG text to send", ListSetting.Kind.TEXT, "GG"));
      private Entity lastAttacked;
      private int timeout;

      public AutoGG() {
         super("AutoGG", "Automatically say GG when a player you recently fought dies.", Category.CLIENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onAttack(Entity target) {
         if (target instanceof Player) {
            this.lastAttacked = target;
            this.timeout = 100;
         }
      }

      @Override
      public void onTick() {
         if (this.timeout > 0) {
            this.timeout--;
            if (this.lastAttacked != null && (!this.lastAttacked.isAlive() || this.lastAttacked.isRemoved())) {
               String msg = this.phrase.isEmpty() ? "GG" : this.phrase.get().getFirst();
               CommandManager.sendRaw(msg);
               this.lastAttacked = null;
               this.timeout = 0;
            }
         }
      }
   }

   public static final class AutoLeave extends Module {
      private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Leave when health drops at or below this", 6.0, 1.0, 16.0, 1.0));
      private final ModeSetting action = this.addSetting(new ModeSetting("Action", "How to escape", "HubCommand", "HubCommand", "SpawnCommand"));

      public AutoLeave() {
         super("AutoLeave", "Automatically send /hub or /spawn when your health drops critically low.", Category.COMBAT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && Damage.health(player) <= this.health.floatValue()) {
            CommandManager.sendRaw(this.action.is("HubCommand") ? "/hub" : "/spawn");
            this.setEnabled(false);
         }
      }
   }

   public static final class DeathCoords extends Module {
      private boolean announced;

      public DeathCoords() {
         super("Death Coords", "Write your death coordinates in client chat.", Category.CLIENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null) {
            if (!player.isDeadOrDying()) {
               this.announced = false;
            } else if (!this.announced) {
               this.announced = true;
               CommandManager.feedback(
                  "§bDeath Coords §7" + player.blockPosition().getX() + " " + player.blockPosition().getY() + " " + player.blockPosition().getZ()
               );
            }
         }
      }
   }

   public static final class Derp extends Module {
      private final NumberSetting spinSpeed = this.addSetting(new NumberSetting("Spin Speed", "Degrees per tick to spin in packets", 35.0, 5.0, 90.0, 5.0));
      private final BooleanSetting headless = this.addSetting(new BooleanSetting("Pitch Flip", "Oscillate pitch between -90 and 90", true));
      private float yaw;

      public Derp() {
         super("Derp", "Spin your server-side head rotations wildly while keeping your camera steady.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            this.yaw = Mth.wrapDegrees(this.yaw + this.spinSpeed.floatValue());
            float pitch = this.headless.get() ? Mth.sin(player.tickCount * 0.35F) * 89.0F : player.getXRot();
            RotationManager.request(player, this.yaw, pitch, 180.0F, true, 0, 180.0F);
         }
      }
   }

   public static final class FakeLag extends Module {
      private final NumberSetting pulseTicks = this.addSetting(new NumberSetting("Pulse Ticks", "Ticks to choke movement packets", 6.0, 2.0, 20.0, 1.0));
      private final ArrayDeque<Packet<?>> queue = new ArrayDeque<>();
      private boolean flushing;
      private int timer;

      public FakeLag() {
         super("FakeLag", "Hold and burst movement packets in short pulses like LiquidBounce FakeLag.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         this.flush();
      }

      @Override
      public String info() {
         return this.queue.size() + "p";
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (!this.flushing && packet instanceof ServerboundMovePlayerPacket) {
            this.queue.addLast(packet);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void onTick() {
         if (++this.timer >= this.pulseTicks.intValue()) {
            this.timer = 0;
            this.flush();
         }
      }

      private void flush() {
         Minecraft client = Minecraft.getInstance();
         if (client.getConnection() != null && !this.queue.isEmpty()) {
            this.flushing = true;

            try {
               while (!this.queue.isEmpty()) {
                  client.getConnection().send(this.queue.removeFirst());
               }
            } finally {
               this.flushing = false;
            }
         } else {
            this.queue.clear();
         }
      }
   }

   public static final class Notifier extends Module {
      private final BooleanSetting visualRange = this.addSetting(new BooleanSetting("Visual Range", "Alert when players enter or leave render distance", true));
      private final BooleanSetting totemPops = this.addSetting(new BooleanSetting("Totem Pops", "Alert when a player pops a Totem of Undying", true));
      private final Set<UUID> seenPlayers = new HashSet<>();

      public Notifier() {
         super("Notifier", "Alert on totem pops and when players enter or leave your visual range.", Category.CLIENT);
      }

      @Override
      public void onDisable() {
         this.seenPlayers.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer self = client.player;
         if (this.visualRange.get() && self != null && client.level != null) {
            Set<UUID> current = new HashSet<>();

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity != self && entity instanceof Player p) {
                  current.add(p.getUUID());
                  if (this.seenPlayers.add(p.getUUID())) {
                     HudOverlay.pushNotification("Visual Range", p.getName().getString() + " entered range", true);
                     CommandManager.feedback("§b[Notifier] §f" + p.getName().getString() + " §7entered visual range.");
                  }
               }
            }

            this.seenPlayers.retainAll(current);
         }
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         Minecraft client = Minecraft.getInstance();
         if (this.totemPops.get()
            && client.level != null
            && packet instanceof ClientboundEntityEventPacket evt
            && evt.getEventId() == 35
            && evt.getEntity(client.level) instanceof Player p) {
            HudOverlay.pushNotification("Totem Pop", p.getName().getString() + " popped a totem!", true);
            CommandManager.feedback("§b[Notifier] §f" + p.getName().getString() + " §7popped a §eTotem of Undying§7!");
         }

         return false;
      }
   }

   public static final class Presets extends Module {
      private final ModeSetting pack = this.addSetting(
         new ModeSetting("Preset", "Which bundle of modules to enable", "LiquidBounce", "LiquidBounce", "Performance", "PvP", "Explore", "Build", "Travel")
      );
      private final BooleanSetting disableOthers = this.addSetting(new BooleanSetting("Disable Others", "Turn off the other presets' modules first", false));

      public Presets() {
         super(
            "Presets", "Turn on a curated bundle of modules for LiquidBounce, Performance/AntiLag, PvP, exploring, building, or travelling.", Category.CLIENT
         );
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public String info() {
         return this.pack.get();
      }

      @Override
      public void onEnable() {
         Gemini client = Gemini.getInstance();
         if (client == null) {
            this.setEnabled(false, false);
         } else {
            if (this.disableOthers.get()) {
               toggle(
                  client,
                  false,
                  "Fullbright",
                  "Xray",
                  "ESP",
                  "StorageESP",
                  "Waypoints",
                  "Flight",
                  "Speed",
                  "Jesus",
                  "Scaffold",
                  "Nuker",
                  "AutoTool",
                  "FastPlace",
                  "KillAura",
                  "Velocity",
                  "TargetHUD"
               );
            }

            String var2 = this.pack.get();
            switch (var2) {
               case "LiquidBounce":
                  toggle(client, true, "Sprint", "HUD", "TargetHUD", "Notifications", "Velocity", "AutoArmor", "InventoryCleaner", "ESP", "Nametags", "AntiLag");
                  break;
               case "Performance":
                  toggle(client, true, "AntiLag", "EntityCulling", "Unfocused CPU", "Particles", "HUD", "FPS Graph", "AntiLag Status");
                  break;
               case "PvP":
                  toggle(client, true, "KillAura", "Velocity", "Criticals", "AutoTotem", "AutoArmor", "Sprint", "TargetHUD", "HoleESP", "HitEffect");
                  break;
               case "Build":
                  toggle(client, true, "Scaffold", "FastPlace", "AutoTool", "SafeWalk", "Sprint");
                  break;
               case "Travel":
                  toggle(client, true, "Flight", "Speed", "Jesus", "NoFall", "Sprint", "Step");
                  break;
               default:
                  toggle(client, true, "Fullbright", "Xray", "ESP", "StorageESP", "Waypoints", "Coordinates", "Zoom");
            }

            CommandManager.feedback("§bPresets §7applied §f" + this.pack.get() + "§7.");
            this.setEnabled(false, false);
         }
      }

      private static void toggle(Gemini gemini, boolean on, String... names) {
         for (String name : names) {
            gemini.modules().getByName(name).ifPresent(module -> module.setEnabled(on, false));
         }
      }
   }

   public static final class ResetVL extends Module {
      private int hops;

      public ResetVL() {
         super("ResetVL", "Perform micro-hops on ground to decay movement violation levels.", Category.MOVEMENT);
      }

      @Override
      public void onEnable() {
         this.hops = 0;
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.onGround()) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.11, player.getDeltaMovement().z);
            if (++this.hops >= 12) {
               this.setEnabled(false);
            }
         }
      }
   }

   public static final class Spammer extends Module {
      private final ListSetting messages = this.addSetting(
         new ListSetting("Messages", "Lines sent in rotation", ListSetting.Kind.TEXT, "Gemini Client Nextgen on top!")
      );
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay Seconds", "Seconds between chat messages", 8.0, 2.0, 60.0, 1.0));
      private final BooleanSetting bypass = this.addSetting(new BooleanSetting("Anti-Spam Suffix", "Append a random 4-letter tag", true));
      private final Random random = new Random();
      private int wait;
      private int index;

      public Spammer() {
         super("Spammer", "Automatically send customizable messages in chat with an anti-spam bypass suffix.", Category.CLIENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onTick() {
         if (this.wait-- <= 0 && !this.messages.isEmpty()) {
            this.wait = this.delay.intValue() * 20;
            String base = this.messages.get().get(this.index++ % this.messages.size());
            CommandManager.sendRaw(base + (this.bypass.get() ? MiscExtras.randomSuffix(this.random) : ""));
         }
      }
   }

   public static final class Teams extends Module {
      private final BooleanSetting scoreboardTeam = this.addSetting(new BooleanSetting("Scoreboard Team", "Check vanilla scoreboard team", true));
      private final BooleanSetting nameColor = this.addSetting(new BooleanSetting("Name Prefix", "Check matching display name color", true));

      public Teams() {
         super("Teams", "Identify teammates by scoreboard team or display color.", Category.COMBAT);
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
}
