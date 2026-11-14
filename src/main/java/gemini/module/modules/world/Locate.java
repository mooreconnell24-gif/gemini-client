package gemini.module.modules.world;

import com.mojang.datafixers.util.Pair;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Finds the nearest structure or biome on the integrated server, pins it and optionally teleports there. */
public final class Locate extends Module {
   private static final String[] STRUCTURES = new String[]{
      "Village",
      "Pillager Outpost",
      "Mineshaft",
      "Woodland Mansion",
      "Jungle Pyramid",
      "Desert Pyramid",
      "Igloo",
      "Shipwreck",
      "Swamp Hut",
      "Stronghold",
      "Monument",
      "Ocean Ruin",
      "Buried Treasure",
      "Ruined Portal",
      "Ancient City",
      "Trail Ruins",
      "Trial Chambers",
      "Abandoned Camp",
      "Fortress",
      "Bastion Remnant",
      "Nether Fossil",
      "End City"
   };
   private static final String[] BIOMES = new String[]{
      "Mushroom Fields",
      "Cherry Grove",
      "Deep Dark",
      "Lush Caves",
      "Dripstone Caves",
      "Badlands",
      "Jungle",
      "Bamboo Jungle",
      "Dark Forest",
      "Ice Spikes",
      "Flower Forest",
      "Mangrove Swamp",
      "Swamp",
      "Savanna",
      "Desert",
      "Pale Garden",
      "Frozen Ocean",
      "Warm Ocean",
      "Meadow",
      "Jagged Peaks",
      "Soul Sand Valley",
      "Basalt Deltas",
      "Crimson Forest",
      "Warped Forest"
   };
   private final ModeSetting kind = this.addSetting(new ModeSetting("Find", "Look for a structure or a biome", "Structure", "Structure", "Biome"));
   private final ModeSetting structure = this.addSetting(
      new ModeSetting("Structure", "Which structure", "Village", STRUCTURES).showIf(() -> this.kind.is("Structure"))
   );
   private final ModeSetting biome = this.addSetting(new ModeSetting("Biome", "Which biome", "Mushroom Fields", BIOMES).showIf(() -> this.kind.is("Biome")));
   private final NumberSetting radius = this.addSetting(
      new NumberSetting("Radius", "Search distance in chunks (large searches take a moment)", 100.0, 10.0, 400.0, 10.0)
   );
   private final BooleanSetting pin = this.addSetting(new BooleanSetting("Pin Waypoint", "Drop a waypoint at what was found", true));
   private final BooleanSetting teleport = this.addSetting(new BooleanSetting("Teleport", "Teleport to the surface above what was found", false));
   private boolean busy;
   private String last;

   public Locate() {
      super("Locate", "Find the nearest structure or biome, pin it and optionally teleport there.", Category.WORLD);
      this.addSetting(new ActionSetting("Search", "Find the nearest one now", this::search));
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
   public void onEnable() {
      this.search();
      this.setEnabled(false, false);
   }

   private static String id(String label) {
      return "Woodland Mansion".equals(label) ? "mansion" : label.toLowerCase(Locale.ROOT).replace(' ', '_');
   }

   private void search() {
      Minecraft client = Minecraft.getInstance();
      IntegratedServer server = client.getSingleplayerServer();
      if (!SingleplayerGate.allowed() || server == null || client.player == null) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (this.busy) {
         CommandManager.feedback("§bLocate §7is still searching.");
      } else {
         boolean wantBiome = this.kind.is("Biome");
         String label = wantBiome ? this.biome.get() : this.structure.get();
         Identifier id = Identifier.withDefaultNamespace(id(label));
         int chunks = this.radius.intValue();
         UUID playerId = client.player.getUUID();
         this.busy = true;
         CommandManager.feedback("§bLocate §7searching for the nearest §f" + label + "§7...");
         server.execute(() -> {
            BlockPos found = null;

            try {
               ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
               ServerLevel level = serverPlayer != null ? serverPlayer.level() : server.overworld();
               BlockPos origin = serverPlayer != null ? serverPlayer.blockPosition() : BlockPos.ZERO;
               found = wantBiome ? findBiome(level, origin, id, chunks) : findStructure(server, level, origin, id, chunks);
            } catch (RuntimeException e) {
               Gemini.LOGGER.warn("Locate failed", e);
            }

            BlockPos result = found;
            client.execute(() -> this.report(label, result));
         });
      }
   }

   private static BlockPos findBiome(ServerLevel level, BlockPos origin, Identifier id, int chunks) {
      ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
      Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(holder -> holder.is(key), origin, chunks * 16, 32, 64);
      return hit == null ? null : hit.getFirst();
   }

   private static BlockPos findStructure(IntegratedServer server, ServerLevel level, BlockPos origin, Identifier id, int chunks) {
      Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
      Optional<HolderSet.Named<Structure>> tagged = registry.get(TagKey.create(Registries.STRUCTURE, id));
      HolderSet<Structure> targets;
      if (tagged.isPresent()) {
         targets = tagged.get();
      } else {
         Structure single = registry.getOptional(id).orElse(null);
         targets = single == null ? null : HolderSet.direct(registry.wrapAsHolder(single));
      }

      return targets == null ? null : level.findNearestMapStructure(targets, origin, chunks, false);
   }

   private void report(String label, BlockPos found) {
      this.busy = false;
      Minecraft client = Minecraft.getInstance();
      if (found == null) {
         this.last = null;
         CommandManager.feedback("§cLocate §7found no §f" + label + " §7within " + this.radius.intValue() + " chunks of here.");
      } else {
         int distance = client.player == null ? 0 : (int)Math.sqrt(client.player.blockPosition().distSqr(found));
         this.last = label;
         CommandManager.feedback("§bLocate §7nearest §f" + label + " §7is at §f" + found.getX() + " ~ " + found.getZ() + " §7(" + distance + "m away).");
         if (this.pin.get() && Gemini.getInstance() != null) {
            int y = found.getY() == 0 ? (client.player == null ? 64 : client.player.getBlockY()) : found.getY();
            Gemini.getInstance().modules().byClass(WaypointsModule.class).ifPresent(waypoints -> waypoints.pin(label, found.getX(), y, found.getZ()));
         }

         if (this.teleport.get() && client.level != null) {
            SpCommands.run(String.format(Locale.ROOT, "execute positioned %d 0 %d run spreadplayers ~ ~ 0 1 false @s", found.getX(), found.getZ()));
         }
      }
   }

   @Override
   public String info() {
      return this.last != null ? this.last : (this.kind.is("Biome") ? this.biome.get() : this.structure.get());
   }
}
