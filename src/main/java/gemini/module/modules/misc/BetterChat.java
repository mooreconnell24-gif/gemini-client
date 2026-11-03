package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

public final class BetterChat extends Module {
   public static BetterChat instance;
   private static final Pattern COORDINATES = Pattern.compile("(?<![\\d.])-?\\d{2,7}(?:\\.\\d+)?[ ,]+-?\\d{1,3}(?:\\.\\d+)?[ ,]+-?\\d{2,7}(?![\\d.])");
   private static final DateTimeFormatter TWENTY_FOUR = DateTimeFormatter.ofPattern("HH:mm");
   private static final DateTimeFormatter TWELVE = DateTimeFormatter.ofPattern("hh:mm a", Locale.ROOT);
   private final BooleanSetting timestamps = this.addSetting(new BooleanSetting("Timestamps", "Prefix every chat line with the time", true));
   private final ModeSetting format = this.addSetting(
      new ModeSetting("Time Format", "How the time is written", "24 Hour", "24 Hour", "12 Hour").showIf(this.timestamps::get)
   );
   private final BooleanSetting antiSpam = this.addSetting(new BooleanSetting("Anti Spam", "Hide a line that repeats the previous one", true));
   private final NumberSetting window = this.addSetting(
      new NumberSetting("Spam Window", "Seconds a repeat counts as spam", 10.0, 1.0, 60.0, 1.0).showIf(this.antiSpam::get)
   );
   private final ListSetting hidden = this.addSetting(
      new ListSetting("Hidden Words", "Chat lines containing any of these are hidden", ListSetting.Kind.TEXT)
   );
   private final BooleanSetting protectCoordinates = this.addSetting(
      new BooleanSetting("Coordinate Protection", "Block outgoing chat messages containing coordinates", false)
   );
   private String lastText = "";
   private long lastTime;

   public BetterChat() {
      super("Better Chat", "Chat timestamps, anti-spam, and coordinate guard.", Category.CLIENT);
      instance = this;
   }

   public boolean hides(Component message) {
      String text = message.getString();
      if (!this.hidden.isEmpty()) {
         String lower = text.toLowerCase(Locale.ROOT);

         for (String word : this.hidden.get()) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
               return true;
            }
         }
      }

      if (!this.antiSpam.get()) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         boolean repeat = text.equals(this.lastText) && now - this.lastTime < this.window.intValue() * 1000L;
         this.lastText = text;
         this.lastTime = now;
         return repeat;
      }
   }

   public Component decorate(Component message) {
      if (!this.timestamps.get()) {
         return message;
      } else {
         String time = LocalTime.now().format(this.format.is("12 Hour") ? TWELVE : TWENTY_FOUR);
         return Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(message);
      }
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (this.protectCoordinates.get() && packet instanceof ServerboundChatPacket chat && COORDINATES.matcher(chat.message()).find()) {
         CommandManager.feedback("§bBetter Chat §7blocked a message that looks like it contains coordinates.");
         return true;
      } else {
         return false;
      }
   }
}
