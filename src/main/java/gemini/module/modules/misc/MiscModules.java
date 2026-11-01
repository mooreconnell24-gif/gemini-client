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
import gemini.util.Scheduler;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public final class MiscModules {
   private MiscModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new MiscModules.AntiLag());
      sink.accept(new MiscModules.Panic());
      sink.accept(new MiscModules.BetterChat());
      sink.accept(new MiscModules.SoundBlocker());
      sink.accept(new MiscModules.XCarry());
      sink.accept(new MiscModules.PacketLogger());
      sink.accept(new MiscModules.Disabler());
      sink.accept(new MiscModules.PingSpoof());
      sink.accept(new MiscModules.NameProtect());
      sink.accept(new MiscModules.AntiBot());
      sink.accept(new MiscModules.AntiStaff());
      sink.accept(new MiscModules.FlagCheck());
      sink.accept(new MiscModules.PortalMenu());
   }

   static String simplePacketName(Packet<?> packet) {
      String name = packet.getClass().getName();
      return name.substring(name.lastIndexOf(46) + 1);
   }

   public static final class AntiBot extends Module {
      public static MiscModules.AntiBot instance;
      private final BooleanSetting tabCheck = this.addSetting(new BooleanSetting("Tab List Check", "Ignore players missing from the tab list", true));
      private final BooleanSetting zeroHealth = this.addSetting(new BooleanSetting("Zero Health", "Ignore entities with invalid <=0 health", true));

      public AntiBot() {
         super("AntiBot", "Filter out anti-cheat decoy bots from combat.", Category.COMBAT);
         instance = this;
      }

      public boolean isBot(Entity entity) {
         if (entity instanceof Player player) {
            Minecraft client = Minecraft.getInstance();
            return this.zeroHealth.get() && player.getHealth() <= 0.0F
               ? true
               : this.tabCheck.get() && client.getConnection() != null && client.getConnection().getPlayerInfo(player.getUUID()) == null;
         } else {
            return false;
         }
      }
   }

   public static final class AntiLag extends Module {
      public static MiscModules.AntiLag instance;
      private final ModeSetting mode = this.addSetting(
         new ModeSetting(
            "Mode",
            "Dynamic adapts to FPS; Aggressive culls all effects",
            "Dynamic",
            "Dynamic",
            "Always",
            "Aggressive"
         )
      );
      private final NumberSetting targetFps = this.addSetting(
         new NumberSetting("Target FPS", "FPS threshold where Dynamic culling activates", 90.0, 30.0, 240.0, 10.0)
            .showIf(() -> this.mode.is("Dynamic"))
      );
      private final NumberSetting maxParticlesTick = this.addSetting(
         new NumberSetting("Max Particles/Tick", "Maximum particles allowed to spawn in a single tick", 55.0, 5.0, 250.0, 5.0)
      );
      private final NumberSetting particleRange = this.addSetting(
         new NumberSetting("Particle Distance", "Cull particles beyond this distance in blocks", 28.0, 8.0, 96.0, 4.0)
      );
      private final BooleanSetting blockExplosions = this.addSetting(
         new BooleanSetting("Block Explosions & Fireworks", "Block explosion emitters", true)
      );
      private final BooleanSetting blockSmoke = this.addSetting(
         new BooleanSetting("Block Smoke & Campfires", "Block heavy campfire smoke", true)
      );
      private final BooleanSetting blockWeather = this.addSetting(
         new BooleanSetting("Block Weather & Portals", "Block rain splash, dripping water, and portal particles", true)
      );
      private final BooleanSetting packetGuard = this.addSetting(
         new BooleanSetting("Packet Flood Guard", "Drop server packets spawning excessive particles", true)
      );
      private final BooleanSetting soundThrottle = this.addSetting(
         new BooleanSetting("Sound Spam Filter", "Cap overlapping sounds per tick to prevent lag", true)
      );
      private final NumberSetting maxSoundsTick = this.addSetting(
         new NumberSetting("Max Sounds/Tick", "Maximum sound instances started per tick", 14.0, 4.0, 40.0, 2.0).showIf(this.soundThrottle::get)
      );
      private final BooleanSetting cullNametags = this.addSetting(
         new BooleanSetting("Cull Distant Nametags", "Hide distant entity and armor stand nametags", true)
      );
      private int particlesThisTick;
      private int soundsThisTick;
      private long totalBlocked;

      public AntiLag() {
         super("AntiLag", "Boost FPS and prevent lag machines.", Category.CLIENT);
         instance = this;
      }

      @Override
      public void onEnable() {
         this.particlesThisTick = 0;
         this.soundsThisTick = 0;
      }

      @Override
      public void onTick() {
         this.particlesThisTick = 0;
         this.soundsThisTick = 0;
      }

      public long totalBlocked() {
         return this.totalBlocked;
      }

      public boolean isStrictNow() {
         if (!this.mode.is("Always") && !this.mode.is("Aggressive")) {
            int fps = Minecraft.getInstance().getFps();
            return fps > 0 && fps < this.targetFps.intValue();
         } else {
            return true;
         }
      }

      @Override
      public String info() {
         return this.mode.get() + " · " + this.totalBlocked;
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         if (!(this.packetGuard.get() && packet instanceof ClientboundLevelParticlesPacket p)
            || p.getCount() <= 150 && (!this.mode.is("Aggressive") || p.getCount() <= 40)) {
            return false;
         } else {
            this.totalBlocked++;
            return true;
         }
      }

      public boolean shouldBlockParticle(ParticleOptions options, double x, double y, double z) {
         if (!this.isEnabled()) {
            return false;
         } else {
            boolean strict = this.isStrictNow();
            int cap = this.mode.is("Aggressive")
               ? Math.max(5, this.maxParticlesTick.intValue() / 3)
               : (strict ? this.maxParticlesTick.intValue() : this.maxParticlesTick.intValue() * 2);
            if (this.particlesThisTick >= cap) {
               this.totalBlocked++;
               return true;
            } else {
               Minecraft client = Minecraft.getInstance();
               if (client.player != null) {
                  double maxDist = strict ? this.particleRange.get() : this.particleRange.get() * 1.35;
                  if (client.player.distanceToSqr(x, y, z) > maxDist * maxDist) {
                     this.totalBlocked++;
                     return true;
                  }
               }

               Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
               if (key != null) {
                  String path = key.getPath();
                  if (this.blockExplosions.get()
                     && (
                        path.contains("explosion")
                           || path.contains("firework")
                           || path.equals("flash")
                           || path.contains("sonic_boom")
                           || path.contains("elder_guardian")
                     )) {
                     this.totalBlocked++;
                     return true;
                  }

                  if (this.blockSmoke.get() && (path.contains("campfire") || path.contains("large_smoke") || path.equals("smoke") || path.contains("ash"))) {
                     this.totalBlocked++;
                     return true;
                  }

                  if (this.blockWeather.get() && (path.equals("rain") || path.contains("dripping") || path.contains("portal"))) {
                     this.totalBlocked++;
                     return true;
                  }
               }

               this.particlesThisTick++;
               return false;
            }
         }
      }

      public boolean shouldBlockSound(Identifier id) {
         if (this.isEnabled() && this.soundThrottle.get()) {
            int cap = this.isStrictNow() ? this.maxSoundsTick.intValue() : this.maxSoundsTick.intValue() * 2;
            if (this.soundsThisTick >= cap) {
               this.totalBlocked++;
               return true;
            } else {
               String path = id.getPath();
               if (!this.mode.is("Aggressive") || !path.contains("explosion") && !path.contains("firework") && !path.contains("piston")) {
                  this.soundsThisTick++;
                  return false;
               } else {
                  this.totalBlocked++;
                  return true;
               }
            }
         } else {
            return false;
         }
      }

      public boolean shouldCullNametag(Entity entity, double distanceSq) {
         if (!this.isEnabled() || !this.cullNametags.get()) {
            return false;
         } else if (entity instanceof ArmorStand) {
            return true;
         } else {
            double maxDist = this.isStrictNow() ? 14.0 : 28.0;
            return distanceSq > maxDist * maxDist;
         }
      }
   }

   public static final class AntiStaff extends Module {
      private int cooldown;

      public AntiStaff() {
         super("AntiStaff", "Alert when spectator or staff players are nearby.", Category.CLIENT);
      }

      @Override
      public void onTick() {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            Minecraft client = Minecraft.getInstance();
            LocalPlayer self = client.player;
            if (self != null && client.level != null) {
               for (Entity entity : client.level.entitiesForRendering()) {
                  if (entity != self && entity instanceof Player p && p.isSpectator()) {
                     HudOverlay.pushNotification("AntiStaff", "Spectator nearby: " + p.getName().getString(), false);
                     CommandManager.feedback("§c[AntiStaff] §7Spectator detected: §f" + p.getName().getString());
                     this.cooldown = 200;
                     break;
                  }
               }
            }
         }
      }
   }

   public static final class BetterChat extends Module {
      public static MiscModules.BetterChat instance;
      private static final Pattern COORDINATES = Pattern.compile("(?<![\\d.])-?\\d{2,7}(?:\\.\\d+)?[ ,]+-?\\d{1,3}(?:\\.\\d+)?[ ,]+-?\\d{2,7}(?![\\d.])");
      private static final DateTimeFormatter TWENTY_FOUR = DateTimeFormatter.ofPattern("HH:mm");
      private static final DateTimeFormatter TWELVE = DateTimeFormatter.ofPattern("hh:mm a", Locale.ROOT);
      private final BooleanSetting timestamps = this.addSetting(new BooleanSetting("Timestamps", "Prefix every chat line with the time", true));
      private final ModeSetting format = this.addSetting(
         new ModeSetting("Time Format", "How the time is written", "24 Hour", "24 Hour", "12 Hour").showIf(this.timestamps::get)
      );
      private final BooleanSetting antiSpam = this.addSetting(new BooleanSetting("Anti Spam", "Hide a line that repeats the previous one", true));
      private final NumberSetting window = this.addSetting(
         new NumberSetting("Spam Window", "Seconds a repeat counts as spam", 10.0, 1.0, 60.0, 1.0).showIf(this.antiSpam::get)
      );
      private final ListSetting hidden = this.addSetting(
         new ListSetting("Hidden Words", "Chat lines containing any of these are hidden", ListSetting.Kind.TEXT)
      );
      private final BooleanSetting protectCoordinates = this.addSetting(
         new BooleanSetting("Coordinate Protection", "Block outgoing chat messages containing coordinates", false)
      );
      private String lastText = "";
      private long lastTime;

      public BetterChat() {
         super("Better Chat", "Chat timestamps, anti-spam, and coordinate guard.", Category.CLIENT);
         instance = this;
      }

      public boolean hides(Component message) {
         String text = message.getString();
         if (!this.hidden.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);

            for (String word : this.hidden.get()) {
               if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                  return true;
               }
            }
         }

         if (!this.antiSpam.get()) {
            return false;
         } else {
            long now = System.currentTimeMillis();
            boolean repeat = text.equals(this.lastText) && now - this.lastTime < this.window.intValue() * 1000L;
            this.lastText = text;
            this.lastTime = now;
            return repeat;
         }
      }

      public Component decorate(Component message) {
         if (!this.timestamps.get()) {
            return message;
         } else {
            String time = LocalTime.now().format(this.format.is("12 Hour") ? TWELVE : TWENTY_FOUR);
            return Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(message);
         }
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (this.protectCoordinates.get() && packet instanceof ServerboundChatPacket chat && COORDINATES.matcher(chat.message()).find()) {
            CommandManager.feedback("§bBetter Chat §7blocked a message that looks like it contains coordinates.");
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class Disabler extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Packet filter profile", "VulcanSprint", "VulcanSprint", "VerusCombat", "GrimPlace")
      );

      public Disabler() {
         super("Disabler", "Filter sprint and action packets for anti-cheats.", Category.CLIENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         return this.mode.is("VulcanSprint") && packet instanceof ServerboundPlayerCommandPacket cmd
            ? cmd.getAction() == Action.START_SPRINTING || cmd.getAction() == Action.STOP_SPRINTING
            : false;
      }
   }

   public static final class FlagCheck extends Module {
      private int flags;

      public FlagCheck() {
         super("FlagCheck", "Count and alert on server position setbacks.", Category.CLIENT);
      }

      @Override
      public void onEnable() {
         this.flags = 0;
      }

      @Override
      public String info() {
         return Integer.toString(this.flags);
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         if (packet instanceof ClientboundPlayerPositionPacket) {
            this.flags++;
            HudOverlay.pushNotification("FlagCheck", "Setback #" + this.flags + " detected", false);
         }

         return false;
      }
   }

   public static final class NameProtect extends Module {
      public static MiscModules.NameProtect instance;
      private final ListSetting alias = this.addSetting(
         new ListSetting("Alias", "Replacement name shown for your character", ListSetting.Kind.TEXT, "GeminiUser")
      );

      public NameProtect() {
         super("NameProtect", "Mask your account name in HUD and chat.", Category.CLIENT);
         instance = this;
      }

      public String aliasName() {
         return this.alias.isEmpty() ? "GeminiUser" : this.alias.get().getFirst();
      }

      public Component filterComponent(Component message) {
         Minecraft client = Minecraft.getInstance();
         if (client.player == null || message == null) {
            return message;
         }
         String selfName = client.player.getGameProfile().name();
         String raw = message.getString();
         if (selfName != null && !selfName.isEmpty() && raw.contains(selfName)) {
            return Component.literal(raw.replace(selfName, this.aliasName())).withStyle(message.getStyle());
         }
         return message;
      }

      @Override
      public String info() {
         return this.aliasName();
      }
   }

   public static final class PacketLogger extends Module {
      private final BooleanSetting outgoing = this.addSetting(new BooleanSetting("Outgoing", "Log packets sent to the server", true));
      private final BooleanSetting incoming = this.addSetting(new BooleanSetting("Incoming", "Log packets received from the server", false));

      public PacketLogger() {
         super("PacketLogger", "Log outgoing and incoming network packets in chat.", Category.CLIENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (this.outgoing.get()) {
            CommandManager.feedback("§b[OUT] §7" + MiscModules.simplePacketName(packet));
         }

         return false;
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         if (this.incoming.get()) {
            CommandManager.feedback("§a[IN] §7" + MiscModules.simplePacketName(packet));
         }

         return false;
      }
   }

   public static final class Panic extends Module {
      public Panic() {
         super("Panic", "Instantly disable every module and close the menu.", Category.CLIENT);
      }

      @Override
      public boolean restoresEnabledState() {
         return false;
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public boolean showInArrayList() {
         return false;
      }

      @Override
      public void onEnable() {
         Gemini client = Gemini.getInstance();
         if (client != null) {
            client.modules().panic();
         }

         CommandManager.feedback("§bPanic §7all modules disabled.");
      }
   }

   public static final class PingSpoof extends Module {
      private final NumberSetting delayTicks = this.addSetting(new NumberSetting("Delay Ticks", "Ticks to delay KeepAlive packets", 6.0, 1.0, 40.0, 1.0));
      private boolean forwarding;

      public PingSpoof() {
         super("PingSpoof", "Delay KeepAlive packets to spoof higher ping.", Category.CLIENT);
      }

      @Override
      public String info() {
         return this.delayTicks.intValue() * 50 + "ms";
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (!this.forwarding && packet instanceof ServerboundKeepAlivePacket keepAlive) {
            Scheduler.later(this.delayTicks.intValue(), () -> {
               Minecraft client = Minecraft.getInstance();
               if (client.getConnection() != null) {
                  this.forwarding = true;

                  try {
                     client.getConnection().send(keepAlive);
                  } finally {
                     this.forwarding = false;
                  }
               }
            });
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class PortalMenu extends Module {
      public static MiscModules.PortalMenu instance;

      public PortalMenu() {
         super("PortalMenu", "Allow opening GUIs and chat inside Nether portals.", Category.PLAYER);
         instance = this;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.gui.screen() == null && client.player.portalProcess != null && client.player.portalProcess.isInsidePortalThisTick()) {
            while (client.options.keyInventory.consumeClick()) {
               client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
            }
            while (client.options.keyChat.consumeClick()) {
               client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
            }
         }
      }
   }

   public static final class SoundBlocker extends Module {
      public static MiscModules.SoundBlocker instance;
      private final ListSetting sounds = this.addSetting(
         new ListSetting(
               "Sounds", "Sound ids, or any part of one, to mute", ListSetting.Kind.TEXT, "ambient.cave", "entity.phantom"
            )
            .onChange(this::rebuild)
      );
      private volatile String[] needles = new String[0];

      public SoundBlocker() {
         super("Sound Blocker", "Mute annoying sounds by name.", Category.CLIENT);
         instance = this;
         this.rebuild();
      }

      private void rebuild() {
         List<String> lowered = new ArrayList<>();

         for (String entry : this.sounds.get()) {
            lowered.add(entry.toLowerCase(Locale.ROOT));
         }

         this.needles = lowered.toArray(new String[0]);
      }

      public boolean blocks(Identifier sound) {
         String[] list = this.needles;
         if (list.length == 0) {
            return false;
         } else {
            String id = sound.toString();

            for (String needle : list) {
               if (id.contains(needle)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public static final class XCarry extends Module {
      public XCarry() {
         super("XCarry", "Store items in the 2x2 crafting grid.", Category.PLAYER);
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         return packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == 0;
      }

      @Override
      public void onDisable() {
         Minecraft client = Minecraft.getInstance();
         if (client.getConnection() != null && client.player != null) {
            client.getConnection().send(new ServerboundContainerClosePacket(0));
         }
      }
   }
}
