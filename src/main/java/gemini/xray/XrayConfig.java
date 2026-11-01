package gemini.xray;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class XrayConfig {
   private static volatile XrayConfig current;
   private final ReferenceOpenHashSet<Block> targets;
   private final ReferenceOpenHashSet<Block> visible;
   private final Reference2IntOpenHashMap<Block> colors;
   private final int ghostAlpha;
   private final boolean exposedOnly;
   private final boolean fullbright;

   public XrayConfig(
      ReferenceOpenHashSet<Block> targets,
      ReferenceOpenHashSet<Block> keptFluids,
      Reference2IntOpenHashMap<Block> colors,
      int ghostAlpha,
      boolean exposedOnly,
      boolean fullbright
   ) {
      this.targets = targets;
      this.visible = new ReferenceOpenHashSet(targets);
      this.visible.addAll(keptFluids);
      this.colors = colors;
      this.colors.defaultReturnValue(0);
      this.ghostAlpha = ghostAlpha;
      this.exposedOnly = exposedOnly;
      this.fullbright = fullbright;
   }

   public static XrayConfig current() {
      return current;
   }

   public static void install(XrayConfig config) {
      current = config;
   }

   public boolean isTarget(BlockState state) {
      return this.targets.contains(state.getBlock());
   }

   public boolean isTarget(Block block) {
      return this.targets.contains(block);
   }

   public boolean isVisible(BlockState state) {
      return this.visible.contains(state.getBlock());
   }

   public boolean hides() {
      return this.ghostAlpha <= 0;
   }

   public int ghostAlpha() {
      return this.ghostAlpha;
   }

   public boolean exposedOnly() {
      return this.exposedOnly;
   }

   public boolean fullbright() {
      return this.fullbright;
   }

   public int color(Block block) {
      return this.colors.getInt(block);
   }

   public int targetCount() {
      return this.targets.size();
   }
}
