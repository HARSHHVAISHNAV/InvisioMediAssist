package com.muggles.invisioassist
//code is here
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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

class ScanPreviewActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var capturedImage by mutableStateOf<Bitmap?>(null)
    private lateinit var textToSpeech: TextToSpeech
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastDetectedText by mutableStateOf("")

    // Toggle for test mode
    private val testWithDummyBase64 = false

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
            capturedImage = bitmap
            processImage(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.US
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }

        setContent {
            ScanPreviewScreen(
                onProfileClick = { startActivity(Intent(this, ProfileActivity::class.java)) },
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onCaptureImage = { bitmap ->
                    capturedImage = bitmap
                    processImage(bitmap)
                },
                onDoubleTap = { stopTTSAndReset() },
                onRepeat = { repeatText() }
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun repeatText() {
        if (lastDetectedText.isNotEmpty()) {
            speakOut(lastDetectedText)
        }
    }

    private fun stopTTSAndReset() {
        textToSpeech.stop()
        capturedImage = null
        lastDetectedText = ""

        runOnUiThread {
            cameraProvider?.unbindAll()
            setContent {
                ScanPreviewScreen(
                    onProfileClick = { startActivity(Intent(this, ProfileActivity::class.java)) },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onCaptureImage = { bitmap ->
                        capturedImage = bitmap
                        processImage(bitmap)
                    },
                    onDoubleTap = { stopTTSAndReset() },
                    onRepeat = { repeatText() }
                )
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val base64Image = if (testWithDummyBase64) {
            Log.d("Debug", "Using dummy base64 for testing.")
            "dGVzdA=="
        } else {
            bitmapToBase64(bitmap)
        }

        val imageRequest = ImageRequest(base64Image)

        Log.d("Debug", "Sending image to backend...")

        RetrofitClient.apiService.sendImage(imageRequest)
            .enqueue(object : Callback<MedicineResponse> {
                override fun onResponse(call: Call<MedicineResponse>, response: Response<MedicineResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val medicine = response.body()
                        val resultText = "Medicine: ${medicine?.medicine_name ?: "Unknown"}\nDescription: ${medicine?.description ?: "No description available"}"
                        lastDetectedText = resultText
                        Log.d("Retrofit", "Response: $resultText")
                        speakOut(resultText)
                    } else {
                        Log.e("Retrofit", "Server error: ${response.code()}")
                        speakOut("Failed to recognize the medicine.")
                    }
                }

                override fun onFailure(call: Call<MedicineResponse>, t: Throwable) {
                    Log.e("Retrofit", "Failed to connect: ${t.message}")
                    speakOut("Connection error occurred.")
                }
            })
    }

    @Composable
    fun ScanPreviewScreen(
        onProfileClick: () -> Unit,
        onGalleryClick: () -> Unit,
        onCaptureImage: (Bitmap) -> Unit,
        onDoubleTap: () -> Unit,
        onRepeat: () -> Unit
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() }
                    )
                }
        ) {
            if (capturedImage == null && isCameraReady) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onCaptureImage = onCaptureImage
                )
            } else {
                capturedImage?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured Image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Button(
                    onClick = { onRepeat() },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(Color.Black)
                ) {
                    Text("Repeat", color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onGalleryClick, modifier = Modifier.size(64.dp)) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    }
                    IconButton(onClick = onProfileClick, modifier = Modifier.size(64.dp)) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            }
        }
    }

    @Composable
    fun CameraPreview(modifier: Modifier, onCaptureImage: (Bitmap) -> Unit) {
        val context = LocalContext.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))

                previewView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.toBitmap()
                                    onCaptureImage(bitmap)
                                    image.close()
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    Log.e("Camera", "Image capture failed", exc)
                                }
                            }
                        )
                    }
                    true
                }

                previewView
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}
