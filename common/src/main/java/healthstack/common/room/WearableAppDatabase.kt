package healthstack.common.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import healthstack.common.model.Accelerometer
import healthstack.common.room.converter.EcgConverter
import healthstack.common.room.dao.EcgDao
import healthstack.common.model.EcgSet
import healthstack.common.model.HeartRate
import healthstack.common.model.PpgGreen
import healthstack.common.room.converter.HeartRateConverter
import healthstack.common.room.dao.AccelerometerDao
import healthstack.common.room.dao.HeartRateDao
import healthstack.common.room.dao.PpgGreenDao

@Database(
    version = 1,
    exportSchema = false,

    entities = [
        Accelerometer::class,
        EcgSet::class,
        PpgGreen::class,
        HeartRate::class,
    ],
)
@TypeConverters(
    value = [
        EcgConverter::class,
        HeartRateConverter::class,
    ]
)
abstract class WearableAppDatabase : RoomDatabase() {
    abstract fun accelerometerDao(): AccelerometerDao
    abstract fun ecgDao(): EcgDao
    abstract fun ppgGreenDao(): PpgGreenDao
    abstract fun heartRateDao(): HeartRateDao

    companion object {
        @Volatile
        private var INSTANCE: WearableAppDatabase? = null

        fun initialize(
            context: Context,
        ): WearableAppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WearableAppDatabase::class.java,
                    "wearable_app_db"
                )
                    .fallbackToDestructiveMigration()
                    .enableMultiInstanceInvalidation()
                    .addTypeConverter(EcgConverter())
                    .addTypeConverter(HeartRateConverter())
                    .build()
                INSTANCE = instance
                instance
            }

        fun getInstance(): WearableAppDatabase? = INSTANCE
    }
}
