package com.aegispay.android.network

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.math.BigDecimal

/**
 * Moshi adapter for [BigDecimal].
 *
 * The backend serialises monetary amounts as JSON numbers (e.g. 1234.56).
 * Moshi has no built-in BigDecimal support — without this adapter amounts are
 * deserialised as Double, which loses precision for values like 1234.455.
 * Registered in [com.aegispay.android.di.NetworkModule].
 */
class BigDecimalAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): BigDecimal {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> BigDecimal(reader.nextString())
            JsonReader.Token.STRING -> BigDecimal(reader.nextString())
            JsonReader.Token.NULL   -> { reader.nextNull<Unit>(); BigDecimal.ZERO }
            else -> throw JsonDataException(
                "Expected NUMBER but was ${reader.peek()} at path ${reader.path}"
            )
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: BigDecimal) {
        writer.value(value)
    }
}
