package healthstack.common.room.dao

import androidx.room.Dao
import healthstack.common.model.HEART_RATE_TABLE_NAME
import healthstack.common.model.HeartRate

@Dao
abstract class HeartRateDao : PrivDao<HeartRate>(HEART_RATE_TABLE_NAME)
