package dji.sampleV5.aircraft.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.data.DJIToastResult
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.KeyTools.createKey

import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.error.RxError
import dji.v5.common.utils.CallbackUtils
import dji.v5.common.utils.RxUtil
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.media.*
import dji.v5.utils.common.LogUtils
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.value.camera.CameraStorageLocation
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.v5.utils.common.ContextUtil
import dji.v5.utils.common.DiskUtil
import dji.v5.utils.common.StringUtils
import io.reactivex.rxjava3.annotations.Nullable
import kotlinx.android.synthetic.main.layout_media_play_download_progress.progressBar
import kotlinx.android.synthetic.main.layout_media_play_download_progress.progressInfo
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * @author feel.feng
 * @time 2022/04/20 2:19 下午
 * @description: 媒体回放下载数据
 */
class MediaVM : DJIViewModel() {
    var mediaFileListData = MutableLiveData<MediaFileListData>()
    var fileListState = MutableLiveData<MediaFileListState>()
    var isPlayBack = MutableLiveData<Boolean?>()
    fun init() {
        addMediaFileListStateListener()
        mediaFileListData.value = MediaDataCenter.getInstance().mediaManager.mediaFileListData
        MediaDataCenter.getInstance().mediaManager.addMediaFileListStateListener { mediaFileListState ->
            if (mediaFileListState == MediaFileListState.UP_TO_DATE) {
                val data = MediaDataCenter.getInstance().mediaManager.mediaFileListData;
                mediaFileListData.postValue(data)
            }
        }

    }

    fun destroy() {
        KeyManager.getInstance().cancelListen(this);
        removeAllFileListStateListener()

        MediaDataCenter.getInstance().mediaManager.release()
    }

    fun pullMediaFileListFromCamera(mediaFileIndex: Int, count: Int) {
        var currentTime = System.currentTimeMillis()
        MediaDataCenter.getInstance().mediaManager.pullMediaFileListFromCamera(
            PullMediaFileListParam.Builder().mediaFileIndex(mediaFileIndex).count(count).build(),
            object :
                CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("Spend time:${(System.currentTimeMillis() - currentTime) / 1000}s")
                    LogUtils.i(logTag, "fetch success")
                }

                override fun onFailure(error: IDJIError) {
                    LogUtils.e(logTag, "fetch failed$error")
                }
            })
    }

    private fun addMediaFileListStateListener() {
        MediaDataCenter.getInstance().mediaManager.addMediaFileListStateListener(object :
            MediaFileListStateListener {
            override fun onUpdate(mediaFileListState: MediaFileListState) {
                fileListState.postValue(mediaFileListState)
            }

        })
    }

    private fun removeAllFileListStateListener() {
        MediaDataCenter.getInstance().mediaManager.removeAllMediaFileListStateListener()
    }

    fun getMediaFileList(): List<MediaFile> {
        return mediaFileListData.value?.data!!
    }

    fun setMediaFileXMPCustomInfo(info: String) {
        MediaDataCenter.getInstance().mediaManager.setMediaFileXMPCustomInfo(info, object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                toastResult?.postValue(DJIToastResult.success())
            }

            override fun onFailure(error: IDJIError) {
                toastResult?.postValue(DJIToastResult.failed(error.toString()))
            }
        })
    }

    fun getMediaFileXMPCustomInfo() {
        MediaDataCenter.getInstance().mediaManager.getMediaFileXMPCustomInfo(object :
            CommonCallbacks.CompletionCallbackWithParam<String> {
            override fun onSuccess(s: String) {
                toastResult?.postValue(DJIToastResult.success(s))
            }

            override fun onFailure(error: IDJIError) {
                toastResult?.postValue(DJIToastResult.failed(error.toString()))
            }
        })
    }

    fun setComponentIndex(index: ComponentIndexType) {
        isPlayBack.postValue(false)
        KeyManager.getInstance().cancelListen(this)
        KeyManager.getInstance().listen(
            KeyTools.createKey(
                CameraKey.KeyIsPlayingBack, index
            ), this
        ) { _, newValue ->
            isPlayBack.postValue(newValue)
        }
        val mediaSource = MediaFileListDataSource.Builder().setIndexType(index).build()
        MediaDataCenter.getInstance().mediaManager.setMediaFileDataSource(mediaSource)
    }

    fun setStorage(location: CameraStorageLocation) {
        val mediaSource = MediaFileListDataSource.Builder().setLocation(location).build()
        MediaDataCenter.getInstance().mediaManager.setMediaFileDataSource(mediaSource)
    }

    fun enable() {
        MediaDataCenter.getInstance().mediaManager.enable(object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                LogUtils.e(logTag, "enable playback success")
            }

            override fun onFailure(error: IDJIError) {
                LogUtils.e(logTag, "error is ${error.description()}")
            }
        })
    }

    fun disable() {
        MediaDataCenter.getInstance().mediaManager.disable(object :
            CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                LogUtils.e(logTag, "exit playback success")
            }

            override fun onFailure(error: IDJIError) {
                LogUtils.e(logTag, "error is ${error.description()}")
            }
        })
    }

    fun takePhoto(callback: CommonCallbacks.CompletionCallback) {
        // 设置相机模式
        RxUtil.setValue(createKey<CameraMode>(
            CameraKey.KeyCameraMode), CameraMode.PHOTO_NORMAL)
            // 执行拍照步骤
            .andThen(RxUtil.performActionWithOutResult(createKey(CameraKey.KeyStartShootPhoto)))
            // 执行结果
            .subscribe({ CallbackUtils.onSuccess(callback) }
            ) { throwable: Throwable ->
                CallbackUtils.onFailure(
                    callback,
                    (throwable as RxError).djiError
                )
            }
    }

    fun formatSDCard(callback: CommonCallbacks.CompletionCallback) {
        KeyManager.getInstance().performAction(KeyTools.createKey(CameraKey.KeyFormatStorage),CameraStorageLocation.SDCARD  , object :CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>{
            override fun onSuccess(t: EmptyMsg?) {
               callback.onSuccess()
            }

            override fun onFailure(error: IDJIError) {
                callback.onFailure(error)
            }

        })
    }

    fun  downloadMediaFile(mediaList : ArrayList<MediaFile>){
        mediaList.forEach {
            downloadFile(it)
        }
    }

