package gemini.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import gemini.command.Suggestions;
import gemini.module.Module;
import gemini.module.ModuleManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class KeybindReport {
   public static final String CONTROLS_SUFFIX = " (Controls)";

   private KeybindReport() {
   }

   public static Map<String, Integer> snapshot(ModuleManager modules) {
      Map<String, Integer> bindings = new LinkedHashMap<>();

      for (Module module : modules.getModules()) {
         if (module.isBindable() && KeybindConflicts.isBound(module.getKeybind())) {
            bindings.put(module.getName(), module.getKeybind());
         }
      }

      Minecraft client = Minecraft.getInstance();
      if (client != null && client.options != null) {
         for (KeyMapping mapping : client.options.keyMappings) {
            int code = keyboardCode(mapping);
            if (KeybindConflicts.isBound(code)) {
               bindings.put(Component.translatable(mapping.getName()).getString() + " (Controls)", code);
            }
         }
      }

      return bindings;
   }

   public static String warning(ModuleManager modules, Module module, int key) {
      List<String> clashes = KeybindConflicts.others(snapshot(modules), module.getName(), key);
      return clashes.isEmpty() ? null : GeminiKeys.displayName(key) + " is also bound to " + Suggestions.humanList(clashes);
   }

   private static int keyboardCode(KeyMapping mapping) {
      if (mapping.isUnbound()) {
         return -1;
      } else {
         try {
            Key key = InputConstants.getKey(mapping.saveString());
            return key.getType() == Type.KEYSYM ? key.getValue() : -1;
         } catch (RuntimeException var2) {
            return -1;
         }
      }
   }
}
