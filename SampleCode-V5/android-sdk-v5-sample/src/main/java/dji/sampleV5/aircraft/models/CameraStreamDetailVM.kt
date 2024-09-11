package dji.sampleV5.aircraft.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.Surface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.value.camera.CameraHybridZoomSpec
import dji.sdk.keyvalue.value.camera.CameraType
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.et.set
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager.FrameFormat
import dji.v5.manager.interfaces.ICameraStreamManager.ScaleType
import dji.v5.utils.common.ContextUtil
import dji.v5.utils.common.DiskUtil
import dji.v5.utils.common.LogUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

const val TAG = "CameraStreamDetailFragmentVM"
class CameraStreamDetailVM : DJIViewModel() {

    private val _availableLensListData = MutableLiveData<List<CameraVideoStreamSourceType>>(ArrayList())
    private val _currentLensData = MutableLiveData(CameraVideoStreamSourceType.DEFAULT_CAMERA)
    private val _cameraName = MutableLiveData("Unknown")
    private var cameraIndex = ComponentIndexType.UNKNOWN
    override fun onCleared() {
        super.onCleared()
        setCameraIndex(ComponentIndexType.UNKNOWN)
    }

    fun setCameraIndex(cameraIndex: ComponentIndexType) {
        KeyManager.getInstance().cancelListen(this)
        this.cameraIndex = cameraIndex
        listenCameraType()
        listenAvailableLens()
        listenCurrentLens()
    }

    private fun listenCameraType() {
        _cameraName.postValue("")
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        if (cameraIndex == ComponentIndexType.FPV) {
            _cameraName.postValue(ComponentIndexType.FPV.name)
            return
        }
        CameraKey.KeyCameraType.create(cameraIndex).listen(this) {
            var result = it;
            if (result == null) {
                result = CameraType.NOT_SUPPORTED
            }
            _cameraName.postValue(result.toString())
        }
    }

    private fun listenAvailableLens() {
        _availableLensListData.postValue(arrayListOf())
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        CameraKey.KeyCameraVideoStreamSourceRange.create(cameraIndex).listen(this) {
            val list: List<CameraVideoStreamSourceType> = it ?: arrayListOf()
            _availableLensListData.postValue(list)
        }
    }

    private fun listenCurrentLens() {
        _currentLensData.postValue(CameraVideoStreamSourceType.DEFAULT_CAMERA)
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        CameraKey.KeyCameraVideoStreamSource.create(cameraIndex).listen(this) {
            if (it != null) {
                _currentLensData.postValue(it)
            } else {
                _currentLensData.postValue(CameraVideoStreamSourceType.DEFAULT_CAMERA)
            }
        }
    }


    fun changeCameraLens(lensType: CameraVideoStreamSourceType) {
        CameraKey.KeyCameraVideoStreamSource.create(cameraIndex).set(lensType)
    }

    fun putCameraStreamSurface(
        surface: Surface,
        width: Int,
        height: Int,
        scaleType: ScaleType
    ) {
        MediaDataCenter.getInstance().cameraStreamManager.putCameraStreamSurface(cameraIndex, surface, width, height, scaleType)
    }

    fun removeCameraStreamSurface(surface: Surface) {
        MediaDataCenter.getInstance().cameraStreamManager.removeCameraStreamSurface(surface)
    }


