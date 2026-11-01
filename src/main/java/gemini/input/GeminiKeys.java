package gemini.input;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public final class GeminiKeys {
   public static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("gemini", "main"));
   public static final KeyMapping OPEN_GUI = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gemini.open_gui", Type.KEYSYM, 344, CATEGORY));

   private GeminiKeys() {
   }

   public static void bootstrap() {
      if (OPEN_GUI == null) {
         throw new IllegalStateException("Gemini key mapping failed to register");
      }
   }

   public static String displayName(int keyCode) {
      return keyCode != -1 && keyCode != 0 ? Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString() : "NONE";
   }
}
