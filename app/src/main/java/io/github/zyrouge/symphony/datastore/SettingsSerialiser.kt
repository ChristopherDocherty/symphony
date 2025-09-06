package io.github.zyrouge.symphony.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import io.github.zyrouge.symphony.Settings
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Settings =
        try { Settings.parseFrom(input) }
        catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", e)
        }

    override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}
