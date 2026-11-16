package gemini.module.modules.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.Setting;
import org.junit.jupiter.api.Test;

/**
 * Multi Task's gates through its public API. The open gate needs the module running, which needs a single-player
 * world, so what is checked here is that the gates stay shut whenever the module is not running, whatever the
 * settings say, and the info text for each combination of settings.
 */
class MultiTaskTest {
   private static BooleanSetting setting(MultiTask module, String name) {
      for (Setting<?> setting : module.settings()) {
         if (setting.name().equals(name)) {
            return (BooleanSetting)setting;
         }
      }

      throw new AssertionError("no setting " + name);
   }

   @Test
   void gatesStayShutWhileTheModuleIsOff() {
      MultiTask module = new MultiTask();
      assertFalse(module.isEnabled());
      assertFalse(MultiTask.attackWhileUsing());
      assertFalse(MultiTask.mineWhileUsing());
   }

   @Test
   void outsideAWorldTheModuleCannotStartSoTheGatesStayShut() {
      MultiTask module = new MultiTask();
      // A saved "on" state is only applied when the module may run; without a world it may not.
      module.loadState(true, -1);
      assertFalse(module.isEnabled());
      assertFalse(MultiTask.attackWhileUsing());
      assertFalse(MultiTask.mineWhileUsing());
   }

   @Test
   void infoNamesWhatIsSwitchedOn() {
      MultiTask module = new MultiTask();
      BooleanSetting attack = setting(module, "Attack While Using");
      BooleanSetting mine = setting(module, "Mine While Using");
      assertEquals("Both", module.info());
      mine.set(false);
      assertEquals("Attack", module.info());
      attack.set(false);
      mine.set(true);
      assertEquals("Mine", module.info());
      mine.set(false);
      assertEquals("Off", module.info());
   }
}
