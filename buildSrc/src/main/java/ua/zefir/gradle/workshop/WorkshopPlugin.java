package ua.zefir.gradle.workshop;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class WorkshopPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getExtensions().create("workshop", WorkshopExtension.class, project);
  }
}
