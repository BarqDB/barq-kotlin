# Barq Kotlin

[![License](https://img.shields.io/github/license/BarqDB/barq-kotlin)](./LICENSE)
![Status](https://img.shields.io/badge/status-alpha-f7c948)
![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7f52ff)

Barq Kotlin is the Kotlin Multiplatform SDK for BarqDB.

This repo contains the Kotlin Multiplatform SDK, Gradle plugin, compiler plugin, native interop layer, tests, and benchmarks.

## At A Glance

- Kotlin Multiplatform local database API
- Sync API backed by Barq sync
- Gradle and compiler plugins
- Native interop through barq-core

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

## Roadmap

- Stabilize published Maven artifacts
- Improve Android and KMP sample apps
- Keep sync tests easy to run locally
- Track barq-core releases closely
