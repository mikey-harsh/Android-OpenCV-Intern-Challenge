package com.example.edgedetectionapp // Make sure this matches your package name

import android.graphics.SurfaceTexture
import android.content.Context
import android.hardware.Camera
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
private val frameSync = Any()

// --- Bonus: FPS Logging ---
private var lastTime: Long = System.currentTimeMillis()
private var frameCount: Int = 0
class CameraGLRenderer(private val context: Context, private val surfaceView: GLSurfaceView) :
    GLSurfaceView.Renderer, Camera.PreviewCallback {

    private var camera: Camera? = null
    private var cameraWidth: Int = 0
    private var cameraHeight: Int = 0

    private var textureId: Int = 0
    private var shaderProgram: Int = 0

    // Buffers for drawing the full-screen quad
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // A buffer to hold the processed frame bytes from C++
    private var processedFrame: ByteArray? = null
    private var frameDataBuffer: ByteBuffer? = null
    private var isFrameReady = false
    private val frameSync = Any()

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f
    )

    // Texture coordinates are flipped vertically (Y=0 is top in OpenGL)
    private val texCoords = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )

    // NEW Shaders: These now use a standard 'sampler2D'
    // instead of the 'samplerExternalOES'
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 fTexCoord;
        void main() {
            gl_Position = vPosition;
            fTexCoord = vTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 fTexCoord;
        uniform sampler2D sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, fTexCoord);
        }
    """.trimIndent()


    init {
        // Init vertex buffer
        var bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        // Init texture coordinates buffer
        bb = ByteBuffer.allocateDirect(texCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        texCoordBuffer = bb.asFloatBuffer()
        texCoordBuffer.put(texCoords)
        texCoordBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Create standard 2D texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Create shader program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Start camera
        startCamera()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        synchronized(frameSync) {
            if (isFrameReady && processedFrame != null) {
                // Lazily allocate buffer
                if (frameDataBuffer == null || frameDataBuffer!!.capacity() != processedFrame!!.size) {
                    frameDataBuffer = ByteBuffer.allocateDirect(processedFrame!!.size)
                }

                // Copy processed data to buffer and upload to texture
                frameDataBuffer!!.clear()
                frameDataBuffer!!.put(processedFrame!!)
                frameDataBuffer!!.position(0)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    cameraWidth, cameraHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frameDataBuffer
                )
                isFrameReady = false
            }
        }

        // Draw the texture
        if (processedFrame != null) {
            GLES20.glUseProgram(shaderProgram)

            val vPositionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
            GLES20.glEnableVertexAttribArray(vPositionHandle)
            GLES20.glVertexAttribPointer(vPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            val vTexCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoord")
            GLES20.glEnableVertexAttribArray(vTexCoordHandle)
            GLES20.glVertexAttribPointer(vTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(vPositionHandle)
            GLES20.glDisableVertexAttribArray(vTexCoordHandle)
        }
    }

    // This is called for every camera frame
    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        // Process the frame in C++
        // Note: This is a blocking call on the UI thread.
        // For a real app, this should be on a background thread.
        // For this assignment, this is fine.
        val processed = MainActivity.processFrame(cameraWidth, cameraHeight, data)

        // Store the result
        synchronized(frameSync) {
            processedFrame = processed
            isFrameReady = true
        }

        // Request a render to draw the new frame
        surfaceView.requestRender()

        // Re-queue the buffer
        camera.addCallbackBuffer(data)

        camera.addCallbackBuffer(data)

        // --- Bonus: Log FPS ---
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) { // Log every 1 second
            android.util.Log.d("EdgeDetectionApp", "FPS: $frameCount")
            frameCount = 0
            lastTime = currentTime
        }
    }

    private fun startCamera() {
        try {
            camera = Camera.open(0) // Open back camera
            val params = camera!!.parameters

            // Find a good preview size
            val previewSize = params.supportedPreviewSizes[0]
            params.setPreviewSize(previewSize.width, previewSize.height)
            cameraWidth = previewSize.width
            cameraHeight = previewSize.height

            // Set YUV format
            params.previewFormat = android.graphics.ImageFormat.NV21
            camera!!.parameters = params

            // Set up buffer for onPreviewFrame
            val previewFormat = params.previewFormat
            val bitsPerPixel = android.graphics.ImageFormat.getBitsPerPixel(previewFormat)
            val bufferSize = (cameraWidth * cameraHeight * bitsPerPixel) / 8
            camera!!.addCallbackBuffer(ByteArray(bufferSize))
            camera!!.addCallbackBuffer(ByteArray(bufferSize))
            camera!!.setPreviewCallbackWithBuffer(this)

            // We must set a dummy texture, even though we don't use it
            // This is a quirk of the Camera1 API
            val dummyTexture = SurfaceTexture(10)
            camera!!.setPreviewTexture(dummyTexture)
            camera!!.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPause() {
        camera?.stopPreview()
        camera?.setPreviewCallbackWithBuffer(null)
        camera?.release()
        camera = null
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}