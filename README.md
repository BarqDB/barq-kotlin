# Barq Kotlin

Barq Kotlin is the Kotlin client for Barq.

This repo contains the Kotlin Multiplatform SDK, Gradle plugin, compiler plugin, native interop layer, tests, and benchmarks.

## Modules

The main Gradle build lives in `packages`.

- `library-base`: local database API
- `library-sync`: Barq sync API
- `cinterop`: native bindings
- `plugin-compiler`: model compiler plugin
- `gradle-plugin`: Gradle plugin
- `test-base` and `test-sync`: test support

## Build

From `packages`:

```sh
./gradlew :library-base:compileKotlinJvm
./gradlew :library-sync:compileKotlinJvm
```

## Sync Tests

Sync tests use a Barq sync server.

The default test URL is:

```text
http://localhost:9090
```

Set `syncTestUrl` in `packages/gradle.properties` or pass it on the Gradle command line.
