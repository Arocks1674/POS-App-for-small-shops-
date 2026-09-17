package com.example.data.repository

import com.example.data.database.SaleTransaction
import com.example.data.database.TransactionDao
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {
    val allTransactions: Flow<List<SaleTransaction>> = dao.getAllTransactions()
    val cashTotal: Flow<Int?> = dao.getCashTotal()
    val upiTotal: Flow<Int?> = dao.getUpiTotal()

    suspend fun insertTransaction(transaction: SaleTransaction) {
        dao.insertTransaction(transaction)
    }

    suspend fun clearAllTransactions() {
        dao.clearAllTransactions()
    }
}
