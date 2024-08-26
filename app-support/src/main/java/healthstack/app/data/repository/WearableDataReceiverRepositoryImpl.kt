package healthstack.app.data.repository

import android.util.Log
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import healthstack.app.domain.repository.WearableDataReceiverRepository
import healthstack.common.model.Accelerometer
import healthstack.common.model.EcgSet
import healthstack.common.model.HeartRate
import healthstack.common.model.PpgGreen
import healthstack.common.model.PrivDataType
import healthstack.common.model.Timestamp
import healthstack.common.room.WearableAppDatabase
import healthstack.common.room.dao.PrivDao
import java.io.IOException
import java.io.InputStream

class WearableDataReceiverRepositoryImpl(
    // private val wearableAppDataBase: WearableAppDatabase,
) : WearableDataReceiverRepository {
    private val gson = Gson()

    val objectMapper = jacksonObjectMapper().apply {
        class ListDeserializer : JsonDeserializer<List<*>>(), ContextualDeserializer {
            private val objectMapper = ObjectMapper()
            private val types: MutableMap<String, JavaType> = mutableMapOf()

            override fun createContextual(
                ctxt: DeserializationContext,
                property: BeanProperty
            ): JsonDeserializer<List<*>> {
                types[property.name] = property.type
                return this
            }

            @Throws(IOException::class)
            override fun deserialize(
                jsonParser: JsonParser,
                deserializationContext: DeserializationContext
            ): List<*> {
                val node: JsonNode = jsonParser.getCodec().readTree(jsonParser)
                val jsonStr = node.toString().drop(1).dropLast(1).replace("\\\"", "\"")
                return objectMapper.readValue(jsonStr, types[jsonParser.parsingContext.currentName])
            }
        }
        val listModule = SimpleModule().addDeserializer(List::class.java, ListDeserializer())
        registerModule(listModule)
        registerModule(JavaTimeModule())
    }

    override fun saveWearableData(dataType: PrivDataType, csvInputStream: InputStream) {
        Log.i(TAG, "try to saveWearableData: $dataType")
//        when (dataType) {
//            PrivDataType.ACCELEROMETER -> saveData<Accelerometer>(readCsv<Accelerometer>(csvInputStream), wearableAppDataBase.accelerometerDao())
//            PrivDataType.ECG -> saveData<EcgSet>(readCsv<EcgSet>(csvInputStream), wearableAppDataBase.ecgDao())
//            PrivDataType.PPG_GREEN -> saveData<PpgGreen>(readCsv<PpgGreen>(csvInputStream), wearableAppDataBase.ppgGreenDao())
//            PrivDataType.HEART_RATE -> saveData<HeartRate>(readCsv<HeartRate>(csvInputStream), wearableAppDataBase.heartRateDao())
//        }
    }

    override suspend fun syncWearableData() {
        TODO("Not yet implemented")
    }

    inline fun <reified T> readCsv(inputStream: InputStream): List<T> {
        val csvMapper = CsvMapper().apply {
            enable(CsvParser.Feature.TRIM_SPACES)
            enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        }

        val schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|')

        val data = csvMapper.readerFor(Map::class.java)
            .with(schema)
            .readValues<Map<String, String>>(inputStream)
            .readAll()
            .map {
                objectMapper.convertValue(it, T::class.java)
            }

        Log.i(
            WearableDataReceiverRepositoryImpl::class.simpleName,
            "data synced from wearOS: ${T::class.java.simpleName}, size: ${data.size}"
        )

        return data
    }

    private inline fun <reified T : Timestamp> saveData(data: List<T>, privDao: PrivDao<T>) {
        privDao.insertAll(data)
    }

    companion object {
        private val TAG = this::class.simpleName
    }
}
