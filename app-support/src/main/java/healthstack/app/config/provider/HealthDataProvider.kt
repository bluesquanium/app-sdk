package healthstack.app.config.provider

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import healthstack.app.data.repository.WearableDataReceiverRepositoryImpl
import healthstack.app.domain.repository.WearableDataReceiverRepository
import healthstack.common.room.WearableAppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class HealthDataProvider {
    @Singleton
    @Provides
    fun provideWearableDataRepository(
        // wearableAppDatabase: WearableAppDatabase,
    ): WearableDataReceiverRepository =
        WearableDataReceiverRepositoryImpl()
}
