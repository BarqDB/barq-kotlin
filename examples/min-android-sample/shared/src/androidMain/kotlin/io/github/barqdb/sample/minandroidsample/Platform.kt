package io.github.barqdb.sample.minandroidsample

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.types.BarqObject

class Sample: BarqObject {
    var name: String = ""
}

actual class Platform actual constructor() {
    val config = BarqConfiguration.create(schema = setOf(Sample::class))
    val barq = Barq.open(config)
    actual val platform: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
