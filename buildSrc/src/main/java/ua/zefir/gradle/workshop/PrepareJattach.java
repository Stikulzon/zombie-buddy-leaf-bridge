package ua.zefir.gradle.workshop;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class PrepareJattach extends DefaultTask {
  private static final String RELEASE =
      "https://github.com/jattach/jattach/releases/download/v2.2/";
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
  private static final List<Artifact> ARTIFACTS =
      List.of(
          new Artifact(
              "jattach-windows.zip",
              "d021e861cf3c1172497b419786d96836d91506a55ce1d067cd3ffff4ed50a3ff",
              "windows-x86_64/jattach.exe"),
          new Artifact(
              "jattach-linux-x64.tgz",
              "acd9e17f15749306be843df392063893e97bfecc5260eef73ee98f06e5cfe02f",
              "linux-x86_64/jattach"),
          new Artifact(
              "jattach-linux-arm64.tgz",
              "288ae5ed87ee7fe0e608c06db5a23a096a6217c9878ede53c4e33710bdcaab51",
              "linux-aarch64/jattach"),
          new Artifact(
              "jattach-macos.zip",
              "02ac721f9af9025db99d77ecddcdef8e69a381afb2241564de6553af2663288b",
              "macos-universal/jattach"));

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @Inject
  protected abstract ArchiveOperations getArchives();

  @TaskAction
  public void prepare() throws Exception {
    Path output = getOutputDirectory().get().getAsFile().toPath().resolve("jattach");
    Files.createDirectories(output);
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(DOWNLOAD_TIMEOUT)
            .build()) {
      for (Artifact artifact : ARTIFACTS) {
        Path archive =
            download(client, RELEASE + artifact.archive(), artifact.archive(), artifact.hash());
        FileTree files;
        if (artifact.archive().endsWith(".tgz")) {
          Path tar = getTemporaryDir().toPath().resolve(artifact.archive() + ".tar");
          try (var input = new GZIPInputStream(Files.newInputStream(archive))) {
            Files.copy(input, tar, StandardCopyOption.REPLACE_EXISTING);
          }
          files = getArchives().tarTree(tar.toFile());
        } else {
          files = getArchives().zipTree(archive.toFile());
        }
        String executable = artifact.destination().endsWith(".exe") ? "jattach.exe" : "jattach";
        var binaries = files.matching(pattern -> pattern.include(executable)).getFiles();
        if (binaries.size() != 1) {
          throw new GradleException("Expected one " + executable + " in " + artifact.archive());
        }
        Path destination = output.resolve(artifact.destination());
        Files.createDirectories(destination.getParent());
        Files.copy(
            binaries.iterator().next().toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
      }
      Path license =
          download(
              client,
              "https://raw.githubusercontent.com/jattach/jattach/v2.2/LICENSE",
              "LICENSE",
              "b40930bbcf80744c86c46a12bc9da056641d722716c378f5659b9e555ef833e1");
      Files.copy(license, output.resolve("LICENSE"), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Path download(HttpClient client, String url, String name, String expectedHash)
      throws Exception {
    Path file = getTemporaryDir().toPath().resolve(name);
    if (Files.isRegularFile(file) && hash(file).equals(expectedHash)) return file;
    getLogger().lifecycle("Downloading pinned jattach 2.2 artifact {}", name);
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).build();
    HttpResponse<Path> response =
        client.send(
            request,
            HttpResponse.BodyHandlers.ofFile(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING));
    if (response.statusCode() != 200) {
      throw new IOException("Cannot download " + url + ": HTTP " + response.statusCode());
    }
    if (!hash(file).equals(expectedHash)) {
      Files.delete(file);
      throw new GradleException("SHA-256 mismatch for " + url);
    }
    return file;
  }

  private static String hash(Path file) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }

  private record Artifact(String archive, String hash, String destination) {}
}
