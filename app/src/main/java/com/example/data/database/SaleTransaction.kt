package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class SaleTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val paymentType: String, // "CASH" or "UPI"
    val itemsSummary: String, // e.g., "Gold Flake Loose x2, Water Bottle x1"
    val timestamp: Long = System.currentTimeMillis()
)
