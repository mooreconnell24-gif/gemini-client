package gemini.module.modules.hud;

import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.TextHudWidget;
import gemini.module.modules.misc.MiscModules;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import gemini.ui.clickgui.Menus;
import gemini.ui.HudOverlay;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class HudWidgets {
   static final int WHITE = -1511689;
   static final int GREY = -6641995;
   static final int GREEN = -11086174;
   static final int YELLOW = -14249;
   static final int RED = -1548963;

   private HudWidgets() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new HudWidgets.Fps());
      sink.accept(new HudWidgets.Tps());
      sink.accept(new HudWidgets.Speedometer());
      sink.accept(new HudWidgets.Memory());
      sink.accept(new HudWidgets.SessionTimer());
      sink.accept(new HudWidgets.Cps());
      sink.accept(new HudWidgets.Position());
      sink.accept(new HudWidgets.Facing());
      sink.accept(new HudWidgets.BiomeInfo());
      sink.accept(new HudWidgets.Clock());
      sink.accept(new HudWidgets.LightLevel());
      sink.accept(new HudWidgets.LastDeath());
      sink.accept(new HudWidgets.ChunkInfo());
      sink.accept(new HudWidgets.LookingAt());
      sink.accept(new HudWidgets.CompassBar());
      sink.accept(new HudWidgets.Welcome());
      sink.accept(new HudWidgets.LagNotifier());
      sink.accept(new HudWidgets.CustomText());
      sink.accept(new HudWidgets.PotionTimers());
      sink.accept(new HudWidgets.EntityCounter());
      sink.accept(new HudWidgets.ArmorHud());
      sink.accept(new HudWidgets.HeldItem());
      sink.accept(new HudWidgets.ItemCounter());
      sink.accept(new HudWidgets.TotemCounter());
      sink.accept(new HudWidgets.InventoryViewer());
      sink.accept(new HudWidgets.Keystrokes());
      sink.accept(new HudWidgets.TargetHud());
      sink.accept(new HudWidgets.Radar());
      sink.accept(new HudWidgets.CombatItems());
      sink.accept(new HudWidgets.FpsGraph());
      sink.accept(new HudWidgets.KeybindList());
      sink.accept(new HudWidgets.Cooldowns());
      sink.accept(new HudWidgets.AntiLagHud());
   }

   static String pretty(String path) {
      StringBuilder out = new StringBuilder(path.length());
      boolean upper = true;

      for (int i = 0; i < path.length(); i++) {
         char c = path.charAt(i);
         if (c != '_' && c != '/') {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
         } else {
            out.append(' ');
            upper = true;
         }
      }

      return out.toString();
   }

   static String ticksToClock(int ticks) {
      int seconds = Math.max(0, ticks) / 20;
      return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
   }

   static int ratioColor(float ratio) {
      return ratio > 0.6F ? -11086174 : (ratio > 0.3F ? -14249 : -1548963);
   }

   static String roman(int number) {
      return switch (number) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         default -> Integer.toString(number);
      };
   }

   static int count(LocalPlayer player, Item item) {
      int total = 0;

      for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
         if (stack.is(item)) {
            total += stack.getCount();
         }
      }

      ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
      if (offhand.is(item)) {
         total += offhand.getCount();
      }

      return total;
   }

   static final class AntiLagHud extends HudWidgets.Ticked {
      AntiLagHud() {
         super(
            "AntiLag Status", "Live telemetry showing FPS stability, memory usage, and total lag particles/sounds blocked by AntiLag.", "Top Right", 6, 40, 200
         );
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         int fps = hud.client.getFps();
         Runtime rt = Runtime.getRuntime();
         long usedPct = rt.maxMemory() > 0L ? (rt.totalMemory() - rt.freeMemory()) * 100L / rt.maxMemory() : 0L;
         MiscModules.AntiLag antiLag = MiscModules.AntiLag.instance;
         long blocked = antiLag == null ? 0L : antiLag.totalBlocked();
         boolean active = antiLag != null && antiLag.isEnabled();
         return List.of(
            new TextHudWidget.Line("AntiLag " + (active ? "ACTIVE" : "OFF") + "  ·  " + fps + " FPS", active ? -11086174 : -6641995),
            new TextHudWidget.Line("Blocked " + blocked + " lag spikes  ·  Heap " + usedPct + "%", hud.accent)
         );
      }
   }

   static final class ArmorHud extends HudWidget {
      private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
      private final ModeSetting layout = this.addSetting(
         new ModeSetting("Layout", "Stack the pieces in a row or a column", "Horizontal", "Horizontal", "Vertical")
      );
      private final BooleanSetting percent = this.addSetting(new BooleanSetting("Percent", "Show remaining durability as a percentage", true));
      private final BooleanSetting hands = this.addSetting(new BooleanSetting("Hands", "Include the items in both hands", false));

      ArmorHud() {
         super("Armor", "Your armor pieces with their durability.", "Bottom Center", 0, 58);
      }

      private List<ItemStack> stacks(LocalPlayer player) {
         List<ItemStack> list = new ArrayList<>(6);

         for (EquipmentSlot slot : SLOTS) {
            list.add(player.getItemBySlot(slot));
         }

         if (this.hands.get()) {
            list.add(player.getMainHandItem());
            list.add(player.getOffhandItem());
         }

         return list;
      }

      private boolean vertical() {
         return this.layout.is("Vertical");
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         int n = this.stacks(hud.client.player).size();
         return this.vertical() ? 24 + (this.percent.get() ? 4 + hud.font.width("100%") : 0) : 8 + n * 20 - 4;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         int n = this.stacks(hud.client.player).size();
         return this.vertical() ? 8 + n * 18 - 2 : 24 + (this.percent.get() ? 10 : 0);
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         List<ItemStack> stacks = this.stacks(hud.client.player);

         for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int x = this.vertical() ? 6 : 6 + i * 20;
            int y = this.vertical() ? 4 + i * 18 : 4;
            if (stack.isEmpty()) {
               hud.graphics.fill(x, y, x + 16, y + 16, GeminiMark.withAlpha(-12958120, 60));
            } else {
               hud.graphics.item(stack, x, y);
               hud.graphics.itemDecorations(hud.font, stack, x, y);
               if (this.percent.get() && stack.isDamageableItem()) {
                  float ratio = 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage();
                  String text = Math.round(ratio * 100.0F) + "%";
                  if (this.vertical()) {
                     hud.graphics.text(hud.font, text, x + 20, y + 4, HudWidgets.ratioColor(ratio), hud.shadow);
                  } else {
                     hud.graphics.text(hud.font, text, x + 8 - hud.font.width(text) / 2, y + 18, HudWidgets.ratioColor(ratio), hud.shadow);
                  }
               }
            }
         }
      }
   }

   static final class BiomeInfo extends HudWidgets.Ticked {
      BiomeInfo() {
         super("Biome", "The biome you are standing in.", "Top Left", 6, 94, 500);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         Holder<Biome> biome = hud.client.level.getBiome(hud.client.player.blockPosition());
         String name = biome.unwrapKey().map(key -> HudWidgets.pretty(key.identifier().getPath())).orElse("Unknown");
         return List.of(new TextHudWidget.Line(name, hud.accent));
      }
   }

   static final class ChunkInfo extends HudWidgets.Ticked {
      ChunkInfo() {
         super("Chunk Info", "Chunk and region coordinates.", "Top Left", 6, 220, 250);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         int x = Mth.floor(hud.client.player.getX());
         int z = Mth.floor(hud.client.player.getZ());
         int cx = x >> 4;
         int cz = z >> 4;
         return List.of(
            new TextHudWidget.Line("Chunk " + cx + ", " + cz + "  (" + (x & 15) + ", " + (z & 15) + ")", -1511689),
            new TextHudWidget.Line("Region r." + (cx >> 5) + "." + (cz >> 5), -6641995)
         );
      }
   }

   static final class Clock extends HudWidgets.Ticked {
      private final BooleanSetting real = this.addSetting(new BooleanSetting("Real Time", "Show your computer's clock", true));
      private final BooleanSetting world = this.addSetting(new BooleanSetting("World Time", "Show the in-game day and time", true));
      private final BooleanSetting twelveHour = this.addSetting(new BooleanSetting("12 Hour", "Use AM/PM for the real clock", false));

      Clock() {
         super("Clock", "Real time and the in-game day.", "Top Left", 6, 112, 500);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         List<TextHudWidget.Line> lines = new ArrayList<>(2);
         if (this.real.get()) {
            DateTimeFormatter format = DateTimeFormatter.ofPattern(this.twelveHour.get() ? "hh:mm:ss a" : "HH:mm:ss", Locale.ENGLISH);
            lines.add(new TextHudWidget.Line(LocalTime.now().format(format), -1511689));
         }

         if (this.world.get() && hud.client.level != null) {
            long ticks = hud.client.level.getOverworldClockTime();
            long day = ticks / 24000L;
            long inDay = Math.floorMod(ticks, 24000L);
            int hour = (int)((inDay / 1000L + 6L) % 24L);
            int minute = (int)(inDay % 1000L * 60L / 1000L);
            lines.add(new TextHudWidget.Line(String.format(Locale.ROOT, "Day %d  %02d:%02d", day, hour, minute), -6641995));
         }

         return lines;
      }
   }

   static final class CombatItems extends HudWidget {
      private static final Item[] ITEMS = new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE};

      CombatItems() {
         super("Combat Items", "Show your live Totem, Crystal, Gapple, and XP counts side by side.", "Bottom Center", -90, 68);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 8 + ITEMS.length * 24;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 28;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         for (int i = 0; i < ITEMS.length; i++) {
            Item item = ITEMS[i];
            int count = HudWidgets.count(hud.client.player, item);
            int x = 6 + i * 24;
            hud.graphics.item(new ItemStack(item), x, 2);
            String label = Integer.toString(count);
            hud.graphics.text(hud.font, label, x + 8 - hud.font.width(label) / 2, 18, count > 0 ? -1511689 : -6641995, hud.shadow);
         }
      }
   }

   static final class CompassBar extends HudWidget {
      private static final String[] NAMES = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
      private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Pixel width of the strip", 200.0, 100.0, 400.0, 10.0));
      private final NumberSetting span = this.addSetting(new NumberSetting("Span", "Degrees visible at once", 140.0, 60.0, 270.0, 10.0));

      CompassBar() {
         super("Compass", "A heading strip with cardinal directions.", "Top Center", 0, 6);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return this.width.intValue();
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 22;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         float yaw = Mth.wrapDegrees(hud.client.player.getYRot() + 180.0F);
         float pxPerDegree = w / this.span.floatValue();
         int center = w / 2;
         hud.graphics.enableScissor(0, 0, w, h);

         for (int degree = 0; degree < 360; degree += 15) {
            float delta = Mth.wrapDegrees(degree - yaw);
            int x = center + Math.round(delta * pxPerDegree);
            if (x >= 0 && x <= w) {
               boolean cardinal = degree % 45 == 0;
               hud.graphics.fill(x, h - (cardinal ? 8 : 4) - 2, x + 1, h - 2, cardinal ? -1511689 : -6641995);
               if (cardinal) {
                  String label = NAMES[degree / 45];
                  boolean main = degree % 90 == 0;
                  hud.graphics.text(hud.font, label, x - hud.font.width(label) / 2, 3, main ? hud.accent : -6641995, hud.shadow);
               }
            }
         }

         hud.graphics.disableScissor();
         hud.graphics.fill(center, 0, center + 1, 4, hud.accent2);
      }
   }

   static final class Cooldowns extends HudWidget {
      private static final Item[] TRACKED = new Item[]{Items.ENDER_PEARL, Items.WIND_CHARGE, Items.GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD};

      Cooldowns() {
         super("Cooldowns", "Live progress bars for Ender Pearl, Wind Charge, Golden Apple, Chorus Fruit, and Shield cooldowns.", "Top Center", -95, 42);
      }

      private List<ItemStack> activeStacks(LocalPlayer player) {
         List<ItemStack> out = new ArrayList<>();
         if (player == null) {
            return out;
         } else {
            for (Item item : TRACKED) {
               ItemStack stack = new ItemStack(item);
               if (player.getCooldowns().isOnCooldown(stack)) {
                  out.add(stack);
               }
            }

            return out;
         }
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 112;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         List<ItemStack> active = this.activeStacks(hud.client.player);
         return active.isEmpty() ? 0 : 8 + active.size() * 18;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         LocalPlayer player = hud.client.player;
         List<ItemStack> active = this.activeStacks(player);

         for (int i = 0; i < active.size(); i++) {
            ItemStack stack = active.get(i);
            float pct = player.getCooldowns().getCooldownPercent(stack, hud.partialTick);
            int y = 4 + i * 18;
            hud.graphics.item(stack, 6, y);
            String name = stack.getHoverName().getString();
            hud.graphics.text(hud.font, name, 25, y + 2, -1511689, false);
            int barW = w - 31;
            hud.graphics.fill(25, y + 12, 25 + barW, y + 15, -14142908);
            hud.graphics.fill(25, y + 12, 25 + Math.round(barW * pct), y + 15, hud.accent);
         }
      }
   }

   static final class Cps extends HudWidgets.Ticked {
      private final ArrayDeque<Long> left = new ArrayDeque<>();
      private final ArrayDeque<Long> right = new ArrayDeque<>();
      private boolean wasLeft;
      private boolean wasRight;

      Cps() {
         super("CPS", "Clicks per second for both mouse buttons.", "Bottom Right", 6, 114, 100);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         long now = System.currentTimeMillis();
         boolean l = client.mouseHandler.isLeftPressed();
         boolean r = client.mouseHandler.isRightPressed();
         if (l && !this.wasLeft) {
            this.left.addLast(now);
         }

         if (r && !this.wasRight) {
            this.right.addLast(now);
         }

         this.wasLeft = l;
         this.wasRight = r;
         trim(this.left, now);
         trim(this.right, now);
      }

      private static void trim(ArrayDeque<Long> clicks, long now) {
         while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.removeFirst();
         }
      }

      int leftCps() {
         trim(this.left, System.currentTimeMillis());
         return this.left.size();
      }

      int rightCps() {
         trim(this.right, System.currentTimeMillis());
         return this.right.size();
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         return List.of(new TextHudWidget.Line(this.leftCps() + " | " + this.rightCps() + " CPS", -1511689));
      }
   }

   static final class CustomText extends HudWidgets.Ticked {
      private final ListSetting entries = this.addSetting(
         new ListSetting(
            "Lines",
            "Custom HUD lines ({fps} {ping} {x} {y} {z} {speed} {health})",
            ListSetting.Kind.TEXT,
            "Gemini {version}",
            "{fps} FPS  ·  {x} {y} {z}"
         )
      );
      private final BooleanSetting accentColor = this.addSetting(new BooleanSetting("Accent Color", "Tint the text with the theme accent", false));

      CustomText() {
         super("Custom Text", "Your own lines of text with live placeholders.", "Left Center", 6, 0, 200);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         if (this.entries.isEmpty()) {
            return List.of();
         } else {
            LocalPlayer player = hud.client.player;
            Map<String, String> values = new HashMap<>();
            values.put("{version}", "1.0.0");
            values.put("{fps}", Integer.toString(hud.client.getFps()));
            values.put("{x}", Integer.toString(Mth.floor(player.getX())));
            values.put("{y}", Integer.toString(Mth.floor(player.getY())));
            values.put("{z}", Integer.toString(Mth.floor(player.getZ())));
            values.put("{player}", player.getName().getString());
            values.put("{health}", String.format(Locale.ROOT, "%.1f", player.getHealth()));
            values.put("{food}", Integer.toString(player.getFoodData().getFoodLevel()));
            values.put("{facing}", HudWidgets.pretty(Direction.fromYRot(player.getYRot()).getName()));
            values.put("{speed}", String.format(Locale.ROOT, "%.1f", Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 20.0));
            values.put("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            values.put(
               "{biome}", hud.client.level.getBiome(player.blockPosition()).unwrapKey().map(key -> HudWidgets.pretty(key.identifier().getPath())).orElse("?")
            );
            ServerData server = hud.client.getCurrentServer();
            values.put("{server}", server == null ? "Single-player" : server.ip);
            values.put("{online}", hud.client.getConnection() == null ? "0" : Integer.toString(hud.client.getConnection().getOnlinePlayers().size()));
            PlayerInfo info = hud.client.getConnection() == null ? null : hud.client.getConnection().getPlayerInfo(player.getUUID());
            values.put("{ping}", info == null ? "0" : Integer.toString(info.getLatency()));
            List<TextHudWidget.Line> lines = new ArrayList<>();

            for (String entry : this.entries.get()) {
               String text = entry;

               for (Entry<String, String> value : values.entrySet()) {
                  text = text.replace(value.getKey(), value.getValue());
               }

               lines.add(new TextHudWidget.Line(text, this.accentColor.get() ? hud.accent : -1511689));
            }

            return lines;
         }
      }
   }

   static final class EntityCounter extends HudWidgets.Ticked {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Only count entities this close", 48.0, 8.0, 256.0, 4.0));
      private final NumberSetting entries = this.addSetting(new NumberSetting("Entries", "Maximum number of rows", 6.0, 1.0, 20.0, 1.0));
      private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Count dropped items", false));
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Count other players", false));

      EntityCounter() {
         super("Entity Counter", "How many of each kind of entity are around you.", "Right Center", 6, -80, 500);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         LocalPlayer self = hud.client.player;
         double limit = this.radius.get() * this.radius.get();
         Map<EntityType<?>, Integer> counts = new HashMap<>();

         for (Entity entity : hud.client.level.entitiesForRendering()) {
            if (entity != self
               && !(entity.distanceToSqr(self) > limit)
               && (!(entity instanceof Player) || this.players.get())
               && (!(entity instanceof ItemEntity) || this.items.get())) {
               counts.merge(entity.getType(), 1, Integer::sum);
            }
         }

         List<Entry<EntityType<?>, Integer>> sorted = new ArrayList<>(counts.entrySet());
         sorted.sort(Entry.<EntityType<?>, Integer>comparingByValue().reversed());
         List<TextHudWidget.Line> lines = new ArrayList<>();

         for (int i = 0; i < Math.min(sorted.size(), this.entries.intValue()); i++) {
            Entry<EntityType<?>, Integer> entry = sorted.get(i);
            lines.add(new TextHudWidget.Line(entry.getKey().getDescription().getString() + " x" + entry.getValue(), -1511689));
         }

         return lines;
      }
   }

   static final class Facing extends HudWidgets.Ticked {
      Facing() {
         super("Facing", "The direction you are looking, with yaw and pitch.", "Top Left", 6, 62, 50);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         LocalPlayer player = hud.client.player;
         Direction direction = Direction.fromYRot(player.getYRot());

         String axis = switch (direction) {
            case NORTH -> "-Z";
            case SOUTH -> "+Z";
            case EAST -> "+X";
            case WEST -> "-X";
            default -> "";
         };
         return List.of(
            new TextHudWidget.Line(HudWidgets.pretty(direction.getName()) + " (" + axis + ")", hud.accent),
            new TextHudWidget.Line(String.format(Locale.ROOT, "Yaw %.1f  Pitch %.1f", Mth.wrapDegrees(player.getYRot()), player.getXRot()), -6641995)
         );
      }
   }

   static final class Fps extends HudWidgets.Ticked {
      Fps() {
         super("FPS", "Frames per second.", "Bottom Right", 6, 6, 250);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         int fps = hud.client.getFps();
         return List.of(new TextHudWidget.Line(fps + " FPS", fps >= 100 ? -11086174 : (fps >= 45 ? -14249 : -1548963)));
      }
   }

   static final class FpsGraph extends HudWidget {
      private final int[] history = new int[48];
      private int head;
      private long lastSampleMs;

      FpsGraph() {
         super("FPS Graph", "Live sparkline graph of your framerate with min, max, and current FPS.", "Bottom Left", 6, 86);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 112;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 38;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         long now = System.currentTimeMillis();
         int currentFps = Math.max(0, hud.client.getFps());
         if (now - this.lastSampleMs >= 100L) {
            this.lastSampleMs = now;
            this.history[this.head] = currentFps;
            this.head = (this.head + 1) % this.history.length;
         }

         int min = Integer.MAX_VALUE;
         int max = 1;

         for (int v : this.history) {
            if (v > 0) {
               min = Math.min(min, v);
               max = Math.max(max, v);
            }
         }

         if (min == Integer.MAX_VALUE) {
            min = currentFps;
            max = Math.max(60, currentFps);
         }

         String title = currentFps + " FPS";
         String sub = "min " + min + " · max " + max;
         hud.graphics.text(hud.font, title, 6, 4, hud.accent, hud.shadow);
         hud.graphics.text(hud.font, sub, w - 6 - hud.font.width(sub), 4, -6641995, false);
         int graphTop = 15;
         int graphBottom = h - 4;
         int graphH = Math.max(8, graphBottom - graphTop);

         for (int i = 0; i < this.history.length; i++) {
            int val = this.history[(this.head + i) % this.history.length];
            float norm = Mth.clamp((float)val / Math.max(60, max), 0.05F, 1.0F);
            int barH = Math.max(1, Math.round(graphH * norm));
            int bx = 6 + i * 2;
            int by = graphBottom - barH;
            hud.graphics.fill(bx, by, bx + 2, graphBottom, GeminiMark.withAlpha(hud.accent, 110));
            hud.graphics.fill(bx, by, bx + 2, by + 1, hud.accent2);
         }
      }
   }

   static final class HeldItem extends HudWidget {
      HeldItem() {
         super("Held Item", "The item in your hand with its durability.", "Bottom Center", 0, 84);
      }

      private String label(LocalPlayer player) {
         ItemStack stack = player.getMainHandItem();
         if (stack.isEmpty()) {
            return null;
         } else {
            String name = stack.getHoverName().getString();
            if (stack.isDamageableItem()) {
               int left = stack.getMaxDamage() - stack.getDamageValue();
               return name + "  " + left + "/" + stack.getMaxDamage();
            } else {
               return stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
            }
         }
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         String label = this.label(hud.client.player);
         return label == null ? 0 : 28 + hud.font.width(label);
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return this.label(hud.client.player) == null ? 0 : 24;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         LocalPlayer player = hud.client.player;
         ItemStack stack = player.getMainHandItem();
         String label = this.label(player);
         if (label != null) {
            hud.graphics.item(stack, 6, 4);
            float ratio = stack.isDamageableItem() ? 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage() : 1.0F;
            hud.graphics.text(hud.font, label, 26, 8, stack.isDamageableItem() ? HudWidgets.ratioColor(ratio) : -1511689, hud.shadow);
         }
      }
   }

   static final class InventoryViewer extends HudWidget {
      private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Hotbar", "Also draw the hotbar row", false));

      InventoryViewer() {
         super("Inventory Viewer", "Your inventory on screen.", "Bottom Left", 6, 6);
      }

      private int rows() {
         return this.hotbar.get() ? 4 : 3;
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 168;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 8 + this.rows() * 18 - 2;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         List<ItemStack> items = hud.client.player.getInventory().getNonEquipmentItems();

         for (int row = 0; row < this.rows(); row++) {
            for (int column = 0; column < 9; column++) {
               int index = row < 3 ? 9 + row * 9 + column : column;
               int x = 6 + column * 18;
               int y = 4 + row * 18;
               hud.graphics.fill(x, y, x + 16, y + 16, GeminiMark.withAlpha(-12958120, 70));
               ItemStack stack = items.get(index);
               if (!stack.isEmpty()) {
                  hud.graphics.item(stack, x, y);
                  hud.graphics.itemDecorations(hud.font, stack, x, y);
               }
            }
         }
      }
   }

   static final class ItemCounter extends HudWidget {
      private final ListSetting watched = this.addSetting(
         new ListSetting(
            "Items",
            "Items to count across your inventory and offhand",
            ListSetting.Kind.ITEM,
            "minecraft:totem_of_undying",
            "minecraft:end_crystal",
            "minecraft:experience_bottle",
            "minecraft:golden_apple"
         )
      );
      private final BooleanSetting hideZero = this.addSetting(new BooleanSetting("Hide Zero", "Skip items you have none of", false));

      ItemCounter() {
         super("Item Counter", "Counts of the items you pick, with icons.", "Top Center", 0, 90);
      }

      private List<Item> rows(LocalPlayer player) {
         List<Item> rows = new ArrayList<>();

         for (Item item : this.watched.items()) {
            if (!this.hideZero.get() || HudWidgets.count(player, item) > 0) {
               rows.add(item);
            }
         }

         return rows;
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         List<Item> rows = this.rows(hud.client.player);
         return rows.isEmpty() ? 0 : 30 + hud.font.width("000x");
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         List<Item> rows = this.rows(hud.client.player);
         return rows.isEmpty() ? 0 : 8 + rows.size() * 18 - 2;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         List<Item> rows = this.rows(hud.client.player);

         for (int i = 0; i < rows.size(); i++) {
            Item item = rows.get(i);
            int amount = HudWidgets.count(hud.client.player, item);
            int y = 4 + i * 18;
            hud.graphics.item(new ItemStack(item), 6, y);
            hud.graphics.text(hud.font, amount + "x", 28, y + 4, amount == 0 ? -1548963 : -1511689, hud.shadow);
         }
      }
   }

   static final class KeybindList extends HudWidget {
      private final BooleanSetting onlyActive = this.addSetting(
         new BooleanSetting("Only Active", "Only show keybound modules that are currently active", false)
      );

      KeybindList() {
         super("Keybinds", "LiquidBounce-style card listing your keybound modules and their live state.", "Left Center", 6, -45);
      }

      private List<Module> boundModules() {
         Gemini g = Gemini.getInstance();
         if (g == null) {
            return List.of();
         } else {
            List<Module> list = new ArrayList<>();

            for (Module m : g.modules().getModules()) {
               if (m.getKeybind() != -1 && (!this.onlyActive.get() || m.isEnabled())) {
                  list.add(m);
               }
            }

            return list;
         }
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         int max = hud.font.width("Keybinds") + 28;

         for (Module m : this.boundModules()) {
            String key = "[" + GeminiKeys.displayName(m.getKeybind()) + "]";
            max = Math.max(max, hud.font.width(m.getName()) + hud.font.width(key) + 22);
         }

         return Math.max(106, max);
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         List<Module> list = this.boundModules();
         return 18 + Math.max(1, list.size()) * 11;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         hud.graphics.text(hud.font, "Keybinds", 6, 4, hud.accent, hud.shadow);
         hud.graphics.fill(4, 14, w - 4, 15, GeminiMark.withAlpha(hud.accent, 90));
         List<Module> list = this.boundModules();
         if (list.isEmpty()) {
            hud.graphics.text(hud.font, "None bound", 6, 18, -6641995, false);
         } else {
            int y = 18;

            for (Module m : list) {
               String key = "[" + GeminiKeys.displayName(m.getKeybind()) + "]";
               hud.graphics.text(hud.font, m.getName(), 6, y, m.isEnabled() ? -1 : -6641995, hud.shadow);
               hud.graphics.text(hud.font, key, w - 6 - hud.font.width(key), y, m.isEnabled() ? hud.accent2 : -6641995, false);
               y += 11;
            }
         }
      }
   }

   static final class Keystrokes extends HudWidget {
      private final BooleanSetting mouse = this.addSetting(new BooleanSetting("Mouse", "Show the mouse buttons", true));
      private final BooleanSetting space = this.addSetting(new BooleanSetting("Space Bar", "Show the jump key", true));
      private final float[] keyAnims = new float[7];

      Keystrokes() {
         super("Keystrokes", "WASD, jump and mouse buttons as they are pressed.", "Left Center", 6, 40);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 72;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 48 + (this.space.get() ? 14 : 0) + (this.mouse.get() ? 22 : 0) + 2;
      }

      private void key(HudContext hud, int idx, int x, int y, int width, int height, String label, boolean down) {
         float target = down ? 1.0F : 0.0F;
         this.keyAnims[idx] = Mth.lerp(0.28F, this.keyAnims[idx], target);
         float a = this.keyAnims[idx];
         int bg = HudOverlay.lerp(-14011324, hud.accent, a);
         hud.graphics.fill(x, y, x + width, y + height, GeminiMark.withAlpha(bg, 195));
         hud.graphics.fill(x, y + height - 1, x + width, y + height, GeminiMark.withAlpha(hud.accent2, Math.round(80.0F + 175.0F * a)));
         hud.graphics.text(hud.font, label, x + (width - hud.font.width(label)) / 2, y + (height - 8) / 2, a > 0.55F ? -1 : -1511689, false);
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         Options options = hud.client.options;
         int x = 4;
         int y = 4;
         this.key(hud, 0, x + 22, y, 20, 20, "W", options.keyUp.isDown());
         this.key(hud, 1, x, y + 22, 20, 20, "A", options.keyLeft.isDown());
         this.key(hud, 2, x + 22, y + 22, 20, 20, "S", options.keyDown.isDown());
         this.key(hud, 3, x + 44, y + 22, 20, 20, "D", options.keyRight.isDown());
         int below = y + 44;
         if (this.space.get()) {
            this.key(hud, 4, x, below, 64, 12, "SPACE", options.keyJump.isDown());
            below += 14;
         }

         if (this.mouse.get()) {
            this.key(hud, 5, x, below, 31, 20, "LMB", options.keyAttack.isDown());
            this.key(hud, 6, x + 33, below, 31, 20, "RMB", options.keyUse.isDown());
         }
      }
   }

   static final class LagNotifier extends HudWidgets.Ticked {
      private final NumberSetting threshold = this.addSetting(
         new NumberSetting("Threshold", "Seconds without a packet before the warning shows", 1.0, 0.5, 10.0, 0.5)
      );
      private volatile long lastPacket = System.currentTimeMillis();

      LagNotifier() {
         super("Lag Notifier", "Warns when the server stops sending data.", "Top Center", 0, 56, 100);
      }

      @Override
      public void onEnable() {
         this.lastPacket = System.currentTimeMillis();
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         this.lastPacket = System.currentTimeMillis();
         return false;
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         double gap = (System.currentTimeMillis() - this.lastPacket) / 1000.0;
         return !(gap < this.threshold.get()) && !hud.client.hasSingleplayerServer()
            ? List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "Server not responding for %.1fs", gap), -1548963))
            : List.of();
      }
   }

   static final class LastDeath extends HudWidgets.Ticked {
      private BlockPos position;
      private String dimension = "";
      private boolean dead;

      LastDeath() {
         super("Last Death", "Where you last died, and how far away that is.", "Top Left", 6, 184, 500);
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            if (player.isDeadOrDying()) {
               if (!this.dead) {
                  this.dead = true;
                  this.position = player.blockPosition();
                  this.dimension = Minecraft.getInstance().level.dimension().identifier().getPath();
               }
            } else {
               this.dead = false;
            }
         }
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         if (this.position == null) {
            return List.of();
         } else {
            boolean sameWorld = hud.client.level.dimension().identifier().getPath().equals(this.dimension);
            String where = this.position.getX() + "  " + this.position.getY() + "  " + this.position.getZ();
            if (!sameWorld) {
               return List.of(new TextHudWidget.Line("Died in " + HudWidgets.pretty(this.dimension), -1548963), new TextHudWidget.Line(where, -6641995));
            } else {
               double distance = Math.sqrt(this.position.distSqr(hud.client.player.blockPosition()));
               return List.of(new TextHudWidget.Line("Died " + Math.round(distance) + "m away", -1548963), new TextHudWidget.Line(where, -6641995));
            }
         }
      }
   }

   static final class LightLevel extends HudWidgets.Ticked {
      LightLevel() {
         super("Light Level", "Light at your feet and whether monsters can spawn there.", "Top Left", 6, 166, 200);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         ClientLevel level = hud.client.level;
         BlockPos pos = hud.client.player.blockPosition();
         int block = level.getBrightness(LightLayer.BLOCK, pos);
         int sky = level.getBrightness(LightLayer.SKY, pos);
         int blockLimit = level.dimensionType().monsterSpawnBlockLightLimit();
         int skyLimit = level.dimensionType().monsterSpawnLightTest().maxInclusive();
         int effective = Math.min(level.getMaxLocalRawBrightness(pos), 15);
         boolean spawnable = block <= blockLimit && effective <= skyLimit;
         return List.of(
            new TextHudWidget.Line("Light " + effective + "  (sky " + sky + " / block " + block + ")", -1511689),
            new TextHudWidget.Line(spawnable ? "Monsters can spawn" : "Spawn-proof", spawnable ? -1548963 : -11086174)
         );
      }
   }

   static final class LookingAt extends HudWidgets.Ticked {
      LookingAt() {
         super("Looking At", "The block or entity under your crosshair.", "Top Center", 0, 40, 50);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         HitResult hit = hud.client.hitResult;
         if (hit instanceof BlockHitResult block && block.getType() == Type.BLOCK) {
            BlockState state = hud.client.level.getBlockState(block.getBlockPos());
            BlockPos pos = block.getBlockPos();
            return List.of(
               new TextHudWidget.Line(state.getBlock().getName().getString(), hud.accent),
               new TextHudWidget.Line(pos.getX() + "  " + pos.getY() + "  " + pos.getZ(), -6641995)
            );
         } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            List<TextHudWidget.Line> lines = new ArrayList<>(2);
            lines.add(new TextHudWidget.Line(entity.getName().getString(), hud.accent));
            if (entity instanceof LivingEntity living) {
               lines.add(
                  new TextHudWidget.Line(
                     String.format(Locale.ROOT, "%.1f / %.0f HP", living.getHealth(), living.getMaxHealth()),
                     HudWidgets.ratioColor(living.getHealth() / Math.max(1.0F, living.getMaxHealth()))
                  )
               );
            }

            return lines;
         } else {
            return List.of();
         }
      }
   }

   static final class Memory extends HudWidgets.Ticked {
      Memory() {
         super("Memory", "Java heap usage of the game.", "Bottom Right", 6, 78, 1000);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         Runtime runtime = Runtime.getRuntime();
         long max = runtime.maxMemory() / 1048576L;
         long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
         float ratio = max <= 0L ? 0.0F : (float)used / (float)max;
         return List.of(new TextHudWidget.Line("Mem " + used + " / " + max + " MB", ratio < 0.7F ? -1511689 : (ratio < 0.9F ? -14249 : -1548963)));
      }
   }

   static final class Position extends HudWidgets.Ticked {
      private final BooleanSetting decimals = this.addSetting(new BooleanSetting("Decimals", "Show fractional coordinates", false));
      private final BooleanSetting nether = this.addSetting(new BooleanSetting("Nether Coords", "Also show the matching Nether or Overworld position", true));
      private final BooleanSetting dimension = this.addSetting(new BooleanSetting("Dimension", "Show the dimension name", false));

      Position() {
         super("Position", "Your coordinates, with the Nether or Overworld equivalent.", "Top Left", 6, 34, 100);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         LocalPlayer player = hud.client.player;
         ClientLevel level = hud.client.level;
         List<TextHudWidget.Line> lines = new ArrayList<>(3);
         if (this.decimals.get()) {
            lines.add(new TextHudWidget.Line(String.format(Locale.ROOT, "XYZ %.2f  %.2f  %.2f", player.getX(), player.getY(), player.getZ()), -1511689));
         } else {
            lines.add(new TextHudWidget.Line("XYZ " + Mth.floor(player.getX()) + "  " + Mth.floor(player.getY()) + "  " + Mth.floor(player.getZ()), -1511689));
         }

         if (this.nether.get() && level != null) {
            boolean inNether = level.dimension() == Level.NETHER;
            if (inNether || level.dimension() == Level.OVERWORLD) {
               double factor = inNether ? 8.0 : 0.125;
               lines.add(
                  new TextHudWidget.Line(
                     (inNether ? "Overworld " : "Nether ") + Math.round(player.getX() * factor) + "  " + Math.round(player.getZ() * factor), -6641995
                  )
               );
            }
         }

         if (this.dimension.get() && level != null) {
            lines.add(new TextHudWidget.Line(HudWidgets.pretty(level.dimension().identifier().getPath()), hud.accent));
         }

         return lines;
      }
   }

   static final class PotionTimers extends HudWidgets.Ticked {
      PotionTimers() {
         super("Potion Timers", "Active status effects with their remaining time.", "Right Center", 6, 0, 250);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         List<MobEffectInstance> effects = new ArrayList<>(hud.client.player.getActiveEffects());
         if (effects.isEmpty()) {
            return List.of();
         } else {
            effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
            List<TextHudWidget.Line> lines = new ArrayList<>(effects.size());

            for (MobEffectInstance effect : effects) {
               String name = ((MobEffect)effect.getEffect().value()).getDisplayName().getString();
               if (effect.getAmplifier() > 0) {
                  name = name + " " + HudWidgets.roman(effect.getAmplifier() + 1);
               }

               String time = effect.isInfiniteDuration() ? "inf" : HudWidgets.ticksToClock(effect.getDuration());
               MobEffectCategory category = ((MobEffect)effect.getEffect().value()).getCategory();
               int color = category == MobEffectCategory.HARMFUL ? -1548963 : (category == MobEffectCategory.BENEFICIAL ? -11086174 : -1511689);
               lines.add(new TextHudWidget.Line(name + "  " + time, color));
            }

            return lines;
         }
      }
   }

   static final class Radar extends HudWidget {
      private final NumberSetting size = this.addSetting(new NumberSetting("Size", "Radar box size in pixels", 76.0, 50.0, 130.0, 2.0));
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks shown from center", 48.0, 16.0, 128.0, 4.0));

      Radar() {
         super("Radar", "2D rotating entity radar showing nearby players and mobs.", "Top Left", 6, 40);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return this.size.intValue();
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return this.size.intValue();
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         LocalPlayer self = hud.client.player;
         int cx = w / 2;
         int cy = h / 2;
         hud.graphics.fill(cx, 2, cx + 1, h - 2, 872415231);
         hud.graphics.fill(2, cy, w - 2, cy + 1, 872415231);
         if (self != null && hud.client.level != null) {
            double maxR = this.range.get();
            double yawRad = Math.toRadians(self.getYRot() + 180.0F);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);

            for (Entity entity : hud.client.level.entitiesForRendering()) {
               if (entity != self && entity instanceof LivingEntity) {
                  double dx = entity.getX() - self.getX();
                  double dz = entity.getZ() - self.getZ();
                  double rx = dx * cos - dz * sin;
                  double rz = dx * sin + dz * cos;
                  int px = cx + (int)Math.round(rx / maxR * (cx - 4));
                  int py = cy + (int)Math.round(rz / maxR * (cy - 4));
                  if (px >= 3 && px < w - 3 && py >= 3 && py < h - 3) {
                     int dotColor = entity instanceof Player ? -16722689 : -1548963;
                     hud.graphics.fill(px - 1, py - 1, px + 2, py + 2, dotColor);
                  }
               }
            }
         }

         hud.graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, -1);
      }
   }

   static final class SessionTimer extends HudWidgets.Ticked {
      private final BooleanSetting distance = this.addSetting(new BooleanSetting("Distance", "Also show blocks travelled this session", true));
      private long started;
      private double travelled;

      SessionTimer() {
         super("Session Timer", "How long you have been playing and how far you walked.", "Bottom Right", 6, 96, 250);
      }

      @Override
      public void onEnable() {
         this.started = System.currentTimeMillis();
         this.travelled = 0.0;
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            double step = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
            if (step < 8.0) {
               this.travelled += step;
            }
         }
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         long seconds = (System.currentTimeMillis() - this.started) / 1000L;
         String time = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
         return !this.distance.get()
            ? List.of(new TextHudWidget.Line("Session " + time, -1511689))
            : List.of(new TextHudWidget.Line("Session " + time, -1511689), new TextHudWidget.Line(Math.round(this.travelled) + " blocks walked", -6641995));
      }
   }

   static final class Speedometer extends HudWidgets.Ticked {
      private final ModeSetting unit = this.addSetting(new ModeSetting("Unit", "How speed is displayed", "Blocks/s", "Blocks/s", "km/h", "Blocks/tick"));
      private final BooleanSetting vertical = this.addSetting(new BooleanSetting("Vertical", "Include vertical speed", false));

      Speedometer() {
         super("Speedometer", "How fast you are moving.", "Bottom Right", 6, 60, 100);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         LocalPlayer player = hud.client.player;
         double dx = player.getX() - player.xo;
         double dz = player.getZ() - player.zo;
         double dy = this.vertical.get() ? player.getY() - player.yo : 0.0;
         double perTick = Math.sqrt(dx * dx + dy * dy + dz * dz);
         String var12 = this.unit.get();

         String text = switch (var12) {
            case "km/h" -> String.format(Locale.ROOT, "%.1f km/h", perTick * 20.0 * 3.6);
            case "Blocks/tick" -> String.format(Locale.ROOT, "%.3f b/t", perTick);
            default -> String.format(Locale.ROOT, "%.1f b/s", perTick * 20.0);
         };
         return List.of(new TextHudWidget.Line(text, -1511689));
      }
   }

   static final class TargetHud extends HudWidget {
      private final BooleanSetting showGear = this.addSetting(new BooleanSetting("Show Gear", "Render target held item and armor icons", true));
      private float animHealth = 20.0F;
      private float trailHealth = 20.0F;

      TargetHud() {
         super("TargetHUD", "LiquidBounce Nextgen Target HUD card with animated health bar, gear icons, and win/loss indicator.", "Bottom Center", 90, 68);
      }

      private LivingEntity findTarget(LocalPlayer self) {
         if (self != null && self.level() != null) {
            LivingEntity best = null;
            double bestD = 36.0;

            for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
               if (e != self && e instanceof LivingEntity living && living.isAlive() && (e instanceof Player || living.hurtTime > 0)) {
                  double d = self.distanceToSqr(e);
                  if (d < bestD) {
                     bestD = d;
                     best = living;
                  }
               }
            }

            return best;
         } else {
            return null;
         }
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 154;
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return this.showGear.get() ? 52 : 38;
      }

      @Override
      protected boolean shouldRenderContent(HudContext hud) {
         return Menus.isMenu(hud.client.gui.screen()) || this.findTarget(hud.client.player) != null;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         LocalPlayer self = hud.client.player;
         LivingEntity target = this.findTarget(self);
         if (target == null) {
            hud.graphics.text(hud.font, "TargetHUD · Preview", 8, 6, -1, hud.shadow);
            hud.graphics.text(hud.font, "20.0 HP  ·  0.0m  ·  WIN", 8, 16, -6641995, false);
            int barY = this.showGear.get() ? 42 : 27;
            hud.graphics.fill(8, barY, w - 8, barY + 6, -14142908);
            hud.graphics.fill(8, barY, w - 28, barY + 6, hud.accent);
         } else {
            float hp = target.getHealth() + target.getAbsorptionAmount();
            float maxHp = Math.max(1.0F, target.getMaxHealth() + target.getAbsorptionAmount());
            this.animHealth = Mth.lerp(0.22F, this.animHealth, hp);
            this.trailHealth = Mth.lerp(0.07F, this.trailHealth, hp);
            float ratio = Mth.clamp(this.animHealth / maxHp, 0.0F, 1.0F);
            float trailRatio = Mth.clamp(this.trailHealth / maxHp, ratio, 1.0F);
            boolean winning = self.getHealth() + self.getAbsorptionAmount() >= hp;
            if (target.hurtTime > 0) {
               hud.graphics.fill(0, 0, w, h, GeminiMark.withAlpha(-1548963, target.hurtTime * 10));
            }

            String name = target.getName().getString();
            String badge = winning ? "WIN" : "LOSE";
            hud.graphics.text(hud.font, name, 8, 5, -1, hud.shadow);
            hud.graphics.text(hud.font, badge, w - 8 - hud.font.width(badge), 5, winning ? -11086174 : -1548963, hud.shadow);
            String stats = String.format(Locale.ROOT, "%.1f HP  ·  %.1fm  ·  HT %d", hp, self.distanceTo(target), target.hurtTime);
            hud.graphics.text(hud.font, stats, 8, 15, -6641995, false);
            int barW = w - 16;
            int barY = this.showGear.get() ? 42 : 27;
            hud.graphics.fill(8, barY, 8 + barW, barY + 6, -14142908);
            hud.graphics.fill(8, barY, 8 + Math.round(barW * trailRatio), barY + 6, GeminiMark.withAlpha(-1548963, 140));
            hud.graphics.fill(8, barY, 8 + Math.round(barW * ratio), barY + 6, HudWidgets.ratioColor(ratio));
            if (this.showGear.get()) {
               ItemStack[] gear = new ItemStack[]{
                  target.getMainHandItem(),
                  target.getOffhandItem(),
                  target.getItemBySlot(EquipmentSlot.HEAD),
                  target.getItemBySlot(EquipmentSlot.CHEST),
                  target.getItemBySlot(EquipmentSlot.LEGS),
                  target.getItemBySlot(EquipmentSlot.FEET)
               };
               int gx = 8;

               for (ItemStack stack : gear) {
                  if (!stack.isEmpty()) {
                     hud.graphics.item(stack, gx, 24);
                     gx += 18;
                  }
               }
            }
         }
      }
   }

   abstract static class Ticked extends TextHudWidget {
      private final int refreshMs;
      private List<TextHudWidget.Line> cache = List.of();
      private long stamp;

      Ticked(String name, String description, String corner, int x, int y, int refreshMs) {
         super(name, description, corner, x, y);
         this.refreshMs = refreshMs;
      }

      @Override
      protected final List<TextHudWidget.Line> lines(HudContext hud) {
         long now = System.currentTimeMillis();
         if (now - this.stamp >= this.refreshMs || now < this.stamp) {
            this.cache = this.build(hud);
            this.stamp = now;
         }

         return this.cache;
      }

      protected abstract List<TextHudWidget.Line> build(HudContext var1);
   }

   static final class TotemCounter extends HudWidget {
      TotemCounter() {
         super("Totem Counter", "How many Totems of Undying you are carrying.", "Bottom Center", 0, 110);
      }

      @Override
      protected int widgetWidth(HudContext hud) {
         return 30 + hud.font.width("00x");
      }

      @Override
      protected int widgetHeight(HudContext hud) {
         return 24;
      }

      @Override
      protected void paint(HudContext hud, int w, int h) {
         int amount = HudWidgets.count(hud.client.player, Items.TOTEM_OF_UNDYING);
         hud.graphics.item(new ItemStack(Items.TOTEM_OF_UNDYING), 6, 4);
         hud.graphics.text(hud.font, amount + "x", 28, 8, amount == 0 ? -1548963 : (amount == 1 ? -14249 : -11086174), hud.shadow);
      }
   }

   static final class Tps extends HudWidgets.Ticked {
      private static final int SAMPLES = 5;
      private final double[] samples = new double[5];
      private int filled;
      private int next;
      private long lastGameTime = Long.MIN_VALUE;
      private long lastReal;
      private volatile double current = 20.0;
      private volatile long lastUpdate;

      Tps() {
         super("TPS", "Server ticks per second, estimated from time updates.", "Bottom Right", 6, 42, 500);
      }

      @Override
      public void onEnable() {
         this.lastGameTime = Long.MIN_VALUE;
         this.filled = 0;
         this.next = 0;
         this.lastUpdate = 0L;
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         if (packet instanceof ClientboundSetTimePacket time) {
            long now = System.nanoTime();
            if (this.lastGameTime != Long.MIN_VALUE && time.gameTime() > this.lastGameTime) {
               double seconds = (now - this.lastReal) / 1.0E9;
               if (seconds > 0.2) {
                  double rate = Math.min(20.0, (time.gameTime() - this.lastGameTime) / seconds);
                  this.samples[this.next] = rate;
                  this.next = (this.next + 1) % 5;
                  this.filled = Math.min(5, this.filled + 1);
                  double sum = 0.0;

                  for (int i = 0; i < this.filled; i++) {
                     sum += this.samples[i];
                  }

                  this.current = sum / this.filled;
                  this.lastUpdate = System.currentTimeMillis();
               }
            }

            this.lastGameTime = time.gameTime();
            this.lastReal = now;
         }

         return false;
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         if (hud.client.hasSingleplayerServer()) {
            return List.of(new TextHudWidget.Line("TPS 20.0", -11086174));
         } else if (this.lastUpdate == 0L) {
            return List.of(new TextHudWidget.Line("TPS ...", -6641995));
         } else {
            double tps = this.current;
            return List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "TPS %.1f", tps), tps >= 19.0 ? -11086174 : (tps >= 15.0 ? -14249 : -1548963)));
         }
      }
   }

   static final class Welcome extends HudWidgets.Ticked {
      private final ListSetting message = this.addSetting(
         new ListSetting("Message", "Greeting text; {player} becomes your name", ListSetting.Kind.TEXT, "Welcome to Gemini, {player}")
      );

      Welcome() {
         super("Welcome", "A greeting with your name.", "Top Center", 0, 28, 1000);
      }

      @Override
      protected List<TextHudWidget.Line> build(HudContext hud) {
         String text = this.message.isEmpty() ? "Welcome, {player}" : this.message.get().getFirst();
         return List.of(new TextHudWidget.Line(text.replace("{player}", hud.client.player.getName().getString()), hud.accent));
      }
   }
}
