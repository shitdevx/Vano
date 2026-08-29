package com.vamora.vano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vamora.vano.data.VanoViewModel
import com.vamora.vano.ui.AppNavHost
import com.vamora.vano.ui.theme.VanoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        val vmFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(VanoViewModel::class.java)) {
                    return VanoViewModel(application) as T
                }
                return factory.create(modelClass)
            }
        }
        setContent {
            VanoTheme {
                val vm: VanoViewModel = viewModel(factory = vmFactory)
                AppNavHost(viewModel = vm)
            }
        }
    }
}