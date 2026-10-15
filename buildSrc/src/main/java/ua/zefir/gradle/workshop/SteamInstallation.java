package ua.zefir.gradle.workshop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

public final class SteamInstallation {
  private static final String APP_ID = "108600";
  private static final String CLIENT_GAME_PATH = "LEAF_CLIENT_GAME_PATH";
  private static final String SERVER_GAME_PATH = "LEAF_SERVER_GAME_PATH";

  public static List<File> workshopRoots(Project project) {
    try {
      String configured = property(project, "steamWorkshopDirectories");
      if (hasValue(configured)) {
        Set<File> roots = new LinkedHashSet<>();
        for (String path : configured.split(Pattern.quote(File.pathSeparator))) {
          roots.add(
              requireDirectory(
                  project.file(path), "steamWorkshopDirectories contains a missing directory: "));
        }
        return List.copyOf(roots);
      }
      return libraries(project).stream()
          .map(library -> new File(library, "steamapps/workshop/content/" + APP_ID))
          .filter(File::isDirectory)
          .toList();
    } catch (IOException failure) {
      throw new GradleException("Failed to discover Steam Workshop directories.", failure);
    }
  }

  private static List<File> libraries(Project project) throws IOException {
    Set<File> roots = new LinkedHashSet<>();
    String configured = property(project, "steamDirectory");
    if (hasValue(configured)) {
      roots.add(
          requireDirectory(
              project.file(configured), "steamDirectory points to a missing directory: "));
    } else {
      File gameRoot = gameRoot(project);
      if (gameRoot != null) roots.add(gameRoot);
      roots.addAll(defaultRoots(project));
    }
    Set<File> libraries = new LinkedHashSet<>();
    for (File root : roots) {
      if (!root.isDirectory()) continue;
      libraries.add(root.getCanonicalFile());
      for (String path : values(new File(root, "steamapps/libraryfolders.vdf"), "path")) {
        libraries.add(new File(path).getCanonicalFile());
      }
    }
    return libraries.stream().filter(File::isDirectory).toList();
  }

  private static File gameRoot(Project project) throws IOException {
    String path = project.getProviders().environmentVariable(CLIENT_GAME_PATH).getOrNull();
    if (!hasValue(path)) {
      path = project.getProviders().environmentVariable(SERVER_GAME_PATH).getOrNull();
    }
    if (!hasValue(path)) return null;
    File directory = project.file(path).getCanonicalFile();
    while (directory != null && !new File(directory, "steamapps").isDirectory()) {
      directory = directory.getParentFile();
    }
    return directory;
  }

  private static List<File> defaultRoots(Project project) {
    String os = project.getProviders().systemProperty("os.name").get().toLowerCase(Locale.ROOT);
    File home = new File(project.getProviders().systemProperty("user.home").get());
    if (os.contains("mac")) return List.of(new File(home, "Library/Application Support/Steam"));
    if (!os.contains("win")) {
      return List.of(new File(home, ".steam/steam"), new File(home, ".local/share/Steam"));
    }
    List<File> roots = new ArrayList<>();
    for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles")) {
      String path = project.getProviders().environmentVariable(variable).getOrNull();
      if (hasValue(path)) roots.add(new File(path, "Steam"));
    }
    String registryPath =
        project.getProviders().of(SteamRegistryPath.class, spec -> {}).getOrNull();
    if (registryPath != null) roots.add(new File(registryPath));
    return roots;
  }

  private static List<String> values(File file, String key) throws IOException {
    if (!file.isFile()) return List.of();
    var pattern =
        Pattern.compile("(?m)^\\s*\"" + Pattern.quote(key) + "\"\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    return pattern
        .matcher(Files.readString(file.toPath()))
        .results()
        .map(match -> match.group(1).replace("\\\"", "\"").replace("\\\\", "\\"))
        .toList();
  }

  private static File requireDirectory(File file, String message) throws IOException {
    File directory = file.getCanonicalFile();
    if (!directory.isDirectory()) throw new GradleException(message + directory);
    return directory;
  }

  private static String property(Project project, String name) {
    return Objects.toString(project.findProperty(name), null);
  }

  private static boolean hasValue(String value) {
    return value != null && !value.isEmpty();
  }
}
