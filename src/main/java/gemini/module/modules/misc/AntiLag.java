package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class AntiLag extends Module {
   public static AntiLag instance;
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Dynamic adapts to FPS; Aggressive culls all effects",
         "Dynamic",
         "Dynamic",
         "Always",
         "Aggressive"
      )
   );
   private final NumberSetting targetFps = this.addSetting(
      new NumberSetting("Target FPS", "FPS threshold where Dynamic culling activates", 90.0, 30.0, 240.0, 10.0)
         .showIf(() -> this.mode.is("Dynamic"))
   );
   private final NumberSetting maxParticlesTick = this.addSetting(
      new NumberSetting("Max Particles/Tick", "Maximum particles allowed to spawn in a single tick", 55.0, 5.0, 250.0, 5.0)
   );
   private final NumberSetting particleRange = this.addSetting(
      new NumberSetting("Particle Distance", "Cull particles beyond this distance in blocks", 28.0, 8.0, 96.0, 4.0)
   );
   private final BooleanSetting blockExplosions = this.addSetting(
      new BooleanSetting("Block Explosions & Fireworks", "Block explosion emitters", true)
   );
   private final BooleanSetting blockSmoke = this.addSetting(
      new BooleanSetting("Block Smoke & Campfires", "Block heavy campfire smoke", true)
   );
   private final BooleanSetting blockWeather = this.addSetting(
      new BooleanSetting("Block Weather & Portals", "Block rain splash, dripping water, and portal particles", true)
   );
   private final BooleanSetting packetGuard = this.addSetting(
      new BooleanSetting("Packet Flood Guard", "Drop server packets spawning excessive particles", true)
   );
   private final BooleanSetting soundThrottle = this.addSetting(
      new BooleanSetting("Sound Spam Filter", "Cap overlapping sounds per tick to prevent lag", true)
   );
   private final NumberSetting maxSoundsTick = this.addSetting(
      new NumberSetting("Max Sounds/Tick", "Maximum sound instances started per tick", 14.0, 4.0, 40.0, 2.0).showIf(this.soundThrottle::get)
   );
   private final BooleanSetting cullNametags = this.addSetting(
      new BooleanSetting("Cull Distant Nametags", "Hide distant entity and armor stand nametags", true)
   );
   private int particlesThisTick;
   private int soundsThisTick;
   private long totalBlocked;

   public AntiLag() {
      super("AntiLag", "Boost FPS and prevent lag machines.", Category.CLIENT);
      instance = this;
   }

   @Override
   public void onEnable() {
      this.particlesThisTick = 0;
      this.soundsThisTick = 0;
   }

   @Override
   public void onTick() {
      this.particlesThisTick = 0;
      this.soundsThisTick = 0;
   }

   public long totalBlocked() {
      return this.totalBlocked;
   }

   public boolean isStrictNow() {
      if (!this.mode.is("Always") && !this.mode.is("Aggressive")) {
         int fps = Minecraft.getInstance().getFps();
         return fps > 0 && fps < this.targetFps.intValue();
      } else {
         return true;
      }
   }

   @Override
   public String info() {
      return this.mode.get() + " · " + this.totalBlocked;
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (!(this.packetGuard.get() && packet instanceof ClientboundLevelParticlesPacket p)
         || p.getCount() <= 150 && (!this.mode.is("Aggressive") || p.getCount() <= 40)) {
         return false;
      } else {
         this.totalBlocked++;
         return true;
      }
   }

   public boolean shouldBlockParticle(ParticleOptions options, double x, double y, double z) {
      if (!this.isEnabled()) {
         return false;
      } else {
         boolean strict = this.isStrictNow();
         int cap = this.mode.is("Aggressive")
            ? Math.max(5, this.maxParticlesTick.intValue() / 3)
            : (strict ? this.maxParticlesTick.intValue() : this.maxParticlesTick.intValue() * 2);
         if (this.particlesThisTick >= cap) {
            this.totalBlocked++;
            return true;
         } else {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
               double maxDist = strict ? this.particleRange.get() : this.particleRange.get() * 1.35;
               if (client.player.distanceToSqr(x, y, z) > maxDist * maxDist) {
                  this.totalBlocked++;
                  return true;
               }
            }

            Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
            if (key != null) {
               String path = key.getPath();
               if (this.blockExplosions.get()
                  && (
                     path.contains("explosion")
                        || path.contains("firework")
                        || path.equals("flash")
                        || path.contains("sonic_boom")
                        || path.contains("elder_guardian")
                  )) {
                  this.totalBlocked++;
                  return true;
               }

               if (this.blockSmoke.get() && (path.contains("campfire") || path.contains("large_smoke") || path.equals("smoke") || path.contains("ash"))) {
                  this.totalBlocked++;
                  return true;
               }

               if (this.blockWeather.get() && (path.equals("rain") || path.contains("dripping") || path.contains("portal"))) {
                  this.totalBlocked++;
                  return true;
               }
            }

            this.particlesThisTick++;
            return false;
         }
      }
   }

   public boolean shouldBlockSound(Identifier id) {
      if (this.isEnabled() && this.soundThrottle.get()) {
         int cap = this.isStrictNow() ? this.maxSoundsTick.intValue() : this.maxSoundsTick.intValue() * 2;
         if (this.soundsThisTick >= cap) {
            this.totalBlocked++;
            return true;
         } else {
            String path = id.getPath();
            if (!this.mode.is("Aggressive") || !path.contains("explosion") && !path.contains("firework") && !path.contains("piston")) {
               this.soundsThisTick++;
               return false;
            } else {
               this.totalBlocked++;
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean shouldCullNametag(Entity entity, double distanceSq) {
      if (!this.isEnabled() || !this.cullNametags.get()) {
         return false;
      } else if (entity instanceof ArmorStand) {
         return true;
      } else {
         double maxDist = this.isStrictNow() ? 14.0 : 28.0;
         return distanceSq > maxDist * maxDist;
      }
   }
}
