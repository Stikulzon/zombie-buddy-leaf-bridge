package ua.zefir.zombiebuddyleafbridge;

import dev.aoqia.leaf.loader.api.entrypoint.PreLaunchEntrypoint;

public final class BuddyLeafBridge implements PreLaunchEntrypoint {
  @Override
  public void onPreLaunch() {
    try {
      GameRuntime.initialize();
    } catch (Exception | LinkageError failure) {
      throw new IllegalStateException("Cannot initialize ZombieBuddy inside Leaf", failure);
    }
  }
}
