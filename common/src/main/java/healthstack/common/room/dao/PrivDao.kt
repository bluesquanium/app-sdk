package healthstack.common.room.dao

import androidx.paging.PagingSource
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import healthstack.common.model.Accelerometer
import healthstack.common.model.EcgSet
import healthstack.common.model.HeartRate
import healthstack.common.model.PpgGreen
import healthstack.common.model.Timestamp

abstract class PrivDao<T : Timestamp>(
    private val tableName: String,
) {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(data: T)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(data: Collection<T>)

    @RawQuery(
        observedEntities =
            [
                Accelerometer::class,
                EcgSet::class,
                HeartRate::class,
                PpgGreen::class,
            ]
    )
    protected abstract fun getGreaterThan(query: SupportSQLiteQuery): PagingSource<Int, T>

    fun getGreaterThan(timeStamp: Long): PagingSource<Int, T> {
        return getGreaterThan(
            SimpleSQLiteQuery("SELECT * FROM $tableName WHERE timeStamp > $timeStamp ORDER BY timeStamp ASC")
        )
    }

    @RawQuery
    abstract fun deleteLEThan(query: SupportSQLiteQuery): Boolean

    fun deleteLEThan(timeStamp: Long) {
        deleteLEThan(SimpleSQLiteQuery("DELETE FROM $tableName  WHERE timeStamp <= $timeStamp"))
    }
}
