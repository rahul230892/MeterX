package com.meterx.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalBackup(private val context: Context) {
    suspend fun write(meters: List<MeterWithReadings>) = withContext(Dispatchers.IO) {
        val meterArray = JSONArray()
        meters.forEach { item ->
            val readings = JSONArray()
            item.sortedReadings.forEach { reading ->
                readings.put(
                    JSONObject()
                        .put("id", reading.id)
                        .put("value", reading.value)
                        .put("date", reading.readingDate)
                        .put("isBilled", reading.isBilled),
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
                    .put("readings", readings),
            )
        }

        val backup = JSONObject()
            .put("version", 1)
            .put("syncedAt", System.currentTimeMillis())
            .put("meters", meterArray)

        val target = context.filesDir.resolve("meterx_backup.json")
        val temporary = context.filesDir.resolve("meterx_backup.tmp")
        temporary.writeText(backup.toString(2))
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }
}