//    fun  downloadMediaFileFixedPath(mediaList : ArrayList<MediaFile>) : String? {
//        var bitmap: String? = null
//        mediaList.forEach {
//            bitmap = downloadFileFixedPath(it)
//        }
//        return bitmap
//    }

    private fun downloadFile(mediaFile :MediaFile ) {
        val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile"))
        if (!dirs.exists()) {
            dirs.mkdirs()
        }
        val filepath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile/"  + mediaFile?.fileName)
        val file = File(filepath)
        var offset = 0L
        val outputStream = FileOutputStream(file, true)
        val bos = BufferedOutputStream(outputStream)
        mediaFile?.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
            override fun onStart() {
                LogUtils.i("MediaFile" , "${mediaFile.fileIndex } start download"  )
            }

            override fun onProgress(total: Long, current: Long) {
                val fullSize = offset + total;
                val downloadedSize = offset + current
                val data: Double = StringUtils.formatDouble((downloadedSize.toDouble() / fullSize.toDouble()))
                val result: String = StringUtils.formatDouble(data * 100, "#0").toString() + "%"
                LogUtils.i("MediaFile"  , "${mediaFile.fileIndex}  progress $result")
            }

            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
                try {
                    bos.write(data)
                    bos.flush()
                } catch (e: IOException) {
                    LogUtils.e("MediaFile", "write error" + e.message)
                }
            }

            override fun onFinish() {
                try {
                    outputStream.close()
                    bos.close()
                } catch (error: IOException) {
                    LogUtils.e("MediaFile", "close error$error")
                }
                LogUtils.i("MediaFile" , "${mediaFile.fileIndex }  download finish"  )
            }

            override fun onFailure(error: IDJIError?) {
                LogUtils.e("MediaFile", "download error$error")
            }

        })
    }

