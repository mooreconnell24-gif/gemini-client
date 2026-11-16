package gemini.input;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

public final class GeminiKeys {
   public static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("gemini", "main"));
   public static final KeyMapping OPEN_GUI = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gemini.open_gui", Type.KEYBOARD, Keys.RIGHT_SHIFT, CATEGORY));
   /**
    * Opens the quick-search palette. In game the key works on its own (so Ctrl+K works too, vanilla mappings ignore
    * modifiers); inside Gemini's screens, where plain letters type into search fields, it needs Ctrl. Rebindable in
    * Controls like any other key.
    */
   public static final KeyMapping PALETTE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gemini.palette", Type.KEYBOARD, Keys.K, CATEGORY));

   private GeminiKeys() {
   }

   public static void bootstrap() {
      if (OPEN_GUI == null || PALETTE == null) {
         throw new IllegalStateException("Gemini key mapping failed to register");
      }
   }

   /** Whether a key press inside a Gemini screen asks for the palette: Ctrl plus the palette key. */
   public static boolean opensPalette(KeyEvent event) {
      return event.hasControlDown() && !PALETTE.isUnbound() && PALETTE.matches(event);
   }

   /** The palette shortcut as shown in hints, e.g. "Ctrl+K". */
   public static String paletteHint() {
      return PALETTE.isUnbound() ? "unbound" : "Ctrl+" + PALETTE.getTranslatedKeyMessage().getString();
   }

   public static String displayName(int keyCode) {
      return keyCode != -1 && keyCode != 0 ? Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString() : "NONE";
   }
}
