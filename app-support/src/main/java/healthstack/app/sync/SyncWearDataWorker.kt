package healthstack.app.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import healthstack.backend.integration.BackendFacade
import healthstack.backend.integration.BackendFacadeHolder
import healthstack.common.HEALTH_DATA_FOLDER_NAME
import healthstack.common.model.PrivDataType
import healthstack.healthdata.link.HealthData
import java.io.File
import java.util.concurrent.TimeUnit


class SyncWearDataWorker(
    appContext: Context,
    workerParams: WorkerParameters,
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

        Log.i("syncFile", "start to read file")
        val contents = file.readLines()
        val dataType = PrivDataType.valueOf(contents.first())
        Log.i("syncFile", "dataType: $dataType")
        syncFileContents(dataType, contents.drop(1))

        // upload
        Log.i("syncFile", "upload File: ${file.name}")
    }

    private suspend fun syncFileContents(dataType: PrivDataType, contents: List<String>) {
        Log.i(TAG, "try to saveWearableData: $dataType")
        syncData(dataType, readFileContents(contents))
    }

    private fun readFileContents(contents: List<String>): List<Map<String, Any>> {
        val schema = contents.first().split('|')

        return contents.drop(1).map {
            schema.zip(it.split('|')).toMap()
        }
    }

    suspend fun syncData(dataType: PrivDataType, data: List<Map<String, Any>>) {
        Log.i(TAG, "data synced from wearOS: $dataType, size: ${data.size}")
        Log.i(TAG, "data: $data")

        // TODO: Modify jwt-issuer and bearerToken fields
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
        SyncWearDataWorker::class.java.simpleName,
        ExistingPeriodicWorkPolicy.REPLACE,
        PeriodicWorkRequestBuilder<SyncWearDataWorker>(
            syncInterval, syncIntervalTimeUnit,
        ).build(),
    )
}
