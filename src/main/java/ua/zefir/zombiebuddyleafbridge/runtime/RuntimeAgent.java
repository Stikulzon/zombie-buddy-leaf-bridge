package ua.zefir.zombiebuddyleafbridge.runtime;

import java.lang.instrument.Instrumentation;

public final class RuntimeAgent {
  private static volatile Instrumentation instrumentation;

  public static void agentmain(String arguments, Instrumentation value) {
    instrumentation = value;
  }

  public static Instrumentation instrumentation() {
    return instrumentation;
  }
}
