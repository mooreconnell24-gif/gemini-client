package gemini.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a module's hook override ({@code onTick}, {@code onRender3D}, {@code onSendPacket}, ...) to run that
 * hook at a different priority than the module's own {@code priority()}. Hooks without it use the module priority.
 *
 * <p>The hooks themselves are the handlers: a module subscribes to an event simply by overriding its hook. The
 * {@link EventBus} reads the overrides and this annotation once when the module is registered, so dispatch is a
 * plain loop over pre-sorted arrays with no reflection, lookups or allocation per event.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
   ActionPriority value();
}
