package gemini.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

public final class SingleplayerGate {
   private static final String UNLOCK_CODE = "4566";
   private static boolean allowEverywhere = false;
   private static boolean initialized = false;

   private SingleplayerGate() {
   }

   public static void tick(Minecraft client) {
      if (client == null) {
         return;
      }

      if (!initialized && client.options != null) {
         initialized = true;
         if (client.options.lastMpIp != null && UNLOCK_CODE.equals(client.options.lastMpIp.trim())) {
            client.options.lastMpIp = "";
         }
      }

      boolean justUnlocked = false;
      if (!allowEverywhere) {
         justUnlocked = checkDirectConnectUnlock(client);
      } else if (client.options != null && client.options.lastMpIp != null && UNLOCK_CODE.equals(client.options.lastMpIp.trim())) {
         client.options.lastMpIp = "";
      }

      Screen screen = client.gui != null ? client.gui.screen() : null;
      if (justUnlocked && screen != null && screen.getClass().getSimpleName().contains("Connect") && !screen.getClass().getSimpleName().contains("Direct")) {
         returnToParentScreen(client, screen);
      }

      if (!allowEverywhere) {
         if (client.level != null && !client.hasSingleplayerServer()) {
            client.level.disconnect(Component.literal(denialMessage()));
         } else if (screen != null
            && screen.getClass().getSimpleName().contains("Connect")
            && !screen.getClass().getSimpleName().contains("Direct")
            && !client.hasSingleplayerServer()) {
            returnToParentScreen(client, screen);
         }
      }
   }

   private static void returnToParentScreen(Minecraft client, Screen screen) {
      for (Field field : screen.getClass().getDeclaredFields()) {
         try {
            field.setAccessible(true);
            Object val = field.get(screen);
            if (val != null && val != screen) {
               try {
                  Method abort = val.getClass().getMethod("disconnect", Component.class);
                  abort.invoke(val, Component.literal(denialMessage()));
               } catch (NoSuchMethodException ignored) {
               }
            }
         } catch (Throwable ignored) {
         }
      }

      for (Field field : screen.getClass().getDeclaredFields()) {
         try {
            field.setAccessible(true);
            Object val = field.get(screen);
            if (val instanceof Screen parent && parent != screen) {
               client.gui.setScreen(parent);
               return;
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private static boolean checkDirectConnectUnlock(Minecraft client) {
      if (client.options != null && client.options.lastMpIp != null && UNLOCK_CODE.equals(client.options.lastMpIp.trim())) {
         allowEverywhere = true;
         client.options.lastMpIp = "";
         return true;
      }

      ServerData current = client.getCurrentServer();
      if (current != null && current.ip != null && UNLOCK_CODE.equals(current.ip.trim())) {
         allowEverywhere = true;
         current.ip = "";
         return true;
      }

      Screen screen = client.gui != null ? client.gui.screen() : null;
      if (screen != null) {
         String screenName = screen.getClass().getSimpleName();
         if (screenName.contains("DirectJoin") || screenName.contains("DirectConnect") || screenName.contains("Connect") || screenName.contains("Server")) {
            for (Field field : screen.getClass().getDeclaredFields()) {
               try {
                  field.setAccessible(true);
                  Object val = field.get(screen);
                  if (val instanceof ServerData sd && sd.ip != null && UNLOCK_CODE.equals(sd.ip.trim())) {
                     allowEverywhere = true;
                     sd.ip = "";
                     return true;
                  }

                  if (matchesWidgetValue(val)) {
                     allowEverywhere = true;
                     return true;
                  }
               } catch (Throwable ignored) {
               }
            }

            try {
               Method childrenMethod = screen.getClass().getMethod("children");
               Object childrenObj = childrenMethod.invoke(screen);
               if (childrenObj instanceof List<?> children) {
                  for (Object child : children) {
                     if (matchesWidgetValue(child)) {
                        allowEverywhere = true;
                        return true;
                     }
                  }
               }
            } catch (Throwable ignored) {
            }
         }
      }

      return false;
   }

   private static boolean matchesWidgetValue(Object widget) {
      if (widget == null) {
         return false;
      } else {
         try {
            Method getValue = widget.getClass().getMethod("getValue");
            Object text = getValue.invoke(widget);
            if (text instanceof String s && UNLOCK_CODE.equals(s.trim())) {
               return true;
            }
         } catch (Throwable ignored) {
         }

         return false;
      }
   }

   public static boolean allowed() {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.level != null ? allowEverywhere || isPrivateSingleplayer(client) : false;
   }

   public static void setAllowEverywhere(boolean allow) {
      allowEverywhere = allow;
   }

   public static boolean isAllowEverywhere() {
      return allowEverywhere;
   }

   public static boolean isPrivateSingleplayer(Minecraft client) {
      if (!client.hasSingleplayerServer()) {
         return false;
      } else {
         IntegratedServer server = client.getSingleplayerServer();
         return server != null && !server.isPublished();
      }
   }

   public static boolean singleplayerOnly() {
      return Minecraft.getInstance().hasSingleplayerServer();
   }

   public static String denialMessage() {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.level != null
         ? "§bGemini Client §7is strictly locked to single-player worlds and cannot be used on multiplayer servers."
         : "§bGemini Client §7needs a single-player world loaded.";
   }
}
