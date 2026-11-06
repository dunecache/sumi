package com.sumi.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/** Testable dispatcher holder. Inject this; never hard-code Dispatchers in logic under test. */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)

@Singleton
class DispatchersProvider @Inject constructor() {
    fun get(): AppDispatchers = AppDispatchers(
        io = Dispatchers.IO,
        main = Dispatchers.Main,
        default = Dispatchers.Default,
    )
}
