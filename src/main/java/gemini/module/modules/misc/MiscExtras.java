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
import gemini.util.RotationManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class MiscExtras {
   private MiscExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new MiscExtras.DeathCoords());
      sink.accept(new MiscExtras.Presets());
      sink.accept(new MiscExtras.AutoGG());
      sink.accept(new MiscExtras.Notifier());
      sink.accept(new MiscExtras.Teams());
      sink.accept(new MiscExtras.Derp());
   }

   public static final class AutoGG extends Module {
      private final ListSetting phrase = this.addSetting(new ListSetting("Message", "GG text to send", ListSetting.Kind.TEXT, "GG"));
      private Entity lastAttacked;
      private int timeout;

      public AutoGG() {
         super("AutoGG", "Auto-say GG when a player you recently fought dies.", Category.CLIENT);
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
         super("Derp", "Spin server-side head rotations without moving camera.", Category.PLAYER);
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

   public static final class Notifier extends Module {
      private final BooleanSetting visualRange = this.addSetting(new BooleanSetting("Visual Range", "Alert when players enter or leave render distance", true));
      private final BooleanSetting totemPops = this.addSetting(new BooleanSetting("Totem Pops", "Alert when a player pops a Totem of Undying", true));
      private final Set<UUID> seenPlayers = new HashSet<>();

      public Notifier() {
         super("Notifier", "Alert on totem pops and visual range changes.", Category.CLIENT);
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
            "Presets", "One-click module bundles for PvP, FPS, and travel.", Category.CLIENT
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

   public static final class Teams extends Module {
      public static MiscExtras.Teams instance;
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
}
