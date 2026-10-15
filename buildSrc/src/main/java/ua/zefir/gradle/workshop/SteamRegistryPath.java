package ua.zefir.gradle.workshop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class SteamRegistryPath implements ValueSource<String, ValueSourceParameters.None> {
  private static final Logger LOGGER = Logging.getLogger(SteamRegistryPath.class);
  private static final long TIMEOUT_MILLIS = 3000;
  private static final Pattern REGISTRY_PATH = Pattern.compile("SteamPath\\s+REG_SZ\\s+(.+)");

  @Override
  public String obtain() {
    Process process = null;
    try {
      process =
          new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        LOGGER.warn("Steam registry lookup timed out; using standard installation paths.");
        return null;
      }
      if (process.exitValue() != 0) {
        LOGGER.info(
            "Steam registry lookup exited with {}; using standard installation paths.",
            process.exitValue());
        return null;
      }
      try (var output = process.getInputStream()) {
        var matcher =
            REGISTRY_PATH.matcher(new String(output.readAllBytes(), StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1).trim() : null;
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new GradleException("Steam registry lookup was interrupted.", failure);
    } catch (IOException failure) {
      LOGGER.warn("Steam registry lookup failed; using standard installation paths.", failure);
      return null;
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
