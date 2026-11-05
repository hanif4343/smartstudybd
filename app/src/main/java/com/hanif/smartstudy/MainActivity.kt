package com.hanif.smartstudy

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hanif.smartstudy.vision.VisionApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VisionApp() }
    }
}
