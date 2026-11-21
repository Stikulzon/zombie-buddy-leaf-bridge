package ua.zefir.zombiebuddyleafbridge.runtime;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class RuntimeInstrumentation {
  private static final System.Logger LOGGER =
      System.getLogger(RuntimeInstrumentation.class.getName());
  private static final String AGENT_CLASS = "ua.zefir.zombiebuddyleafbridge.runtime.RuntimeAgent";
  private static Instrumentation instrumentation;

  public static synchronized Instrumentation acquire() throws Exception {
    if (instrumentation != null) return instrumentation;
    Path directory =
        Files.createTempDirectory(
            Path.of(System.getProperty("java.io.tmpdir")), "zombie-buddy-leaf-bridge-attach-");
    Path agent = directory.resolve("instrumentation.jar");
    directory.toFile().deleteOnExit();
    agent.toFile().deleteOnExit();
    try {
      createAgentJar(agent);
      NativeAttach.attach(agent);
      instrumentation = readInstrumentation();
      return instrumentation;
    } finally {
      if (cleanup(agent, instrumentation != null)) cleanup(directory, false);
    }
  }

  private static void createAgentJar(Path agent) throws IOException {
    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.putValue("Manifest-Version", "1.0");
    attributes.putValue("Agent-Class", AGENT_CLASS);
    attributes.putValue("Can-Redefine-Classes", "true");
    attributes.putValue("Can-Retransform-Classes", "true");
    String resource = AGENT_CLASS.replace('.', '/') + ".class";
    try (var input = RuntimeInstrumentation.class.getClassLoader().getResourceAsStream(resource);
        var jar = new JarOutputStream(Files.newOutputStream(agent), manifest)) {
      if (input == null) throw new IllegalStateException("Missing runtime instrumentation agent");
      jar.putNextEntry(new JarEntry(resource));
      input.transferTo(jar);
      jar.closeEntry();
    }
  }

  private static Instrumentation readInstrumentation() throws ReflectiveOperationException {
    Class<?> installed = Class.forName(AGENT_CLASS, true, ClassLoader.getSystemClassLoader());
    Instrumentation acquired =
        (Instrumentation) installed.getMethod("instrumentation").invoke(null);
    if (acquired == null || !acquired.isRetransformClassesSupported()) {
      throw new IllegalStateException(
          "ZombieBuddy runtime instrumentation does not support retransformation");
    }
    return acquired;
  }

  private static boolean cleanup(Path file, boolean loadedAgent) {
    try {
      Files.deleteIfExists(file);
      return true;
    } catch (IOException failure) {
      if (loadedAgent
          && failure instanceof FileSystemException
          && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        LOGGER.log(
            System.Logger.Level.DEBUG,
            "The JVM retains the loaded instrumentation agent; deletion is deferred: " + file);
        return false;
      }
      LOGGER.log(
          System.Logger.Level.ERROR, "Cannot delete runtime attachment file: " + file, failure);
      return false;
    }
  }
}
