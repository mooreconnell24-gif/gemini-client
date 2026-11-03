package gemini.input;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Key and mouse-button codes for Minecraft 26.3+, which reads input through SDL3 instead of GLFW.
 * Keyboard values are SDL scancodes (what {@code KeyEvent.key()}, {@code InputConstants.isKeyDown} and
 * {@code InputConstants.Type.KEYBOARD} use); mouse buttons are SDL buttons, numbered from 1.
 *
 * <p>Gemini 3.0.0 and earlier (Minecraft 26.2) stored GLFW key codes; {@link #fromGlfw(int)} converts them.
 */
public final class Keys {
   public static final int NONE = -1;

   public static final int A = 4;
   public static final int F = 9;
   public static final int ENTER = 40;
   public static final int ESCAPE = 41;
   public static final int BACKSPACE = 42;
   public static final int TAB = 43;
   public static final int SPACE = 44;
   public static final int DELETE = 76;
   public static final int RIGHT = 79;
   public static final int LEFT = 80;
   public static final int DOWN = 81;
   public static final int UP = 82;
   public static final int KP_ENTER = 88;
   public static final int LEFT_CONTROL = 224;
   public static final int LEFT_SHIFT = 225;
   public static final int LEFT_ALT = 226;
   public static final int RIGHT_CONTROL = 228;
   public static final int RIGHT_SHIFT = 229;
   public static final int RIGHT_ALT = 230;

   public static final int MOUSE_LEFT = 1;
   public static final int MOUSE_MIDDLE = 2;
   public static final int MOUSE_RIGHT = 3;

   private static final Int2IntOpenHashMap GLFW_TO_SDL = new Int2IntOpenHashMap();

   static {
      GLFW_TO_SDL.defaultReturnValue(NONE);

      for (int i = 0; i < 26; i++) {
         GLFW_TO_SDL.put(65 + i, 4 + i); // A-Z
      }

      GLFW_TO_SDL.put(48, 39); // 0
      for (int i = 1; i <= 9; i++) {
         GLFW_TO_SDL.put(48 + i, 29 + i); // 1-9
      }

      for (int i = 0; i < 12; i++) {
         GLFW_TO_SDL.put(290 + i, 58 + i); // F1-F12
      }

      for (int i = 0; i < 12; i++) {
         GLFW_TO_SDL.put(302 + i, 104 + i); // F13-F24
      }

      put(32, SPACE);
      put(39, 52); // apostrophe
      put(44, 54); // comma
      put(45, 45); // minus
      put(46, 55); // period
      put(47, 56); // slash
      put(59, 51); // semicolon
      put(61, 46); // equals
      put(91, 47); // left bracket
      put(92, 49); // backslash
      put(93, 48); // right bracket
      put(96, 53); // grave accent
      put(161, 100); // world 1 / non-US backslash
      put(256, ESCAPE);
      put(257, ENTER);
      put(258, TAB);
      put(259, BACKSPACE);
      put(260, 73); // insert
      put(261, DELETE);
      put(262, RIGHT);
      put(263, LEFT);
      put(264, DOWN);
      put(265, UP);
      put(266, 75); // page up
      put(267, 78); // page down
      put(268, 74); // home
      put(269, 77); // end
      put(280, 57); // caps lock
      put(281, 71); // scroll lock
      put(282, 83); // num lock
      put(283, 70); // print screen
      put(284, 72); // pause

      for (int i = 1; i <= 9; i++) {
         GLFW_TO_SDL.put(320 + i, 88 + i); // keypad 1-9
      }

      put(320, 98); // keypad 0
      put(330, 99); // keypad decimal
      put(331, 84); // keypad divide
      put(332, 85); // keypad multiply
      put(333, 86); // keypad subtract
      put(334, 87); // keypad add
      put(335, KP_ENTER);
      put(336, 103); // keypad equals
      put(340, LEFT_SHIFT);
      put(341, LEFT_CONTROL);
      put(342, LEFT_ALT);
      put(343, 227); // left super
      put(344, RIGHT_SHIFT);
      put(345, RIGHT_CONTROL);
      put(346, RIGHT_ALT);
      put(347, 231); // right super
      put(348, 101); // menu
   }

   private Keys() {
   }

   private static void put(int glfw, int sdl) {
      GLFW_TO_SDL.put(glfw, sdl);
   }

   /** Converts a GLFW key code (Minecraft 26.2 and earlier) to an SDL scancode, or {@link #NONE} if it has no match. */
   public static int fromGlfw(int glfwKey) {
      return glfwKey <= 0 ? NONE : GLFW_TO_SDL.get(glfwKey);
   }

   /**
    * Converts an SDL mouse button (1 = left, 2 = middle, 3 = right) to the GLFW numbering Gemini's screens were
    * written against (0 = left, 1 = right, 2 = middle).
    */
   public static int glfwButton(int sdlButton) {
      return switch (sdlButton) {
         case MOUSE_LEFT -> 0;
         case MOUSE_RIGHT -> 1;
         case MOUSE_MIDDLE -> 2;
         default -> sdlButton - 1;
      };
   }

   public static boolean isEnter(int key) {
      return key == ENTER || key == KP_ENTER;
   }
}
