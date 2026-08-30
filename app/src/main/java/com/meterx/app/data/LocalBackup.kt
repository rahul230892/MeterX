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
    val paymentMethodCount: Int,
    val paymentCount: Int,
)

data class ImportedData(
    val meters: List<MeterEntity>,
    val readings: List<ReadingEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
    val payments: List<PaymentRecordEntity>,
)

class LocalBackup(private val context: Context) {
    suspend fun write(
        meters: List<MeterWithReadings>,
        paymentMethods: List<PaymentMethodEntity>,
    ) = withContext(Dispatchers.IO) {
        val target = context.filesDir.resolve("meterx_backup.json")
        val temporary = context.filesDir.resolve("meterx_backup.tmp")
        temporary.writeText(encode(meters, paymentMethods))
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }

    suspend fun export(
        uri: Uri,
        meters: List<MeterWithReadings>,
        paymentMethods: List<PaymentMethodEntity>,
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use {
            it.write(encode(meters, paymentMethods))
        } ?: error("Unable to open the selected file.")
    }

    suspend fun previewImport(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val data = decode(readText(uri))
        ImportPreview(
            uri = uri,
            meterCount = data.meters.size,
            readingCount = data.readings.size,
            paymentMethodCount = data.paymentMethods.size,
            paymentCount = data.payments.size,
        )
    }

