package healthstack.app.domain.usecase

import healthstack.app.domain.repository.WearableDataReceiverRepository
import healthstack.common.model.PrivDataType
import java.io.InputStream
import javax.inject.Inject

class SyncWearableDataUseCase @Inject constructor(
    private val wearableDataReceiverRepository: WearableDataReceiverRepository
) {
    operator fun invoke(dataType: PrivDataType, csvInputStream: InputStream) = wearableDataReceiverRepository.saveWearableData(dataType, csvInputStream)
}
