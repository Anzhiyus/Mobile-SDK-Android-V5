package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraStreamListVM
import dji.sdk.keyvalue.value.common.ComponentIndexType

class SPFCameraStreamListFragment : DJIFragment() {

    private lateinit var llCameraList: LinearLayout

    private val viewModule: CameraStreamListVM by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // 加载 Fragment 的布局文件并返回对应的视图
        return inflater.inflate(R.layout.frag_camera_stream_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 在 Fragment 的视图创建完成后调用，用于初始化视图和设置逻辑
        super.onViewCreated(view, savedInstanceState)
        llCameraList = view.findViewById(R.id.ll_camera_preview_list)
        // 观察 LiveData 数据变化，监听摄像头列表数据 availableCameraListData 的变化，实时更新 UI
        viewModule.availableCameraListData.observe(viewLifecycleOwner) { availableCameraList ->
            updateAvailableCamera(availableCameraList)
        }
    }

    private fun updateAvailableCamera(availableCameraList: List<ComponentIndexType>) {
        var ft = childFragmentManager.beginTransaction()
        val fragmentList = childFragmentManager.fragments
        for (fragment in fragmentList) {
            ft.remove(fragment!!)
        }
        ft.commitAllowingStateLoss()
        llCameraList.removeAllViews()
        ft = childFragmentManager.beginTransaction()
        val onlyOneCamera = availableCameraList.size == 1   // 手动设置为单相机，即要显示的fpv

//        for (cameraIndex in availableCameraList) {
//            val frameLayout = FrameLayout(llCameraList.context)
//            frameLayout.id = View.generateViewId()
//            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
//            llCameraList.addView(frameLayout, lp)
//            ft.replace(frameLayout.id, CameraStreamDetailFragment.newInstance(cameraIndex, true), cameraIndex.name)
//        }

        if (availableCameraList.isNotEmpty()) {
            val lastCameraIndex = availableCameraList.last() // 获取最后一个元素
            val frameLayout = FrameLayout(llCameraList.context)
            frameLayout.id = View.generateViewId()
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            llCameraList.addView(frameLayout, lp)

            ft.replace(
                frameLayout.id,
                SPFCameraStreamDetailFragment.newInstance(lastCameraIndex, true),
                lastCameraIndex.name
            )
        }

        ft.commitAllowingStateLoss()
    }
}