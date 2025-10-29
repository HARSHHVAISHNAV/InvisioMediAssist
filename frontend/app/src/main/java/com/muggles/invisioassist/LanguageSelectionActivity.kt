package com.muggles.invisioassist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color


class LanguageSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this@LanguageSelectionActivity // Fix: Explicit context reference
            LanguageSelectionScreen { selectedLanguage ->
                // Move to Scan Preview Page after confirming language
                val intent = Intent(this, ScanPreviewActivity::class.java)
                intent.putExtra("selectedLanguage", selectedLanguage)
                context.startActivity(intent)
                finish()
            }
        }
    }
}

@Composable
fun LanguageSelectionScreen(onConfirm: (String) -> Unit) {
    var selectedLanguage by remember { mutableStateOf("English") }
    val languages = listOf("Hindi", "English", "Marathi")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Language", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text("Choose a language for text-to-speech conversion.", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Language Options List
        Column {
            languages.forEach { language ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedLanguage = language }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedLanguage == language, onClick = { selectedLanguage = language })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(language, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Selected Language Display
        Text("Selected Language", fontSize = 16.sp)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = "Selected")
                Spacer(modifier = Modifier.width(8.dp))
                Text(selectedLanguage, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Button
        Button(
            onClick = { onConfirm(selectedLanguage) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(Color.Black)
        ) {
            Text("Confirm", color = Color.White)
        }
    }
}
