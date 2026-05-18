package com.aegispay.android.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.math.BigDecimal

@Database(
    entities = [OfflinePaymentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(OfflineDatabase.Converters::class)
abstract class OfflineDatabase : RoomDatabase() {

    abstract fun offlinePaymentDao(): OfflinePaymentDao

    class Converters {
        @TypeConverter
        fun fromStatus(value: OfflinePaymentStatus): String = value.name

        @TypeConverter
        fun toStatus(value: String): OfflinePaymentStatus =
            OfflinePaymentStatus.valueOf(value)

        @TypeConverter
        fun fromBigDecimal(value: BigDecimal): String = value.toPlainString()

        @TypeConverter
        fun toBigDecimal(value: String): BigDecimal = BigDecimal(value)
    }

    companion object {
        private const val DB_NAME = "aegispay_offline.db"

        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        fun getInstance(context: Context): OfflineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration()   // offline queue is ephemeral — safe to drop
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
