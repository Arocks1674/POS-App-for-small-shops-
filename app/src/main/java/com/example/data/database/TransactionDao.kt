package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<SaleTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SaleTransaction)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("SELECT SUM(amount) FROM transactions WHERE paymentType = 'CASH'")
    fun getCashTotal(): Flow<Int?>

    @Query("SELECT SUM(amount) FROM transactions WHERE paymentType = 'UPI'")
    fun getUpiTotal(): Flow<Int?>
}
