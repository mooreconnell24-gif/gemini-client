package gemini.mixin;

import gemini.diags.MixinReport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Skips mixins whose target class is missing from the running Minecraft version, and after each apply checks that
 * every {@code gemini$} handler was actually wired to a call site. Anything that found no target is logged and
 * recorded in {@link MixinReport} so {@code .version} and the first world join can tell the player.
 *
 * <p>Only plain Mixin injectors are checked this way; see {@link #lateHandlers} for why MixinExtras ones are not.
 */
public final class GeminiMixinPlugin implements IMixinConfigPlugin {
   private static final Logger LOGGER = LoggerFactory.getLogger("Gemini Mixins");
   private static final String HANDLER_MARK = "$gemini$";
   /** How handler methods are named in the mixin classes themselves. */
   private static final String MIXIN_PREFIX = "gemini$";

   @Override
   public void onLoad(String mixinPackage) {
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (targetClassName == null || targetClassName.isEmpty()) {
         return false;
      }

      try {
         ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
         if (node != null) {
            return true;
         }
      } catch (Throwable ignored) {
         // Falls through to the "missing" report below.
      }

      LOGGER.warn("{} is not in this Minecraft version; {} was skipped", targetClassName, mixinClassName);
      MixinReport.missed(targetClassName, mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " (whole mixin)");
      return false;
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      return null;
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   /**
    * Handlers that MixinExtras wires up ({@code @WrapOperation}, {@code @WrapMethod} and the like). MixinExtras
    * weaves them in a later pass than this plugin's {@code postApply}, so at that point they have no caller yet
    * and would always be reported as missing. MixinExtras fails loudly by itself when one of them finds no target.
    */
   private static Set<String> lateHandlers(IMixinInfo mixinInfo) {
      Set<String> names = new HashSet<>();
      for (MethodNode method : mixinInfo.getClassNode(0).methods) {
         if (hasMixinExtrasAnnotation(method.visibleAnnotations) || hasMixinExtrasAnnotation(method.invisibleAnnotations)) {
            int mark = method.name.indexOf(MIXIN_PREFIX);
            names.add(mark >= 0 ? method.name.substring(mark + MIXIN_PREFIX.length()) : method.name);
         }
      }

      return names;
   }

   private static boolean hasMixinExtrasAnnotation(List<AnnotationNode> annotations) {
      if (annotations != null) {
         for (AnnotationNode annotation : annotations) {
            if (annotation.desc.startsWith("Lcom/llamalad7/mixinextras/")) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
      try {
         Set<String> handlers = new HashSet<>();
         for (MethodNode method : targetClass.methods) {
            if (method.name.contains(HANDLER_MARK)) {
               handlers.add(method.name);
            }
         }

         if (handlers.isEmpty()) {
            return;
         }

         Set<String> called = new HashSet<>();
         for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
               if (insn instanceof MethodInsnNode call) {
                  if (call.owner.equals(targetClass.name)) {
                     called.add(call.name);
                  }
               } else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                  for (Object arg : dynamic.bsmArgs) {
                     if (arg instanceof Handle handle && handle.getOwner().equals(targetClass.name)) {
                        called.add(handle.getName());
                     }
                  }
               }
            }
         }

         Set<String> appliedLater = lateHandlers(mixinInfo);
         for (String handler : handlers) {
            if (!called.contains(handler)) {
               String name = handler.substring(handler.indexOf(HANDLER_MARK) + HANDLER_MARK.length());
               if (appliedLater.contains(name)) {
                  continue;
               }

               LOGGER.warn("Injection gemini${} found no target in {} and is inactive", name, targetClassName);
               MixinReport.missed(targetClassName, "gemini$" + name);
            }
         }
      } catch (RuntimeException e) {
         LOGGER.debug("Could not verify injections in {}", targetClassName, e);
      }
   }
}