//    private fun downloadFileFixedPath(mediaFile :MediaFile ) : String?{
//        val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile"))
//        if (!dirs.exists()) {
//            dirs.mkdirs()
//        }
//        val filepath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(),  "/mediafile/"  +"DroneFlyDownLoad.jpg")
//        val file = File(filepath)
//        var offset = 0L
//        val outputStream = FileOutputStream(file, true)
//        val bos = BufferedOutputStream(outputStream)
//        mediaFile?.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
//            override fun onStart() {
//                LogUtils.i("MediaFile" , "${mediaFile.fileIndex } start download"  )
//            }
//
//            override fun onProgress(total: Long, current: Long) {
//                val fullSize = offset + total;
//                val downloadedSize = offset + current
//                val data: Double = StringUtils.formatDouble((downloadedSize.toDouble() / fullSize.toDouble()))
//                val result: String = StringUtils.formatDouble(data * 100, "#0").toString() + "%"
//                LogUtils.i("MediaFile"  , "${mediaFile.fileIndex}  progress $result")
//            }
//
//            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
//                try {
//                    bos.write(data)
//                    bos.flush()
//                } catch (e: IOException) {
//                    LogUtils.e("MediaFile", "write error" + e.message)
//                }
//            }
//
//            override fun onFinish() {
//                try {
//                    outputStream.close()
//                    bos.close()
//                } catch (error: IOException) {
//                    LogUtils.e("MediaFile", "close error$error")
//                }
//                LogUtils.i("MediaFile" , "${mediaFile.fileIndex }  download finish"  )
//            }
//
//            override fun onFailure(error: IDJIError?) {
//                LogUtils.e("MediaFile", "download error$error")
//            }
//
//        })
//        return filepath
//    }

    // onDownloadComplete 回调
//    fun downloadMediaFileFixedPath(mediaList: ArrayList<MediaFile>, onDownloadComplete: (String?) -> Unit) {
//        if (mediaList.isEmpty()) {
//            onDownloadComplete(null) // 如果列表为空，直接返回
//            return
//        }
//
//        // 获取列表中的第一个 MediaFile
//        val mediaFile = mediaList[0]
//
//        // 下载该 MediaFile
//        downloadFileFixedPath(mediaFile) { filepath ->
//            if (filepath != null) {
//                LogUtils.i("Download", "File downloaded successfully to $filepath")
//                onDownloadComplete(filepath) // 下载成功，返回文件路径
//            } else {
//                LogUtils.e("Download", "File download failed for ${mediaFile.fileIndex}")
//                onDownloadComplete(null) // 下载失败，返回 null
//            }
//        }
//    }
//
//    fun downloadFileFixedPath(mediaFile: MediaFile, onDownloadComplete: (String?) -> Unit) {
//        val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile"))
//        if (!dirs.exists()) {
//            dirs.mkdirs()
//        }
//        val filepath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile/" + "DroneFlyDownLoad.jpg")
//        val file = File(filepath)
//        var offset = 0L
//        val outputStream = FileOutputStream(file, true)
//        val bos = BufferedOutputStream(outputStream)
//
//        mediaFile?.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
//            override fun onStart() {
//                LogUtils.i("MediaFile", "${mediaFile.fileIndex} start download")
//            }
//
//            override fun onProgress(total: Long, current: Long) {
//                val fullSize = offset + total
//                val downloadedSize = offset + current
//                val data: Double = StringUtils.formatDouble((downloadedSize.toDouble() / fullSize.toDouble()))
//                val result: String = StringUtils.formatDouble(data * 100, "#0").toString() + "%"
//                LogUtils.i("MediaFile", "${mediaFile.fileIndex} progress $result")
//            }
//
//            override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
//                try {
//                    bos.write(data)
//                    bos.flush()
//                } catch (e: IOException) {
//                    LogUtils.e("MediaFile", "write error" + e.message)
//                }
//            }
//
//            override fun onFinish() {
//                try {
//                    outputStream.close()
//                    bos.close()
//                } catch (error: IOException) {
//                    LogUtils.e("MediaFile", "close error$error")
//                }
//                LogUtils.i("MediaFile", "${mediaFile.fileIndex} download finish")
//                onDownloadComplete(filepath)  // 下载完成后，通过回调返回文件路径
//            }
//
//            override fun onFailure(error: IDJIError?) {
//                LogUtils.e("MediaFile", "download error$error")
//                onDownloadComplete(null)  // 失败时返回 null
//            }
//        })
//    }
//
//    fun onDownloadComplete(filePath: String?) {
//        if (filePath != null) {
//            // 下载成功，执行相关操作
//            LogUtils.i("Download", "File downloaded successfully at: $filePath")
//
//            // 在此处可以加载 Bitmap 或进行其他处理
//            val bitmap = BitmapFactory.decodeFile(filePath)
//            // 进行显示等操作，例如更新 UI
//            // imageView.setImageBitmap(bitmap)
//        } else {
//            // 下载失败，执行相关操作
//            LogUtils.e("Download", "File download failed")
//            // 可以显示错误信息给用户，或进行其他处理
//        }
//    }

    // suspend 函数和 suspendCoroutine
