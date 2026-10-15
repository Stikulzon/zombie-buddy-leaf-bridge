package ua.zefir.gradle.workshop;

import java.io.File;

public record WorkshopMod(String id, String workshopId, File jar, String version) {}
