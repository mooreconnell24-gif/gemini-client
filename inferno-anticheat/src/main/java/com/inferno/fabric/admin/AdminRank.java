package com.inferno.fabric.admin;

import java.util.Locale;

public enum AdminRank {
   NONE(0, "None", "§7"),
   HELPER(1, "Helper", "§a"),
   MOD(2, "Mod", "§b"),
   ADMIN(3, "Admin", "§6"),
   OWNER(4, "Owner", "§c§l");

   private final int level;
   private final String label;
   private final String color;

   AdminRank(int level, String label, String color) {
      this.level = level;
      this.label = label;
      this.color = color;
   }

   public int level() {
      return this.level;
   }

   public String label() {
      return this.label;
   }

   public String colored() {
      return this.color + this.label;
   }

   public boolean atLeast(AdminRank other) {
      return this.level >= other.level;
   }

   public static AdminRank from(String name) {
      if (name != null && !name.isBlank()) {
         try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            return NONE;
         }
      } else {
         return NONE;
      }
   }
}
