package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class NameProtect extends Module {
   public static NameProtect instance;
   private final ListSetting alias = this.addSetting(
      new ListSetting("Alias", "Replacement name shown for your character", ListSetting.Kind.TEXT, "GeminiUser")
   );

   public NameProtect() {
      super("NameProtect", "Mask your account name in HUD and chat.", Category.CLIENT);
      instance = this;
   }

   public String aliasName() {
      return this.alias.isEmpty() ? "GeminiUser" : this.alias.get().getFirst();
   }

   public Component filterComponent(Component message) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || message == null) {
         return message;
      }
      String selfName = client.player.getGameProfile().name();
      String raw = message.getString();
      if (selfName != null && !selfName.isEmpty() && raw.contains(selfName)) {
         return Component.literal(raw.replace(selfName, this.aliasName())).withStyle(message.getStyle());
      }
      return message;
   }

   @Override
   public String info() {
      return this.aliasName();
   }
}
