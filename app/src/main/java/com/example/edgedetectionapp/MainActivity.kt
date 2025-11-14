package com.example.edgedetectionapp // Make sure this matches your package name

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraRenderer: CameraGLRenderer
    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.gl_surface_view)
        // --- Bonus: Toggle Button ---
        val toggleButton: android.widget.Button = findViewById(R.id.toggle_button)
        var isEdgeMode = true
        toggleButton.setOnClickListener {
            isEdgeMode = !isEdgeMode
            if (isEdgeMode) {
                setProcessingMode(1) // 1 = Canny
                toggleButton.text = "Show Raw"
            } else {
                setProcessingMode(0) // 0 = Raw
                toggleButton.text = "Show Edges"
            }
        }

        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED) {
            // Request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            // Permission already granted
            setupGLSurfaceView()
        }
    }

    private fun setupGLSurfaceView() {
        glSurfaceView.setEGLContextClientVersion(2)
        cameraRenderer = CameraGLRenderer(this, glSurfaceView)
        glSurfaceView.setRenderer(cameraRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                setupGLSurfaceView()
            } else {
                // Permission denied
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.onPause()
            if(::cameraRenderer.isInitialized) {
                cameraRenderer.onPause()
            }
        }
    }

    // We still need to load our native library
    companion object {
        @JvmStatic
        external fun processFrame(width: Int, height: Int, yuvData: ByteArray): ByteArray

        @JvmStatic
        external fun setProcessingMode(mode: Int)

        init {
            System.loadLibrary("native-lib")
        }
    }
}