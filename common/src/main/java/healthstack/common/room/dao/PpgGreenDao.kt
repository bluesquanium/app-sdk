package healthstack.common.room.dao

import androidx.room.Dao
import healthstack.common.model.PPG_GREEN_TABLE_NAME
import healthstack.common.model.PpgGreen

@Dao
abstract class PpgGreenDao : PrivDao<PpgGreen>(PPG_GREEN_TABLE_NAME)
