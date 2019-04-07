package no.alacho.basicprojectforcamera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import kotlinx.android.synthetic.main.somefragment.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.*

class BasicFragment : Fragment(){

    private val MAX_PREVIEW_WIDTH = 700
    private val MAX_PREVIEW_HEIGHT = 700
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var galleryFolder: File

    private lateinit var cameraDevice: CameraDevice
    private val deviceStateCallback = object : CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice?) {
            Log.d("OnOpened", "Camera device opened successful")
            if(camera != null){
                cameraDevice = camera
                previewSession()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d("onDisconnected", "Camera device disconnected")
            camera?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d("onError", "Camera Device $error")
            this@BasicFragment.activity!!.finish()
        }

    }
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val cameraManager by lazy {
        activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun previewSession(){
        val surfaceTexture = previewTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
        val surface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(Arrays.asList(surface), object: CameraCaptureSession.StateCallback(){
            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("onConfigureFailed", "Creating capture session failed")
            }

            override fun onConfigured(session: CameraCaptureSession?) {
                if(session != null){
                    captureSession = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                }
            }

        }, null)
    }

    private fun closeCamera(){
        if(this::captureSession.isInitialized){
            captureSession.close()
        }
        if(this::cameraDevice.isInitialized){
            cameraDevice.close()
        }
    }

    private fun startBackgroundThread(){
        backgroundThread = HandlerThread("Camera Thread Handler").also {  it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread(){
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e("Exception", e.toString())
        }
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when(key){
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)
            else -> {
                throw IllegalArgumentException("Key is not recognized")
            }
        }
    }

    private fun cameraId(lens: Int) : String {
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            deviceId = cameraIdList.filter { lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING) }
        } catch (ex: CameraAccessException){
            Log.e("Exception Thrown", "Camera Access was denied, $ex")
        }
        return deviceId[0]
    }

    private fun connectCamera(){
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        Log.d("Device ID", "Device ID: $deviceId")
        try {
            //We already check permission other places.
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("Exception", e.toString())
        } catch (e: InterruptedException) {
            Log.d("Exception", "Open camera device interrupted while opened")
        }
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 100
        const val REQUEST_WRITING_PERMISSION = 101
        const val REQUEST_READING_PERMISSION = 102
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.somefragment, container, false)
    }

    private val textureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit



        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = true


        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d("Inside Surface Available", "$width $height")
            openCamera()
        }

    }

    private fun openCamera() {
        Log.d("Hallo", "I'm in camera open")
        checkCameraPermission()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        captureButton.setOnClickListener{
            createImageGallery()
            onTakeButtonClicked()
        }
    }


    override fun onResume() {
        super.onResume()

        startBackgroundThread()

        if(previewTextureView.isAvailable){
            openCamera()
            Log.d("Camera was opened", "It opened itself by default")
        } else {
            Log.d("It wasn't ready, so we hand it over to somwhere", "Helo")
            previewTextureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    private fun checkCameraPermission(){
        if (EasyPermissions.hasPermissions(activity!!, Manifest.permission.CAMERA)){
            Log.d("CameraPermission", "App has permission for camera")
            connectCamera()
        } else {
            EasyPermissions.requestPermissions(activity!!,
                getString(R.string.camera_request_rational),
                REQUEST_CAMERA_PERMISSION,
                Manifest.permission.CAMERA)
        }
        if(EasyPermissions.hasPermissions(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            Log.d("WritingPermission","App has permission for writing to storage")
        } else {
            EasyPermissions.requestPermissions(activity!!,
                getString(R.string.writing_request_rational),
                REQUEST_WRITING_PERMISSION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(EasyPermissions.hasPermissions(activity!!, Manifest.permission.READ_EXTERNAL_STORAGE)){
            Log.d("ReadingPermission", "App has permission for reading from storage")
        } else {
            EasyPermissions.requestPermissions(activity!!,
                getString(R.string.reading_request_rational),
                REQUEST_READING_PERMISSION,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun createImageGallery(){
        val storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        galleryFolder = File(storageDirectory, resources.getString(R.string.app_name))
        if(!galleryFolder.exists()){
            val wasCreated: Boolean = galleryFolder.mkdirs()
            if (!wasCreated){
                Log.e("CapturedImages", "Failed to create the desired directory")
            }
        }
    }

    private fun createImageFile(galleryFolder: File): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "image_" + timeStamp + "_"
        return File.createTempFile(imageFileName, ".png", galleryFolder)
    }

    private fun onTakeButtonClicked(){
        var outputPhoto: FileOutputStream? = null
        try{
            outputPhoto = FileOutputStream(createImageFile(galleryFolder))
            previewTextureView.bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputPhoto)
            Log.d("onTakeButtonClicked", "Image was created")
        } catch (ex: Exception){
            Log.d("Logging", ex.toString())
        } finally {
        try {
            outputPhoto?.close()
        } catch (ex: IOException) {
            Log.d("Exception", ex.toString())
        }
    }
    }
}