package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.resources.Identifier;

public final class NoRenderModule extends Module {
   private static volatile NoRenderModule instance;
   private final BooleanSetting fire = this.addSetting(new BooleanSetting("Fire", "Hide the fire overlay on your screen", true));
   private final BooleanSetting water = this.addSetting(new BooleanSetting("Water Overlay", "Hide the blue tint when your eyes are underwater", true));
   private final BooleanSetting wall = this.addSetting(new BooleanSetting("Wall Overlay", "Hide the black screen when your head is inside a block", true));
   private final BooleanSetting pumpkin = this.addSetting(new BooleanSetting("Pumpkin Overlay", "Hide the carved pumpkin blur", true));
   private final BooleanSetting powderSnow = this.addSetting(new BooleanSetting("Powder Snow Overlay", "Hide the frozen screen border", true));
   private final BooleanSetting spyglass = this.addSetting(new BooleanSetting("Spyglass Overlay", "Hide the spyglass scope circle", false));
   private final BooleanSetting portal = this.addSetting(new BooleanSetting("Portal Overlay", "Hide the purple nether portal swirl", true));
   private final BooleanSetting nausea = this.addSetting(new BooleanSetting("Nausea", "Hide the wobble from the nausea effect", true));
   private final BooleanSetting vignette = this.addSetting(new BooleanSetting("Vignette", "Hide the dark screen corners", true));
   private final BooleanSetting totem = this.addSetting(new BooleanSetting("Totem Animation", "Skip the totem of undying pop animation", true));
   private final BooleanSetting bossBars = this.addSetting(new BooleanSetting("Boss Bars", "Hide boss health bars", false));
   private final BooleanSetting scoreboard = this.addSetting(new BooleanSetting("Scoreboard", "Hide the sidebar scoreboard", false));
   private final BooleanSetting titles = this.addSetting(new BooleanSetting("Titles", "Hide big title and subtitle text", false));
   private final BooleanSetting actionBar = this.addSetting(new BooleanSetting("Action Bar", "Hide the text above the hotbar", false));
   private final BooleanSetting effects = this.addSetting(new BooleanSetting("Status Effects", "Hide the potion effect icons", false));
   private final BooleanSetting heldName = this.addSetting(new BooleanSetting("Held Item Name", "Hide the item name that pops up when you switch slots", false));
   private final BooleanSetting sleeping = this.addSetting(new BooleanSetting("Sleep Overlay", "Hide the dark fade while sleeping", false));

   public NoRenderModule() {
      super("NoRender", "Strip screen overlays and HUD clutter.", Category.RENDER);
   }

   public static NoRenderModule running() {
      NoRenderModule module = instance;
      return module != null && module.isEnabled() ? module : null;
   }

   @Override
   public void onEnable() {
      instance = this;
   }

   @Override
   public void onDisable() {
      if (instance == this) {
         instance = null;
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
