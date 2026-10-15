package ua.zefir.zombiebuddyleafbridge.runtime;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class RuntimeInstrumentation {
  private static final System.Logger LOGGER =
      System.getLogger(RuntimeInstrumentation.class.getName());
  private static final String AGENT_CLASS = "ua.zefir.zombiebuddyleafbridge.runtime.RuntimeAgent";
  private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;
  private static Instrumentation instrumentation;

  public static synchronized Instrumentation acquire(int timeoutSeconds) throws Exception {
    if (timeoutSeconds <= 0)
      throw new IllegalArgumentException("Attachment timeout must be positive");
    if (instrumentation != null) return instrumentation;
    Path directory =
        Files.createTempDirectory(
            Path.of(System.getProperty("java.io.tmpdir")), "zombie-buddy-leaf-bridge-attach-");
    Path agent = directory.resolve("instrumentation.jar");
    Path output = directory.resolve("attach.log");
    Path helper = null;
    directory.toFile().deleteOnExit();
    agent.toFile().deleteOnExit();
    output.toFile().deleteOnExit();
    try {
      createAgentJar(agent);
      helper = NativeAttach.extract(directory);
      helper.toFile().deleteOnExit();
      attachAgent(helper, agent, output, timeoutSeconds);
      instrumentation = readInstrumentation();
      return instrumentation;
    } finally {
      boolean helperDeleted = helper == null || cleanup(helper, false);
      boolean outputDeleted = cleanup(output, false);
      boolean agentDeleted = cleanup(agent, instrumentation != null);
      if (helperDeleted && outputDeleted && agentDeleted) cleanup(directory, false);
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

  private static void attachAgent(Path helper, Path agent, Path output, int timeoutSeconds)
      throws Exception {
    ProcessBuilder builder =
        new ProcessBuilder(
                helper.toString(),
                Long.toString(ProcessHandle.current().pid()),
                "load",
                "instrument",
                "false",
                agent.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
    Process process;
    try {
      process = builder.start();
    } catch (IOException failure) {
      throw new IOException(
          "Cannot start bundled jattach: "
              + helper
              + ". Check executable permissions, antivirus restrictions, and whether the temporary"
              + " directory allows execution.",
          failure);
    }
    try {
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        throw attachmentFailure(
            "ZombieBuddy runtime attachment timed out after " + timeoutSeconds + " seconds",
            process,
            output);
      }
      if (process.exitValue() != 0) {
        throw attachmentFailure("ZombieBuddy requires runtime attachment support", process, output);
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for ZombieBuddy runtime attachment", failure);
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  private static IllegalStateException attachmentFailure(
      String message, Process process, Path output) {
    String status = process.isAlive() ? "still running" : "exit code " + process.exitValue();
    String context = message + " (helper " + status + ", output: " + output + ")";
    try {
      return new IllegalStateException(context + System.lineSeparator() + Files.readString(output));
    } catch (IOException failure) {
      IllegalStateException attachmentFailure = new IllegalStateException(context);
      attachmentFailure.addSuppressed(failure);
      return attachmentFailure;
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
