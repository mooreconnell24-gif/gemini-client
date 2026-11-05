package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;

/**
 * Gets Gemini's modules past a client-side anti-cheat in a single-player world.
 *
 * <p>A client-side anti-cheat is just another Fabric mod running in the same game as Gemini. It can only see what
 * Gemini does three ways: it reads the packets Gemini sends, it reads the player's state (attributes, abilities)
 * each tick, and it listens to the same Fabric callbacks. This module closes all three, without being tied to any
 * one anti-cheat or version:
 *
 * <ul>
 *   <li><b>Packet Tunnel</b> hands packets straight to the network connection, underneath the hook an anti-cheat
 *       reads them from, so none of its packet-based movement, timer, combat or block checks ever see them.</li>
 *   <li><b>Hide Boosts</b> gives Gemini's attribute boosts (Step, High Jump, Reach, Fast Break) a random id each
 *       launch, so an anti-cheat can't find them to strip, whatever ids it looks for.</li>
 *   <li><b>Reassert</b> re-applies those boosts at the very end of every tick, after every other mod has run, so
 *       anything an anti-cheat does strip is back before the next tick. This runs regardless of how the check
 *       works, so it does not depend on the anti-cheat's internals.</li>
 *   <li><b>Clamp Reach</b> keeps Reach within limits you set, so a reach check never sees an impossible distance.</li>
 * </ul>
 *
 * <p>See {@code docs/bypass.md} for how each mechanism works and what a same-game anti-cheat can still catch.
 */
public final class BypassModule extends Module {
   private static BypassModule instance;

   private final BooleanSetting tunnel = this.addSetting(
      new BooleanSetting(
         "Packet Tunnel",
         "Hand packets straight to the network connection, underneath the hook an anti-cheat reads them from",
         true
      )
   );
   private final BooleanSetting hideBoosts = this.addSetting(
      new BooleanSetting("Hide Boosts", "Give attribute boosts a random id each launch so an anti-cheat can't find them to strip", true)
   );
   private final BooleanSetting reassert = this.addSetting(
      new BooleanSetting("Reassert", "Re-apply attribute boosts at the end of every tick, after other mods run, so a strip is undone", true)
   );
   private final BooleanSetting clampReach = this.addSetting(
      new BooleanSetting("Clamp Reach", "Keep Reach within the limits below, so a reach check never sees too far", true)
   );
   private final NumberSetting maxBlockReach = this.addSetting(
      new NumberSetting("Max Block Reach", "Highest block reach to use while clamping (vanilla is 4.5)", 5.3, 4.5, 10.0, 0.1).showIf(this.clampReach::get)
   );
   private final NumberSetting maxEntityReach = this.addSetting(
      new NumberSetting("Max Entity Reach", "Highest entity reach to use while clamping (vanilla is 3.0)", 3.3, 3.0, 8.0, 0.1).showIf(this.clampReach::get)
   );

   public BypassModule() {
      super("Bypass", "Gets Gemini's modules past a client-side anti-cheat in single-player.", Category.CLIENT);
      instance = this;
   }

   /** Whether outgoing packets should skip the packet listener and go straight to the connection. */
   public static boolean tunnel() {
      BypassModule module = instance;
      return module != null && module.isEnabled() && module.tunnel.get();
   }

   /** Whether attribute boosts should use a random id an anti-cheat can't predict. */
   public static boolean hideBoosts() {
      BypassModule module = instance;
      return module != null && module.isEnabled() && module.hideBoosts.get();
   }

   /** Whether attribute boosts should be re-applied at the end of each tick, after other mods have run. */
   public static boolean reassert() {
      BypassModule module = instance;
      return module != null && module.isEnabled() && module.reassert.get();
   }

   /** Block interaction range Reach may use: capped while the bypass clamps reach. */
   public static double blockReach(double wanted) {
      BypassModule module = instance;
      return module != null && module.isEnabled() && module.clampReach.get() ? Math.min(wanted, module.maxBlockReach.get()) : wanted;
   }

   /** Entity interaction range Reach may use: capped while the bypass clamps reach. */
   public static double entityReach(double wanted) {
      BypassModule module = instance;
      return module != null && module.isEnabled() && module.clampReach.get() ? Math.min(wanted, module.maxEntityReach.get()) : wanted;
   }

   @Override
   public List<String> aliases() {
      List<String> list = new ArrayList<>(super.aliases());
      list.add("AntiCheat");
      list.add("Inferno");
      list.add("InfernoBypass");
      return list;
   }

   @Override
   public String info() {
      List<String> on = new ArrayList<>();
      if (this.tunnel.get()) {
         on.add("tunnel");
      }

      if (this.hideBoosts.get()) {
         on.add("hide");
      }

      if (this.reassert.get()) {
         on.add("reassert");
      }

      if (this.clampReach.get()) {
         on.add("reach");
      }

      return on.isEmpty() ? "off" : String.join("+", on);
   }
}
