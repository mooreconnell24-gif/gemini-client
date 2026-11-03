package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;

public final class SoundBlocker extends Module {
   public static SoundBlocker instance;
   private final ListSetting sounds = this.addSetting(
      new ListSetting(
            "Sounds", "Sound ids, or any part of one, to mute", ListSetting.Kind.TEXT, "ambient.cave", "entity.phantom"
         )
         .onChange(this::rebuild)
   );
   private volatile String[] needles = new String[0];

   public SoundBlocker() {
      super("Sound Blocker", "Mute annoying sounds by name.", Category.CLIENT);
      instance = this;
      this.rebuild();
   }

   private void rebuild() {
      List<String> lowered = new ArrayList<>();

      for (String entry : this.sounds.get()) {
         lowered.add(entry.toLowerCase(Locale.ROOT));
      }

      this.needles = lowered.toArray(new String[0]);
   }

   public boolean blocks(Identifier sound) {
      String[] list = this.needles;
      if (list.length == 0) {
         return false;
      } else {
         String id = sound.toString();

         for (String needle : list) {
            if (id.contains(needle)) {
               return true;
            }
         }

         return false;
      }
   }
}
