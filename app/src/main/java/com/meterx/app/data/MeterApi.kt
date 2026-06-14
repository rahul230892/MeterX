package com.meterx.app.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuthResult(
    val token: String,
    val user: AuthUser,
)

data class CloudSnapshot(
    val meters: List<CloudMeter>,
    val readings: List<CloudReading>,
)

data class CloudMeter(
    val clientId: String,
    val nickname: String,
    val type: MeterType,
    val meterNumber: String,
    val consumerNumber: String?,
    val freeUnits: Double?,
    val cycleBaseline: Double?,
    val createdAt: Long,
)

data class CloudReading(
    val clientId: String,
    val meterClientId: String,
    val value: Double,
    val readingDate: Long,
    val isBilled: Boolean,
    val createdAt: Long,
)

class MeterApi(baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')

    suspend fun register(username: String, password: String): AuthResult =
        authenticate("/api/auth/register", username, password)

    suspend fun login(username: String, password: String): AuthResult =
        authenticate("/api/auth/login", username, password)

    suspend fun downloadSnapshot(token: String): CloudSnapshot = withContext(Dispatchers.IO) {
        val response = request("GET", "/api/sync", token)
        val meters = response.getJSONArray("meters").toCloudMeters()
        val readings = response.getJSONArray("readings").toCloudReadings()
        CloudSnapshot(meters, readings)
    }

    suspend fun uploadSnapshot(token: String, snapshot: List<MeterWithReadings>) {
        withContext(Dispatchers.IO) {
            val meters = JSONArray()
            val readings = JSONArray()
            snapshot.forEach { item ->
                meters.put(
                    JSONObject()
                        .put("clientId", item.meter.id.toString())
                        .put("nickname", item.meter.nickname)
                        .put("type", item.meter.type.name)
                        .put("meterNumber", item.meter.meterNumber)
                        .putNullable("consumerNumber", item.meter.consumerNumber)
                        .putNullable("freeUnits", item.meter.freeUnits)
                        .putNullable("cycleBaseline", item.meter.cycleBaseline)
                        .put("createdAt", item.meter.createdAt),
                )
                item.readings.forEach { reading ->
                    readings.put(
                        JSONObject()
                            .put("clientId", reading.id.toString())
                            .put("meterClientId", item.meter.id.toString())
                            .put("value", reading.value)
                            .put("readingDate", reading.readingDate)
                            .put("isBilled", reading.isBilled)
                            .put("createdAt", reading.createdAt),
                    )
                }
            }
            request(
                method = "PUT",
                path = "/api/sync",
                token = token,
                body = JSONObject().put("meters", meters).put("readings", readings),
            )
        }
    }

    private suspend fun authenticate(
        path: String,
        username: String,
        password: String,
    ): AuthResult = withContext(Dispatchers.IO) {
        val response = request(
            "POST",
            path,
            body = JSONObject().put("username", username).put("password", password),
        )
        val user = response.getJSONObject("user")
        AuthResult(
            token = response.getString("token"),
            user = AuthUser(user.getString("id"), user.getString("username")),
        )
    }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (connection.responseCode !in 200..299) {
                throw ApiException(json.optString("error", "Request failed (${connection.responseCode})."))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

class ApiException(message: String) : Exception(message)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONArray.toCloudMeters(): List<CloudMeter> = buildList {
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        add(
            CloudMeter(
                clientId = item.getString("clientId"),
                nickname = item.getString("nickname"),
                type = MeterType.valueOf(item.getString("type")),
                meterNumber = item.getString("meterNumber"),
                consumerNumber = item.optNullableString("consumerNumber"),
                freeUnits = item.optNullableDouble("freeUnits"),
                cycleBaseline = item.optNullableDouble("cycleBaseline"),
                createdAt = item.getLong("createdAt"),
            ),
        )
    }
}

private fun JSONArray.toCloudReadings(): List<CloudReading> = buildList {
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        add(
            CloudReading(
                clientId = item.getString("clientId"),
                meterClientId = item.getString("meterClientId"),
                value = item.getDouble("value"),
                readingDate = item.getLong("readingDate"),
                isBilled = item.getBoolean("isBilled"),
                createdAt = item.getLong("createdAt"),
            ),
        )
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key)) null else getDouble(key)
