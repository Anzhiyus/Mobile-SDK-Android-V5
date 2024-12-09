package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraStreamDetailVM
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.interfaces.ICameraStreamManager
import kotlinx.android.synthetic.main.fragment_camera_stream_detail.sv_camera

class SPFCameraStreamDetailFragment : Fragment() {

    companion object {
        private const val KEY_CAMERA_INDEX = "cameraIndex"
        private const val KEY_ONLY_ONE_CAMERA = "onlyOneCamera"
        private val SUPPORT_YUV_FORMAT = mapOf(
            "YUV420（i420）" to ICameraStreamManager.FrameFormat.YUV420_888,
            "YUV444（i444）" to ICameraStreamManager.FrameFormat.YUV444_888,
            "NV21" to ICameraStreamManager.FrameFormat.NV21,
            "YUY2" to ICameraStreamManager.FrameFormat.YUY2,
            "RGBA" to ICameraStreamManager.FrameFormat.RGBA_8888
        )

        fun newInstance(cameraIndex: ComponentIndexType, onlyOneCamera: Boolean): SPFCameraStreamDetailFragment {
            val args = Bundle()
            args.putInt(KEY_CAMERA_INDEX, cameraIndex.value())
            args.putBoolean(KEY_ONLY_ONE_CAMERA, onlyOneCamera)
            val fragment = SPFCameraStreamDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private val viewModel: CameraStreamDetailVM by viewModels()

    private lateinit var cameraSurfaceView: SurfaceView
    private lateinit var cameraIndex: ComponentIndexType
    private var onlyOneCamera = false
    private var surface: Surface? = null
    private var width = -1
    private var height = -1
    private var scaleType = ICameraStreamManager.ScaleType.CENTER_INSIDE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraIndex = ComponentIndexType.find(arguments?.getInt(KEY_CAMERA_INDEX, 0) ?: 0)
        onlyOneCamera = arguments?.getBoolean(KEY_ONLY_ONE_CAMERA, false) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.spf_fragment_camera_stream_detail_single, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraSurfaceView = view.findViewById(R.id.sv_camera)
        // 为 SurfaceView 的 SurfaceHolder 添加回调，以监听 SurfaceView 的创建、大小变化和销毁
        cameraSurfaceView.holder.addCallback(cameraSurfaceCallback)
        viewModel.setCameraIndex(cameraIndex)
    }

    private fun updateCameraStream() {
        // 更新摄像头流绑定到 SurfaceView 的逻辑
        sv_camera.visibility = View.VISIBLE
        if (width <= 0 || height <= 0 || surface == null ) {
            if (surface != null) {
                // 如果宽度、高度或 Surface 尚未准备好，解绑当前的摄像头流。
                viewModel.removeCameraStreamSurface(surface!!)
            }
            return
        }
        viewModel.putCameraStreamSurface(
            surface!!,
            width,
            height,
            scaleType
        )
    }

    private val cameraSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this@SPFCameraStreamDetailFragment.width = width
            this@SPFCameraStreamDetailFragment.height = height
            updateCameraStream()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            width = 0
            height = 0
            updateCameraStream()
        }
    }
}