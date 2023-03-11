package com.example.iguanas

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService

import com.example.iguanas.databinding.ActivityIguanasBinding
import java.util.concurrent.Executors


class Iguanas : AppCompatActivity(), ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "Iguanas"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        init {
            System.loadLibrary("iguanas")
        }
    }

    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var detectorAddr = 0L
    private lateinit var rgbaFrame: ByteArray
    private val labelsMap = arrayListOf<String>()
    private val _paint = Paint()
    private val _paintText = Paint()
    private lateinit var binding: ActivityIguanasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIguanasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Permisos de la camara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Inicio para dibujar las detecciones
        _paint.color = Color.GREEN
        _paint.style = Paint.Style.STROKE
        _paint.strokeWidth = 3f
        _paint.textSize = 50f
        _paint.textAlign = Paint.Align.LEFT

        // Transparencia para la detección
        binding.surfaceView.setZOrderOnTop(true)
        binding.surfaceView.holder.setFormat(PixelFormat.TRANSPARENT)
        loadLabels()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Se utiliza para el ciclo de las cámaras
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val rotation = binding.viewFinder.display.rotation

            // Visualización de la camara
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Configuraciones de la camara como resolución y la rotación
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(768, 1024))
                .setTargetRotation(rotation)
                .setOutputImageRotationEnabled(true)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, this)
                }

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun analyze(image: ImageProxy) {
        if (image.planes.isEmpty()) {return}
        if (detectorAddr == 0L) {
            detectorAddr = initDetector(this.assets)
        }

        val buffer = image.planes[0].buffer
        val size = buffer.capacity()
        if (!::rgbaFrame.isInitialized) {
            rgbaFrame = ByteArray(size)
        }

        buffer.position(0)
        buffer.get(rgbaFrame, 0, size)

        val start = System.currentTimeMillis()
        val res = detect(detectorAddr, rgbaFrame, image.width, image.height)
        val span = System.currentTimeMillis() - start

        val canvas = binding.surfaceView.holder.lockCanvas()
        if (canvas != null) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

            // Se dibuja las detecciones
            for (i in 0 until res[0].toInt()) {
                this.drawDetection(canvas, image.width, image.height, res, i)
            }

            binding.surfaceView.holder.unlockCanvasAndPost(canvas)
        }

        runOnUiThread {
        }

        image.close()
    }

    private fun drawDetection(canvas: Canvas, frameWidth: Int, frameHeight: Int, detectionsArr: FloatArray, detectionIdx: Int) {

        val pos = detectionIdx * 6 + 1
        val score = detectionsArr[pos + 0]
        val classId = detectionsArr[pos + 1]
        var xmin = detectionsArr[pos + 2]
        var ymin = detectionsArr[pos + 3]
        var xmax = detectionsArr[pos + 4]
        var ymax = detectionsArr[pos + 5]

        // Filtro
        if (score < 0.4) return

        // Las coordenadas de las detecciones
        val scaleX = binding.viewFinder.width.toFloat() / frameWidth
        val scaleY = binding.viewFinder.height.toFloat() / frameHeight

        val xoff = 0
        val yoff = 0

        xmin = xoff + xmin * scaleX
        xmax = xoff + xmax * scaleX
        ymin = yoff + ymin * scaleY
        ymax = yoff + ymax * scaleY

        // Se dibuja el rectángulo para las detecciones
        val p = Path()
        p.moveTo(xmin, ymin)
        p.lineTo(xmax, ymin)
        p.lineTo(xmax, ymax)
        p.lineTo(xmin, ymax)
        p.lineTo(xmin, ymin)

        _paintText.color = Color.RED
        _paintText.style = Paint.Style.FILL_AND_STROKE
        _paintText.strokeWidth = 3f
        _paintText.textSize = 50f
        _paintText.textAlign = Paint.Align.LEFT

        canvas.drawPath(p, _paint)

        val label = labelsMap[classId.toInt()]

        // Se dibujan los nombres de cada objeto detectado
        val txt = "%s".format(label)
        canvas.drawText(txt, xmin, ymin, _paintText)
    }

    private fun loadLabels() {
        // Se leen todos los modelos de la red
        val labelsInput = this.assets.open("labels.txt")
        val br = BufferedReader(InputStreamReader(labelsInput))
        var line = br.readLine()
        while (line != null) {
            labelsMap.add(line)
            line = br.readLine()
        }

        br.close()
    }

    private external fun initDetector(assetManager: AssetManager?): Long
    private external fun destroyDetector(ptr: Long)
    private external fun detect(ptr: Long, srcAddr: ByteArray, width: Int, height: Int): FloatArray
}
