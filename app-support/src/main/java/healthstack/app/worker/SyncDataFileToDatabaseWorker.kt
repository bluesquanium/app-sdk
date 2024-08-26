package healthstack.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import healthstack.app.domain.usecase.SyncWearableDataUseCase
import healthstack.common.HEALTH_DATA_FOLDER_NAME
import healthstack.common.model.PrivDataType
import org.apache.commons.io.input.ReaderInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

@HiltWorker
class SyncDataFileToDatabaseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted val workerParams: WorkerParameters,
    private val syncWearableDataUseCase: SyncWearableDataUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dataDir = "${applicationContext.filesDir}" + HEALTH_DATA_FOLDER_NAME
        var file = File(dataDir)
        kotlin.runCatching {
            val fileName = workerParams.tags.first {
                it.contains(".csv")
            }
            Log.i(TAG, "Try to sync $fileName file to db")
            File(dataDir, fileName).takeIf { it.exists() }?.let {
                file = it
                FileInputStream(file).use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val dataType = PrivDataType.valueOf(reader.readLine())
                    syncWearableDataUseCase(dataType, ReaderInputStream(reader))
                }
            }
        }.onSuccess {
            Log.i(TAG, "Succeeded to sync ${file.name} file to db")
            file.delete()
            return Result.success()
        }.onFailure {
            Log.e(TAG, "Failed to sync ${file.name} file to db: ${it.message}")
            if (runAttemptCount > 5) {
                return Result.failure()
            }
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private val TAG = SyncDataFileToDatabaseWorker::class.simpleName
    }
}