//    suspend fun downloadMediaFileFixedPath(mediaList: ArrayList<MediaFile>): String? {
//        return suspendCoroutine { continuation ->
//            if (mediaList.isEmpty()) {
//                continuation.resume(null) // 如果列表为空，直接返回
//                return@suspendCoroutine
//            }
//
//            // 获取列表中的第一个 MediaFile
//            val mediaFile = mediaList[0]
//
//            // 使用协程启动 downloadFileFixedPath，并等待其完成
//            kotlinx.coroutines.GlobalScope.launch {
//                val filepath = downloadFileFixedPath(mediaFile)
//
//                if (filepath != null) {
//                    // 照片下载完成后的操作
//                    continuation.resume(filepath)
//                } else {
//                    // 处理下载失败的情况
//                    continuation.resume(null)
//                }
//            }
//        }
//    }
//
//    suspend fun downloadFileFixedPath(mediaFile: MediaFile): String? {
//        return suspendCoroutine { continuation ->
//            val dirs =
//                File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile"))
//            if (!dirs.exists()) {
//                dirs.mkdirs()
//            }
//            val filepath = DiskUtil.getExternalCacheDirPath(
//                ContextUtil.getContext(),
//                "/mediafile/DroneFlyDownLoad.jpg"
//            )
//            val file = File(filepath)
//            val outputStream = FileOutputStream(file, true)
//            val bos = BufferedOutputStream(outputStream)
//            var offset = 0L
//
//            mediaFile.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
//                override fun onStart() {
//                    LogUtils.i("MediaFile", "${mediaFile.fileIndex} start download")
//                }
//
//                override fun onProgress(total: Long, current: Long) {
//                    val fullSize = offset + total
//                    val downloadedSize = offset + current
//                    val data: Double =
//                        StringUtils.formatDouble((downloadedSize.toDouble() / fullSize.toDouble()))
//                    val result: String = StringUtils.formatDouble(data * 100, "#0") + "%"
//                    LogUtils.i("MediaFile", "${mediaFile.fileIndex} progress $result")
//                }
//
//                override fun onRealtimeDataUpdate(data: ByteArray, position: Long) {
//                    try {
//                        bos.write(data)
//                        bos.flush()
//                    } catch (e: IOException) {
//                        LogUtils.e("MediaFile", "write error" + e.message)
//                    }
//                }
//
//                override fun onFinish() {
//                    try {
//                        bos.close()
//                        outputStream.close()
//                    } catch (error: IOException) {
//                        LogUtils.e("MediaFile", "close error$error")
//                    }
//                    LogUtils.i("MediaFile", "${mediaFile.fileIndex} download finish")
//                    continuation.resume(filepath)  // 下载完成后恢复协程并返回文件路径
//                }
//
//                override fun onFailure(error: IDJIError?) {
//                    LogUtils.e("MediaFile", "download error$error")
//                    continuation.resume(null)  // 下载失败，返回 null
//                }
//            })
//        }
//
//    }

}