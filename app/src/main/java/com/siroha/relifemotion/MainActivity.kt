package com.siroha.relifemotion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.siroha.relifemotion.ui.AppRoot
import com.siroha.relifemotion.ui.theme.ReLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val s = Graph.settings
            ReLifeTheme(mode = s.themeMode, dynamic = s.dynamicColor) {
                AppRoot()
            }
        }
    }
}
