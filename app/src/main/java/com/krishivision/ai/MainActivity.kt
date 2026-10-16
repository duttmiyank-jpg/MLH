package com.krishivision.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krishivision.ai.ui.screens.HomeScreen
import com.krishivision.ai.ui.theme.KrishiVisionAITheme
import com.krishivision.ai.ui.viewmodel.KrishiVisionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KrishiVisionAITheme {
                val viewModel: KrishiVisionViewModel = viewModel()
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
