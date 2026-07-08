## We cannot discard unused symbols for the non-test configurations as it might  all symbols not used
-dontoptimize
-dontshrink

# io.github.barqdb.kotlin.test.sync.shared.FlexibleSyncConfigurationTests.toString_nonEmpty,
# SyncConfigTests.unsupportedSchemaTypesThrowException_flexibleSync and
# SyncConfigTests.unsupportedSchemaTypesThrowException_partitionBasedSync verifies exception
# messages with explicit class names in them
-keep class io.github.barqdb.kotlin.sync.internal.SyncConfigurationImpl
-keep class io.github.barqdb.kotlin.dynamic.DynamicBarqObject

## Serialization related rules
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# If a companion has the serializer function, keep the companion field on the original type so that
# the reflective lookup succeeds.
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class <1>.<2> {
  <1>.<2>$Companion Companion;
}

## Required to make asserted messages in FunctionTests and BsonEncoder work
-keep class io.github.barqdb.kotlin.types.MutableBarqInt
-keep class io.github.barqdb.kotlin.types.BarqUUID
-keep class io.github.barqdb.kotlin.types.BarqList
-keep class io.github.barqdb.kotlin.bson.* {
    *;
}

-keep class io.github.barqdb.kotlin.bson.serialization.* {
    *;
}

-dontwarn androidx.annotation.experimental.Experimental$Level
-dontwarn androidx.annotation.experimental.Experimental
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
