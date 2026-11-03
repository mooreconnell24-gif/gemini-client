package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import gemini.module.modules.misc.BetterChat;
import gemini.module.modules.misc.NameProtect;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ChatComponent.class})
public abstract class ChatComponentMixin {
   @WrapMethod(
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"}
   )
   private void gemini$betterChat(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<Void> original) {
      BetterChat chat = BetterChat.instance;
      if (chat != null && chat.isEnabled()) {
         if (chat.hides(message)) {
            return;
         }

         message = chat.decorate(message);
      }

      NameProtect nameProtect = NameProtect.instance;
      if (nameProtect != null && nameProtect.isEnabled() && message != null) {
         message = nameProtect.filterComponent(message);
      }

      original.call(new Object[]{message, signature, source, tag});
   }
}
