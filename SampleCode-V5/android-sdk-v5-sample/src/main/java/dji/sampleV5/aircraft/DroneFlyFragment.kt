//package dji.sampleV5.aircraft
//
//import android.R
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.activityViewModels
//import dji.sampleV5.aircraft.models.MediaVM
//import dji.sampleV5.aircraft.pages.DJIFragment
//import dji.sampleV5.aircraft.util.ToastUtils
//import dji.v5.common.callback.CommonCallbacks
//import dji.v5.common.error.IDJIError
//import dji.v5.manager.datacenter.media.MediaFileDownloadListener
//import dji.v5.utils.common.ContextUtil
//import dji.v5.utils.common.DiskUtil
//import dji.v5.utils.common.LogUtils
//import kotlinx.android.synthetic.main.activity_drone_fly.btn_take_photo
//import java.io.BufferedOutputStream
//import java.io.File
//import java.io.FileOutputStream
//import java.io.IOException
//
//
//class DroneFlyFragment : DJIFragment() {
//    private val mediaVM: MediaVM by activityViewModels()
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        return inflater.inflate(dji.sampleV5.aircraft.R.layout.frag_drone_fly, container, false);
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        initView()
//    }
//
//    private fun initView() {
//
//        btn_take_photo.setOnClickListener {
//            mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
//                override fun onSuccess() {
//                    ToastUtils.showToast("take photo success")
//                }
//
//                override fun onFailure(error: IDJIError) {
//                    ToastUtils.showToast("take photo failed")
//                }
//            })
//        }
//    }
//
//    private fun downloadFile() {
//        val dirs: File = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), mediaFileDir))
//        if (!dirs.exists()) {
//            dirs.mkdirs()
//        }
//
//        val filepath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), mediaFileDir + "/" + mediaFile?.fileName)
//        val file: File = File(filepath)
//        var offset: Long = 0L
//        if (file.exists()) {
//            offset = file.length();
//        }
//        val outputStream = FileOutputStream(file, true)
//        val bos = BufferedOutputStream(outputStream)
//        var beginTime = System.currentTimeMillis()
//        // 从摄像头中下载文件，并实现一个匿名 MediaFileDownloadListener，用于处理下载过程的各种事件
//        mediaFile?.pullOriginalMediaFileFromCamera(offset, object : MediaFileDownloadListener {
//            override fun onStart() {
//                showProgress()
//            }
//
//            override fun onProgress(total: Long, current: Long) {
//                updateProgress(offset, current, total)
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
//
//                var spendTime = (System.currentTimeMillis() - beginTime)
//                var speedBytePerMill: Float? = mediaFile?.fileSize?.div(spendTime.toFloat())
//                var divs = 1000.div(1024 * 1024.toFloat());
//                var speedKbPerSecond: Float? = speedBytePerMill?.times(divs)
//
//                if (mediaFile!!.fileSize <= offset) {
//                    ToastUtils.showToast(getString(R.string.already_download))
//                } else {
//                    ToastUtils.showToast(
//                        getString(R.string.msg_download_compelete_tips) + "${speedKbPerSecond}Mbps"
//                                + getString(R.string.msg_download_save_tips) + "${filepath}"
//                    )
//                }
//                hideProgress()
//                try {
//                    outputStream.close()
//                    bos.close()
//                } catch (error: IOException) {
//                    LogUtils.e("MediaFile", "close error$error")
//                }
//            }
//
//            override fun onFailure(error: IDJIError?) {
//                LogUtils.e("MediaFile", "download error$error")
//            }
//
//        })
//    }
//}