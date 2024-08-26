package healthstack.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import healthstack.app.data.repository.WearableDataReceiverRepositoryImpl
import healthstack.backend.integration.BackendFacade
import healthstack.backend.integration.BackendFacadeHolder
import healthstack.common.HEALTH_DATA_FOLDER_NAME
import healthstack.common.model.Accelerometer
import healthstack.common.model.EcgSet
import healthstack.common.model.HeartRate
import healthstack.common.model.PpgGreen
import healthstack.common.model.PrivDataType
import healthstack.common.model.Timestamp
import healthstack.common.room.dao.PrivDao
import healthstack.healthdata.link.HealthData
import org.apache.commons.io.input.ReaderInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@HiltWorker
class UploadHealthDataFileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
) : NetworkAwareWorker(appContext, workerParams) {
    private val healthDataSyncClient: BackendFacade = BackendFacadeHolder.getInstance()

    override suspend fun doTask(): Result {
        Log.i("AAAAA", "Start UploadHealthDataFileWorker")
        val dataDir = "${applicationContext.filesDir}" + HEALTH_DATA_FOLDER_NAME
        val dir = File(dataDir)

        dir.listFiles().forEach { file ->
            syncFile(file).onSuccess {
                Log.i(TAG, "Succeeded to sync ${file.name} file to db")
                file.delete()
            }
        }
        return Result.success()
    }

    private suspend fun syncFile(file: File): kotlin.Result<Unit> = runCatching {
        Log.i(TAG, "Try to sync ${file.name} file to the server")

        FileInputStream(file).use { inputStream ->
            Log.i("syncFile", "start to read file")
            //Log.i("syncFile", "${file.readLines()}")
            val fileStr = file.readLines()
            // val dataType = PrivDataType.valueOf(fileStr.first())\
            val dataType = PrivDataType.WEAR_PPG_GREEN
            Log.i("syncFile", "dataType: $dataType")
            // saveWearableData(dataType, ReaderInputStream(reader))
            val contents = fileStr.joinToString(separator = "\n")
            Log.i("syncFile", "content: $contents")
            saveWearableData(dataType, contents)
        }

        // upload
        Log.i("syncFile", "upload File: ${file.name}")
    }

    suspend fun saveWearableData(dataType: PrivDataType, fileStr: String) {
        Log.i(TAG, "try to saveWearableData: $dataType")
        syncData(dataType, readCsv(fileStr))
    }

    fun readCsv(fileStr: String): List<Map<String, Any>> {
        val csvMapper = CsvMapper().apply {
            enable(CsvParser.Feature.TRIM_SPACES)
            enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        }

        val schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|')

        val data = csvMapper.readerFor(Map::class.java)
            .with(schema)
            .readValues<Map<String, String>>(fileStr)
            .readAll()

        return data
    }

    suspend fun saveWearableData(dataType: PrivDataType, csvInputStream: InputStream) {
        Log.i(TAG, "try to saveWearableData: $dataType")
        syncData(dataType, readCsv(csvInputStream))
    }

    fun readCsv(inputStream: InputStream): List<Map<String, Any>> {
        val csvMapper = CsvMapper().apply {
            enable(CsvParser.Feature.TRIM_SPACES)
            enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        }

        val schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|')

        val data = csvMapper.readerFor(Map::class.java)
            .with(schema)
            .readValues<Map<String, String>>(inputStream)
            .readAll()

        return data
    }

    suspend fun syncData(dataType: PrivDataType, data: List<Map<String, Any>>) {
        Log.i(TAG, "data synced from wearOS: $dataType, size: ${data.size}")

        // TODO: jwt-issuer, bearerToken
        healthDataSyncClient.syncHealthData(
            "super-tokens",
            "Bearer abc",
            HealthData(
                dataType.name,
                data
            )
        )
    }

    companion object {
        private val TAG = UploadHealthDataFileWorker::class.simpleName
        private const val WEAR_DIR = "wear"
    }
}

fun setUploadDataWorker(
    context: Context,
    syncInterval: Long,
    syncIntervalTimeUnit: TimeUnit,
) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UploadHealthDataFileWorker::class.java.simpleName,
        ExistingPeriodicWorkPolicy.REPLACE,
        PeriodicWorkRequestBuilder<UploadHealthDataFileWorker>(
            syncInterval, syncIntervalTimeUnit,
        ).build(),
    )
}
