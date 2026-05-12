package com.solar.dashka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solar.dashka.presentation.theme.DashkaTheme
import com.solar.dashka.presentation.translator.TranslatorScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DashkaTheme {
                TranslatorScreen()
            }
        }
    }
}
