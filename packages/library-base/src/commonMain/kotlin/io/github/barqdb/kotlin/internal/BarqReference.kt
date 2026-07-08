package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.internal.interop.FrozenBarqPointer
import io.github.barqdb.kotlin.internal.interop.LiveBarqPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqPointer
import io.github.barqdb.kotlin.internal.schema.CachedSchemaMetadata
import io.github.barqdb.kotlin.internal.schema.SchemaMetadata
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * A _Barq Reference_ that links a specific Kotlin BaseBarq instance with an underlying C++
 * SharedBarq.
 *
 * This is needed as each Results, List or Object need to know, both which public Barq they belong
 * to, but also what underlying SharedBarq they are part of. Each object linked to a Barq needs
 * to keep it's own C++ SharedInstance, as the owning [Barq]'s C++ SharedBarq instance is updated
 * on writes/notifications.
 *
 * For frozen Barqs, the `dbPointer` will point to a specific version of a read transaction that
 * is guaranteed to not change.
 *
 * For live Barqs, the `dbPointer` will point to a live SharedBarq that can advance its internal
 * version.
 *
 * NOTE: There should never be multiple BarqReferences with the same `dbPointer` as the underlying
 * C++ SharedBarq is closed when the BarqReference is no longer referenced by the [Barq].
 */
// TODO Public due to being a transitive dependency to Notifiable
public interface BarqReference : BarqState {
    public val owner: BaseBarqImpl
    public val schemaMetadata: SchemaMetadata
    public val dbPointer: BarqPointer

    override fun version(): VersionId {
        checkClosed()
        return VersionId(BarqInterop.barq_get_version_id(dbPointer))
    }
    public fun uncheckedVersion(): VersionId =
        VersionId(BarqInterop.barq_get_version_id(dbPointer))

    override fun isFrozen(): Boolean {
        checkClosed()
        return BarqInterop.barq_is_frozen(dbPointer)
    }

    override fun isClosed(): Boolean {
        return BarqInterop.barq_is_closed(dbPointer)
    }

    public fun close() {
        checkClosed()
        BarqInterop.barq_close(dbPointer)
    }

    public fun asValidLiveBarqReference(): LiveBarqReference {
        if (this !is LiveBarqReference) {
            throw IllegalStateException("Cannot modify managed objects outside of a write transaction")
        }
        checkClosed()
        return this
    }

    public fun checkClosed() {
        if (isClosed()) {
            throw IllegalStateException("Barq has been closed and is no longer accessible: ${owner.configuration.path}")
        }
    }
}

public interface FrozenBarqReference : BarqReference {
    override val dbPointer: FrozenBarqPointer
}

public data class FrozenBarqReferenceImpl(
    override val owner: BaseBarqImpl,
    override val dbPointer: FrozenBarqPointer,
    override val schemaMetadata: SchemaMetadata = CachedSchemaMetadata(
        dbPointer,
        owner.configuration.mapOfKClassWithCompanion.values
    ),
) : FrozenBarqReference {
    init {
        // barq_open/barq_freeze doesn't implicitly create a transaction which can cause the
        // underlying core version to be cleaned up if the barq is advanced before any objects,
        // queries, etc. triggers creation of the transaction. Thus, we need to force a transaction
        // on any barq references to keep the version around for future operations.
        BarqInterop.barq_begin_read(dbPointer)
    }
}

/**
 * A **live barq reference** linking to the underlying live SharedBarq with the option to update
 * schema metadata when the schema has changed.
 */
public data class LiveBarqReference(
    override val owner: BaseBarqImpl,
    override val dbPointer: LiveBarqPointer
) : BarqReference {

    override val schemaMetadata: SchemaMetadata
        get() = _schemaMetadata.value

    private val _schemaMetadata: AtomicRef<SchemaMetadata> =
        atomic(CachedSchemaMetadata(dbPointer, owner.configuration.mapOfKClassWithCompanion.values))

    /**
     * Returns a frozen barq reference of the current live barq reference.
     */
    public fun snapshot(owner: BaseBarqImpl): FrozenBarqReference {
        return FrozenBarqReferenceImpl(owner, BarqInterop.barq_freeze(dbPointer), schemaMetadata)
    }

    /**
     * Refreshes the barq reference's cached schema meta data from the current live barq reference.
     *
     * This means that any existing live barq objects will get an updated schema. This should be
     * safe as we don't expect live objects to leave the scope of the write block of [Barq.write].
     */
    public fun refreshSchemaMetadata() {
        _schemaMetadata.value = CachedSchemaMetadata(dbPointer, owner.configuration.mapOfKClassWithCompanion.values)
    }
}
