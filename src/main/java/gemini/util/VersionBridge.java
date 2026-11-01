package gemini.util;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class VersionBridge {
   public enum Profile {
      AUTO("Auto", -1),
      MC_1_7("1.7.10", 5),
      MC_1_8("1.8.9", 47),
      MC_1_12("1.12.2", 340),
      MC_1_16("1.16.5", 754),
      MC_1_18("1.18.2", 758),
      MC_1_19("1.19.4", 762),
      MC_1_20("1.20.6", 766),
      MC_1_21("1.21.4", 769),
      MC_26_2("26.2+", 800);

      private final String label;
      private final int protocol;

      Profile(String label, int protocol) {
         this.label = label;
         this.protocol = protocol;
      }

      public String label() {
         return this.label;
      }

      public int protocol() {
         return this.protocol;
      }
   }

   private static Profile forcedProfile = Profile.AUTO;
   private static boolean viaChecked = false;
   private static Object viaTranslator = null;
   private static Method viaGetTargetVersion = null;
   private static Method viaVersionGetName = null;
   private static Method viaVersionGetVersion = null;

   private VersionBridge() {
   }

   public static Profile getForcedProfile() {
      return forcedProfile;
   }

   public static void setForcedProfile(Profile profile) {
      forcedProfile = profile == null ? Profile.AUTO : profile;
   }

   public static boolean setForcedProfileByName(String input) {
      if (input == null || input.isBlank()) {
         forcedProfile = Profile.AUTO;
         return true;
      }
      String norm = input.trim().toLowerCase(Locale.ROOT);
      if (norm.equals("auto") || norm.equals("default") || norm.equals("native")) {
         forcedProfile = Profile.AUTO;
         return true;
      }
      if (norm.startsWith("1.7")) {
         forcedProfile = Profile.MC_1_7;
         return true;
      }
      if (norm.startsWith("1.8")) {
         forcedProfile = Profile.MC_1_8;
         return true;
      }
      if (norm.startsWith("1.9") || norm.startsWith("1.10") || norm.startsWith("1.11") || norm.startsWith("1.12")) {
         forcedProfile = Profile.MC_1_12;
         return true;
      }
      if (norm.startsWith("1.13") || norm.startsWith("1.14") || norm.startsWith("1.15") || norm.startsWith("1.16") || norm.startsWith("1.17")) {
         forcedProfile = Profile.MC_1_16;
         return true;
      }
      if (norm.startsWith("1.18")) {
         forcedProfile = Profile.MC_1_18;
         return true;
      }
      if (norm.startsWith("1.19")) {
         forcedProfile = Profile.MC_1_19;
         return true;
      }
      if (norm.startsWith("1.20")) {
         forcedProfile = Profile.MC_1_20;
         return true;
      }
      if (norm.startsWith("1.21")) {
         forcedProfile = Profile.MC_1_21;
         return true;
      }
      if (norm.startsWith("26") || norm.startsWith("1.26") || norm.equals("latest") || norm.equals("modern")) {
         forcedProfile = Profile.MC_26_2;
         return true;
      }
      return false;
   }

   public static Profile activeProfile() {
      if (forcedProfile != Profile.AUTO) {
         return forcedProfile;
      }
      String viaName = detectViaTargetName();
      if (viaName != null && !viaName.isEmpty()) {
         Profile fromVia = parseVersionString(viaName);
         if (fromVia != Profile.AUTO) {
            return fromVia;
         }
      }
      String nativeName = nativeVersionName();
      Profile fromNative = parseVersionString(nativeName);
      return fromNative == Profile.AUTO ? Profile.MC_26_2 : fromNative;
   }

   public static String activeVersionLabel() {
      if (forcedProfile != Profile.AUTO) {
         return forcedProfile.label() + " (Forced)";
      }
      String viaName = detectViaTargetName();
      if (viaName != null && !viaName.isEmpty()) {
         return viaName + " (ViaFabricPlus)";
      }
      return nativeVersionName() + " (Native)";
   }

   public static boolean isLegacyCombat() {
      Profile p = activeProfile();
      return p == Profile.MC_1_7 || p == Profile.MC_1_8;
   }

   public static boolean requiresTwoBlockCrystalSpace() {
      Profile p = activeProfile();
      return p == Profile.MC_1_7 || p == Profile.MC_1_8 || p == Profile.MC_1_12;
   }

   public static boolean isPre1_18WorldHeight() {
      Profile p = activeProfile();
      return p == Profile.MC_1_7 || p == Profile.MC_1_8 || p == Profile.MC_1_12 || p == Profile.MC_1_16;
   }

   public static boolean supportsMaceAndWindCharges() {
      Profile p = activeProfile();
      return p == Profile.MC_1_21 || p == Profile.MC_26_2;
   }

   public static float legacyHitboxMargin() {
      return isLegacyCombat() ? 0.1F : 0.0F;
   }

   public static boolean canPlaceCrystalSpace(Level level, BlockPos basePos) {
      if (level == null || basePos == null) {
         return false;
      }
      BlockPos above1 = basePos.above();
      if (!level.getBlockState(above1).isAir()) {
         return false;
      }
      if (requiresTwoBlockCrystalSpace()) {
         BlockPos above2 = basePos.above(2);
         return level.getBlockState(above2).isAir();
      }
      return true;
   }

   public static int minWorldY(Level level) {
      if (isPre1_18WorldHeight()) {
         return 0;
      }
      if (level != null) {
         try {
            return level.getMinY();
         } catch (Throwable t) {
            try {
               Method m = level.getClass().getMethod("getMinBuildHeight");
               Object res = m.invoke(level);
               if (res instanceof Integer i) {
                  return i;
               }
            } catch (Throwable ignored) {
            }
         }
      }
      return -64;
   }

   public static int maxWorldY(Level level) {
      if (isPre1_18WorldHeight()) {
         return 256;
      }
      if (level != null) {
         try {
            return level.getMaxY();
         } catch (Throwable t) {
            try {
               Method m = level.getClass().getMethod("getMaxBuildHeight");
               Object res = m.invoke(level);
               if (res instanceof Integer i) {
                  return i;
               }
            } catch (Throwable ignored) {
            }
         }
      }
      return 320;
   }

   private static Profile parseVersionString(String raw) {
      if (raw == null) {
         return Profile.AUTO;
      }
      String s = raw.toLowerCase(Locale.ROOT);
      if (s.contains("1.7")) {
         return Profile.MC_1_7;
      }
      if (s.contains("1.8")) {
         return Profile.MC_1_8;
      }
      if (s.contains("1.9") || s.contains("1.10") || s.contains("1.11") || s.contains("1.12")) {
         return Profile.MC_1_12;
      }
      if (s.contains("1.13") || s.contains("1.14") || s.contains("1.15") || s.contains("1.16") || s.contains("1.17")) {
         return Profile.MC_1_16;
      }
      if (s.contains("1.18")) {
         return Profile.MC_1_18;
      }
      if (s.contains("1.19")) {
         return Profile.MC_1_19;
      }
      if (s.contains("1.20")) {
         return Profile.MC_1_20;
      }
      if (s.contains("1.21")) {
         return Profile.MC_1_21;
      }
      return Profile.MC_26_2;
   }

   private static String nativeVersionName() {
      try {
         Object worldVersion = SharedConstants.getCurrentVersion();
         if (worldVersion != null) {
            for (String methodName : new String[]{"getName", "name", "id"}) {
               try {
                  Method m = worldVersion.getClass().getMethod(methodName);
                  Object val = m.invoke(worldVersion);
                  if (val instanceof String str && !str.isEmpty()) {
                     return str;
                  }
               } catch (NoSuchMethodException ignored) {
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return "26.2";
   }

   private static String detectViaTargetName() {
      try {
         if (!viaChecked) {
            viaChecked = true;
            Class<?> translatorClass = Class.forName("de.florianmichael.viafabricplus.protocoltranslator.ProtocolTranslator");
            viaGetTargetVersion = translatorClass.getMethod("getTargetVersion");
         }
         if (viaGetTargetVersion != null) {
            Object versionObj = viaGetTargetVersion.invoke(viaTranslator);
            if (versionObj != null) {
               if (viaVersionGetName == null) {
                  viaVersionGetName = versionObj.getClass().getMethod("getName");
               }
               Object nameObj = viaVersionGetName.invoke(versionObj);
               if (nameObj instanceof String str) {
                  return str;
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return null;
   }
}
