package io.github.barqdb.kotlin.internal.query

import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.BarqResultsImpl
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqResultsPointer
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlin.reflect.KClass

internal fun <T : BaseBarqObject> thawResults(
    liveBarq: BarqReference,
    resultsPointer: BarqResultsPointer,
    classKey: ClassKey,
    clazz: KClass<T>,
    mediator: Mediator
): BarqResultsImpl<T> {
    val liveResultPtr = BarqInterop.barq_results_resolve_in(resultsPointer, liveBarq.dbPointer)
    return BarqResultsImpl(liveBarq, liveResultPtr, classKey, clazz, mediator)
}
