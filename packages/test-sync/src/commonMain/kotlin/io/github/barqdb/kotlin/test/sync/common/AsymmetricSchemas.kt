package io.github.barqdb.kotlin.test.sync.common

import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.AsymmetricBarqObject
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.annotations.PersistedName
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId

class DeviceParent : BarqObject {
    @PersistedName("_id")
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var device: Device? = null
}

class Measurement : AsymmetricBarqObject {
    @PersistedName("_id")
    @PrimaryKey
    var id: ObjectId = ObjectId()
    var type: String = "temperature"
    var value: Float = 0.0f
    var device: Device? = null
    var backups: BarqList<BackupDevice> = barqListOf()
}

class BackupDevice() : EmbeddedBarqObject {
    constructor(name: String, serialNumber: String) : this() {
        this.name = name
        this.serialNumber = serialNumber
    }
    var name: String = ""
    var serialNumber: String = ""
}

class Device() : EmbeddedBarqObject {
    constructor(name: String, serialNumber: String) : this() {
        this.name = name
        this.serialNumber = serialNumber
    }
    var name: String = ""
    var serialNumber: String = ""
    var backupDevice: BackupDevice? = null
}

class AsymmetricA : AsymmetricBarqObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var child: EmbeddedB? = null
}

class EmbeddedB : EmbeddedBarqObject {
    var child: StandardC? = null
}

class StandardC : BarqObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
}
