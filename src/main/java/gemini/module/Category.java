package gemini.module;

public enum Category {
   COMBAT("Combat", -41846),
   MOVEMENT("Movement", -11086174),
   PLAYER("Player", -9524993),
   WORLD("World", -14249),
   RENDER("Render", -5011201),
   HUD("HUD", -26020),
   CLIENT("Misc", -12714014);

   private final String displayName;
   private final int accent;

   private Category(String displayName, int accent) {
      this.displayName = displayName;
      this.accent = accent;
   }

   public String displayName() {
      return this.displayName;
   }

   public int accent() {
      return this.accent;
   }
}
