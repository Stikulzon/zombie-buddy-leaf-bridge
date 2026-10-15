package ua.zefir.zombiebuddyleafbridge.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;

final class NativeAttach {
  static Path extract(Path directory) throws IOException {
    String resource = resourcePath(System.getProperty("os.name"), System.getProperty("os.arch"));
    Path executable = directory.resolve(resource.endsWith(".exe") ? "jattach.exe" : "jattach");
    try {
      try (var input = NativeAttach.class.getClassLoader().getResourceAsStream(resource)) {
        if (input == null) throw new IOException("Missing bundled jattach executable: " + resource);
        Files.copy(input, executable);
      }
      if (!resource.endsWith(".exe")) {
        Files.setPosixFilePermissions(
            executable,
            Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
      }
    } catch (IOException | RuntimeException failure) {
      try {
        Files.deleteIfExists(executable);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return executable;
  }

  static String resourcePath(String osName, String architecture) {
    String os = osName.toLowerCase(Locale.ROOT);
    String arch = architecture.toLowerCase(Locale.ROOT);
    boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
    boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
    if ((os.contains("mac") || os.contains("darwin")) && (x64 || arm64)) {
      return "jattach/macos-universal/jattach";
    }
    if (os.contains("windows") && x64) return "jattach/windows-x86_64/jattach.exe";
    if (os.contains("linux")) {
      if (x64) return "jattach/linux-x86_64/jattach";
      if (arm64) return "jattach/linux-aarch64/jattach";
    }
    throw new IllegalStateException(
        "Unsupported jattach platform: "
            + osName
            + " / "
            + architecture
            + ". Supported: Windows x64, Linux x64/ARM64, macOS x64/ARM64.");
  }
}
