package healthstack.common.room.dao

import androidx.room.Dao
import healthstack.common.model.ACCELEROMETER_TABLE_NAME
import healthstack.common.model.Accelerometer

@Dao
abstract class AccelerometerDao : PrivDao<Accelerometer>(ACCELEROMETER_TABLE_NAME)
