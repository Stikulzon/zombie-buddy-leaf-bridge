package ua.zefir.zombiebuddyleafbridge.runtime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimePaths {
  public static final String CONFIG_FILE = "zombie-buddy-leaf-bridge.properties";
  public static final String RUNTIME_JAR_PROPERTY = "zombieBuddyLeafBridge.runtimeJar";
  private static final String WORKSHOP_ID = "3619862853";
  private static final String WORKSHOP_CONTENT_PATH = "workshop/content/108600";
  private static final String RUNTIME_JAR_PATH = "mods/ZombieBuddy/libs/ZombieBuddy.jar";
  private static final String OVERRIDE_GUIDANCE =
      "Set runtimeJar in " + CONFIG_FILE + " or -D" + RUNTIME_JAR_PROPERTY + "=<ZombieBuddy.jar>.";

  public static Path runtimeJar(Path gameJar, Path gameDirectory, String override)
      throws IOException {
    if (override != null) {
      if (override.isBlank()) {
        throw new IOException(
            "ZombieBuddy runtime override must name a JAR file. " + OVERRIDE_GUIDANCE);
      }
      Path candidate = Path.of(override);
      if (!candidate.isAbsolute()) candidate = gameDirectory.resolve(candidate);
      return requireRuntimeJar(candidate.toAbsolutePath().normalize());
    }
    if (gameJar == null) {
      throw new IOException("Leaf did not provide the game JAR location. " + OVERRIDE_GUIDANCE);
    }
    Path location = gameJar.toRealPath();
    for (Path directory = location.getParent();
        directory != null;
        directory = directory.getParent()) {
      if (directory.getFileName() == null
          || !directory.getFileName().toString().equalsIgnoreCase("steamapps")) continue;
      return requireRuntimeJar(
          directory.resolve(WORKSHOP_CONTENT_PATH).resolve(WORKSHOP_ID).resolve(RUNTIME_JAR_PATH));
    }
    throw new IOException(
        "Cannot locate Steam Workshop relative to " + location + ". " + OVERRIDE_GUIDANCE);
  }

  private static Path requireRuntimeJar(Path candidate) throws IOException {
    if (!Files.isRegularFile(candidate)) {
      throw new FileNotFoundException(
          "ZombieBuddy runtime JAR does not exist: "
              + candidate
              + ". Subscribe to ZombieBuddy (Workshop "
              + WORKSHOP_ID
              + "), finish its download, or use an explicit override. "
              + OVERRIDE_GUIDANCE);
    }
    return candidate.toRealPath();
  }
}