    suspend fun readImport(uri: Uri): ImportedData = withContext(Dispatchers.IO) {
        decode(readText(uri))
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to open the selected file.")

    private fun encode(
        meters: List<MeterWithReadings>,
        paymentMethods: List<PaymentMethodEntity>,
    ): String {
        val methodArray = JSONArray()
        paymentMethods.forEach { method ->
            methodArray.put(
                JSONObject()
                    .put("id", method.id)
                    .put("name", method.name)
                    .put("createdAt", method.createdAt),
            )
        }

        val meterArray = JSONArray()
        meters.forEach { item ->
            val readings = JSONArray()
            item.sortedReadings.forEach { reading ->
                val customValues = JSONArray()
                reading.customValues.toSortedMap().forEach { (fieldId, value) ->
                    customValues.put(
                        JSONObject()
                            .put("fieldId", fieldId)
                            .put("value", value),
                    )
                }
                readings.put(
                    JSONObject()
                        .put("id", reading.id)
                        .put("value", reading.value)
                        .put("date", reading.readingDate)
                        .put("isBilled", reading.isBilled)
                        .put("customValues", customValues)
                        .put("createdAt", reading.createdAt),
                )
            }

            val payments = JSONArray()
            item.payments.forEach { payment ->
                payments.put(
                    JSONObject()
                        .put("id", payment.id)
                        .put("readingId", payment.readingId)
                        .put("amount", payment.amount)
                        .put("paymentDate", payment.paymentDate)
                        .put("methodName", payment.methodName)
                        .put("createdAt", payment.createdAt),
                )
            }

            val customFields = JSONArray()
            item.meter.customFields.forEach { field ->
                customFields.put(
                    JSONObject()
                        .put("id", field.id)
                        .put("name", field.name),
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
                    .put("customFields", customFields)
                    .put("createdAt", item.meter.createdAt)
                    .put("readings", readings)
                    .put("payments", payments),
            )
        }

        return JSONObject()
            .put("format", "meterx-backup")
            .put("version", 3)
            .put("exportedAt", System.currentTimeMillis())
            .put("paymentMethods", methodArray)
            .put("meters", meterArray)
            .toString(2)
    }

    private fun decode(text: String): ImportedData {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            error("This is not a valid MeterX data file.")
        }
        val version = root.optInt("version", -1)
        require(version in 1..3) {
            "This MeterX data file version is not supported."
        }
        val meterArray = root.optJSONArray("meters")
            ?: error("The data file does not contain meters.")
        val meters = mutableListOf<MeterEntity>()
        val readings = mutableListOf<ReadingEntity>()
        val paymentMethods = mutableListOf<PaymentMethodEntity>()
        val payments = mutableListOf<PaymentRecordEntity>()
        val meterIds = mutableSetOf<Long>()
        val readingIds = mutableSetOf<Long>()
        val paymentMethodIds = mutableSetOf<Long>()
        val paymentIds = mutableSetOf<Long>()

        val methodArray = root.optJSONArray("paymentMethods") ?: JSONArray()
        repeat(methodArray.length()) { methodIndex ->
            val json = methodArray.optJSONObject(methodIndex)
                ?: error("A payment method record is invalid.")
            val id = json.optLong("id", 0)
            val name = json.optString("name").trim()
            require(id > 0 && paymentMethodIds.add(id) && name.isNotEmpty()) {
                "A payment method record is incomplete or duplicated."
            }
            paymentMethods += PaymentMethodEntity(
                id = id,
                name = name,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
        }

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
            val customFields = mutableListOf<CustomFieldDefinition>()
            val customFieldIds = mutableSetOf<String>()
            val customFieldNames = mutableSetOf<String>()
            val customFieldArray = json.optJSONArray("customFields") ?: JSONArray()
            require(customFieldArray.length() <= 50) {
                "A meter has too many custom columns."
            }
            repeat(customFieldArray.length()) { fieldIndex ->
                val field = customFieldArray.optJSONObject(fieldIndex)
                    ?: error("A custom column definition is invalid.")
                val fieldId = field.optString("id").trim()
                val fieldName = field.optString("name").trim()
                require(
                    fieldId.isNotEmpty() &&
                        fieldId.length <= 80 &&
                        customFieldIds.add(fieldId) &&
                        fieldName.isNotEmpty() &&
                        fieldName.length <= 60 &&
                        customFieldNames.add(fieldName.lowercase()),
                ) {
                    "A custom column definition is incomplete or duplicated."
                }
                customFields += CustomFieldDefinition(fieldId, fieldName)
            }
            meters += MeterEntity(
                id = id,
                nickname = nickname,
                type = type,
                meterNumber = meterNumber,
                consumerNumber = json.nullableString("consumerNumber"),
                freeUnits = freeUnits.takeIf { type == MeterType.ELECTRICITY },
                cycleBaseline = json.nullableDouble("cycleBaseline"),
                customFields = customFields,
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
                val customValues = mutableMapOf<String, String>()
                val customValueArray = reading.optJSONArray("customValues") ?: JSONArray()
                repeat(customValueArray.length()) { valueIndex ->
                    val customValue = customValueArray.optJSONObject(valueIndex)
                        ?: error("A custom column value is invalid.")
                    val fieldId = customValue.optString("fieldId").trim()
                    val fieldValue = customValue.optString("value").trim()
                    require(
                        fieldId in customFieldIds &&
                            fieldValue.isNotEmpty() &&
                            fieldValue.length <= 240 &&
                            customValues.put(fieldId, fieldValue) == null,
                    ) {
                        "A custom column value is incomplete or duplicated."
                    }
                }
                readings += ReadingEntity(
                    id = readingId,
                    meterId = id,
                    value = value,
                    readingDate = reading.getLong("date"),
                    isBilled = reading.optBoolean("isBilled", false),
                    customValues = customValues,
                    createdAt = reading.optLong("createdAt", System.currentTimeMillis()),
                )
            }

            val paymentArray = json.optJSONArray("payments") ?: JSONArray()
            repeat(paymentArray.length()) { paymentIndex ->
                val payment = paymentArray.optJSONObject(paymentIndex)
                    ?: error("A payment record is invalid.")
                val paymentId = payment.optLong("id", 0)
                val readingId = payment.optLong("readingId", 0)
                val amount = payment.optDouble("amount", Double.NaN)
                val methodName = payment.optString("methodName").trim()
                require(
                    paymentId > 0 &&
                        paymentIds.add(paymentId) &&
                        readingIds.contains(readingId) &&
                        amount.isFinite() &&
                        amount > 0 &&
                        methodName.isNotEmpty(),
                ) {
                    "A payment record is incomplete or duplicated."
                }
                payments += PaymentRecordEntity(
                    id = paymentId,
                    meterId = id,
                    readingId = readingId,
                    amount = amount,
                    paymentDate = payment.getLong("paymentDate"),
                    methodName = methodName,
                    createdAt = payment.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }
        return ImportedData(meters, readings, paymentMethods, payments)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key, Double.NaN).takeIf(Double::isFinite)
}
