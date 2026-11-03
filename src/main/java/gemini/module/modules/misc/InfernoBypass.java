package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gets Gemini's modules past the Inferno anti-cheat (the {@code inferno} Fabric mod) in a single-player world.
 *
 * <p>Inferno runs inside the same game as Gemini. It reads every outgoing packet from a hook at the top of
 * {@code ClientCommonPacketListenerImpl.send}, and every client tick it strips Gemini's attribute boosts from the
 * player by their exact ids. Each setting here removes one of those footholds; {@code docs/inferno-bypass.md}
 * goes through every Inferno check and what happens to it.
 */
public final class InfernoBypass extends Module {
   private static InfernoBypass instance;

   private final BooleanSetting tunnel = this.addSetting(
      new BooleanSetting(
         "Packet Tunnel",
         "Hand packets straight to the network connection, underneath the hook Inferno reads them from, so its movement, timer and packet checks never see them",
         true
      )
   );
   private final BooleanSetting hideBoosts = this.addSetting(
      new BooleanSetting(
         "Hide Boosts",
         "Give Step, High Jump, Reach and Fast Break attribute boosts ids Inferno's integrity guard does not look for",
         true
      )
   );
   private final BooleanSetting safeBlockReach = this.addSetting(
      new BooleanSetting("Safe Block Reach", "Keep Reach's block range under the 5.35 Inferno checks on every block click", true)
   );

   public InfernoBypass() {
      super("Inferno Bypass", "Gets Gemini's modules past the Inferno anti-cheat in single-player.", Category.CLIENT);
      instance = this;
   }

   /** Whether outgoing packets should skip the packet listener and go straight to the connection. */
   public static boolean tunnel() {
      InfernoBypass module = instance;
      return module != null && module.isEnabled() && module.tunnel.get();
   }

   /** Whether attribute boosts should use ids Inferno does not recognise. */
   public static boolean hideBoosts() {
      InfernoBypass module = instance;
      return module != null && module.isEnabled() && module.hideBoosts.get();
   }

   /** Block interaction range Reach may use: capped just under Inferno's limit while the bypass is on. */
   public static double blockReach(double wanted) {
      InfernoBypass module = instance;
      return module != null && module.isEnabled() && module.safeBlockReach.get() ? Math.min(wanted, 5.3) : wanted;
   }

   public static boolean infernoInstalled() {
      return FabricLoader.getInstance().isModLoaded("inferno");
   }

   @Override
   public String info() {
      return infernoInstalled() ? "Inferno found" : "No Inferno";
   }
}
