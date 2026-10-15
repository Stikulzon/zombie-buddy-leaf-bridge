package ua.zefir.zombiebuddyleafbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.classgraph.ClassInfo;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.matcher.ElementMatchers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.zefir.zombiebuddyleafbridge.GameRuntime;

@Mixin(targets = "me.zed_0xff.zombie_buddy.PatchEngine", remap = false)
public abstract class PatchEngineMixin {
  @ModifyExpressionValue(
      method = "applyPatches",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/lang/instrument/Instrumentation;getAllLoadedClasses()[Ljava/lang/Class;"))
  private static Class<?>[] zombieBuddyLeafBridge$filterLoadedClasses(Class<?>[] original) {
    return GameRuntime.filterGameClasses(original);
  }

  @Redirect(
      method = "applyPatches",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"),
      require = 2)
  private static Class<?> zombieBuddyLeafBridge$resolveWithoutInitialization(String name)
      throws ClassNotFoundException {
    return GameRuntime.resolveTarget(name);
  }

  @Redirect(
      method = "collectPatches",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/github/classgraph/ClassInfo;loadClass()Ljava/lang/Class;"),
      require = 2)
  private static Class<?> zombieBuddyLeafBridge$resolveScannedClass(ClassInfo info)
      throws ClassNotFoundException {
    return GameRuntime.resolveTarget(info.getName());
  }

  @ModifyExpressionValue(
      method = "applyPatches",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/bytebuddy/agent/builder/AgentBuilder;type(Lnet/bytebuddy/matcher/ElementMatcher;)Lnet/bytebuddy/agent/builder/AgentBuilder$Identified$Narrowable;"))
  private static AgentBuilder.Identified.Narrowable zombieBuddyLeafBridge$scopeToGameLoader(
      AgentBuilder.Identified.Narrowable original) {
    return original.and(ElementMatchers.any(), ElementMatchers.is(GameRuntime.gameLoader()));
  }
}
