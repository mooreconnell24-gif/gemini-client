package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.LiveProfiler;
import gemini.module.Module;
import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

public final class Performance extends Module {
   private final NumberSetting renderDistance = this.addSetting(new NumberSetting("Render Distance", "Chunks", 6.0, 2.0, 32.0, 1.0).onChange(this::apply));
   private final NumberSetting simulationDistance = this.addSetting(
      new NumberSetting("Simulation Distance", "Chunks that tick", 6.0, 5.0, 32.0, 1.0).onChange(this::apply)
   );
   private final NumberSetting entityDistance = this.addSetting(
      new NumberSetting("Entity Distance", "Entity render distance in percent", 50.0, 50.0, 500.0, 25.0).onChange(this::apply)
   );
   private final BooleanSetting noClouds = this.addSetting(new BooleanSetting("No Clouds", "Turn clouds off", true).onChange(this::apply));
   private final BooleanSetting noShadows = this.addSetting(new BooleanSetting("No Entity Shadows", "Turn entity shadows off", true).onChange(this::apply));
   private final BooleanSetting minimalParticles = this.addSetting(
      new BooleanSetting("Minimal Particles", "Show as few particles as possible", true).onChange(this::apply)
   );
   private final BooleanSetting noSmoothLighting = this.addSetting(
      new BooleanSetting("No Smooth Lighting", "Turn ambient occlusion off", false).onChange(this::apply)
   );
   private final BooleanSetting noBiomeBlend = this.addSetting(new BooleanSetting("No Biome Blend", "Sharp biome colour borders", true).onChange(this::apply));
   private final BooleanSetting noVsync = this.addSetting(new BooleanSetting("No VSync", "Turn vertical sync off", false).onChange(this::apply));
   private final BooleanSetting profiler = this.addSetting(
      new BooleanSetting("Profiler", "Show a live breakdown of the heaviest modules (microseconds per tick or frame, averaged over a second)", false)
   );
   private final NumberSetting profilerRows = this.addSetting(
      new NumberSetting("Profiler Rows", "Modules listed per hook", 5.0, 1.0, 12.0, 1.0).showIf(this.profiler::get)
   );
   private final ModeSetting profilerHook = this.addSetting(
      new ModeSetting("Profiler Hook", "Which hook to break down", "All", "All", "Tick", "World frame", "HUD frame").showIf(this.profiler::get)
   );
   private final List<TextHudWidget.Line> profileLines = new ArrayList<>();
   private long profileStamp;
   private int profileWidth;
   private int savedRender;
   private int savedSimulation;
   private double savedEntity;
   private CloudStatus savedClouds;
   private boolean savedShadows;
   private ParticleStatus savedParticles;
   private boolean savedAo;
   private int savedBlend;
   private boolean savedVsync;
   private boolean saved;

   public Performance() {
      super("Performance", "FPS mode: low render distance, no clouds, shadows or particles, all restored when switched off.", Category.CLIENT);
   }

   @Override
   public void onEnable() {
      Options options = Minecraft.getInstance().options;
      this.savedRender = options.renderDistance().get();
      this.savedSimulation = options.simulationDistance().get();
      this.savedEntity = options.entityDistanceScaling().get();
      this.savedClouds = options.cloudStatus().get();
      this.savedShadows = options.entityShadows().get();
      this.savedParticles = options.particles().get();
      this.savedAo = options.ambientOcclusion().get();
      this.savedBlend = options.biomeBlendRadius().get();
      this.savedVsync = options.enableVsync().get();
      this.saved = true;
      this.apply();
   }

   @Override
   public void onDisable() {
      this.profileLines.clear();
      this.profileStamp = 0L;
      if (this.saved) {
         Options options = Minecraft.getInstance().options;
         options.renderDistance().set(this.savedRender);
         options.simulationDistance().set(this.savedSimulation);
         options.entityDistanceScaling().set(this.savedEntity);
         options.cloudStatus().set(this.savedClouds);
         options.entityShadows().set(this.savedShadows);
         options.particles().set(this.savedParticles);
         options.ambientOcclusion().set(this.savedAo);
         options.biomeBlendRadius().set(this.savedBlend);
         options.enableVsync().set(this.savedVsync);
         this.saved = false;
      }
   }

   private void apply() {
      if (this.isEnabled() && this.saved) {
         Options options = Minecraft.getInstance().options;
         options.renderDistance().set(this.renderDistance.intValue());
         options.simulationDistance().set(this.simulationDistance.intValue());
         options.entityDistanceScaling().set(this.entityDistance.get() / 100.0);
         options.cloudStatus().set(this.noClouds.get() ? CloudStatus.OFF : this.savedClouds);
         options.entityShadows().set(this.noShadows.get() ? false : this.savedShadows);
         options.particles().set(this.minimalParticles.get() ? ParticleStatus.MINIMAL : this.savedParticles);
         options.ambientOcclusion().set(this.noSmoothLighting.get() ? false : this.savedAo);
         options.biomeBlendRadius().set(this.noBiomeBlend.get() ? 0 : this.savedBlend);
         options.enableVsync().set(this.noVsync.get() ? false : this.savedVsync);
      }
   }

   /** Asked once a tick by the live profiler: timing runs only while this (or the AntiLag widget) wants it. */
   public boolean wantsProfiler() {
      return this.isEnabled() && this.profiler.get();
   }

   @Override
   public void onRenderHud(HudContext hud) {
      if (!this.profiler.get()) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.profileStamp >= 250L || now < this.profileStamp) {
         this.profileStamp = now;
         this.profileLines.clear();
         this.profileLines.add(new TextHudWidget.Line("Module profiler", Glass.TEXT));
         LiveProfiler.describe(this.profileLines, this.profilerRows.intValue(), this.profilerHook.get(), hud.accent, Glass.TEXT, Glass.TEXT_DIM);
         int widest = 0;
         for (TextHudWidget.Line line : this.profileLines) {
            widest = Math.max(widest, hud.font.width(line.text()));
         }

         this.profileWidth = widest;
      }

      int x = 6;
      int y = hud.height / 2 - this.profileLines.size() * 5;
      int w = this.profileWidth + 12;
      int h = this.profileLines.size() * 10 + 8;
      Glass.panel(hud.graphics, x, y, x + w, y + h, Glass.RADIUS, hud.surface, hud.accent, 0.94F, 0.0F);
      for (int i = 0; i < this.profileLines.size(); i++) {
         TextHudWidget.Line line = this.profileLines.get(i);
         hud.graphics.text(hud.font, line.text(), x + 6, y + 5 + i * 10, line.color(), false);
      }
   }

   @Override
   public String info() {
      return this.renderDistance.intValue() + " chunks";
   }
}
