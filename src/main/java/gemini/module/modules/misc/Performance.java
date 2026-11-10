package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
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

   @Override
   public String info() {
      return this.renderDistance.intValue() + " chunks";
   }
}
