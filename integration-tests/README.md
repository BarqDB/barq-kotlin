# Integration test projects

This folder holds the various integration test projects.

- `gradle/` - Various smoke test project that verifies that our top level Gradle plugin can be
  applied on a both single and a multi platform modules. It is currently testing:
  - `single-platform` - Android single module project
  - `multi-platform` - Kotlin Multiplatform project with JVM and Native targets running on the host
     platform.
  There are various projects with specific Gradle versions that have been troublesome for plugin
  compatibility and a `current` project that will use the versions used to build the SDK.
