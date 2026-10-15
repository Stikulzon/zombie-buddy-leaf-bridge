package ua.zefir.zombiebuddyleafbridge;

import dev.aoqia.leaf.loader.api.LeafLoader;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import org.spongepowered.asm.mixin.Mixins;
import ua.zefir.zombiebuddyleafbridge.runtime.RuntimeInstrumentation;
import ua.zefir.zombiebuddyleafbridge.runtime.RuntimePaths;

public final class GameRuntime {
  private static final System.Logger LOGGER = System.getLogger(GameRuntime.class.getName());
  private static final String PACKAGE = "me.zed_0xff.zombie_buddy.";
  private static final String INPUT_GAME_JARS = "leaf-loader:inputGameJars";
  private static final String MIXIN_CONFIGURATION = "zombie-buddy-leaf-bridge.mixins.json";
  private static final int DEFAULT_ATTACH_TIMEOUT_SECONDS = 30;
  private static boolean initialized;
  private static boolean started;

  public static synchronized void initialize() throws Exception {
    if (initialized) return;
    if (started)
      throw new IllegalStateException("ZombieBuddy startup failed; restart before retrying");

    Properties config = configuration();
    if (!Boolean.parseBoolean(config.getProperty("enabled", "true"))) return;
    int attachTimeoutSeconds = attachTimeoutSeconds(config);
    started = true;
    Path source = runtimeJar(config, LeafLoader.getInstance().getGameDir());
    validateRuntimeJar(source);
    Instrumentation instrumentation = RuntimeInstrumentation.acquire(attachTimeoutSeconds);
    ensureRuntimeNotLoaded(instrumentation);
    LeafLauncherBase.getLauncher().addToClassPath(source);
    Mixins.addConfiguration(MIXIN_CONFIGURATION);
    startAgent(source, config.getProperty("arguments", ""), instrumentation);
  }

  private static int attachTimeoutSeconds(Properties config) {
    String value =
        config.getProperty(
            "attachTimeoutSeconds", Integer.toString(DEFAULT_ATTACH_TIMEOUT_SECONDS));
    try {
      int seconds = Integer.parseInt(value.trim());
      if (seconds > 0) return seconds;
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(
          "attachTimeoutSeconds must be a positive integer: " + value, failure);
    }
    throw new IllegalArgumentException("attachTimeoutSeconds must be a positive integer: " + value);
  }

  private static void validateRuntimeJar(Path source) throws Exception {
    try (JarFile jar = new JarFile(source.toFile())) {
      if (jar.getJarEntry(PACKAGE.replace('.', '/') + "Agent.class") == null) {
        throw new IllegalStateException("Not a ZombieBuddy runtime JAR: " + source);
      }
    }
  }

  private static void ensureRuntimeNotLoaded(Instrumentation instrumentation) {
    for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
      if (loaded.getName().startsWith(PACKAGE)) {
        throw new IllegalStateException(
            "ZombieBuddy loaded before bridge startup: " + loaded.getName());
      }
    }
  }

  private static void startAgent(Path source, String arguments, Instrumentation instrumentation)
      throws Exception {
    ClassLoader game = gameLoader();
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(game);
      Class<?> agent = Class.forName(PACKAGE + "Agent", false, game);
      if (agent.getClassLoader() != game) {
        throw new IllegalStateException("ZombieBuddy runtime must be defined by Leaf");
      }
      var codeSource = agent.getProtectionDomain().getCodeSource();
      if (codeSource == null) {
        throw new IllegalStateException("Cannot determine the loaded ZombieBuddy runtime JAR");
      }
      Path loadedSource = Path.of(codeSource.getLocation().toURI()).toRealPath();
      if (!Files.isSameFile(source, loadedSource)) {
        throw new IllegalStateException(
            "ZombieBuddy runtime loaded from "
                + loadedSource
                + " instead of the selected JAR "
                + source);
      }
      agent
          .getMethod("premain", String.class, Instrumentation.class)
          .invoke(null, arguments, instrumentation);
      initialized = true;
      LOGGER.log(System.Logger.Level.INFO, "ZombieBuddy initialized inside Leaf from " + source);
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  static Properties configuration() throws Exception {
    Properties config = new Properties();
    Path file = LeafLoader.getInstance().getConfigDir().resolve(RuntimePaths.CONFIG_FILE);
    if (Files.isRegularFile(file)) {
      try (var input = Files.newInputStream(file)) {
        config.load(input);
      }
    }
    return config;
  }

  public static Path runtimeJar(Properties config, Path gameDirectory) throws Exception {
    String configured =
        config.getProperty("runtimeJar", System.getProperty(RuntimePaths.RUNTIME_JAR_PROPERTY));
    Path gameJar = null;
    if (configured == null) {
      Object inputGameJars = LeafLoader.getInstance().getObjectShare().get(INPUT_GAME_JARS);
      if (inputGameJars instanceof List<?> jars
          && !jars.isEmpty()
          && jars.getFirst() instanceof Path path) {
        gameJar = path;
      }
    }
    return RuntimePaths.runtimeJar(gameJar, gameDirectory, configured);
  }

  public static ClassLoader gameLoader() {
    return GameRuntime.class.getClassLoader();
  }

  public static Class<?> resolveTarget(String name) throws ClassNotFoundException {
    return Class.forName(name, false, gameLoader());
  }

  public static Class<?>[] filterGameClasses(Class<?>[] classes) {
    ClassLoader loader = gameLoader();
    return java.util.Arrays.stream(classes)
        .filter(type -> type.getClassLoader() == loader)
        .toArray(Class<?>[]::new);
  }

  public static void appendApprovedLibrary(JarFile jar) throws Exception {
    try (jar) {
      LeafLauncherBase.getLauncher().addToClassPath(Path.of(jar.getName()).toRealPath());
    }
  }
}
