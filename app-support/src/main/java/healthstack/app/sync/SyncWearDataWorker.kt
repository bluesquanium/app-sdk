package healthstack.app.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
import healthstack.app.mapper.WearDataObjectMapper
import healthstack.backend.integration.BackendFacade
import healthstack.backend.integration.BackendFacadeHolder
import healthstack.common.HEALTH_DATA_FOLDER_NAME
import healthstack.common.model.EcgSet
import healthstack.common.model.HeartRate
import healthstack.common.model.PpgGreen
import healthstack.common.model.PrivDataType
import healthstack.common.model.WearData
import healthstack.healthdata.link.HealthData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.input.ReaderInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


class SyncWearDataWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : NetworkAwareWorker(appContext, workerParams) {
    private val healthDataSyncClient: BackendFacade = BackendFacadeHolder.getInstance()
    private val objectMapper = WearDataObjectMapper.getInstance()

    override suspend fun doTask(): Result {
        Log.i("AAAAA", "Start UploadHealthDataFileWorker")
        val dataDir = "${applicationContext.filesDir}" + HEALTH_DATA_FOLDER_NAME
        val dir = File(dataDir)

        dir.listFiles()?.forEach { file ->
            syncFile(file).onSuccess {
                Log.i(TAG, "Succeeded to sync ${file.name} file to db")
                file.delete()
            }
        }
        return Result.success()
    }

    private suspend fun syncFile(file: File): kotlin.Result<Unit> = runCatching {
        Log.i(TAG, "Try to sync ${file.name} file to the server")

        CoroutineScope(Dispatchers.IO).launch {
            FileInputStream(file).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val dataType = PrivDataType.valueOf(reader.readLine())
                val data = convertToData(dataType, ReaderInputStream(reader))

                syncData(dataType, data.map { it.toDataMap() })
            }
        }.join()
        Log.i(TAG, "after join")
    }

    private inline fun <reified T> readCsv(inputStream: InputStream): List<T> {
        val csvMapper = CsvMapper().apply {
            enable(CsvParser.Feature.TRIM_SPACES)
            enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        }

        val schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|')

        return csvMapper.readerFor(Map::class.java)
            .with(schema)
            .readValues<Map<String, String>>(inputStream)
            .readAll()
            .map {
                objectMapper.convertValue(it, T::class.java)
            }
    }

    private fun readFileContents(contents: List<String>): List<Map<String, Any>> {
        val schema = contents.first().split('|')

        return contents.drop(1).map {
            schema.zip(it.split('|')).toMap()
        }
    }

    private fun convertToData(dataType: PrivDataType, csvInputStream: InputStream): List<WearData> {
        return  when(dataType) {
            PrivDataType.WEAR_ECG -> readCsv<EcgSet>(csvInputStream)
            PrivDataType.WEAR_HEART_RATE -> readCsv<HeartRate>(csvInputStream)
            PrivDataType.WEAR_PPG_GREEN -> readCsv<PpgGreen>(csvInputStream)
            else -> throw Exception()
        }
    }

    suspend fun syncData(dataType: PrivDataType, data: List<Map<String, Any>>) {
        Log.i(TAG, "data synced from wearOS: $dataType, size: ${data.size}")
        Log.i(TAG, "data: $data")

        // TODO: Modify jwt-issuer and bearerToken fields
        healthDataSyncClient.syncHealthData(
            "super-tokens",
            "Bearer eyJraWQiOiJzLWI4NGIwZjZhLTExZmUtNGFjMC04MjQ2LWQzMDgzNTA3NjNmMyIsInR5cCI6IkpXVCIsImFsZyI6IlJTMjU2In0.eyJpYXQiOjE3MjQ3NjkxMDEsImV4cCI6MTcyNDc3MjcwMSwiaXNzIjoicmVzZWFyY2gtaHViLmNvbSIsInN1YiI6IjFmNjc3NDE1LTExYjQtNGRjZC05MjlmLTc4NjAxNmI5YzUwNCIsImVtYWlsIjoiY3VsYXRlcjAwMEB0ZXN0LmNvbSJ9.TksMX6UV9NfAHryP6yep7xewPA7U4OHXu86e_EDfxEN3UMqou2APl5FnQVjEPZqhIlUx5Tc7H34E7qlT9J1p3pf2c79aXOj94BRKhJS5lLWgrdk3_YyhY5n3BHs3Xz2Ta8_NJ6o4M4ssDjwfMppFXT7EbjXfvKtes0gYqsbxuF_SHqI2cafL7ao4DtdpMekgpI4ELYbj47AodWyAIaV8qEoQdLzXHZg0_xMXT4VCREAJIPZ0QEKvJwpmPRFpHu55PKM4yMH8IdyvBtq9-VJh5qumCOzONyV3zl8rNjbyWvx6BGb2Kwr68ihlj7mfBzH7fzhHKQqyFlm8sM8tACYSdQ",
            HealthData(
                dataType.name,
                data
            )
        )
    }

    companion object {
        private val TAG = SyncWearDataWorker::class.simpleName
        private const val WEAR_DIR = "wear"
    }
}

fun setUploadDataWorker(
    context: Context,
    syncInterval: Long,
    syncIntervalTimeUnit: TimeUnit,
) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "one-time-synewear",
        ExistingPeriodicWorkPolicy.REPLACE,
        PeriodicWorkRequestBuilder<SyncWearDataWorker>(
            syncInterval, syncIntervalTimeUnit,
        ).build(),
    )
}
