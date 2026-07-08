package io.github.barqdb.kotlin.test.platform

import okio.FileSystem

actual val platformFileSystem: FileSystem = FileSystem.SYSTEM
