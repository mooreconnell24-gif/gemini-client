package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class NameProtect extends Module {
   public static NameProtect instance;
   private final ListSetting alias = this.addSetting(
      new ListSetting("Alias", "Name shown in chat in place of yours; only the first line is used", ListSetting.Kind.TEXT, "GeminiUser")
   );

   public NameProtect() {
      super("NameProtect", "Replace your account name with an alias in every chat line.", Category.CLIENT);
      instance = this;
   }

   public String aliasName() {
      String first = this.alias.isEmpty() ? "" : this.alias.get().getFirst().trim();
      return first.isEmpty() ? "GeminiUser" : first;
   }

   public Component filterComponent(Component message) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || message == null) {
         return message;
      }
      String selfName = client.player.getGameProfile().name();
      String raw = message.getString();
      if (selfName != null && !selfName.isEmpty() && raw.contains(selfName)) {
         // Rebuilt piece by piece so every part keeps its own colour, formatting and click/hover actions.
         String alias = this.aliasName();
         MutableComponent masked = Component.empty();
         message.visit((style, text) -> {
            masked.append(Component.literal(text.replace(selfName, alias)).setStyle(style));
            return Optional.empty();
         }, Style.EMPTY);
         return masked;
      }
      return message;
   }

   @Override
   public String info() {
      return this.aliasName();
   }
}
