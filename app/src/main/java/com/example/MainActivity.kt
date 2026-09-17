package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.TransactionRepository
import com.example.ui.MainPosScreen
import com.example.ui.PosViewModel
import com.example.ui.PosViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // ViewModel setup backed by lazy repository initiation
    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { TransactionRepository(db.transactionDao()) }
    private val viewModel: PosViewModel by viewModels { PosViewModelFactory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Supports borderless drawing safe areas
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainPosScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Capture physical volume key presses to instantly activate scanner
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            viewModel.toggleScanner()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
