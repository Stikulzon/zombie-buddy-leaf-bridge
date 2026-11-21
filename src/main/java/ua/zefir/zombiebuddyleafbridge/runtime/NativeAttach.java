package ua.zefir.zombiebuddyleafbridge.runtime;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class NativeAttach {
  private static final String LIBRARY_PATH_PROPERTY = "sun.boot.library.path";
  private static final String VM_DIRECTORY = "server";
  private static final String JVM_LIBRARY = "jvm";
  private static final String LAUNCHER_LIBRARY = "jli";
  private static final String INSTRUMENT_LIBRARY = "instrument";
  private static final String CREATED_VMS_SYMBOL = "JNI_GetCreatedJavaVMs";
  private static final String ON_ATTACH_SYMBOL = "Agent_OnAttach";
  private static final int JNI_OK = 0;
  private static final int MAX_VIRTUAL_MACHINES = 1;
  private static final FunctionDescriptor CREATED_VMS =
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS);
  private static final FunctionDescriptor ON_ATTACH =
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);

  static void attach(Path agent) throws IOException {
    Path libraries =
        Path.of(System.getProperty(LIBRARY_PATH_PROPERTY).split(File.pathSeparator)[0]);
    Arena global = Arena.global();
    SymbolLookup jvm = load(libraries.resolve(VM_DIRECTORY), JVM_LIBRARY, global);
    for (String dependency : dependencies(System.getProperty("os.name"))) {
      load(libraries, dependency, global);
    }
    SymbolLookup instrument = load(libraries, INSTRUMENT_LIBRARY, global);
    Linker linker = Linker.nativeLinker();
    MethodHandle createdVms = linker.downcallHandle(find(jvm, CREATED_VMS_SYMBOL), CREATED_VMS);
    MethodHandle onAttach = linker.downcallHandle(find(instrument, ON_ATTACH_SYMBOL), ON_ATTACH);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment vms = arena.allocate(ADDRESS, MAX_VIRTUAL_MACHINES);
      MemorySegment count = arena.allocate(JAVA_INT);
      int status = (int) createdVms.invokeExact(vms, MAX_VIRTUAL_MACHINES, count);
      if (status != JNI_OK || count.get(JAVA_INT, 0) < MAX_VIRTUAL_MACHINES) {
        throw new IllegalStateException(
            "Cannot locate the running JVM (JNI status " + status + ")");
      }
      MemorySegment options = arena.allocateFrom(agent.toAbsolutePath().toString());
      int result = (int) onAttach.invokeExact(vms.get(ADDRESS, 0), options, MemorySegment.NULL);
      if (result != JNI_OK) {
        throw new IllegalStateException(
            "The JDK instrument library rejected the runtime agent (status "
                + result
                + "). See the game console for details.");
      }
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException("Cannot attach the runtime agent", failure);
    }
  }

  static List<String> dependencies(String osName) {
    if (osName.toLowerCase(Locale.ROOT).contains("windows")) return List.of(LAUNCHER_LIBRARY);
    return List.of();
  }

  private static SymbolLookup load(Path directory, String name, Arena arena) throws IOException {
    Path library = directory.resolve(System.mapLibraryName(name));
    if (!Files.isRegularFile(library)) {
      throw new FileNotFoundException("Missing JDK library: " + library);
    }
    return SymbolLookup.libraryLookup(library, arena);
  }

  private static MemorySegment find(SymbolLookup lookup, String symbol) {
    return lookup
        .find(symbol)
        .orElseThrow(() -> new IllegalStateException("Missing JDK symbol: " + symbol));
  }
}
