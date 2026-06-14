package com.meterx.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ImportPreview(
    val uri: Uri,
    val meterCount: Int,
    val readingCount: Int,
)

data class ImportedData(
    val meters: List<MeterEntity>,
    val readings: List<ReadingEntity>,
)

class LocalBackup(private val context: Context) {
    suspend fun write(meters: List<MeterWithReadings>) = withContext(Dispatchers.IO) {
        val target = context.filesDir.resolve("meterx_backup.json")
        val temporary = context.filesDir.resolve("meterx_backup.tmp")
        temporary.writeText(encode(meters))
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }

    suspend fun export(uri: Uri, meters: List<MeterWithReadings>) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use {
                it.write(encode(meters))
            } ?: error("Unable to open the selected file.")
        }

    suspend fun previewImport(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val data = decode(readText(uri))
        ImportPreview(uri, data.meters.size, data.readings.size)
    }

    suspend fun readImport(uri: Uri): ImportedData = withContext(Dispatchers.IO) {
        decode(readText(uri))
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to open the selected file.")

    private fun encode(meters: List<MeterWithReadings>): String {
        val meterArray = JSONArray()
        meters.forEach { item ->
            val readings = JSONArray()
            item.sortedReadings.forEach { reading ->
                readings.put(
                    JSONObject()
                        .put("id", reading.id)
                        .put("value", reading.value)
                        .put("date", reading.readingDate)
                        .put("isBilled", reading.isBilled)
                        .put("createdAt", reading.createdAt),
                )
            }
            meterArray.put(
                JSONObject()
                    .put("id", item.meter.id)
                    .put("nickname", item.meter.nickname)
                    .put("type", item.meter.type.name)
                    .put("meterNumber", item.meter.meterNumber)
                    .put("consumerNumber", item.meter.consumerNumber ?: JSONObject.NULL)
                    .put("freeUnits", item.meter.freeUnits ?: JSONObject.NULL)
                    .put("cycleBaseline", item.meter.cycleBaseline ?: JSONObject.NULL)
                    .put("createdAt", item.meter.createdAt)
                    .put("readings", readings),
            )
        }

        return JSONObject()
            .put("format", "meterx-backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("meters", meterArray)
            .toString(2)
    }

    private fun decode(text: String): ImportedData {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            error("This is not a valid MeterX data file.")
        }
        require(root.optInt("version", -1) == 1) {
            "This MeterX data file version is not supported."
        }
        val meterArray = root.optJSONArray("meters")
            ?: error("The data file does not contain meters.")
        val meters = mutableListOf<MeterEntity>()
        val readings = mutableListOf<ReadingEntity>()
        val meterIds = mutableSetOf<Long>()
        val readingIds = mutableSetOf<Long>()

        repeat(meterArray.length()) { meterIndex ->
            val json = meterArray.optJSONObject(meterIndex)
                ?: error("A meter record is invalid.")
            val id = json.optLong("id", 0)
            val nickname = json.optString("nickname").trim()
            val meterNumber = json.optString("meterNumber").trim()
            require(id > 0 && meterIds.add(id) && nickname.isNotEmpty() && meterNumber.isNotEmpty()) {
                "A meter record is incomplete or duplicated."
            }
            val type = try {
                MeterType.valueOf(json.getString("type"))
            } catch (_: Exception) {
                error("A meter has an unsupported type.")
            }
            val freeUnits = json.nullableDouble("freeUnits")
            require(type != MeterType.ELECTRICITY || (freeUnits != null && freeUnits > 0)) {
                "An electricity meter has invalid free units."
            }
            meters += MeterEntity(
                id = id,
                nickname = nickname,
                type = type,
                meterNumber = meterNumber,
                consumerNumber = json.nullableString("consumerNumber"),
                freeUnits = freeUnits.takeIf { type == MeterType.ELECTRICITY },
                cycleBaseline = json.nullableDouble("cycleBaseline"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )

            val readingArray = json.optJSONArray("readings") ?: JSONArray()
            repeat(readingArray.length()) { readingIndex ->
                val reading = readingArray.optJSONObject(readingIndex)
                    ?: error("A reading record is invalid.")
                val readingId = reading.optLong("id", 0)
                val value = reading.optDouble("value", Double.NaN)
                require(readingId > 0 && readingIds.add(readingId) && value.isFinite() && value >= 0) {
                    "A reading record is incomplete or duplicated."
                }
                readings += ReadingEntity(
                    id = readingId,
                    meterId = id,
                    value = value,
                    readingDate = reading.getLong("date"),
                    isBilled = reading.optBoolean("isBilled", false),
                    createdAt = reading.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }
        return ImportedData(meters, readings)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key, Double.NaN).takeIf(Double::isFinite)
}
