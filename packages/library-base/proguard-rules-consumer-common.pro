## Keep Companion classes and class.Companion member of all classes that can be used in our API to
#  allow calling barqObjectCompanionOrThrow and barqObjectCompanionOrNull on the classes
-keep class io.github.barqdb.kotlin.types.BarqInstant$Companion
-keepclassmembers class io.github.barqdb.kotlin.types.BarqInstant {
    io.github.barqdb.kotlin.types.BarqInstant$Companion Companion;
}
-keep class io.github.barqdb.kotlin.bson.BsonObjectId$Companion
-keepclassmembers class io.github.barqdb.kotlin.bson.BsonObjectId {
    io.github.barqdb.kotlin.bson.BsonObjectId$Companion Companion;
}
-keep class io.github.barqdb.kotlin.dynamic.DynamicBarqObject$Companion, io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject$Companion
-keepclassmembers class io.github.barqdb.kotlin.dynamic.DynamicBarqObject, io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject {
    **$Companion Companion;
}
-keep,allowobfuscation class ** implements io.github.barqdb.kotlin.types.BaseBarqObject
-keep class ** implements io.github.barqdb.kotlin.internal.BarqObjectCompanion
-keepclassmembers class ** implements io.github.barqdb.kotlin.types.BaseBarqObject {
    **$Companion Companion;
}

## Preserve all native method names and the names of their classes.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

## Preserve all classes that are looked up from native code
# Notification callback
-keep class io.github.barqdb.kotlin.internal.interop.NotificationCallback {
    *;
}
# Utils to convert core errors into Kotlin exceptions
-keep class io.github.barqdb.kotlin.internal.interop.CoreErrorConverter {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.JVMScheduler {
    *;
}
# Interop, sync-specific classes
-keep class io.github.barqdb.kotlin.internal.interop.sync.NetworkTransport {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.Response {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.LongPointerWrapper {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.AppError {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.CoreConnectionState {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.SyncError {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.LogCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.SyncErrorCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.JVMSyncSessionTransferCompletionCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.ResponseCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.ResponseCallbackImpl {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.AppCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.CompactOnLaunchCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.MigrationCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.DataInitializationCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.SubscriptionSetCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.SyncBeforeClientResetHandler {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.SyncAfterClientResetHandler {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.AsyncOpenCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.NativePointer {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.ProgressCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.ApiKeyWrapper {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.ConnectionStateChangeCallback {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.SyncThreadObserver {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.CoreCompensatingWriteInfo {
    *;
}
# Preserve Function<X> methods as they back various functional interfaces called from JNI
-keep class kotlin.jvm.functions.Function* {
    *;
}
-keep class kotlin.Unit {
    *;
}

# Platform networking callback
-keep class io.github.barqdb.kotlin.internal.interop.sync.WebSocketTransport {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.CancellableTimer {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.WebSocketClient {
    *;
}
-keep class io.github.barqdb.kotlin.internal.interop.sync.WebSocketObserver {
    *;
}

# Un-comment for debugging
#-printconfiguration /tmp/full-r8-config.txt
#-keepattributes LineNumberTable,SourceFile
#-printusage /tmp/removed_entries.txt
#-printseeds /tmp/kept_entries.txt
