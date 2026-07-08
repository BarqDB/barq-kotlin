package io.github.barqdb.kotlin.internal.platform

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
public actual typealias WeakReference<T> = kotlin.native.ref.WeakReference<T>
