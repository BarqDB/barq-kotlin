package io.github.barqdb.kotlin.test.common.utils

import io.github.barqdb.kotlin.MutableBarq
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1

/**
 * Dataset container and helper operations. Given a [property] this manager returns the appropriate
 * [dataSetToLoad] for exhaustive type testing.
 *
 * TODO could also be used for BarqLists - https://github.com/BarqDB/barq-kotlin/issues/941
 */
internal interface GenericTypeSafetyManager<Type, Container, BarqCollection> {

    /**
     * Property from the [Container] class containing a [BarqCollection] attribute.
     */
    val property: KMutableProperty1<Container, BarqCollection>

    /**
     * Dataset used to test the validity of the [BarqCollection] operations.
     *
     * See 'BarqListTests' for values used here.
     */
    val dataSetToLoad: List<Type>

    override fun toString(): String // Default implementation not allowed as it comes from "Any"

    /**
     * Creates a managed [Container] from which we can access the [property] pointing to an empty,
     * managed [BarqCollection].
     */
    fun createContainerAndGetCollection(barq: MutableBarq): BarqCollection

    /**
     * Creates a managed [Container] whose [property] contains a pre-populated [BarqCollection].
     */
    fun createPrePopulatedContainer(): Container

    /**
     * Convenience function that retrieves the given [property] for the provided [container].
     */
    fun getCollection(container: Container): BarqCollection
}

/**
 * Provides an execution block collection exhaustive tests. In case the test fails due to an
 * assertion error, it will show enough information to infer the type being tested to easily
 * identify the problem.
 */
internal interface ErrorCatcher {

    /**
     * The actual type to be tested by the error catcher.
     */
    val classifier: KClassifier

    /**
     * This method acts as an assertion error catcher in case one of the classifiers we use for
     * testing fails, ensuring the error message can easily be identified in the log.
     *
     * Assertions should be wrapped around this function, e.g.:
     * ```
     * override fun specificTest() {
     *     errorCatcher {
     *         // Write your test logic here
     *     }
     * }
     * ```
     *
     * @param block lambda with the actual test logic to be run
     */
    fun errorCatcher(block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("'${toString()}' failed - ${e.message}", e)
        }
    }
}
