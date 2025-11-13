package com.example.edgedetectionapp // Make sure this matches your package name

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGLRenderer(private val context: Context, private val surfaceView: GLSurfaceView) :
    GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private lateinit var surfaceTexture: SurfaceTexture
    private var textureId: Int = 0
    private var camera: Camera? = null

    private val vPositionHandle: Int = 0
    private val vTexCoordHandle: Int = 0
    private var shaderProgram: Int = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )

    // Simple shaders to draw the texture
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
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 fTexCoord;
        uniform samplerExternalOES sTexture;
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
        // Create texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Create SurfaceTexture
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(this)

        // Start camera
        startCamera()

        // Create shader program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Update texture
        surfaceTexture.updateTexImage()

        // Draw
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(shaderProgram)

        val vPositionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttribPointer(vPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val vTexCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoord")
        GLES20.glEnableVertexAttribArray(vTexCoordHandle)
        GLES20.glVertexAttribPointer(vTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(vPositionHandle)
        GLES20.glDisableVertexAttribArray(vTexCoordHandle)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        // Request a render
        surfaceView.requestRender()
    }

    private fun startCamera() {
        try {
            camera = Camera.open(0) // Open back camera
            camera?.setPreviewTexture(surfaceTexture)
            camera?.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPause() {
        camera?.stopPreview()
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