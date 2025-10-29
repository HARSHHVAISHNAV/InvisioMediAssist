package com.muggles.invisioassist

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class IntroductionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Navigate to ScanPreviewActivity after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ScanPreviewActivity::class.java))
            finish()
        }, 3000)

        // Set UI Content
        setContent {
            IntroductionScreen()
        }
    }
}

@Composable
fun IntroductionScreen() {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500) // Small delay before fade-in starts
        isVisible = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isVisible, enter = fadeIn()) {
                Text(
                    text = "Welcome to InvisioMediAssist",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
