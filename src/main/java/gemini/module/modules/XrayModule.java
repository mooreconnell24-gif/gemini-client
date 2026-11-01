package gemini.module.modules;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import gemini.ui.HudContext;
import gemini.xray.XrayConfig;
import gemini.xray.XrayData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class XrayModule extends Module {
   private static final int RELOAD_DELAY_TICKS = 8;
   private static final int REFRESH_TICKS = 10;
   private static final int SCAN_CAP = 20000;
   private static final List<XrayModule.Preset> PRESETS = List.of(
      new XrayModule.Preset("Coal", "Coal ore", -7695456, true, "coal_ore", "deepslate_coal_ore"),
      new XrayModule.Preset("Iron", "Iron ore and raw iron blocks", -1848908, true, "iron_ore", "deepslate_iron_ore", "raw_iron_block"),
      new XrayModule.Preset("Copper", "Copper ore and raw copper blocks", -2061732, true, "copper_ore", "deepslate_copper_ore", "raw_copper_block"),
      new XrayModule.Preset(
         "Gold", "Gold ore, nether gold and raw gold blocks", -11444, true, "gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold_block"
      ),
      new XrayModule.Preset("Redstone", "Redstone ore", -46261, true, "redstone_ore", "deepslate_redstone_ore"),
      new XrayModule.Preset("Lapis", "Lapis lazuli ore", -10716417, true, "lapis_ore", "deepslate_lapis_ore"),
      new XrayModule.Preset("Diamond", "Diamond ore", -10690049, true, "diamond_ore", "deepslate_diamond_ore"),
      new XrayModule.Preset("Emerald", "Emerald ore", -11804550, true, "emerald_ore", "deepslate_emerald_ore"),
      new XrayModule.Preset("Quartz", "Nether quartz ore", -857123, true, "nether_quartz_ore"),
      new XrayModule.Preset("Ancient Debris", "Ancient debris", -4884917, true, "ancient_debris"),
      new XrayModule.Preset("Spawners", "Monster spawners, trial spawners and vaults", -5214977, true, "spawner", "trial_spawner", "vault"),
      new XrayModule.Preset(
         "Storage",
         "Chests, barrels, ender chests and shulker boxes",
         -1657776,
         false,
         "chest",
         "trapped_chest",
         "barrel",
         "ender_chest",
         "shulker_box",
         "white_shulker_box",
         "orange_shulker_box",
         "magenta_shulker_box",
         "light_blue_shulker_box",
         "yellow_shulker_box",
         "lime_shulker_box",
         "pink_shulker_box",
         "gray_shulker_box",
         "light_gray_shulker_box",
         "cyan_shulker_box",
         "purple_shulker_box",
         "blue_shulker_box",
         "brown_shulker_box",
         "green_shulker_box",
         "red_shulker_box",
         "black_shulker_box"
      ),
      new XrayModule.Preset(
         "Portals", "Nether portals, end portals and end portal frames", -4104961, false, "nether_portal", "end_portal", "end_portal_frame", "end_gateway"
      ),
      new XrayModule.Preset(
         "Amethyst",
         "Budding amethyst and amethyst clusters",
         -4879105,
         false,
         "budding_amethyst",
         "amethyst_cluster",
         "large_amethyst_bud",
         "medium_amethyst_bud",
         "small_amethyst_bud"
      )
   );
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Hide strips non-ores; Ghost renders translucent terrain", "Hide", "Hide", "Ghost")
         .onChange(this::markDirty)
   );
   private final NumberSetting opacity = this.addSetting(
      new NumberSetting("Opacity", "World terrain opacity percentage in Ghost mode", 12.0, 2.0, 80.0, 1.0)
         .showIf(() -> this.mode.is("Ghost"))
         .onChange(this::markDirty)
   );
   private final BooleanSetting exposedOnly = this.addSetting(
      new BooleanSetting("Exposed Only", "Only show ores touching air or fluid (anti-antixray)", false)
         .showIf(() -> this.mode.is("Hide"))
         .onChange(this::markDirty)
   );
   private final BooleanSetting fullbright = this.addSetting(
      new BooleanSetting("Fullbright", "Render Xray target blocks at full brightness", true).onChange(this::markDirty)
   );
   private final BooleanSetting lava = this.addSetting(
      new BooleanSetting("Show Lava", "Keep lava visible while Xray Hide mode is active", true)
         .showIf(() -> this.mode.is("Hide"))
         .onChange(this::markDirty)
   );
   private final BooleanSetting water = this.addSetting(
      new BooleanSetting("Show Water", "Keep water visible in Hide mode", false).showIf(() -> this.mode.is("Hide")).onChange(this::markDirty)
   );
   private final BooleanSetting[] presetToggles = new BooleanSetting[PRESETS.size()];
   private final ListSetting custom = this.addSetting(
      new ListSetting("Custom Blocks", "Extra blocks to reveal, picked from the block list", ListSetting.Kind.BLOCK).onChange(this::markDirty)
   );
   private final BooleanSetting outlines = this.addSetting(new BooleanSetting("Outlines", "Draw 3D outline boxes around nearby target ores", false));
   private final BooleanSetting tracers = this.addSetting(new BooleanSetting("Tracers", "Draw lines from the crosshair to the nearest targets", false));
   private final ShapeSettings shape = new ShapeSettings(this, "Lines", true, () -> this.outlines.get() || this.tracers.get());
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far outlines, tracers and counts look", 48.0, 8.0, 128.0, 4.0));
   private final NumberSetting maxOutlines = this.addSetting(
      new NumberSetting("Max Outlines", "Most boxes drawn per frame, nearest first", 96.0, 8.0, 512.0, 8.0).showIf(this.outlines::get)
   );
   private final NumberSetting tracerCount = this.addSetting(
      new NumberSetting("Tracer Count", "How many of the nearest targets get a line", 3.0, 1.0, 16.0, 1.0).showIf(this.tracers::get)
   );
   private final BooleanSetting counts = this.addSetting(new BooleanSetting("Show Counts", "List nearby targets by type on the HUD", true));
   private final ModeSetting countsSide = this.addSetting(
      new ModeSetting("Counts Side", "Which edge of the screen the list sits on", "Left", "Left", "Right").showIf(this.counts::get)
   );
   private final ActionSetting rebuild = this.addSetting(new ActionSetting("Rebuild Chunks", "Rebuild every visible chunk right now", this::rebuildNow));
   private List<List<Block>> presetBlocks;
   private int reloadIn = -1;
   private int refreshIn;
   private boolean smartCullOverridden;
   private boolean previousSmartCull;
   private long[] nearPositions = new long[0];
   private int[] nearColors = new int[0];
   private int nearCount;
   private List<XrayModule.Group> groups = List.of();
   private final LongArrayList scan = new LongArrayList();

   public XrayModule() {
      super("Xray", "Render target ores and blocks through solid terrain.", Category.RENDER);

      for (int i = 0; i < PRESETS.size(); i++) {
         XrayModule.Preset preset = PRESETS.get(i);
         this.presetToggles[i] = this.addSetting(new BooleanSetting(preset.label(), preset.description(), preset.defaultOn()).onChange(this::markDirty));
      }
   }

   @Override
   public String info() {
      return this.mode.is("Ghost") ? "Ghost " + this.opacity.intValue() + "%" : "Hide";
   }

   private void markDirty() {
      if (this.isEnabled()) {
         this.reloadIn = 8;
      }
   }

   private void rebuildNow() {
      if (this.isEnabled()) {
         this.reloadIn = 0;
      }
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length != 1 || !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("rebuild")) {
         return false;
      } else {
         this.rebuildNow();
         CommandManager.feedback("§bXray §7rebuilding chunks");
         return true;
      }
   }

   @Override
   public void onEnable() {
      this.reloadIn = -1;
      this.refreshIn = 0;
      this.applyAndReload();
   }

   @Override
   public void onDisable() {
      this.reloadIn = -1;
      XrayConfig.install(null);
      XrayData.clear();
      this.nearCount = 0;
      this.groups = List.of();
      Minecraft client = Minecraft.getInstance();
      if (this.smartCullOverridden) {
         client.smartCull = this.previousSmartCull;
         this.smartCullOverridden = false;
      }

      client.levelExtractor.allChanged();
   }

   private void applyAndReload() {
      Minecraft client = Minecraft.getInstance();
      boolean ghost = this.mode.is("Ghost");
      if (ghost && !this.smartCullOverridden) {
         this.previousSmartCull = client.smartCull;
         client.smartCull = false;
         this.smartCullOverridden = true;
      } else if (!ghost && this.smartCullOverridden) {
         client.smartCull = this.previousSmartCull;
         this.smartCullOverridden = false;
      }

      XrayConfig.install(this.buildConfig());
      client.levelExtractor.allChanged();
   }

   private void resolvePresets() {
      if (this.presetBlocks == null) {
         List<List<Block>> resolved = new ArrayList<>();

         for (XrayModule.Preset preset : PRESETS) {
            List<Block> blocks = new ArrayList<>();

            for (String id : preset.ids()) {
               Identifier identifier = Identifier.tryParse("minecraft:" + id);
               if (identifier != null) {
                  BuiltInRegistries.BLOCK.getOptional(identifier).ifPresent(blocks::add);
               }
            }

            resolved.add(blocks);
         }

         this.presetBlocks = resolved;
      }
   }

   private XrayConfig buildConfig() {
      this.resolvePresets();
      ReferenceOpenHashSet<Block> targets = new ReferenceOpenHashSet();
      Reference2IntOpenHashMap<Block> colors = new Reference2IntOpenHashMap();

      for (int i = 0; i < PRESETS.size(); i++) {
         if (this.presetToggles[i].get()) {
            for (Block block : this.presetBlocks.get(i)) {
               targets.add(block);
               colors.put(block, PRESETS.get(i).color());
            }
         }
      }

      for (Block block : this.custom.blocks()) {
         if (targets.add(block)) {
            colors.put(block, customColor(block));
         }
      }

      ReferenceOpenHashSet<Block> fluids = new ReferenceOpenHashSet();
      if (this.lava.get()) {
         fluids.add(Blocks.LAVA);
      }

      if (this.water.get()) {
         fluids.add(Blocks.WATER);
      }

      boolean ghost = this.mode.is("Ghost");
      int alpha = ghost ? Mth.clamp(Math.round(this.opacity.floatValue() * 2.55F), 1, 254) : 0;
      return new XrayConfig(targets, fluids, colors, alpha, this.exposedOnly.get() && !ghost, this.fullbright.get());
   }

   private static int customColor(Block block) {
      int hash = BuiltInRegistries.BLOCK.getKey(block).hashCode() & 2147483647;
      return ColorSetting.hueColor(hash % 360 / 360.0F, 0.65F, 1.0F);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         if (this.reloadIn >= 0) {
            if (this.reloadIn == 0) {
               this.reloadIn = -1;
               this.applyAndReload();
               this.refreshIn = 0;
            } else {
               this.reloadIn--;
            }
         }

         if (--this.refreshIn <= 0) {
            this.refreshIn = 10;
            this.refresh(client);
         }
      }
   }

   private void refresh(Minecraft client) {
      XrayConfig config = XrayConfig.current();
      if (config != null && (this.outlines.get() || this.tracers.get() || this.counts.get())) {
         BlockPos origin = client.player.blockPosition();
         XrayData.prune(origin.getX() >> 4, origin.getZ() >> 4, client.options.getEffectiveRenderDistance() + 2);
         this.scan.clear();
         XrayData.visitNear(origin, this.range.get(), packed -> {
            if (this.scan.size() < 20000) {
               this.scan.add(packed);
            }
         });
         int size = this.scan.size();
         long[] keys = new long[size];
         int[] blockColors = new int[size];
         int valid = 0;
         MutableBlockPos cursor = new MutableBlockPos();
         double ox = client.player.getX();
         double oy = client.player.getY();
         double oz = client.player.getZ();
         Map<Block, XrayModule.Group> grouped = new LinkedHashMap<>();

         for (int i = 0; i < size; i++) {
            long packed = this.scan.getLong(i);
            cursor.set(packed);
            BlockState state = client.level.getBlockState(cursor);
            if (config.isTarget(state)) {
               Block block = state.getBlock();
               double dx = BlockPos.getX(packed) + 0.5 - ox;
               double dy = BlockPos.getY(packed) + 0.5 - oy;
               double dz = BlockPos.getZ(packed) + 0.5 - oz;
               double distanceSq = dx * dx + dy * dy + dz * dz;
               keys[valid] = (long)Math.min(Integer.MAX_VALUE, (int)(distanceSq * 16.0)) << 32 | valid;
               this.scan.set(valid, packed);
               blockColors[valid] = config.color(block);
               XrayModule.Group group = grouped.computeIfAbsent(block, key -> new XrayModule.Group(key.getName().getString(), config.color(key)));
               group.count++;
               group.nearest = Math.min(group.nearest, Math.sqrt(distanceSq));
               valid++;
            }
         }

         Arrays.sort(keys, 0, valid);
         long[] sortedPositions = new long[valid];
         int[] sortedColors = new int[valid];

         for (int ix = 0; ix < valid; ix++) {
            int index = (int)(keys[ix] & 4294967295L);
            sortedPositions[ix] = this.scan.getLong(index);
            sortedColors[ix] = blockColors[index];
         }

         this.nearPositions = sortedPositions;
         this.nearColors = sortedColors;
         this.nearCount = valid;
         List<XrayModule.Group> sorted = new ArrayList<>(grouped.values());
         sorted.sort(Comparator.comparingDouble(groupx -> groupx.nearest));
         this.groups = sorted;
      } else {
         this.nearCount = 0;
         this.groups = List.of();
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      boolean boxes = this.outlines.get();
      boolean lines = this.tracers.get();
      if ((boxes || lines) && this.nearCount != 0) {
         if (boxes) {
            int limit = Math.min(this.nearCount, this.maxOutlines.intValue());

            for (int i = 0; i < limit; i++) {
               this.shape.block(BlockPos.of(this.nearPositions[i]), this.nearColors[i]);
            }
         }

         if (lines) {
            Vec3 from = Render3D.crosshair();
            int limit = Math.min(this.nearCount, this.tracerCount.intValue());

            for (int i = 0; i < limit; i++) {
               this.shape.line(from, Vec3.atCenterOf(BlockPos.of(this.nearPositions[i])), this.nearColors[i]);
            }
         }
      }
   }

   @Override
   public void onRenderHud(HudContext hud) {
      List<XrayModule.Group> rows = this.groups;
      if (this.counts.get() && !rows.isEmpty()) {
         int shown = Math.min(rows.size(), 10);
         String title = "Xray";
         int widest = hud.font.width(title);
         String[] lines = new String[shown];

         for (int i = 0; i < shown; i++) {
            XrayModule.Group group = rows.get(i);
            lines[i] = group.label + "  x" + group.count + "  " + String.format(Locale.ROOT, "%.0fm", group.nearest);
            widest = Math.max(widest, hud.font.width(lines[i]) + 10);
         }

         boolean right = this.countsSide.is("Right");
         int x = right ? hud.width - 6 - widest : 6;
         int y = hud.height / 2 - (shown * 10 + 14) / 2;
         hud.graphics.fill(x - 4, y - 3, x + widest + 4, y + 12 + shown * 10, -1441851872);
         hud.graphics.fill(x - 4, y - 3, x - 2, y + 12 + shown * 10, hud.accent);
         hud.graphics.text(hud.font, title, x, y, hud.accent, hud.shadow);

         for (int i = 0; i < shown; i++) {
            int rowY = y + 12 + i * 10;
            XrayModule.Group group = rows.get(i);
            hud.graphics.fill(x, rowY + 1, x + 6, rowY + 7, group.color);
            hud.graphics.text(hud.font, lines[i], x + 10, rowY, -1511689, hud.shadow);
         }
      }
   }

   private static final class Group {
      private final String label;
      private final int color;
      private int count;
      private double nearest = Double.MAX_VALUE;

      private Group(String label, int color) {
         this.label = label;
         this.color = color;
      }
   }

   private record Preset(String label, String description, int color, boolean defaultOn, String... ids) {
   }
}
