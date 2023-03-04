package com.astroscoding.facedetectiondemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.astroscoding.facedetectiondemo.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

const val TAG = "DEBUG_MATE"

data class Recognition(
    val title: String,
    val extra: FloatArray
)

class MainActivity : AppCompatActivity() {
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    var embeedings: FloatArray? = null
    private val registered = mutableListOf<Recognition>()
    lateinit var binding: ActivityMainBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var interpreter: Interpreter? = null

    private val options = Builder()
        .setPerformanceMode(PERFORMANCE_MODE_FAST)
        .setClassificationMode(CLASSIFICATION_MODE_NONE)
        .setContourMode(CONTOUR_MODE_NONE)
        .build()

    val interpreterOptions = Interpreter.Options().apply {
        setNumThreads(4)
    }

    private val detector = FaceDetection.getClient(options)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        initPermissions()

        binding.switchLensIcon.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                CameraSelector.DEFAULT_BACK_CAMERA
            else
                CameraSelector.DEFAULT_FRONT_CAMERA
            goToCamera()
        }

        binding.addFaceButton.setOnClickListener {
            embeedings?.let { nonNullembeedings ->
                registered.add(Recognition(binding.detectFaceEt.text.toString(), nonNullembeedings))
            }
        }

        val model = FileUtil.loadMappedFile(this@MainActivity, "mobile_face_net.tflite")
        interpreter = Interpreter(model, interpreterOptions)
        interpreter?.allocateTensors()

    }

    private fun initPermissions() {
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { cameraPermissionGranted ->
                if (cameraPermissionGranted)
                    goToCamera()
                else
                    Toast.makeText(this, "nope haven't", Toast.LENGTH_SHORT).show()

            }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        )
            permissionLauncher.launch(Manifest.permission.CAMERA)
        else
            goToCamera()
    }

    override fun onPause() {
        super.onPause()
        permissionLauncher.unregister()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera(
        onSuccess: (TensorImage?, Bitmap?, RectF?) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }


            // Select back camera as a default
            val cameraSelector = cameraSelector

            val executor: Executor = Executors.newSingleThreadExecutor()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        executor,
                        ImageAnalyzer(detector, onSuccess, onFailure)
                    )
                }


            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )


            } catch (exc: Exception) {
                Toast.makeText(this, "Use case binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun goToCamera() = startCamera(
        onSuccess = { tensorImage, bitmap, rect ->
            if (tensorImage == null) {
                binding.currentFaceLabel.text = "No Face detected"
            } else {
                binding.faceOverlay.setFaceRect(rect)
                bitmap?.let {
                    binding.imageView.setImageBitmap(bitmap)
                }
                binding.currentFaceLabel.text = "unknown"
                recognizeImage(tensorImage)
            }
        },
        onFailure = { throwable ->
            Toast.makeText(this, "${throwable.message}", Toast.LENGTH_SHORT).show()
        }
    )

    private fun recognizeImage(tensorImage: TensorImage) {
        // input/output size for the buffer according to mobilefacenet model
        val inputSize = 112
        val outputSize = 128

        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 6)
        outputBuffer.order(ByteOrder.nativeOrder())

        interpreter?.run(tensorImage.buffer, outputBuffer)

        embeedings = FloatArray(outputSize) {
            outputBuffer.getFloat(it * Float.SIZE_BYTES)
        }

        /**
         *  ask chat gbt about:
         * A) if you don't know the IO size of the model
         * B) interpreter.getInput/getOutput-tensors(0) the different with getting the output buffer
         * C) continue discussing the code for further optimization
         */
        if (registered.isNotEmpty()) {
            val neighbours = registered.map {
                val similarity = l2Norm(embeedings!!, it.extra)
                Pair(it.title, similarity)
            }
            val closest = neighbours.minBy { it.second }
            Log.d(TAG, "recognizeImage: neighbour closest: $${closest}")
            Log.d(TAG, "recognizeImage: neighbour $${neighbours}")
            if (closest.second > 1.2) {
                binding.currentFaceLabel.text = "unknown"
            } else
                binding.currentFaceLabel.text = closest.first
        }
    }

    private fun l2Norm(x1: FloatArray, x2: FloatArray): Float {
        var sum = 0.0f
        val mag1 = sqrt(x1.map { xi -> xi.pow(2) }.sum())
        val mag2 = sqrt(x2.map { xi -> xi.pow(2) }.sum())
        for (i in x1.indices) {
            sum += ((x1[i] / mag1) - (x2[i] / mag2)).pow(2)
        }
        return sqrt(sum) * 10 // round the value one unit closer (0.12 -> 1.2)
    }

}
