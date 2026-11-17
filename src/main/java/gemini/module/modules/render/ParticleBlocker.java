package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ParticleBlocker extends Module {
   public static ParticleBlocker instance;
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Hide every particle, or only the listed types", "Listed", "Listed", "All")
   );
   private final ListSetting types = this.addSetting(
      new ListSetting(
            "Particles",
            "Particle ids to hide, e.g. minecraft:explosion",
            ListSetting.Kind.TEXT,
            "minecraft:explosion",
            "minecraft:explosion_emitter",
            "minecraft:large_smoke"
         )
         .showIf(() -> this.mode.is("Listed"))
   );
   private volatile Set<String> cache;

   public ParticleBlocker() {
      super("Particles", "Hides particles you do not want to see, such as explosions and smoke.", Category.RENDER);
      instance = this;
      this.types.onChange(() -> this.cache = null);
   }

   public boolean blocks(ParticleOptions options) {
      if (this.mode.is("All")) {
         return true;
      } else {
         Set<String> ids = this.cache;
         if (ids == null) {
            ids = new HashSet<>();

            for (String entry : this.types.get()) {
               String id = entry.trim().toLowerCase(Locale.ROOT);
               if (!id.isEmpty()) {
                  ids.add(id.indexOf(':') < 0 ? "minecraft:" + id : id);
               }
            }

            this.cache = ids;
         }

         Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
         return key != null && ids.contains(key.toString());
      }
   }
}
