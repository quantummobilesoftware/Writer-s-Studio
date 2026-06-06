package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.WriterDatabase
import com.example.data.repository.WriterRepository
import com.example.ui.screens.WriterAppMainLayout
import com.example.ui.viewmodel.WriterViewModel
import com.example.ui.viewmodel.WriterViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize local SQLite room database
        val database = WriterDatabase.getDatabase(applicationContext)

        // 2. Initialize domain coordinator repository
        val repository = WriterRepository(database)

        // 3. Prepare factory
        val factory = WriterViewModelFactory(repository)

        // 4. Resolve ViewModel
        val viewModel = ViewModelProvider(this, factory)[WriterViewModel::class.java]

        // 5. Initialize network sync listener for Google Drive
        viewModel.initNetworkListener(this)

        // 6. Automatically check and restore existing Google session
        viewModel.checkAndRestoreGoogleSession(this)

        setContent {
            // Draw central composition
            WriterAppMainLayout(viewModel)
        }
    }
}
