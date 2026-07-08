# We cannot discard unused symbols for the non-test configurations as it might  all symbols not used
-dontoptimize
-dontshrink

## Required to make assertions on incorrect type messages in dynamic barq object tests pass
-keep class io.github.barqdb.kotlin.types.BaseBarqObject
-keep class io.github.barqdb.kotlin.types.BarqUUID

## Required to make introspection by reflection in NullabilityTests work
-keep class io.github.barqdb.kotlin.types.MutableBarqInt
-keep class io.github.barqdb.kotlin.entities.Nullability {
   *;
}

## Required to make introspection by reflection in PrimaryKeyTests work
-keepclassmembers class io.github.barqdb.kotlin.entities.primarykey.* {
  *;
}
