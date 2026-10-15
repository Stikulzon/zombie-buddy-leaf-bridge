package ua.zefir.zombiebuddyleafbridge.mixin;

import io.github.classgraph.ClassInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.zefir.zombiebuddyleafbridge.GameRuntime;

@Mixin(targets = "me.zed_0xff.zombie_buddy.Exposer", remap = false)
public abstract class ExposerMixin {
  @Redirect(
      method = "exposeAnnotatedClasses(Lio/github/classgraph/ScanResult;Ljava/lang/String;)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/github/classgraph/ClassInfo;loadClass()Ljava/lang/Class;"))
  private static Class<?> zombieBuddyLeafBridge$resolveScannedClass(ClassInfo info)
      throws ClassNotFoundException {
    return GameRuntime.resolveTarget(info.getName());
  }
}
