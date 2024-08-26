package healthstack.common.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import healthstack.common.model.EcgSet
import healthstack.common.model.ECG_TABLE_NAME

@Dao
abstract class EcgDao : PrivDao<EcgSet>(ECG_TABLE_NAME)
