package com.inferno.fabric.checks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client brands seen during login/configuration, read once when the player joins. */
public final class ClientBrands {
   private static final Map<UUID, String> BRANDS = new ConcurrentHashMap<>();

   private ClientBrands() {
   }

   public static void put(UUID player, String brand) {
      if (player != null && brand != null) {
         BRANDS.put(player, brand.length() > 64 ? brand.substring(0, 64) : brand);
      }
   }

   public static String get(UUID player) {
      return BRANDS.get(player);
   }

   public static void forget(UUID player) {
      BRANDS.remove(player);
   }
}
