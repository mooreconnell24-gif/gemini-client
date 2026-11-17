package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Changes the time of day and the weather you see without changing the world: mobs, crops and beds still follow
 * the real time and weather. The time is swapped where the client reads its clock (the clock mixin asks
 * {@link #timeOverride()}); the weather is written to the client's copy of the level every tick and handed back
 * from the single-player world when the module is switched off.
 */
public final class Ambience extends Module {
   private static volatile Ambience instance;
   private final ModeSetting time = this.addSetting(
      new ModeSetting("Time", "Time of day you see; World leaves it alone", "Day", "World", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom")
   );
   private final NumberSetting customTime = this.addSetting(
      new NumberSetting("Custom Time", "Time of day in ticks (0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight)", 6000.0, 0.0, 24000.0, 250.0)
         .showIf(() -> this.time.is("Custom"))
   );
   private final ModeSetting weather = this.addSetting(
      new ModeSetting("Weather", "Weather you see; World leaves it alone", "World", "World", "Clear", "Rain", "Thunder")
   );
   private boolean changedWeather;

   public Ambience() {
      super("Ambience", "Changes the time of day and weather you see, without changing the world itself.", Category.RENDER);
      instance = this;
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Time Changer");
      names.add("Weather Changer");
      names.add("Client Time");
      return names;
   }

   /** The time of day the client's clocks should report, or -1 to leave them alone. Read from the render path. */
   public static long timeOverride() {
      Ambience module = instance;
      return module != null && module.isEnabled() ? module.chosenTime() : -1L;
   }

   private long chosenTime() {
      return switch (this.time.get()) {
         case "Sunrise" -> 23000L;
         case "Day" -> 1000L;
         case "Noon" -> 6000L;
         case "Sunset" -> 12500L;
         case "Night" -> 14000L;
         case "Midnight" -> 18000L;
         case "Custom" -> this.customTime.intValue();
         default -> -1L;
      };
   }

   @Override
   public void onTick() {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }

      if (this.weather.is("World")) {
         this.restoreWeather(level);
         return;
      }

      this.changedWeather = true;
      level.setRainLevel(this.weather.is("Clear") ? 0.0F : 1.0F);
      level.setThunderLevel(this.weather.is("Thunder") ? 1.0F : 0.0F);
   }

   @Override
   public void onDisable() {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         this.restoreWeather(level);
      }

      this.changedWeather = false;
   }

   /** A new world sends its own weather, so there is nothing of ours left to undo. */
   @Override
   public void onWorldChange() {
      this.changedWeather = false;
   }

   /**
    * The server only sends the weather when it changes, so the real values are read from the single-player world
    * this client is in.
    */
   private void restoreWeather(ClientLevel level) {
      if (!this.changedWeather) {
         return;
      }

      this.changedWeather = false;
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      ServerLevel real = server == null ? null : server.getLevel(level.dimension());
      level.setRainLevel(real == null ? 0.0F : real.getRainLevel(1.0F));
      level.setThunderLevel(real == null ? 0.0F : real.getThunderLevel(1.0F));
   }

   @Override
   public String info() {
      return this.time.is("World") ? this.weather.get() : this.time.get();
   }
}
