package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.Identifier;

public final class NoRenderModule extends Module {
   private static volatile NoRenderModule instance;
   private final BooleanSetting fire = this.addSetting(new BooleanSetting("Fire", "Hide the fire overlay on your screen", true));
   private final BooleanSetting water = this.addSetting(new BooleanSetting("Water Overlay", "Hide the blue tint when your eyes are underwater", true));
   private final BooleanSetting wall = this.addSetting(new BooleanSetting("Wall Overlay", "Hide the overlay shown when you are inside a solid block", true));
   private final BooleanSetting pumpkin = this.addSetting(new BooleanSetting("Pumpkin Overlay", "Hide the carved pumpkin overlay", true));
   private final BooleanSetting powderSnow = this.addSetting(new BooleanSetting("Powder Snow Overlay", "Hide the frozen screen border", true));
   private final BooleanSetting spyglass = this.addSetting(new BooleanSetting("Spyglass Overlay", "Hide the spyglass scope circle", false));
   private final BooleanSetting portal = this.addSetting(new BooleanSetting("Portal Overlay", "Hide the purple nether portal swirl", true));
   private final BooleanSetting nausea = this.addSetting(new BooleanSetting("Nausea", "Hide the wobble from the nausea effect", true));
   private final BooleanSetting blindness = this.addSetting(new BooleanSetting("Blindness", "Remove the fog of the blindness effect", true));
   private final BooleanSetting darkness = this.addSetting(new BooleanSetting("Darkness", "Remove the pulsing dark of the darkness effect", true));
   private final BooleanSetting bobbing = this.addSetting(
      new BooleanSetting("View Bobbing", "Turn off the up-and-down view bobbing while you walk", false).onChange(this::applyOptions)
   );
   private final BooleanSetting hurtShake = this.addSetting(
      new BooleanSetting("Hurt Shake", "Turn off the camera tilt when you get hurt", false).onChange(this::applyOptions)
   );
   private final BooleanSetting vignette = this.addSetting(new BooleanSetting("Vignette", "Hide the dark screen corners", true));
   private final BooleanSetting totem = this.addSetting(new BooleanSetting("Totem Animation", "Skip the totem of undying animation", true));
   private final BooleanSetting bossBars = this.addSetting(new BooleanSetting("Boss Bars", "Hide boss health bars", false));
   private final BooleanSetting scoreboard = this.addSetting(new BooleanSetting("Scoreboard", "Hide the sidebar scoreboard", false));
   private final BooleanSetting titles = this.addSetting(new BooleanSetting("Titles", "Hide big title and subtitle text", false));
   private final BooleanSetting actionBar = this.addSetting(new BooleanSetting("Action Bar", "Hide the text above the hotbar", false));
   private final BooleanSetting effects = this.addSetting(new BooleanSetting("Status Effects", "Hide the potion effect icons", false));
   private final BooleanSetting heldName = this.addSetting(new BooleanSetting("Held Item Name", "Hide the item name shown when you switch items", false));
   private final BooleanSetting sleeping = this.addSetting(new BooleanSetting("Sleep Overlay", "Hide the dark fade while sleeping", false));
   /** The vanilla options as they were before this module changed them; null while it has not. */
   private Boolean previousBobbing;
   private Double previousTilt;

   public NoRenderModule() {
      super("No Render", "Hides the screen overlays, effects and HUD elements you choose.", Category.RENDER);
   }

   public static NoRenderModule running() {
      NoRenderModule module = instance;
      return module != null && module.isEnabled() ? module : null;
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("AntiBlind");
      names.add("NoBob");
      names.add("NoHurtCam");
      return names;
   }

   @Override
   public void onEnable() {
      instance = this;
      this.applyOptions();
   }

   @Override
   public void onDisable() {
      if (instance == this) {
         instance = null;
      }

      this.restoreOptions();
   }

   /**
    * View bobbing and the hurt tilt are vanilla options, so they are switched there and put back afterwards. The
    * value from before is remembered only once, so toggling the setting twice cannot overwrite it with our own.
    */
   private void applyOptions() {
      if (!this.isEnabled()) {
         return;
      }

      Options options = Minecraft.getInstance().options;
      if (this.bobbing.get()) {
         if (this.previousBobbing == null) {
            this.previousBobbing = options.bobView().get();
         }

         options.bobView().set(false);
      } else if (this.previousBobbing != null) {
         options.bobView().set(this.previousBobbing);
         this.previousBobbing = null;
      }

      if (this.hurtShake.get()) {
         if (this.previousTilt == null) {
            this.previousTilt = options.damageTiltStrength().get();
         }

         options.damageTiltStrength().set(0.0);
      } else if (this.previousTilt != null) {
         options.damageTiltStrength().set(this.previousTilt);
         this.previousTilt = null;
      }
   }

   private void restoreOptions() {
      Options options = Minecraft.getInstance().options;
      boolean changed = this.previousBobbing != null || this.previousTilt != null;
      if (this.previousBobbing != null) {
         options.bobView().set(this.previousBobbing);
         this.previousBobbing = null;
      }

      if (this.previousTilt != null) {
         options.damageTiltStrength().set(this.previousTilt);
         this.previousTilt = null;
      }

      if (changed) {
         // The options file may have been written while this was on; write the restored values so they stick.
         options.save();
      }
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         // These effects only draw fog on the client, so dropping the client's copy removes the fog and nothing else.
         if (this.blindness.get()) {
            player.removeEffect(MobEffects.BLINDNESS);
         }

         if (this.darkness.get()) {
            player.removeEffect(MobEffects.DARKNESS);
         }
      }
   }

   public boolean hideFire() {
      return this.fire.get();
   }

   public boolean hideWater() {
      return this.water.get();
   }

   public boolean hideWall() {
      return this.wall.get();
   }

   public boolean hidePortal() {
      return this.portal.get();
   }

   public boolean hideNausea() {
      return this.nausea.get();
   }

   public boolean hideSpyglass() {
      return this.spyglass.get();
   }

   public boolean hideVignette() {
      return this.vignette.get();
   }

   public boolean hideTotem() {
      return this.totem.get();
   }

   public boolean hideBossBars() {
      return this.bossBars.get();
   }

   public boolean hideScoreboard() {
      return this.scoreboard.get();
   }

   public boolean hideTitles() {
      return this.titles.get();
   }

   public boolean hideActionBar() {
      return this.actionBar.get();
   }

   public boolean hideEffects() {
      return this.effects.get();
   }

   public boolean hideHeldName() {
      return this.heldName.get();
   }

   public boolean hideSleep() {
      return this.sleeping.get();
   }

   public boolean hideTextureOverlay(Identifier texture) {
      String path = texture.getPath();
      if (path.contains("pumpkin")) {
         return this.pumpkin.get();
      } else {
         return path.contains("powder_snow") ? this.powderSnow.get() : false;
      }
   }
}
