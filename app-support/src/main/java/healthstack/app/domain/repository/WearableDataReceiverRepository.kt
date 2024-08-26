package healthstack.app.domain.repository

import com.google.gson.JsonObject
import healthstack.common.model.PrivDataType
import java.io.InputStream

interface WearableDataReceiverRepository {
    fun saveWearableData(dataType: PrivDataType, csvInputStream: InputStream)

    suspend fun syncWearableData()
}
