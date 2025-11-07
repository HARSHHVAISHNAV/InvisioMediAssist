package com.muggles.invisioassist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.speech.tts.TextToSpeech
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.muggles.invisioassist.network.ImageRequest
import com.muggles.invisioassist.network.MedicineResponse
import com.muggles.invisioassist.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.content.pm.PackageManager


class ScanPreviewActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var capturedImage by mutableStateOf<Bitmap?>(null)
    private lateinit var textToSpeech: TextToSpeech
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastDetectedText by mutableStateOf("")
    private var isCameraInitialized by mutableStateOf(false)

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent
    private var isSpeaking = false
    private val testWithDummyBase64 = false

    // ✅ Haptic Feedback Manager
    private lateinit var vibrator: Vibrator

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            hapticFeedback(HapticPattern.COMMAND_DETECTED)
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
            capturedImage = bitmap
            processImage(bitmap)
        }
    }

    // ✅ Haptic Feedback Patterns
    enum class HapticPattern(val duration: Long, val repeat: Int = 1, val delay: Long = 0) {
        LISTENING_START(80),           // Subtle single buzz
        COMMAND_DETECTED(60, 2, 100),  // Double buzz
        SCAN_SUCCESS(200),              // Long confirmation
        ERROR(40, 3, 80)                // Triple short buzz
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("VoiceFlow", "onCreate called")
        super.onCreate(savedInstanceState)

        // ✅ Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Permissions
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 101)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
                speakOut(getString(R.string.voice_welcome))
            } else {
                Log.e("TTS", "Initialization failed")
                hapticFeedback(HapticPattern.ERROR)
            }
        }

        // Voice recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
        } else {
            speakOut("Speech recognition not available.")
            hapticFeedback(HapticPattern.ERROR)
        }

        setContent {
            ScanPreviewScreen(
                onProfileClick = {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)
                    startActivity(Intent(this, ProfileActivity::class.java))
                },
                onGalleryClick = {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)
                    galleryLauncher.launch("image/*")
                },
                onCaptureImage = { bitmap ->
                    capturedImage = bitmap
                    processImage(bitmap)
                },
                onDoubleTap = {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)
                    stopTTSAndReset()
                },
                onRepeat = {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)
                    repeatText()
                },
                onCameraReady = {
                    isCameraInitialized = true
                    hapticFeedback(HapticPattern.SCAN_SUCCESS)
                    Log.d("Camera", "✅ Camera ready callback triggered")
                }
            )
        }

        startListening()
    }

    // ✅ Haptic Feedback Function - Fixed Version
    private fun hapticFeedback(pattern: HapticPattern) {
        try {
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Modern Android - Use VibrationEffect with waveform
                val timings = mutableListOf<Long>()
                val amplitudes = mutableListOf<Int>()

                for (i in 0 until pattern.repeat) {
                    if (i > 0) {
                        timings.add(pattern.delay)
                        amplitudes.add(0)
                    }
                    timings.add(pattern.duration)
                    amplitudes.add(VibrationEffect.DEFAULT_AMPLITUDE)
                }

                val effect = VibrationEffect.createWaveform(
                    timings.toLongArray(),
                    amplitudes.toIntArray(),
                    -1
                )
                vibrator.vibrate(effect)
            } else {
                // Older Android versions - Simple vibration
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern.duration)
            }
        } catch (e: Exception) {
            Log.e("Haptic", "Vibration error: ${e.message}")
        }
    }

    // --- TTS function ---
    private fun speakOut(text: String) {
        isSpeaking = true
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ttsId")

        textToSpeech.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                runOnUiThread {
                    android.os.Handler(mainLooper).postDelayed({
                        startListening()
                    }, 900)
                }
            }
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                hapticFeedback(HapticPattern.ERROR)
                runOnUiThread {
                    android.os.Handler(mainLooper).postDelayed({
                        startListening()
                    }, 900)
                }
            }
        })
    }

    private fun repeatText() {
        if (lastDetectedText.isNotEmpty()) {
            speakOut(lastDetectedText)
        } else {
            speakOut(getString(R.string.voice_no_last_result))
            hapticFeedback(HapticPattern.ERROR)
        }
    }

    private fun stopTTSAndReset() {
        textToSpeech.stop()
        capturedImage = null
        lastDetectedText = ""
    }

    // --- Listening and command handling ---
    private fun startListening() {
        Log.d("VoiceFlow", "startListening triggered")

        if (isSpeaking) return

        hapticFeedback(HapticPattern.LISTENING_START)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                Log.d("Voice", "Heard: $command")

                if (command.isNotEmpty()) {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)
                }

                processVoiceCommand(command)
            }
            override fun onError(error: Int) {
                Log.e("Voice", "Recognition error: $error")
                hapticFeedback(HapticPattern.ERROR)

                android.os.Handler(mainLooper).postDelayed({
                    startListening()
                }, 1000)
            }
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("Voice", "Ready for speech")
            }
            override fun onBeginningOfSpeech() {
                Log.d("Voice", "Speech began")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(speechIntent)
    }

    private fun processVoiceCommand(command: String) {
        Log.d("VoiceDebug", "Received voice command: $command")

        val clean = command.lowercase(Locale.getDefault()).trim()
        when {
            clean.contains("scan") -> {
                if (!isCameraInitialized || imageCapture == null) {
                    Log.w("VoiceCapture", "Camera not initialized yet")
                    hapticFeedback(HapticPattern.ERROR)
                    speakOut("Camera is still starting. Please wait and try again.")
                    return
                }

                speakOut(getString(R.string.voice_capturing))
                android.os.Handler(mainLooper).postDelayed({
                    captureImage()
                }, 1500)
            }
            clean.contains("repeat") -> repeatText()
            clean.contains("help") -> {
                val helpText = """
        You can say:
        - Scan: to capture and identify a medicine.
        - Repeat: to repeat the last result.
        - Help: to hear these commands again.
        - Exit: to close the app.
    """.trimIndent()
                speakOut(helpText)
            }
            clean.contains("exit") || clean.contains("close") -> {
                hapticFeedback(HapticPattern.SCAN_SUCCESS)
                speakOut(getString(R.string.voice_closing))
                finish()
            }
            else -> {
                hapticFeedback(HapticPattern.ERROR)
                speakOut(getString(R.string.voice_not_recognized))
            }
        }
    }

    private fun captureImage() {
        Log.d("VoiceCapture", "captureImage called. imageCapture = ${imageCapture != null}, initialized = $isCameraInitialized")

        val capture = imageCapture
        if (capture == null) {
            Log.e("VoiceCapture", "❌ imageCapture is null!")
            hapticFeedback(HapticPattern.ERROR)
            speakOut("Camera not ready yet. Please try again.")
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d("Camera", "✅ Image captured successfully")
                    hapticFeedback(HapticPattern.SCAN_SUCCESS)

                    val bitmap = image.toBitmap()
                    image.close()
                    capturedImage = bitmap
                    processImage(bitmap)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "❌ Capture failed", exc)
                    hapticFeedback(HapticPattern.ERROR)
                    speakOut("Failed to capture image. Please try again.")
                }
            }
        )
    }

    private fun processImage(bitmap: Bitmap) {
        val base64Image = if (testWithDummyBase64) "dGVzdA==" else bitmapToBase64(bitmap)
        val imageRequest = ImageRequest(base64Image)
        RetrofitClient.apiService.sendImage(imageRequest)
            .enqueue(object : Callback<MedicineResponse> {
                override fun onResponse(call: Call<MedicineResponse>, response: Response<MedicineResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val med = response.body()
                        val resultText = "Medicine: ${med?.medicine_name ?: "Unknown"}. Description: ${med?.description ?: "No description available"}"
                        lastDetectedText = resultText

                        hapticFeedback(HapticPattern.SCAN_SUCCESS)
                        speakOut(resultText)
                    } else {
                        hapticFeedback(HapticPattern.ERROR)
                        speakOut("Could not recognize the medicine.")
                    }
                }
                override fun onFailure(call: Call<MedicineResponse>, t: Throwable) {
                    Log.e("Network", "Connection error", t)
                    hapticFeedback(HapticPattern.ERROR)
                    speakOut("Connection error occurred. Please check your internet.")
                }
            })
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // --- Compose UI ---
    @Composable
    fun ScanPreviewScreen(
        onProfileClick: () -> Unit,
        onGalleryClick: () -> Unit,
        onCaptureImage: (Bitmap) -> Unit,
        onDoubleTap: () -> Unit,
        onRepeat: () -> Unit,
        onCameraReady: () -> Unit
    ) {
        val context = LocalContext.current
        var isCameraReady by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            isCameraReady = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
        ) {
            if (capturedImage == null && isCameraReady) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onCaptureImage = onCaptureImage,
                    onCameraReady = onCameraReady
                )
            } else {
                capturedImage?.let {
                    Image(it.asImageBitmap(), "Captured", Modifier.fillMaxSize())
                }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Button(
                    onClick = { onRepeat() },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(Color.Black)
                ) { Text("Repeat", color = Color.White) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onGalleryClick, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    }
                    IconButton(onClick = onProfileClick, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            }
        }
    }

    @Composable
    fun CameraPreview(
        modifier: Modifier,
        onCaptureImage: (Bitmap) -> Unit,
        onCameraReady: () -> Unit
    ) {
        val context = LocalContext.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        AndroidView(modifier = modifier, factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    Log.d("Camera", "✅ Camera initialized successfully")
                    onCameraReady()

                } catch (e: Exception) {
                    Log.e("Camera", "❌ Camera initialization failed", e)
                    hapticFeedback(HapticPattern.ERROR)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hapticFeedback(HapticPattern.COMMAND_DETECTED)

                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                hapticFeedback(HapticPattern.SCAN_SUCCESS)
                                val bitmap = image.toBitmap()
                                image.close()
                                onCaptureImage(bitmap)
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("Camera", "Manual capture failed", exc)
                                hapticFeedback(HapticPattern.ERROR)
                            }
                        })
                }
                true
            }
            previewView
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}