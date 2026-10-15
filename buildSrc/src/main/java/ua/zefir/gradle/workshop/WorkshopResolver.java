package ua.zefir.gradle.workshop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.jspecify.annotations.NonNull;

public final class WorkshopResolver {
  private static final Pattern COORDINATE = Pattern.compile("(?<item>\\d+):(?<mod>[^:]+)");
  private static final Pattern VERSION_DIRECTORY = Pattern.compile("\\d+(?:\\.\\d+)*");
  private static final String DESCRIPTOR = "mod.info";
  private static final String JAVA_VERSION = "media/java/VERSION";

  public static WorkshopMod resolve(Project project, String notation, String expectedVersion) {
    Coordinate coordinate = Coordinate.parse(notation);
    String gameVersion =
        Objects.toString(project.getRootProject().findProperty("zomboid_version"), null);
    try {
      Candidate candidate = findCandidate(project, coordinate, gameVersion);
      File jar = findJar(coordinate, candidate);
      String version = findVersion(candidate);
      if (expectedVersion != null && !expectedVersion.equals(version)) {
        throw new GradleException(
            "Workshop dependency '"
                + notation
                + "' requires version '"
                + expectedVersion
                + "', but installed version is '"
                + (hasValue(version) ? version : "unknown")
                + "'.");
      }
      return new WorkshopMod(coordinate.modId(), coordinate.workshopId(), jar, version);
    } catch (IOException failure) {
      throw new GradleException("Failed to read Workshop dependency '" + notation + "'.", failure);
    }
  }

  private static Candidate findCandidate(Project project, Coordinate coordinate, String gameVersion)
      throws IOException {
    Map<File, Candidate> candidates = new LinkedHashMap<>();
    for (File root : SteamInstallation.workshopRoots(project)) {
      File item = new File(root, coordinate.workshopId());
      if (!item.isDirectory()) continue;
      List<File> modRoots = new ArrayList<>();
      modRoots.add(item);
      modRoots.addAll(directories(new File(item, "mods")));
      for (File directory : modRoots) {
        Candidate candidate = readCandidate(directory, gameVersion);
        if (!coordinate.modId().equals(candidate.metadata().get("id"))) continue;
        candidates.putIfAbsent(candidate.directory(), candidate);
      }
    }
    if (candidates.isEmpty()) {
      throw new GradleException(
          "Workshop dependency '"
              + coordinate
              + "' is not installed for Project Zomboid "
              + Objects.toString(gameVersion, "")
              + ". Subscribe/install item "
              + coordinate.workshopId()
              + " in Steam or configure steamDirectory/steamWorkshopDirectories.");
    }
    if (candidates.size() != 1) {
      throw new GradleException(
          "Workshop dependency '"
              + coordinate
              + "' has multiple installed mod directories: "
              + candidates.keySet());
    }
    return candidates.values().iterator().next();
  }

  private static Candidate readCandidate(File directory, String gameVersion) throws IOException {
    List<File> versions =
        directories(directory).stream()
            .filter(file -> VERSION_DIRECTORY.matcher(file.getName()).matches())
            .filter(
                file ->
                    gameVersion == null
                        || gameVersion.equals(file.getName())
                        || gameVersion.startsWith(file.getName() + "."))
            .sorted(
                Comparator.comparingInt((File file) -> file.getName().split("\\.").length)
                    .reversed())
            .toList();
    List<File> descriptors = new ArrayList<>();
    descriptors.add(new File(directory, DESCRIPTOR));
    descriptors.add(new File(directory, "common/" + DESCRIPTOR));
    versions.stream()
        .map(file -> new File(file, DESCRIPTOR))
        .filter(File::isFile)
        .findFirst()
        .ifPresent(descriptors::add);
    descriptors.removeIf(file -> !file.isFile());
    Map<String, String> metadata = new LinkedHashMap<>();
    for (File descriptor : descriptors) {
      metadata.putAll(readMetadata(descriptor));
    }
    return new Candidate(
        directory.getCanonicalFile(), List.copyOf(descriptors), versions, Map.copyOf(metadata));
  }

  private static File findJar(Coordinate coordinate, Candidate candidate) throws IOException {
    String jarPath = candidate.metadata().get("javaJarFile");
    if (!hasValue(jarPath)) {
      throw new GradleException(
          "Workshop dependency '"
              + coordinate
              + "' does not declare javaJarFile in "
              + candidate.descriptor()
              + ".");
    }
    List<File> directories = new ArrayList<>(candidate.versionDirectories());
    directories.add(candidate.descriptor().getParentFile());
    directories.add(candidate.directory());
    List<File> jars = new ArrayList<>();
    for (File directory : directories.stream().distinct().toList()) {
      jars.add(new File(directory, jarPath.replace('\\', '/')).getCanonicalFile());
    }
    File jar = jars.stream().filter(File::isFile).findFirst().orElse(jars.getFirst());
    if (!jar.toPath().startsWith(candidate.directory().toPath())) {
      throw new GradleException(
          "Workshop dependency '"
              + coordinate
              + "' declares a JAR outside its mod directory: "
              + jarPath);
    }
    if (!jar.isFile()) {
      throw new GradleException(
          "Workshop dependency '" + coordinate + "' declares a missing JAR: " + jar);
    }
    return jar;
  }

  private static String findVersion(Candidate candidate) throws IOException {
    String version = candidate.metadata().get("version");
    if (hasValue(version)) return version;
    version = candidate.metadata().get("modversion");
    if (hasValue(version)) return version;
    List<File> files =
        List.of(
            new File(candidate.descriptor().getParentFile(), JAVA_VERSION),
            new File(candidate.directory(), JAVA_VERSION),
            new File(candidate.directory(), "java/VERSION"),
            new File(candidate.directory(), "VERSION"));
    for (File file : files) {
      if (file.isFile()) return Files.readString(file.toPath()).trim();
    }
    return null;
  }

  private static List<File> directories(File directory) throws IOException {
    if (!directory.isDirectory()) return List.of();
    try (var paths = Files.list(directory.toPath())) {
      return paths.filter(Files::isDirectory).map(path -> path.toFile()).toList();
    }
  }

  private static Map<String, String> readMetadata(File file) throws IOException {
    Map<String, String> metadata = new LinkedHashMap<>();
    for (String line : Files.readAllLines(file.toPath())) {
      int separator = line.indexOf('=');
      if (separator <= 0) continue;
      metadata.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
    }
    return metadata;
  }

  private static boolean hasValue(String value) {
    return value != null && !value.isEmpty();
  }

  private record Coordinate(String workshopId, String modId) {
    private static Coordinate parse(String notation) {
      var matcher = COORDINATE.matcher(Objects.toString(notation, ""));
      if (!matcher.matches() || matcher.group("mod").isBlank()) {
        throw new GradleException(
            "Invalid Workshop dependency '"
                + notation
                + "'; expected '<workshop-item-id>:<mod.info-id>'.");
      }
      return new Coordinate(matcher.group("item"), matcher.group("mod"));
    }

    @Override
    public @NonNull String toString() {
      return workshopId + ":" + modId;
    }
  }

  private record Candidate(
      File directory,
      List<File> descriptors,
      List<File> versionDirectories,
      Map<String, String> metadata) {
    private File descriptor() {
      return descriptors.getLast();
    }
  }
}