    fun downloadYUVImageToLocal(format: FrameFormat, formatName: String) {
        MediaDataCenter.getInstance().cameraStreamManager.addFrameListener(
            cameraIndex,
            format,
            object : ICameraStreamManager.CameraFrameListener {
                override fun onFrame(frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int, format: FrameFormat) {
                    try {
                        val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "CameraStreamImageDir"))
                        if (!dirs.exists()) {
                            dirs.mkdirs()
                        }
                        val file = File(dirs.absolutePath, "$formatName.image")
//                        Log.d("SPF",frameData.toString())
                        FileOutputStream(file).use { stream ->
                            stream.write(frameData, offset, length)
                            stream.flush()
                            stream.close()
                            ToastUtils.showToast("Save to : ${file.path}+SPF")
                        }
                        CameraKey.KeyVideoResolutionFrameRateRange.create(cameraIndex).listen(this) {
                            var result = it;
                            Log.d("SPF","开始")
                            Log.d("SPF",result.toString())
                        }
                        CameraKey.KeySuperResolutionInfo.create(cameraIndex).listen(this) {
                            var result = it;
                            Log.d("SPF","开始")
                            Log.d("SPF",result.toString())
                        }
                        // 测试查看焦距
                        val cameraZoomSpec = CameraHybridZoomSpec()
                        val maxFocalLength = cameraZoomSpec.maxFocalLength.toDouble()
                        Log.d("SPF","Max Focal Length: $maxFocalLength")

                        // 将frameData转换为RGBA格式的字节数组
                        val rgbaData = byteArrayToRGBAByteArray(frameData, offset, length)
                        // 将RGBA字节数组转换为Bitmap对象
                        val bitmap = byteArrayToBitmap(rgbaData, width, height)

                        // 保存Bitmap为PNG文件
                        val filePath = "${dirs.absolutePath}/image.png"
                        saveBitmapAsPng(bitmap, filePath)

                        // 保存Bitmap为原始格式文件（PNG格式，但解释为原始）
                        val rawBitmapFilePath = "${dirs.absolutePath}/image_raw.png"
                        saveBitmapAsRawBitmap(bitmap, rawBitmapFilePath)


//                        // 打印十六进制表示的frameData
//                        val hexString = byteArrayToHexString(frameData.copyOfRange(offset, offset + length))
//                        Log.d("SPF", hexString)

//                        // 将 ByteArray 转换为 JPEG 文件
//                        byteArrayToJpegFile(frameData, dirs.absolutePath+"/image.jpg")
//
//                        // 将 ByteArray 转换为 Bitmap 对象
//                        val bitmap: Bitmap? = byteArrayToBitmap(frameData)

//                        saveByteArrayAsJpeg(frameData, dirs.absolutePath+"/image.jpg")

                        LogUtils.i(TAG, "Save to : ${file.path}")
                    } catch (e: Exception) {
                        ToastUtils.showToast("save fail$e")
                    }
                    // Because only one frame needs to be saved, you need to call removeOnFrameListener here
                    // If you need to read frame data for a long time, you can choose to actually call remove OnFrameListener according to your needs
                    MediaDataCenter.getInstance().cameraStreamManager.removeFrameListener(this)
                }

            })
    }

    val availableLensListData: LiveData<List<CameraVideoStreamSourceType>>
        get() = _availableLensListData

    val currentLensData: LiveData<CameraVideoStreamSourceType>
        get() = _currentLensData

    val cameraName: LiveData<String>
        get() = _cameraName

    fun byteArrayToJpegFile(byteArray: ByteArray, filePath: String) {
        val file = File(filePath)
        try {
            val fos = FileOutputStream(file)
            fos.write(byteArray)
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    fun saveByteArrayAsJpeg(byteArray: ByteArray, filePath: String) {
        // 解码 ByteArray 为 Bitmap
        val bitmap: Bitmap? = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        // 确认 Bitmap 不为 null
        if (bitmap != null) {
            val file = File(filePath)
            try {
                val fos = FileOutputStream(file)
                // 将 Bitmap 压缩并保存为 JPEG 文件
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.close()
                ToastUtils.showToast("File saved at: ${file.absolutePath}")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            ToastUtils.showToast("Failed to decode ByteArray to Bitmap")
        }
    }

    fun byteArrayToHexString(byteArray: ByteArray): String {
        return byteArray.joinToString(" ") { "%02x".format(it) }
    }

    fun byteArrayToRGBAByteArray(byteArray: ByteArray, offset: Int, length: Int): ByteArray {
        // 提取有效的图像数据
        return byteArray.copyOfRange(offset, offset + length)
    }

    fun byteArrayToBitmap(byteArray: ByteArray, width: Int, height: Int): Bitmap {
        // 创建Bitmap对象，配置为ARGB_8888
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // 将字节数组数据填充到Bitmap对象中
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(byteArray))
        return bitmap
    }

    fun saveBitmapAsPng(bitmap: Bitmap, filePath: String) {
        val file = File(filePath)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // 保存Bitmap为未压缩格式（这里仍然是PNG，仅为对比）
    fun saveBitmapAsRawBitmap(bitmap: Bitmap, filePath: String) {
        FileOutputStream(filePath).use { out ->
            // 使用PNG格式保存，但可以解释为未压缩
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

}