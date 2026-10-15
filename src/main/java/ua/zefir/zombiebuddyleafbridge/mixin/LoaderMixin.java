package ua.zefir.zombiebuddyleafbridge.mixin;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.zefir.zombiebuddyleafbridge.GameRuntime;

@Mixin(targets = "me.zed_0xff.zombie_buddy.Loader", remap = false)
public abstract class LoaderMixin {
  @Redirect(
      method = "addJarToClasspath",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/lang/instrument/Instrumentation;appendToSystemClassLoaderSearch(Ljava/util/jar/JarFile;)V"))
  private static void zombieBuddyLeafBridge$appendApprovedLibrary(
      Instrumentation instrumentation, JarFile jar) throws Exception {
    GameRuntime.appendApprovedLibrary(jar);
  }
}
