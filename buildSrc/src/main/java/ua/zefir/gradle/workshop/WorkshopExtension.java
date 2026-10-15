package ua.zefir.gradle.workshop;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

public class WorkshopExtension {
  private final Project project;
  private final Map<Dependency, WorkshopMod> declarations = new LinkedHashMap<>();

  @Inject
  public WorkshopExtension(Project project) {
    this.project = project;
  }

  public Dependency dependency(String notation) {
    return dependency(notation, null);
  }

  public Dependency dependency(String notation, String expectedVersion) {
    WorkshopMod mod = WorkshopResolver.resolve(project, notation, expectedVersion);
    Dependency dependency = project.getDependencies().create(project.files(mod.jar()));
    declarations.put(dependency, mod);
    return dependency;
  }

  public WorkshopMod findMod(Dependency dependency) {
    return declarations.get(dependency);
  }
}
