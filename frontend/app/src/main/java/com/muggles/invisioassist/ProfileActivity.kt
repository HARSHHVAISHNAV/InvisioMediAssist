package com.muggles.invisioassist

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.*
import androidx.compose.foundation.background


class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProfileScreen()
        }
    }
}

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 🔙 Back Button at the Top
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { context.startActivity(Intent(context, ScanPreviewActivity::class.java)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Settings", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🌍 Language Settings (Dropdown)
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Language, contentDescription = "Language")
                Spacer(modifier = Modifier.width(12.dp))
                Text("Language Settings", fontSize = 16.sp)
            }

            // 🆕 Animated Expansion
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp)
                        .background(Color(0xFFF5F5F5), shape = RoundedCornerShape(8.dp)) // ✅ FIXED
                ) {
                    listOf("English", "Hindi", "Marathi").forEach { language ->
                        Text(
                            text = language,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = language
                                    expanded = false
                                    changeAppLanguage(context, language)
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }

        }

        // 📜 History & Past Orders (Shifts down when dropdown opens)
        Spacer(modifier = Modifier.height(if (expanded) 120.dp else 20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Future feature */ }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = "History")
            Spacer(modifier = Modifier.width(12.dp))
            Text("History & Past Orders", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ❓ Help & Support
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(context, HelpSupportActivity::class.java))
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.HelpOutline, contentDescription = "Help")
            Spacer(modifier = Modifier.width(12.dp))
            Text("Help & Support", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.weight(1f)) // Pushes the back button to the bottom

        // 🔙 Back Button (Properly positioned)
        Button(
            onClick = { context.startActivity(Intent(context, ScanPreviewActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back", color = Color.White)
        }
    }
}

// ✅ Function to Change App Language Dynamically
fun changeAppLanguage(context: android.content.Context, language: String) {
    val locale = when (language) {
        "Hindi" -> Locale("hi")
        "Marathi" -> Locale("mr")
        else -> Locale("en")
    }

    Locale.setDefault(locale)
    val config = Configuration()
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)

    Log.d("LanguageChange", "Language changed to $language")
}
