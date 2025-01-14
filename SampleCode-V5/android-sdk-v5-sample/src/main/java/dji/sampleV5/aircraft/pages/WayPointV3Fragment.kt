package dji.sampleV5.aircraft.pages


import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import com.dji.industry.mission.DocumentsUtils
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.common.data.Template
import com.dji.wpmzsdk.common.utils.kml.model.WaypointActionType
import com.dji.wpmzsdk.manager.WPMZManager
import dji.sampleV5.aircraft.DJIMapTool
import dji.sampleV5.aircraft.PhotoProcessingWorker
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.MediaVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.models.WayPointV3VM
import dji.sampleV5.aircraft.util.DialogUtil
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.utils.KMZTestUtil
import dji.sampleV5.aircraft.utils.KMZTestUtil.createWaylineMission
import dji.sampleV5.aircraft.utils.wpml.WaypointInfoModel
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.Attitude
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotation
import dji.sdk.keyvalue.value.gimbal.GimbalAngleRotationMode
import dji.sdk.wpmz.jni.JNIWPMZManager
import dji.sdk.wpmz.value.mission.*
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.utils.GpsUtils
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.simulator.SimulatorManager
import dji.v5.manager.aircraft.waypoint3.WPMZParserManager
import dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener
import dji.v5.manager.aircraft.waypoint3.WaypointActionListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import dji.v5.manager.aircraft.waypoint3.model.BreakPointInfo
import dji.v5.manager.aircraft.waypoint3.model.RecoverActionType
import dji.v5.manager.aircraft.waypoint3.model.WaylineExecutingInfo
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState
import dji.v5.manager.datacenter.media.MediaFile
import dji.v5.utils.common.*
import dji.v5.utils.common.DeviceInfoUtil.getPackageName
import dji.v5.ux.accessory.DescSpinnerCell
import dji.v5.ux.map.MapWidget
import dji.v5.ux.mapkit.core.camera.DJICameraUpdate
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptorFactory
import dji.v5.ux.mapkit.core.models.DJICameraPosition
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions
import dji.v5.ux.mapkit.core.utils.DJIGpsUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_add_waypoint.view.*
import kotlinx.android.synthetic.main.frag_virtual_stick_page.simulator_state_info_tv
import kotlinx.android.synthetic.main.frag_virtual_stick_page.widget_horizontal_situation_indicator
import kotlinx.android.synthetic.main.frag_waypointv3_page.*
import kotlinx.android.synthetic.main.spf_dialog_waylineplan.*
import kotlinx.android.synthetic.main.spf_frag_waypointv3_page.map_widget
import kotlinx.android.synthetic.main.view_mission_setting_home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * @author feel.feng
 * @time 2022/02/27 9:30 上午
 * @description:
 */
class WayPointV3Fragment : DJIFragment() {

    private val wayPointV3VM: WayPointV3VM by activityViewModels()
    private val WAYPOINT_SAMPLE_FILE_NAME: String = "waypointsample.kmz"
    private val WAYPOINT_SAMPLE_FILE_DIR: String = "waypoint/"
    private val WAYPOINT_SAMPLE_FILE_CACHE_DIR: String = "waypoint/cache/"
    private val WAYPOINT_FILE_TAG = ".kmz"
    private var unzipChildDir = "temp/"
    private var unzipDir = "wpmz/"
    private var mDisposable : Disposable ?= null
    private val OPEN_FILE_CHOOSER = 0
    private val OPEN_DOCUMENT_TREE = 1
    private val OPEN_MANAGE_EXTERNAL_STORAGE  = 2
    private val REQUEST_CODE_IMPORT_KML  = 3 // 处理文件选择结果


    private val showWaypoints : ArrayList<WaypointInfoModel> = ArrayList()
    private val pointMarkers : ArrayList<DJIMarker?> = ArrayList()
    var curMissionPath = ""
    val rootDir = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), WAYPOINT_SAMPLE_FILE_DIR)
    var validLenth: Int = 2
    var curMissionExecuteState: WaypointMissionExecuteState? = null
    var selectWaylines: ArrayList<Int> = ArrayList()

    private val mediaVM: MediaVM by activityViewModels()
    private val TAG = "OpencvpictureActivity"
    private val SHARED_PREFS_NAME = "WorkerData"

    var i = 0
    var pictureArray: Array<String> = arrayOf()

    // 虚拟摇杆
    private val basicAircraftControlVM: BasicAircraftControlVM by activityViewModels()
    private val virtualStickVM: VirtualStickVM by activityViewModels()
    private val simulatorVM: SimulatorVM by activityViewModels()

    var routePoints: List<DJILatLng> = mutableListOf()
    // 声明全局变量，用来存储无人机当前位置
    var droneCurrentLocation: DJILatLng? = null
    // 当前航点索引（持久化变量，可存储在文件、数据库或 SharedPreferences 中）
    var currentIndex: Int = 0
    // 全局变量：默认方位角
    var droneLastAzimuth: Double = 0.0
    var isActive:Boolean = true
    private var kmlSpeed: Double = 5.00
    private var kmlHeight: Double = 5.00
    private var kmlTime: Double = 0.00
    private var kmlWaypointDistance: Double = 30.00
    var gpsFileNamePath: String = ""

    private var updateJob: Job? = null


    // 判断OpenCV是否加载成功
    private val loaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(context) {
        override fun onManagerConnected(status: Int) {
            if (status == SUCCESS) {
                // OpenCV加载成功
            } else {
                super.onManagerConnected(status)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.frag_waypointv3_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 虚拟摇杆
        widget_horizontal_situation_indicator.setSimpleModeEnable(true)
        // 摇杆监听
        virtualStickVM.listenRCStick()
        // 显示虚拟遥感监听数据
        simulatorVM.simulatorStateSb.observe(viewLifecycleOwner) {
            simulator_state_info_tv.text = it
        }

        //  在应用启动时，读取保存的数据：
        val sharedPreferences = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE)
        // 读取航线当前运行航点索引
        currentIndex = sharedPreferences.getInt("currentIndex", 0) // 默认从0开始

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(context, "OpenCV初始化失败", Toast.LENGTH_SHORT).show()
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        // 缩略图切换监听
        thumbnail_click_overlay.setOnClickListener {
            Log.d(TAG,"右窗口点击事件")
            switchViews(main_window, thumbnail_window_left, thumbnail_window, isLeftThumbnail = false)
        }
        thumbnail_click_overlay_left.setOnClickListener {
            Log.d(TAG,"左窗口点击事件")
            switchViews(main_window, thumbnail_window_left, thumbnail_window, isLeftThumbnail = true)
        }

        prepareMissionData()
        // 所有按钮监听
        initView(savedInstanceState)
        initData()
        startTaskQueueConsumer()
        WPMZManager.getInstance().init(ContextUtil.getContext())

        mediaVM.init()

//        loadLocalPhoto() // 读取本地文件（计算基线距离）
        clearSharedPreferences()
        i = 0

        // 实例化航线绘制工具、航线规划对话框
        initWayLinePlan()

        // 自定义监听显示无人机位置
        startUpdatingAircraftLocation()

        // 如果没有连接无人机，初始化位置为当前位置
        initLocation()
    }

    // 开启虚拟摇杆
    private fun initBtnClickListener() {
        virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("enableVirtualStick success.")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("enableVirtualStick error,$error")
            }
        })

        // 开启虚拟摇杆高级模式
        virtualStickVM.enableVirtualStickAdvancedMode()
    }

    private fun switchViews(mainWindow: FrameLayout, thumbnailWindow1: FrameLayout, thumbnailWindow2: FrameLayout, isLeftThumbnail: Boolean) {
        val view1 = mainWindow.getChildAt(0)
        val view2 = thumbnailWindow1.getChildAt(0)  // 左视图
        val view3 = thumbnailWindow2.getChildAt(0)

        (view1.parent as? ViewGroup)?.removeView(view1)
        (view2.parent as? ViewGroup)?.removeView(view2)
        (view3.parent as? ViewGroup)?.removeView(view3)

        if (isLeftThumbnail) {
            // 左视图，左视图和主视图切换
            // 确保视图已从其父视图中移除
            mainWindow.addView(view2)
            thumbnailWindow1.addView(view1)
            // 第三个视图不变，但是会被遮挡，所以重新添加
            thumbnailWindow2.addView(view3)
        } else {
            mainWindow.addView(view3)
            thumbnailWindow2.addView(view1)

            thumbnailWindow1.addView(view2)
        }

        // 确保透明层在最上方，且可以点击
        thumbnail_click_overlay.bringToFront()
        thumbnail_click_overlay_left.bringToFront()
    }

    private fun prepareMissionData() {
        val dir = File(rootDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val cachedirName = DiskUtil.getExternalCacheDirPath(
            ContextUtil.getContext(),
            WAYPOINT_SAMPLE_FILE_CACHE_DIR
        )
        val cachedir = File(cachedirName)
        if (!cachedir.exists()) {
            cachedir.mkdirs()
        }
        val destPath = rootDir + WAYPOINT_SAMPLE_FILE_NAME
        if (!File(destPath).exists()) {
            FileUtils.copyAssetsFile(
                ContextUtil.getContext(),
                WAYPOINT_SAMPLE_FILE_NAME,
                destPath
            )
        }
    }

    private fun initView(savedInstanceState: Bundle?) {
        sp_map_switch.adapter = wayPointV3VM.getMapSpinnerAdapter()

        addListener()
        btn_mission_upload?.setOnClickListener {
            if (showWaypoints.isNotEmpty()){
                saveKmz(false)
            }
            val waypointFile = File(curMissionPath)
            if (waypointFile.exists()) {
                wayPointV3VM.pushKMZFileToAircraft(curMissionPath)
            } else {
                ToastUtils.showToast("Mission file not found!")
                return@setOnClickListener
            }
            markWaypoints()
        }

        wayPointV3VM.missionUploadState.observe(viewLifecycleOwner) {
            it?.let {
                when {
                    it.error != null -> {
                        mission_upload_state_tv?.text = "Upload State: error:${getErroMsg(it.error)} "
                    }
                    it.tips.isNotEmpty() -> {
                        mission_upload_state_tv?.text = it.tips
                    }
                    else -> {
                        mission_upload_state_tv?.text = "Upload State: progress:${it.updateProgress} "
                    }
                }

            }
        }

        btn_mission_start.setOnClickListener {
            wayPointV3VM.startMission(
                FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
                selectWaylines,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        ToastUtils.showToast("startMission Success")
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("startMission Failed " + getErroMsg(error))
                    }
                })
        }

        btn_mission_pause.setOnClickListener {
            wayPointV3VM.pauseMission(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("pauseMission Success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("pauseMission Failed " + getErroMsg(error))
                }
            })

        }

        observeBtnResume()


        btn_wayline_select.setOnClickListener {
            selectWaylines.clear()
            var waylineids = wayPointV3VM.getAvailableWaylineIDs(curMissionPath)
            showMultiChoiceDialog(waylineids)
        }

        kmz_btn.setOnClickListener {
            // 如果设备的 Android 版本为 Android 11 或更高版本，并且当前应用没有外部存储管理权限，那么将会执行请求权限的操作。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                var intent = Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")
                startActivityForResult(intent , OPEN_MANAGE_EXTERNAL_STORAGE)
            } else {
                showFileChooser()
            }
        }

        map_locate.setOnClickListener {
            map_widget.setMapCenterLock(MapWidget.MapCenterLock.AIRCRAFT)
        }

        sp_map_switch.setSelection(wayPointV3VM.getMapType(context))

        btn_mission_stop.setOnClickListener {
            if (curMissionExecuteState == WaypointMissionExecuteState.READY) {
                ToastUtils.showToast("Mission not start")
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(curMissionPath)){
                ToastUtils.showToast("curMissionPath is Empty")
                return@setOnClickListener
            }
            wayPointV3VM.stopMission(
                FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        ToastUtils.showToast("stopMission Success")
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("stopMission Failed " + getErroMsg(error))
                    }
                })
        }
        btn_editKmz.setOnClickListener {
            showEditDialog()
        }

        waypoints_clear.setOnClickListener {
            showWaypoints.clear()
            removeAllPoint()
            updateSaveBtn()
        }

        kmz_save.setOnClickListener {
            saveKmz(true)
        }

        btn_breakpoint_resume.setOnClickListener{
            var missionName = FileUtils.getFileName(curMissionPath , WAYPOINT_FILE_TAG );
            WaypointMissionManager.getInstance().queryBreakPointInfoFromAircraft(missionName
                , object :CommonCallbacks.CompletionCallbackWithParam<BreakPointInfo>{
                override fun onSuccess(breakPointInfo: BreakPointInfo?) {
                    breakPointInfo?.let {
                        resumeFromBreakPoint(missionName , it)
                    }
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("queryBreakPointInfo error $error")
                }

            })
        }

        addMapListener()

        createMapView(savedInstanceState)
        observeAircraftLocation()

        btn_take_photo_spf.setOnClickListener {
            ToastUtils.showToast("ToastUtils：DJI开始")
            Log.d(TAG, "DJI开始")
            // 启动协程读取照片
            lifecycleScope.launch {
                performTakePhoto()
            }
        }

//        // 读取视频流中的数据：

        //        // 读取本地文件夹中的数据：
        btn_download_photo_spf.setOnClickListener {
            // 显示kml航线
            Log.d(TAG, "Log：航线$routePoints")
            routePoints.forEach() {
                maptool?.markPoint(R.mipmap.mission_edit_waypoint_normal, it, "+");  // 创建边界中心点
//                markWaypoint(DJIGpsUtils.gcj2wgsInChina(it), 0)
            }

//            Log.d(TAG, "Log：DJI开始")
//            lifecycleScope.launch {
//                try {
//                    for (i in pictureArray.indices) {
//                        val path = pictureArray[i]
//                        if (path != null) {
//                            val resultValue = downloadPhotoSuspend(path, 23.74, 0.01229, 3.3 / 1000 / 1000, requireContext())
//                            Log.d(TAG, "DJI回调返回的结果: $resultValue")
//                        } else {
//                            Log.d(TAG, "DJI回调返回的结果: 下载失败，无法获取路径")
//                        }
//                        Log.d(TAG, "for循环：$i")
//                    }
//                    Log.d(TAG, "for循环：结束")
//                } catch (e: Exception) {
//                    Log.e(TAG, "下载失败: ${e.message}")
//                }
//            }
        }

//        // 按钮点击事件：启动任务
//        btn_download_photo_spf.setOnClickListener {
//
//            // 更新全局变量
//            val location = getAircraftLocation()
//            droneCurrentLocation = LatLng(location.latitude, location.longitude)
//
//            // 启动新的任务
//            currentTaskJob = lifecycleScope.launch {
//                enqueueTask {
//                    performTask()
//                }
//            }
//        }

        // 按钮点击事件：起飞
        btn_fly_spf.setOnClickListener {
            // 开启虚拟遥感
            initBtnClickListener()
            // 起飞
            basicAircraftControlVM.startTakeOff(object :
                CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                override fun onSuccess(t: EmptyMsg?) {
                    ToastUtils.showToast("start takeOff onSuccess.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("start takeOff onFailure,$error")
                }
            })
        }

        // 导入kml
        btn_input_kml_spf.setOnClickListener {
            // 每次导入新建保存经纬度高度信息的txt文件
            val dirs = File(DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile"))
            if (!dirs.exists()) dirs.mkdirs()
            // 获取当前时间并格式化为年月日时分秒
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            gpsFileNamePath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile/GPS_$currentTime.txt")


            // 导入kml，则需要把上次任务记录的索引清空
            currentIndex=1
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/vnd.google-earth.kml+xml" // 过滤 KML 文件类型
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            // 启动文件选择器
            startActivityForResult(intent, REQUEST_CODE_IMPORT_KML)
        }

        // 按钮点击事件：停止和继续
        btn_stop_spf.setOnClickListener {
            // 取消当前任务
            if (isActive)
            {
                isActive = false
                Log.d(TAG, "当前任务已取消")
                btn_stop_spf.text = "恢复任务SPF"
            }else{
                isActive = true
                Log.d(TAG, "当前任务已开始")
                btn_stop_spf.text = "暂停任务SPF"
                // 启动任务
                currentTaskJob = lifecycleScope.launch {
                    enqueueTask {
                        performTask()
                    }
                }
            }
        }

//        // 航线规划
//        btn_waylineplan_spf.setOnClickListener {
////            maptool?.OpenTool(DJIMapTool.TOOL_DRAWAREA)
//        }

        btn_waylineplan_spf.setOnClickListener {
            Log.d("DialogDebug", "Button clicked. wayLinePlanDialogIsVisible: $wayLinePlanDialogIsVisible")

            if (wayLinePlanDialogIsVisible) {
                Log.d("DialogDebug", "Dismiss dialog")
                wayLinePlanDialog?.dismiss() // 隐藏对话框
                wayLinePlanDialogIsVisible = false // 更新对话框显示状态
            } else {
                Log.d("DialogDebug", "Show dialog")
                // 检查 wayLinePlanDialog 是否为空
                if (wayLinePlanDialog == null) {
                    Log.e("DialogDebug", "wayLinePlanDialog is null")
                } else {
                    Log.d("DialogDebug", "wayLinePlanDialog is not null")
                }

                try {
                    wayLinePlanDialog?.show() // 显示对话框
                    wayLinePlanDialogIsVisible = true // 更新对话框显示状态
                } catch (e: Exception) {
                    Log.e("DialogDebug", "Error showing dialog: ${e.message}")
                }
            }
        }


//        // 导出kml
//        btn_output_kml_spf.setOnClickListener {
//            var flyspeed = 5.0
//            var flyWaypointDistance = 50.0
//            var flyheight = 50.0
//
//            // 输出航线端点坐标
//            val routeLinePoints: List<DJILatLng> = maptool?.getPointsFlyLines()?.filterNotNull()?.toList() ?: emptyList()
//            Log.d(TAG, "routeLinePoints:$routeLinePoints")
//            // 根据速度、航线长度、计算航线运行时间
//            // 根据速度、航线长度、计算航线运行时间
//            val routeLength: Double = maptool?.calculateTotalRouteLength(routeLinePoints)?: 0.0
//            var routeTime: Double = routeLength / flyspeed / 60
//            routeTime = (Math.round(routeTime * 100.0f) / 100.0f).toDouble()
////            tv_flyTime.setText(routeTime.toString())
//            Toast.makeText(context, "航线预计运行min:$routeTime", Toast.LENGTH_SHORT).show()
//
//            // 航点间距
////            flyWaypointDistance = tv_pointSpace.getText().toString().toFloat()
//            flyWaypointDistance = (Math.round(flyWaypointDistance * 100) / 100).toDouble()
//
//            // 根据航线端点，航点间距，计算航点坐标
//            routePoints = maptool?.generateIntermediatePoints(routeLinePoints, flyWaypointDistance)?.filterNotNull()?.toList() ?: emptyList()
//
//            // 输出航点坐标为kml文件
//            // 将数据存储到 requestDataMap 中
//            val kmlData: MutableMap<String, Any> = ConcurrentHashMap()
//            flyheight = (Math.round(flyheight * 100) / 100).toDouble()
//            flyspeed = (Math.round(flyspeed * 100) / 100).toDouble()
//            routeTime = (Math.round(routeTime * 100) / 100).toDouble()
//
//            kmlData["points"] = routePoints // 存储 LatLng 列表
//            kmlData["height"] = flyheight // 存储高度
//            kmlData["speed"] = flyspeed // 存储速度
//            kmlData["time"] = routeTime // 飞行时间min
//            kmlData["waypointDistance"] = flyWaypointDistance // 航点间距
//
//            saveKMLFileWithCustomPath(kmlData)
//            Toast.makeText(context, "成功导出航线为kml文件", Toast.LENGTH_SHORT).show()
//        }


    }

    // 定义一个全局的 Job，用于控制任务取消
    private var currentTaskJob: Job? = null
    // 定义一个任务队列（Channel）
    private val taskQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    // 添加任务到队列中
    fun enqueueTask(task: suspend () -> Unit) {
        lifecycleScope.launch {
            taskQueue.send(task)
        }
    }
    // 初始化消费者协程
    fun startTaskQueueConsumer() {
        lifecycleScope.launch {
            for (task in taskQueue) {
                try {
                    task() // 按顺序执行队列中的任务
                } catch (e: CancellationException) {
                    Log.e("TaskQueue", "任务被取消")
                } catch (e: Exception) {
                    Log.e("TaskQueue", "任务执行失败: ${e.message}")
                }
            }
        }
    }

    // 任务函数逻辑
    suspend fun performTask() {
        // 首次运行或者继续运行，需要判断当前飞机与下一个航点的位置和高度是否一样

        // 相机朝下
        Log.e(TAG, "云台旋转")
        performActionGimbalAngleRotation(1.0,-90.0,
            callback = object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    println("云台旋转成功")
                }
                override fun onFailure(error: IDJIError) {
                    println("云台旋转失败，错误：${error}")
                }
            }
        )
        var attitude = getGimbalAttitude()
        Log.e(TAG, "attitude: $attitude")

        // 首先，调整高度到航高
        val location = getAircraftLocation()
        var diffHeight = kmlHeight - location.altitude
        if (abs(diffHeight) > 0.1){
            val time = (diffHeight / 5) // 飞行时间
            // 发送虚拟杆参数：控制无人机向上飞行5m/s
            sendVirtualStickParameters(time, 0.0,0.0,0.0,5.0)
        }

        // 然后，调整位置到航线第一个点
        var distance = DJIGpsUtils.distance(DJILatLng(location.latitude, location.longitude), routePoints[currentIndex])*100/100
        if (abs(distance) > 0.1 && currentIndex > 1){
            // 从当前位置到航线记录的索引点的方向飞行（考虑到断点续飞）
            moveDroneToPoint(DJILatLng(location.latitude, location.longitude),  routePoints[currentIndex] , 0.0, 0.0)
            // 从航线起点到航线记录的索引点的方向飞行（假设当前位置为航线起点）
//            moveDroneToPoint(routePoints[0],  routePoints[currentIndex] , 0.0, 0.0)
        }

        var flyTime = kmlTime + (routePoints.size-currentIndex)*25/60
        ToastUtils.showToast( "航线预计飞行时间min:"+flyTime*100/100, Toast.LENGTH_SHORT)

        if (routePoints.isEmpty()) {
            Log.d(TAG, "航线点为空，无法执行任务")
            return
        }
        if (isActive) {
            Log.d(TAG, "航线正在运行：$isActive")
        }else{
            Log.d(TAG, "航线已经暂停：$isActive")
        }
        Log.d(TAG, "当前航线routePoints.size：${routePoints.size}")
        Log.d(TAG, "当前航线kmlHeight：${kmlHeight}")
        Log.d(TAG, "当前航线kmlSpeed：${kmlSpeed}")

        while (currentIndex < routePoints.size && isActive) {
            Log.d(TAG, "所有航线点已完成飞行任务：$isActive")
            Log.d(TAG, "downloadPhotoFixedPath: start")

            // 无人机拍照
            performTakePhoto()

            var startTime = System.nanoTime()
            // 从无人机下载照片到手柄
            val path = try {
                downloadPhotoFixedPath()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                null
            }
            Log.d(TAG, "downloadPhotoFixedPath: end")
            // 执行需要测量运行时间的代码块
            var endTime = System.nanoTime()
            var elapsedTime = endTime - startTime
            // 将纳秒转换为秒
            var elapsedTimeInSeconds = elapsedTime / 1_000_000_000.0
            Log.d(TAG, "downloadPhotoFixedPath elapsedTime: $elapsedTimeInSeconds")

            startTime = System.nanoTime()
            Log.d("TaskQueue", "path: $path")
            var resultValue = 0.0
            if (path != null) {
                resultValue = downloadPhotoSuspend(
                    path,
                    kmlWaypointDistance,  // 27.7
                    0.01229,
                    3.3 / 1000 / 1000,
                    requireContext()
                )
                Log.d(TAG,  "DJI回调返回的结果: $resultValue")
            } else {
                Log.d(TAG, "DJI回调返回的结果: 下载失败，无法获取路径")
            }

            // 记录当前位置高度。
            val location = getAircraftLocation()
            saveLocationToFile(location, path ?: "null")

            // 执行需要测量运行时间的代码块
            endTime = System.nanoTime()
            elapsedTime = endTime - startTime
            // 将纳秒转换为秒
            elapsedTimeInSeconds = elapsedTime / 1_000_000_000.0
            Log.d(TAG, "downloadPhotoSuspend elapsedTime: $elapsedTimeInSeconds")

            // 高程调整距离
//            resultValue = kmlHeight + 0.0  // 1. 不调整距离
            resultValue = kmlHeight  // 1. 调整固定距离
            resultValue = resultValue - kmlHeight



            Log.d(TAG, "VirtualStick: start：$currentIndex")
            val start: DJILatLng
            val end: DJILatLng

            if (currentIndex == 0) {
                // 第一次运行，将无人机当前位置作为起点
                val currentLocation = droneCurrentLocation
                if (currentLocation == null) {
                    println("无人机当前位置未知，无法执行任务。")
                    return
                }
                start = currentLocation
                end = routePoints[currentIndex]
            } else {
                // 后续运行，从当前索引点到下一个点
                start = routePoints[currentIndex - 1]
                end = routePoints[currentIndex]
            }

            Log.d(TAG, "起始点：$start")

            Log.d(TAG, "目标点：$end")
            var azimuth = moveDroneToPoint(start,  end , droneLastAzimuth, resultValue)
//            // 控制无人机运动，运动后方位角 = moveDroneToPoint（起始点，终点，当前方位角，调整高度）
//            var distance = DJIGpsUtils.distance(start, end)*100/100
//            distance = (distance* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
//            var azimuth = calculateBearing(start, end)
//            Log.d(TAG, "azimuth：$azimuth")
//            azimuth = (azimuth* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
//            if (azimuth > 180) {
//                azimuth -= 360
//            }
//            Log.d(TAG, "azimuth2：$azimuth")
//
//            // 计算飞行时间
//            val time = (distance / kmlSpeed) // 将飞行时间转换为 Int
//            var verticalSpeed = resultValue/time // 垂直速度分量
//            verticalSpeed = (verticalSpeed* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
//
//            // 判断 azimuth 与 globalAzimuth 的差值是否小于 1
//            if (abs(azimuth - droneLastAzimuth) < 1) {
//                // 差值小于 1，直接运行一次
//                // 发送虚拟杆参数：azimuth控制无人机朝向，即镜头方向。northSpeed（东西）和eastSpeed（南北）控制路线（以镜头方向为正北方向）
//                sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed)
//            } else {
//                // 发送虚拟杆参数，修改方位
//                Log.d(TAG, "azimuth3：$azimuth")
//                sendVirtualStickParameters(1.0, 0.0,0.0,azimuth)
//                // 发送虚拟杆参数
//                Log.d(TAG, "azimuth4：$azimuth")
//                sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed)
//            }
            droneLastAzimuth = azimuth

            // 保存数据 在应用退出或暂停时，保存循环状态：
            currentIndex++
            val sharedPreferences = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putInt("currentIndex", currentIndex) // 保存循环的当前索引
            editor.apply()
            Log.d(TAG, "VirtualStick: end：$currentIndex")
            Log.d(TAG, "VirtualStick: end：${routePoints.size}")
        }

        // 相机前视
        Log.e(TAG, "云台旋转")
        performActionGimbalAngleRotation(1.0,0.0,
            callback = object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    println("云台旋转成功")
                }
                override fun onFailure(error: IDJIError) {
                    println("云台旋转失败，错误：${error}")
                }
            }
        )
        attitude = getGimbalAttitude()
        Log.e(TAG, "attitude: $attitude")

        // 航线运行结束，重置索引
        if (currentIndex >= routePoints.size) {
            currentIndex = 1
        }
        val sharedPreferences = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("currentIndex", currentIndex) // 保存循环的当前索引
        editor.apply()

        Log.d(TAG, "所有航线点已完成飞行任务")
        println("所有航线点已完成飞行任务！")

        // 调试测试
//        sendVirtualStickParametersTest()
    }

    suspend fun  sendVirtualStickParametersTest(){
        Log.d(TAG, "sendVirtualStickParameters: picth")
        sendVirtualStickParameters(5.0, 0.0,5.0,0.0)

        Log.d(TAG, "sendVirtualStickParameters: picth2")
        sendVirtualStickParameters(5.0, 0.0,5.0,180.0,0.0)

        Log.d(TAG, "sendVirtualStickParameters: picth3")
        sendVirtualStickParameters(5.0, 0.0,5.0,90.0,0.0)

        Log.d(TAG, "sendVirtualStickParameters: picth4")
        sendVirtualStickParameters(5.0, 0.0,5.0,-45.0)
    }

    /**
     * 按照当前位置和目标位置计算方向和距离，控制无人机向该方向飞行该距离
     * @param start DJI经纬度坐标 DJIDJILatLng(lat, lon)
     * @param end DJI经纬度坐标 DJIDJILatLng(lat, lon)
     * @param droneLastAzimuth 上一次的无人机方向，用于本次方向调整
     * @param adjustHeight 需要控制无人机调整的高度
     * @return 当前无人机方向
     */
    suspend fun moveDroneToPoint(start: DJILatLng, end: DJILatLng , droneLastAzimuth:Double= 0.0, adjustHeight:Double = 0.0):Double{
        // 计算两点之间的距离和方位角
        var distance = DJIGpsUtils.distance(start, end)*100/100
        distance = (distance* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
        var azimuth = calculateBearing(start, end)
        Log.d(TAG, "azimuth：$azimuth")
        azimuth = (azimuth* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
        if (azimuth > 180) {
            azimuth -= 360
        }
        Log.d(TAG, "moveDroneToPoint：azimuth $azimuth")

        // 计算飞行时间
        val time = (distance / kmlSpeed) // 将飞行时间转换为 Int
        var verticalSpeed = adjustHeight/time // 垂直速度分量
        verticalSpeed = (verticalSpeed* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入

        // 判断 azimuth 与 globalAzimuth 的差值是否小于 1
        if (abs(azimuth - droneLastAzimuth) < 1) {
            // 差值小于 1，直接运行一次
            // 发送虚拟杆参数：azimuth控制无人机朝向，即镜头方向。northSpeed（东西）和eastSpeed（南北）控制路线（以镜头方向为正北方向）
            sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed)
        } else {
            // 发送虚拟杆参数，修改方位
            Log.d(TAG, "moveDroneToPoint：调整方向")
            sendVirtualStickParameters(1.0, 0.0,0.0,azimuth)
            // 发送虚拟杆参数
            Log.d(TAG, "moveDroneToPoint：进行飞行")
            sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed)
        }
        return azimuth
    }


    /**
     * 控制无人机按照命令运行
     * @param durationInSeconds 命令持续时间
     * @param pitch 无人机左右移动速度，右为正值
     * @param roll 无人机前后移动速度，前为正值
     * @param yaw 无人机上下移动速度，上为正值
     * @return 运行后无人机朝向
     */
    suspend fun sendVirtualStickParameters(
        durationInSeconds: Double = 1.0,
        pitch: Double = 0.0,
        roll: Double = 0.0,
        yaw: Double = 0.0,
        verticalThrottle: Double = 0.0,
        verticalControlMode: VerticalControlMode = VerticalControlMode.VELOCITY,
        rollPitchControlMode: RollPitchControlMode = RollPitchControlMode.VELOCITY,
        yawControlMode: YawControlMode = YawControlMode.ANGLE,
        rollPitchCoordinateSystem: FlightCoordinateSystem = FlightCoordinateSystem.BODY,
        sendAction: (VirtualStickFlightControlParam) -> Unit = { param ->
            virtualStickVM.sendVirtualStickAdvancedParam(param) // 默认实现
        }
    ) {
        var times= Math.ceil(durationInSeconds / 0.2).toInt()
        repeat( times- 1) { iteration ->
            val param = VirtualStickFlightControlParam().apply {
                this.pitch = pitch
                this.roll = roll
                this.yaw = yaw
                this.verticalThrottle = verticalThrottle
                this.verticalControlMode = verticalControlMode
                this.rollPitchControlMode = rollPitchControlMode
                this.yawControlMode = yawControlMode
                this.rollPitchCoordinateSystem = rollPitchCoordinateSystem
            }
            sendAction(param)
            delay(200) // 每次间隔指定时间，即5Hz
        }
    }

    /**
     * 仿地飞行计算
     * @param path 当前照片路径
     * @param baseLine 基线距离（相邻照片拍照无人机距离）
     * @param focalLength 相机焦距
     * @param pixelDim 相机像元大小
     * @return 无人机需要调整高度
     */
    // 使用Suspend挂起，调用Worker类
    suspend fun downloadPhotoSuspend(
        path: String,
        baseLine: Double,
        focalLength: Double,
        pixelDim: Double,
        context: Context
    ): Double = suspendCancellableCoroutine { continuation ->

        val workRequest = OneTimeWorkRequestBuilder<PhotoProcessingWorker>()
            .setInputData(Data.Builder()
                        .putString("photo_path", path)
                        .putDouble("photo_baseLine", baseLine)
                        .putDouble("photo_focallength", focalLength)
                        .putDouble("photo_pixeldim", pixelDim)
                        .build()
                                        )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    val resultValue = workInfo.outputData.getDouble("result_value", 0.0)
                    continuation.resume(resultValue) // 成功时返回结果
                } else {
                    continuation.resumeWithException(Exception("Worker failed"))
                }
            }
        }

        continuation.invokeOnCancellation {
            workManager.cancelWorkById(workRequest.id) // 取消任务
        }
    }

    // 调用DJI拍照功能
    suspend fun performTakePhoto() {
        // 将回调封装为挂起函数
        suspendCancellableCoroutine<Unit> { continuation ->
            mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    // 恢复挂起函数，通知调用者任务完成
                    continuation.resume(Unit)
                    ToastUtils.showToast("take photo success")
                }

                override fun onFailure(error: IDJIError) {
                    // 恢复挂起函数，通知调用者任务失败
                    continuation.resumeWithException(Exception("Take photo failed: ${error}"))
                    ToastUtils.showToast("take photo failed")
                }
            })

            // 如果协程被取消，停止拍照任务（可选逻辑）
            continuation.invokeOnCancellation {
                // 添加取消拍照任务的代码（如果支持）
            }
        }
    }


    // 计算两点之间的方位角（相对于正北方向的顺时针角度，单位：度）
    fun calculateBearing(start: DJILatLng, end: DJILatLng): Double {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)

        val dLng = endLng - startLng
        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360 // 确保方位角为 0~360 度
    }

    // 封装为函数，返回飞行器的位置
    fun getAircraftLocation(): LocationCoordinate3D {
        return KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D),
            LocationCoordinate3D(0.0, 0.0, 0.0)  // 默认值
        )
    }

    // 文本获取更新当前位置
    private fun startUpdatingAircraftLocation() {
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val location = getAircraftLocation()
                val decimalFormat = DecimalFormat("#.##")
                tv_aircraft_location.text = "Lat: ${location.latitude}, Lon: ${location.longitude}, Alt: ${decimalFormat.format(location.altitude)}"
                delay(1000) // 每秒更新一次
            }
        }
    }

    // 封装为函数，返回云台的姿态
    // Attitude(pitch,roll,yaw)。pitch上下朝向0为水平，-90向下。yaw水平方向0为正北90为正东。
    fun getGimbalAttitude(): Attitude {
        return KeyManager.getInstance().getValue(
            KeyTools.createKey(GimbalKey.KeyGimbalAttitude),
            Attitude(0.0, 0.0, 0.0)  // 默认值
        )
    }

    /**
     * 列出当前文件夹内某一文件类型的文件名
     * @param folderPath 文件夹路径
     * @param pattern 匹配的正则表达式
     * @return 匹配的文件名数组
     */
    // 封装为函数，设置云台的姿态
    suspend fun performActionGimbalAngleRotation(
        duration: Double = 1.0,
        pitch: Double = 0.0,
        roll: Double = 0.0,
        yaw: Double = 0.0,
        pitchIgnored: Boolean = false,
        rollIgnored: Boolean = true,
        yawIgnored: Boolean = true,
        jointReferenceUsed: Boolean = false,
        timeout: Int = 1,
        mode: GimbalAngleRotationMode = GimbalAngleRotationMode.ABSOLUTE_ANGLE,
        callback: CommonCallbacks.CompletionCallback
    ) {
        val gimbalRotation = GimbalAngleRotation().apply {
            this.mode = mode
            this.pitch = pitch
            this.roll = roll
            this.yaw = yaw
            this.pitchIgnored = pitchIgnored
            this.rollIgnored = rollIgnored
            this.yawIgnored = yawIgnored
            this.duration = duration
            this.jointReferenceUsed = jointReferenceUsed
            this.timeout = timeout
        }

        KeyManager.getInstance().performAction(
            KeyTools.createKey(GimbalKey.KeyRotateByAngle),
            gimbalRotation,
            object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                override fun onSuccess(t: EmptyMsg?) {
                    callback.onSuccess()
                }

                override fun onFailure(error: IDJIError) {
                    callback.onFailure(error)
                }
            }
        )
    }

    private val requestDataMap = ConcurrentHashMap<Int, Any>()

    // 打开文件选择器让用户选择保存路径和文件名
    private fun saveKMLFileWithCustomPath(kmlData: Map<String, Any>) {
        // 生成唯一请求码
        val requestCode: Int = generateRequestCode()

        // 保存请求数据到 map
        requestDataMap.put(requestCode, kmlData)

        // 启动文件选择器
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/vnd.google-earth.kml+xml"
        intent.putExtra(Intent.EXTRA_TITLE, "output.kml")
        startActivityForResult(intent, requestCode)
    }

    private fun generateRequestCode(): Int {
        return (Math.random() * Int.MAX_VALUE).toInt()
    }


    // 保存latitude, longitude 和 altitude 属性
    fun saveLocationToFile(location1: LocationCoordinate3D, path: String) {
        try {
            // 使用 BufferedWriter 来写入文件，isAppend 参数控制是否追加数据
            val writer = BufferedWriter(FileWriter(gpsFileNamePath, true))  // isAppend 参数控制追加或覆盖
            val pathname = path.substring(path.lastIndexOf("/") + 1)  // 提取文件名
            val data = "${location1.latitude}, ${location1.longitude}, ${location1.altitude*100/100}, $pathname\n"
            writer.write(data)  // 写入数据
            writer.close()  // 关闭文件流
            println("Data written successfully.")
        } catch (e: IOException) {
            e.printStackTrace()
            println("Error writing to file.")
        }
    }


    fun clearSharedPreferences() {
        val sharedPreferences = requireContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.clear() // 清空所有数据
        editor.apply() // 或者使用 editor.commit()，取决于你是否需要同步
    }

    // 航线规划相关
    // 地图绘制类
    var maptool: DJIMapTool? = null
    var wayLinePlanDialog: Dialog? = null
    var wayLinePlanDialogIsVisible = false // 对话框显示状态变量

    private fun initWayLinePlan() {
        // 实例化航线绘制工具
        maptool = DJIMapTool(map_widget.map ,activity)

        // 实例化航线规划对话框
        wayLinePlanDialog = Dialog(requireContext())
        wayLinePlanDialog?.setContentView(R.layout.spf_dialog_waylineplan)
        wayLinePlanDialog?.setCancelable(false)
        wayLinePlanDialog?.setCanceledOnTouchOutside(false)
        // 设置对话框，将对话框独立出来，可拖动
        val window = wayLinePlanDialog?.window
        val layoutParams = window?.attributes
        layoutParams?.apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            dimAmount = 0.5f
        }
        window?.attributes = layoutParams

        Log.d(TAG, "wayLinePlanDialog")

        // 组件初始化
        // 相机参数
        val focallength = wayLinePlanDialog?.findViewById<EditText>(R.id.focal)
        val opticalFormat = wayLinePlanDialog?.findViewById<EditText>(R.id.opticalFormat)
        val parallelimage = wayLinePlanDialog?.findViewById<EditText>(R.id.imagewidth)
        val adjacentverimage = wayLinePlanDialog?.findViewById<EditText>(R.id.imageheight)
        // 旁向重叠度
        val parallellapping = wayLinePlanDialog?.findViewById<EditText>(R.id.et_parallellapping)
        // 地面分辨率
        val gsd = wayLinePlanDialog?.findViewById<EditText>(R.id.et_gsd)
        // 飞行速度
        val flyspeed = wayLinePlanDialog?.findViewById<EditText>(R.id.et_speed)
        // 航向重叠度
        val adjacentverlapping = wayLinePlanDialog?.findViewById<EditText>(R.id.et_adjacentverlapping)

        val tv_flyHeight = wayLinePlanDialog?.findViewById<TextView>(R.id.tv_flyHeight)
        val tv_lineSpace = wayLinePlanDialog?.findViewById<TextView>(R.id.tv_lineSpace)
        val tv_pointSpace = wayLinePlanDialog?.findViewById<TextView>(R.id.tv_pointSpace)

        val line_angle_et = wayLinePlanDialog?.findViewById<EditText>(R.id.line_angle_et)
        val line_space_et = wayLinePlanDialog?.findViewById<EditText>(R.id.line_space_et)

        val btn_calculate = wayLinePlanDialog?.findViewById<Button>(R.id.btn_calculate)
        val draw_start = wayLinePlanDialog?.findViewById<Button>(R.id.draw_start)
        val draw_delete = wayLinePlanDialog?.findViewById<Button>(R.id.draw_delete)
        val output_route = wayLinePlanDialog?.findViewById<Button>(R.id.output_route)

        btn_calculate?.setOnClickListener(){
            Log.d(TAG, "btn_calculate")
            var parallellapping_num = parallellapping?.text.toString().toDouble()/100
            var gsd_num = gsd?.text.toString().toDouble()/100
            var parallelimage_num = parallelimage?.text.toString().toDouble()
            var adjacentverlapping_num = adjacentverlapping?.text.toString().toDouble()/100
            var adjacentverimage_num = adjacentverimage?.text.toString().toDouble()
            var focallength_num = focallength?.text.toString().toDouble()
            var opticalFormat_num = opticalFormat?.text.toString().toDouble()

            // 航线间距计算
            var spacing = (1 - parallellapping_num) * (gsd_num * parallelimage_num)
            spacing = (Math.round(spacing * 100.0) / 100.0)

            // 航点间距计算
            var pointspacing = (1 - adjacentverlapping_num) * (gsd_num * adjacentverimage_num)
            pointspacing = (Math.round(pointspacing * 100.0) / 100.0)
            // 飞行高度计算
            var flyheight = 1000 * focallength_num * gsd_num / opticalFormat_num
            flyheight = (Math.round(flyheight * 100.0) / 100.0)

            // 更新 UI
            tv_flyHeight?.text = String.format("%.2f", flyheight)
            tv_lineSpace?.text = String.format("%.2f", spacing)
            tv_pointSpace?.text = String.format("%.2f", pointspacing)

            // 更新航线
            line_space_et?.setText(spacing.toString())
            maptool?.UpdateWayLines(
                line_angle_et?.text.toString().toInt(),
                line_space_et?.text.toString().toDouble()
            )

        }

        draw_start?.setOnClickListener(){
            maptool?.OpenTool(DJIMapTool.TOOL_DRAWAREA)
            if (wayLinePlanDialogIsVisible) {
                wayLinePlanDialog?.dismiss() // 隐藏对话框
                wayLinePlanDialogIsVisible = false // 更新对话框显示状态
            }
        }
        draw_delete?.setOnClickListener(){
            maptool?.ClearAll()
        }
        output_route?.setOnClickListener(){
            var flyspeed = 5.0
            var flyWaypointDistance = 50.0
            var flyheight = 50.0

            // 输出航线端点坐标
            val routeLinePoints: List<DJILatLng> = maptool?.getPointsFlyLines()?.filterNotNull()?.toList() ?: emptyList()
            Log.d(TAG, "routeLinePoints:$routeLinePoints")
            // 根据速度、航线长度、计算航线运行时间
            // 根据速度、航线长度、计算航线运行时间
            val routeLength: Double = maptool?.calculateTotalRouteLength(routeLinePoints)?: 0.0
            var routeTime: Double = routeLength / flyspeed / 60
            routeTime = (Math.round(routeTime * 100.0f) / 100.0f).toDouble()
//            tv_flyTime.setText(routeTime.toString())
            Toast.makeText(context, "航线预计运行min:$routeTime", Toast.LENGTH_SHORT).show()

            // 航点间距
//            flyWaypointDistance = tv_pointSpace.getText().toString().toFloat()
            flyWaypointDistance = (Math.round(flyWaypointDistance * 100) / 100).toDouble()

            // 根据航线端点，航点间距，计算航点坐标
            routePoints = maptool?.generateIntermediatePoints(routeLinePoints, flyWaypointDistance)?.filterNotNull()?.toList() ?: emptyList()

            // 输出航点坐标为kml文件
            // 将数据存储到 requestDataMap 中
            val kmlData: MutableMap<String, Any> = ConcurrentHashMap()
            flyheight = (Math.round(flyheight * 100) / 100).toDouble()
            flyspeed = (Math.round(flyspeed * 100) / 100).toDouble()
            routeTime = (Math.round(routeTime * 100) / 100).toDouble()

            kmlData["points"] = routePoints // 存储 LatLng 列表
            kmlData["height"] = flyheight // 存储高度
            kmlData["speed"] = flyspeed // 存储速度
            kmlData["time"] = routeTime // 飞行时间min
            kmlData["waypointDistance"] = flyWaypointDistance // 航点间距

            saveKMLFileWithCustomPath(kmlData)
            Toast.makeText(context, "成功导出航线为kml文件", Toast.LENGTH_SHORT).show()
        }


    }

    // 如果没有连接无人机进行当前位置定位
    private fun initLocation() {
        // 检查无人机是否已连接
        if (!KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyConnection), false)) {
            val locationManager =  requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            try {
                // 获取 GPS 或网络提供的位置
                val location: Location? = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                if (location != null) {
                    val cameraPosition = DJICameraPosition(DJILatLng(location.latitude,location.longitude), 15.0f, 0.0f, 0.0f)
                    var cameraUpdate: DJICameraUpdate? = null
                    cameraUpdate = DJICameraUpdateFactory.newCameraPosition(cameraPosition)
                    map_widget.map?.moveCamera(cameraUpdate)
                } else {

                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

        }
    }


    var picturearray: Array<String> = arrayOf()  // 初始化为空数组
    var BaseLine: DoubleArray = doubleArrayOf()  // 初始化为空的 Double 数组
    //  测试读取当前文件下照片或基线距离
    fun loadLocalPhoto() {
//        // 路径示例：Android/data/cas.igsnrr.dronefly/files/Pictures/DJI/DJI***.jpg
//        picturearray = getMatchingFileNames(
//            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/H1",
//            "^H1.*\\.(jpg|JPG)"
//        )
//        Log.d(TAG, "takePhoto: " + picturearray.size)
//        // 读取文件，计算基线距离
//        try {
//            BaseLine = latlonToBaseLine("H1架次CGCS2000、85高") ?: doubleArrayOf()
//            Log.d(TAG, "H1架次CGCS2000: ${BaseLine.size}")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error calculating BaseLine", e)
//        }

        pictureArray = getMatchingFileNames(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/DJI_20250106",
            "^Picture_20250103_.*\\.(jpg|JPG)"
        )
        ToastUtils.showToast( "DJI开始：${requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/DJI_20250106"}")

    }

    /** 列出当前文件夹内某一文件类型的文件名
     * @param folderPath
     * @param pattern
     * @return
     */
    fun getMatchingFileNames(folderPath: String, pattern: String?): Array<String> {
        val p = Pattern.compile(pattern) // ".+\\.txt"
        val matchingFileNames: MutableList<String> = ArrayList()
        val directory = File(folderPath)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            if (files != null) {
                for (file: File in files) {
                    val m = p.matcher(file.name)
                    if (file.isFile && m.matches()) {
                        matchingFileNames.add(folderPath + "/" + file.name)
                    }
                }
            }
        }
        Collections.sort(matchingFileNames)
        return matchingFileNames.toTypedArray()
//        return Arrays.copyOfRange(matchingFileNames.toArray(new String[0]), 1201, 1260);
    }

    private fun latlonToBaseLine(filename: String): DoubleArray {
        // 读取照片经纬度，用于计算变高的基线
        val pointStack = ArrayList<Point>()
        val filepath: String = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.toString() + "/" + filename + ".txt"

        try {
            val file = File(filepath)
            if (file.exists()) {
                val bufferedReader = BufferedReader(FileReader(file))
                var line: String?

                // 跳过第一行
                bufferedReader.readLine()
                while (bufferedReader.readLine().also { line = it } != null) {
                    // 使用制表符分隔每一列
                    val columns = line?.split("\t".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()

                    // 判断是否有足够的列
                    if (columns != null && columns.size >= 3) {
                        // 读取第二列和第三列作为 double 类型数据
                        val column2 = columns[1].toDoubleOrNull()
                        val column3 = columns[2].toDoubleOrNull()

                        if (column2 != null && column3 != null) {
                            // 在这里可以使用 column2 和 column3 做进一步的处理
                            pointStack.add(Point(column2, column3))
                        } else {
                            Log.e("Parse Error", "Failed to parse column data to double")
                        }
                    } else {
                        Log.e("Format Error", "Incorrect number of columns or null line")
                    }
                }
                bufferedReader.close()
            } else {
                Log.e("File Error", "File does not exist: $filepath")
            }
        } catch (e: IOException) {
            Log.e("IO Error", "Error reading file", e)
        }

        val size = pointStack.size
        if (size <= 1) {
            throw IllegalArgumentException("pointStack must contain more than 1 point to form a baseline.")
        }

        val BaseLine = DoubleArray(size - 1)
        for (i in 0 until size - 1) {
            BaseLine[i] = Math.sqrt(
                (pointStack[i + 1].x - pointStack[i].x).pow(2)
                        + (pointStack[i + 1].y - pointStack[i].y).pow(2)
            )
        }

        return BaseLine
    }

    /**
     * 下载无人机上最新照片
     * @return 下载后文件位置
     */
    suspend fun downloadPhotoFixedPath(): String? {
        // 获取文件列表
        mediaVM.pullMediaFileListFromCamera(-1, 1)

        // 使用挂起函数等待 mediaFileListData 更新
        val mediaFiles = suspendCancellableCoroutine<List<MediaFile>> { cont ->
            mediaVM.mediaFileListData.observe(viewLifecycleOwner) { mediaFileList ->
                if (!cont.isCompleted) {
                    if (mediaFileList.data != null && mediaFileList.data.isNotEmpty()) {
                        cont.resume(mediaFileList.data!!)
                    } else {
                        Log.e(TAG, "No media files found!")
                        cont.resume(emptyList()) // 返回空列表
                    }
                }
            }
        }

        // 转换为 ArrayList<MediaFile>
        val mediaFileList = ArrayList(mediaFiles)  // 将 mediaFiles 转换为 ArrayList<MediaFile>
        Log.d(dji.sampleV5.aircraft.models.TAG, "downloadPhotoFixedPath: mediaFileList.size：${mediaFileList.size}")

        // 下载文件
        var bitmap: String? = null
        bitmap = mediaVM.downloadMediaFileFixedPath(mediaFileList)
        Log.d(dji.sampleV5.aircraft.models.TAG, "downloadPhotoFixedPath: filePath：$bitmap")
        return bitmap
    }



    /**
     * 列出当前文件夹内某一文件类型的文件名
     * @param folderPath 文件夹路径
     * @param pattern 匹配的正则表达式
     * @return 匹配的文件名数组
     */
//    fun getMatchingFileNames(folderPath: String, pattern: String): Array<String> {
//        val regex = Pattern.compile(pattern) // ".+\\.txt"
//        val matchingFileNames = mutableListOf<String>()
//        val directory = File(folderPath)
//
//        if (directory.exists() && directory.isDirectory) {
//            val files = directory.listFiles()
//            files?.forEach { file ->
//                if (file.isFile && regex.matcher(file.name).matches()) {
//                    matchingFileNames.add("${folderPath}/${file.name}")
//                }
//            }
//        }
//
//        matchingFileNames.sort() // 对匹配的文件名进行排序
//        return matchingFileNames.toTypedArray() // 返回数组
//    }



    private fun saveKmz(showToast: Boolean) {
        val kmzOutPath = rootDir + "generate_test.kmz"
        val waylineMission: WaylineMission = createWaylineMission()
        val missionConfig: WaylineMissionConfig = KMZTestUtil.createMissionConfig()
        val template: Template = KMZTestUtil.createTemplate(showWaypoints)
        WPMZManager.getInstance()
            .generateKMZFile(kmzOutPath, waylineMission, missionConfig, template)
        curMissionPath  = kmzOutPath
        if (showToast) {
            ToastUtils.showToast("Save Kmz Success Path is : $kmzOutPath")
        }

        waypoint_add.isChecked = false
    }

    private fun observeAircraftLocation() {
        val location = KeyManager.getInstance()
            .getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation), LocationCoordinate2D(0.0,0.0))
        val isEnable = SimulatorManager.getInstance().isSimulatorEnabled
        if (!GpsUtils.isLocationValid(location) && !isEnable) {
            ToastUtils.showToast("please open simulator")
        }
    }

    private fun observeBtnResume() {
        btn_mission_query.setOnClickListener {
            var missionName = FileUtils.getFileName(curMissionPath , WAYPOINT_FILE_TAG );
            WaypointMissionManager.getInstance().queryBreakPointInfoFromAircraft(missionName
                , object :CommonCallbacks.CompletionCallbackWithParam<BreakPointInfo>{
                    override fun onSuccess(breakPointInfo: BreakPointInfo?) {
                        breakPointInfo?.let {
                            ToastUtils.showLongToast("BreakPointInfo : waypointID-${breakPointInfo.waypointID} " +
                                    "progress:${breakPointInfo.segmentProgress}  location:${breakPointInfo.location}")
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("queryBreakPointInfo error $error")
                    }

                })
        }
        btn_mission_resume.setOnClickListener {
            wayPointV3VM.resumeMission(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("resumeMission Success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("resumeMission Failed " + getErroMsg(error))
                }
            })
        }

        btn_mission_resume_with_bp.setOnClickListener {
            var wp_breakinfo_index = wp_break_index.text.toString()
            var wp_breakinfo_progress = wp_break_progress.text.toString()
            var resume_type = getResumeType()
            if (!TextUtils.isEmpty(wp_breakinfo_index) && !TextUtils.isEmpty(wp_breakinfo_progress)) {
                var breakPointInfo = BreakPointInfo(0 ,wp_breakinfo_index.toInt(),wp_breakinfo_progress.toDouble()  , null, resume_type)
                wayPointV3VM.resumeMission(breakPointInfo , object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        ToastUtils.showToast("resumeMission with BreakInfo Success")
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("resumeMission with BreakInfo Failed " + getErroMsg(error))
                    }
                })
            }
            else {
                ToastUtils.showToast("Please Input breakpoint index or progress")
            }
        }
    }
    //断电续飞
    private fun resumeFromBreakPoint(missionName :String , breakPointInfo: BreakPointInfo ){
        var wp_breakinfo_index = wp_break_index.text.toString()
        var wp_breakinfo_progress = wp_break_progress.text.toString()
        if (!TextUtils.isEmpty(wp_breakinfo_index) && !TextUtils.isEmpty(wp_breakinfo_progress)) {
            breakPointInfo.segmentProgress = wp_breakinfo_progress.toDouble()
            breakPointInfo.waypointID = wp_breakinfo_index.toInt()
        }
        wayPointV3VM.startMission(missionName , breakPointInfo , object :CommonCallbacks.CompletionCallback{
            override fun onSuccess() {
                ToastUtils.showToast("resume success");
            }

            override fun onFailure(error: IDJIError) {
               ToastUtils.showToast("resume error $error")
            }

        })
    }

    private  fun addMapListener(){

        waypoint_add.setOnCheckedChangeListener { _, isOpen ->
            if (isOpen) {
                map_widget.map?.setOnMapClickListener{
                    showWaypointDlg(it , object :CommonCallbacks.CompletionCallbackWithParam<WaypointInfoModel>{
                        override fun onSuccess(waypointInfoModel: WaypointInfoModel) {
                            showWaypoints.add( waypointInfoModel)
                            showWaypoints()
                            updateSaveBtn()
                            ToastUtils.showToast("lat" + it.latitude + " lng" + it.longitude)
                        }
                        override fun onFailure(error: IDJIError) {
                            ToastUtils.showToast("add Failed " )
                        }
                    })
                }
            } else {
                map_widget.map?.removeAllOnMapClickListener()
            }
        }
    }

    private fun addListener(){
        wayPointV3VM.addMissionStateListener() {
            mission_execute_state_tv?.text = "Mission Execute State : ${it.name}"
            btn_mission_upload.isEnabled = it == WaypointMissionExecuteState.READY
            curMissionExecuteState = it
            if (it == WaypointMissionExecuteState.FINISHED) {
                ToastUtils.showToast("Mission Finished")
            }
            LogUtils.i(logTag , "State is ${it.name}")
        }
        wayPointV3VM.addWaylineExecutingInfoListener(object :WaylineExecutingInfoListener {
            override fun onWaylineExecutingInfoUpdate(it: WaylineExecutingInfo) {
                wayline_execute_state_tv?.text = "Wayline Execute Info WaylineID:${it.waylineID} \n" +
                        "WaypointIndex:${it.currentWaypointIndex} \n" +
                        "MissionName : ${ it.missionFileName}"
            }

            override fun onWaylineExecutingInterruptReasonUpdate(error: IDJIError?) {
                if (error != null) {
                    val originStr = wayline_execute_state_tv.getText().toString()
                    wayline_execute_state_tv.text = "$originStr\n InterruptReason:${error.errorCode()}"
                    LogUtils.e(logTag , "interrupt error${error.errorCode()}")
                }
            }

        });


        wayPointV3VM.addWaypointActionListener(object :WaypointActionListener{
            override fun onExecutionStart(actionId: Int) {
                waypint_action_state_tv?.text = "onExecutionStart: ${actionId} "
            }

            override fun onExecutionStart(actionGroup: Int , actionId: Int ) {
                waypint_action_state_tv?.text = "onExecutionStart:${actionGroup}: ${actionId} "
            }

            override fun onExecutionFinish(actionId: Int, error: IDJIError?) {
                waypint_action_state_tv?.text = "onExecutionFinish: ${actionId} "
            }

            override fun onExecutionFinish(actionGroup: Int, actionId: Int,  error: IDJIError?) {
                waypint_action_state_tv?.text = "onExecutionFinish:${actionGroup}: ${actionId} "
            }

        })
    }

    fun updateSaveBtn(){
        kmz_save.isEnabled = showWaypoints.isNotEmpty()
    }
    private fun showEditDialog() {
        val waypointFile = File(curMissionPath)
        if (!waypointFile.exists()) {
            ToastUtils.showToast("Please upload kmz file")
            return
        }

        val unzipFolder = File(rootDir, unzipChildDir)
        // 解压后的waylines路径
        val templateFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.TEMPLATE_FILE)
        val waylineFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.WAYLINE_FILE)

        mDisposable = Single.fromCallable {
            //在cache 目录创建一个wmpz文件夹，并将template.kml 与 waylines.wpml 拷贝进wpmz ，然后压缩wpmz文件夹
            WPMZParserManager.unZipFolder(ContextUtil.getContext(), curMissionPath, unzipFolder.path, false)
            FileUtils.readFile(waylineFile.path , null)
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { wpmlContent: String? ->
                    DialogUtil.showInputDialog(requireActivity() ,"",wpmlContent , "", false , object :CommonCallbacks.CompletionCallbackWithParam<String> {
                        override fun onSuccess(newContent: String?) {
                            newContent?.let {
                                updateWPML(it)
                            }
                        }
                        override fun onFailure(error: IDJIError) {
                            LogUtils.e(logTag , "show input Dialog Failed ${error.description()} ")
                        }

                    })
                }
            ) { throwable: Throwable ->
                LogUtils.e(logTag , "show input Dialog Failed ${throwable.message} ")
            }
    }

    private fun updateWPML(newContent: String) {
        val waylineFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.WAYLINE_FILE)

        Single.fromCallable {
            FileUtils.writeFile(waylineFile.path, newContent, false)
            //将修改后的waylines.wpml重新压缩打包成 kmz
            val zipFiles = mutableListOf<String>()
            val cacheFolder = File(rootDir, unzipChildDir + unzipDir)
            var zipFile = File(rootDir + unzipChildDir + "waypoint.kmz")
            if (waylineFile.exists()) {
                zipFiles.add(cacheFolder.path)
                zipFile.createNewFile()
                WPMZParserManager.zipFiles(ContextUtil.getContext(), zipFiles, zipFile.path)
            }
            //将用户选择的kmz用修改的后的覆盖
            FileUtils.copyFileByChannel(zipFile.path, curMissionPath)
        }.subscribeOn(Schedulers.io()).subscribe()

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE_CHOOSER) {
            data?.apply {
                getData()?.let {
                    curMissionPath = getPath(context, it)
                    checkPath()
                }
            }

        }

        if (requestCode == OPEN_DOCUMENT_TREE) {
            grantUriPermission(  data)
        }


        if (requestCode == OPEN_MANAGE_EXTERNAL_STORAGE
             && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            showFileChooser()
        }


        // 处理文件选择结果
        if (requestCode == REQUEST_CODE_IMPORT_KML && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // 读取 KML 文件内容
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val kmlData: Map<String, Object> = readLatLngsFromKML(inputStream)

                        var points = kmlData["points"] as? List<LatLng>?: emptyList()
                        // 将 DJILatLng 转换为 LatLng
                        routePoints = points.map { djilatLng ->
                            DJILatLng(djilatLng.latitude, djilatLng.longitude)  // 假设 DJILatLng 和 LatLng 结构相同
                        }

                        kmlHeight = kmlData["height"] as? Double?:5.0
                        kmlSpeed = kmlData["speed"] as? Double?:5.0
                        kmlTime = kmlData["speed"] as? Double?:0.0
                        kmlWaypointDistance = kmlData["waypointDistance"] as? Double?:0.0
                        Log.d(TAG, "kmlSpeed：${kmlSpeed}")
                        Log.d(TAG, "kmlSpeed：${kmlData["speed"]}")
                        Log.d(TAG, "kmlHeight：${kmlData["height"]}")
                        Log.d(TAG, "kmlTime：${kmlData["time"]}")
                        Log.d(TAG, "kmlWaypointDistance：${kmlData["waypointDistance"]}")

                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showToast( "无法导入 KML 文件", Toast.LENGTH_SHORT)
                }
            }
        }

        if (resultCode == RESULT_OK && requestDataMap.containsKey(requestCode)) {
            // 获取保存的数据
            val kmlData = requestDataMap[requestCode] as? Map<String, Any> ?: emptyMap()
            requestDataMap.remove(requestCode)  // 移除已处理的数据

            val uri = data?.data
            if (uri != null) {
                saveKMLToFile(uri, kmlData)  // 使用非空的 uri
            } else {
                // 处理 uri 为 null 的情况
                // 可以进行日志记录、错误提示等
            }
        }

    }

    // 保存 KML 文件到用户选择的路径
    private fun saveKMLToFile(uri: Uri, kmlData: Map<String, Any>) {
        try {
            val outputStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
            outputStream?.let {
                // 替换 maptool.writeLatLngsToKML 方法保存数据
                maptool?.writeLatLngsToKML(it, kmlData)
                it.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    fun readLatLngsFromKML(inputStream: InputStream): List<LatLng> {
//        val points = mutableListOf<LatLng>()
//        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
//        var line: String?
//
//        while (reader.readLine().also { line = it } != null) {
//            // 查找包含 <coordinates> 标签的行
//            line?.trim()?.let {
//                if (it.startsWith("<coordinates>") && it.endsWith("</coordinates>")) {
//                    val coordinates = it
//                        .replace("<coordinates>", "")
//                        .replace("</coordinates>", "")
//                        .trim()
//                    val parts = coordinates.split(",")
//                    if (parts.size >= 2) {
//                        try {
//                            val longitude = parts[0].toDouble()
//                            val latitude = parts[1].toDouble()
//                            points.add(LatLng(latitude, longitude))
//                        } catch (e: NumberFormatException) {
//                            e.printStackTrace()
//                        }
//                    }
//                }
//            }
//        }
//        reader.close()
//        return points
//    }

    fun readLatLngsFromKML(inputStream: InputStream): Map<String, Object> {
        val resultMap = mutableMapOf<String, Object>() // 用于存储返回的 Map
        val points = mutableListOf<LatLng>() // 用于存储 LatLng 列表
        var height: Double? = null  // 假设读取的高度数据
        var speed: Double? = null   // 假设读取的速度数据
        var time: Double? = null   // 假设读取的速度数据
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            line?.trim()?.let {
                // 查找包含 <coordinates> 标签的行
                if (it.startsWith("<coordinates>") && it.endsWith("</coordinates>")) {
                    val coordinates = it
                        .replace("<coordinates>", "")
                        .replace("</coordinates>", "")
                        .trim()
                    val parts = coordinates.split(",")
                    if (parts.size >= 2) {
                        try {
                            val longitude = parts[0].toDouble()
                            val latitude = parts[1].toDouble()
                            points.add(LatLng(latitude, longitude))
                        } catch (e: NumberFormatException) {
                            e.printStackTrace()
                        }
                    }
                }

                // 假设你还需要从 KML 中解析 <height> 和 <speed> 等信息
                if (it.contains("<height>")) {
                    val heightMatch = it.replace("<height>", "").replace("</height>", "").trim()
                    height = heightMatch.toDoubleOrNull()  // 解析高度
                }

                if (it.contains("<speed>")) {
                    val speedMatch = it.replace("<speed>", "").replace("</speed>", "").trim()
                    speed = speedMatch.toDoubleOrNull()  // 解析速度
                }

                if (it.contains("<time>")) {
                    val speedMatch = it.replace("<time>", "").replace("</time>", "").trim()
                    time = speedMatch.toDoubleOrNull()  // 解析速度
                }
            }
        }

        // 将解析的数据存储到 Map 中
        resultMap["points"] = points as Object
        height?.let { resultMap["height"] = it as Object}  // 如果有高度数据，存储
        speed?.let { resultMap["speed"] = it as Object}    // 如果有速度数据，存储
        time?.let { resultMap["time"] = it as Object}    // 如果有速度数据，存储

        reader.close()
        return resultMap
    }

    fun checkPath(){
        if (!curMissionPath.contains(".kmz") && !curMissionPath.contains(".kml")) {
            ToastUtils.showToast("Please choose KMZ/KML file")
        } else {

            // Choose a directory using the system's file picker.
            showPermisssionDucument()

            if (curMissionPath.contains(".kml") ){
                if (WPMZManager.getInstance().transKMLtoKMZ(curMissionPath , "" , getHeightMode())) {
                    curMissionPath  =   Environment.getExternalStorageDirectory()
                        .toString() + "/DJI/" + requireContext().packageName + "/KMZ/OutPath/" + getName(curMissionPath) + ".kmz"
                    ToastUtils.showToast("Trans kml success " + curMissionPath)
                } else {
                    ToastUtils.showToast("Trans kml failed!")
                }
            } else {
                ToastUtils.showToast("KMZ file path:${curMissionPath}")
                markWaypoints()
            }
        }
    }
    fun getName(path: String): String? {
        val start = path.lastIndexOf("/")
        val end = path.lastIndexOf(".")
        return if (start != -1 && end != -1) {
            path.substring(start + 1, end)
        } else {
            "unknow"
        }
    }
    fun showPermisssionDucument() {
        val canWrite: Boolean =
            DocumentsUtils.checkWritableRootPath(context, curMissionPath)
        if (!canWrite && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val storageManager =
                requireActivity().getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volume: StorageVolume? =
                storageManager.getStorageVolume(File(curMissionPath))
            if (volume != null) {
                val intent = volume.createOpenDocumentTreeIntent()
                startActivityForResult(intent, OPEN_DOCUMENT_TREE)
                return
            }
        }
    }

    fun showFileChooser(){
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(
            Intent.createChooser(intent, "Select KMZ File"), OPEN_FILE_CHOOSER
        )
    }
    fun grantUriPermission(data: Intent?) {

        val uri = data!!.data
        requireActivity().grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val takeFlags = data.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
        requireActivity().getContentResolver().takePersistableUriPermission(uri!!, takeFlags)
    }

    fun getPath(context: Context?, uri: Uri?): String {
        if (DocumentsContract.isDocumentUri(context, uri) && isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).toTypedArray()
            if (split.size != validLenth) {
                return ""
            }
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
            } else {
                return getExtSdCardPaths(requireContext()).get(0)!! + "/" + split[1]
            }
        }
        return ""
    }

    private fun getExtSdCardPaths(context: Context): ArrayList<String?> {
        var sExtSdCardPaths = ArrayList<String?>()
        for (file in context.getExternalFilesDirs("external")) {
            if (file != null && file != context.getExternalFilesDir("external")) {
                val index = file.absolutePath.lastIndexOf("/Android/data")
                if (index >= 0) {
                    var path: String? = file.absolutePath.substring(0, index)
                    try {
                        path = File(path).canonicalPath
                    } catch (e: IOException) {
                        LogUtils.e(logTag, e.message)
                    }
                    sExtSdCardPaths.add(path)
                }
            }
        }
        if (sExtSdCardPaths.isEmpty()) {
            sExtSdCardPaths.add("/storage/sdcard1")
        }
        return sExtSdCardPaths
    }

    fun isExternalStorageDocument(uri: Uri?): Boolean {
        return "com.android.externalstorage.documents" == uri?.authority
    }

    private fun initData() {
        wayPointV3VM.listenFlightControlState()

        wayPointV3VM.flightControlState.observe(viewLifecycleOwner) {
            it?.let {
                wayline_aircraft_height?.text = String.format("Aircraft Height: %.2f", it.height)
                wayline_aircraft_distance?.text =
                    String.format("Aircraft Distance: %.2f", it.distance)
                wayline_aircraft_speed?.text = String.format("Aircraft Speed: %.2f", it.speed)
            }
        }
    }

    @IntDef(
        MapProvider.MAP_AUTO,
        MapProvider.AMAP_PROVIDER,
        MapProvider.MAPLIBRE_PROVIDER,
        MapProvider.GOOGLE_PROVIDER
    )
    annotation class MapProvider {
        companion object {
            const val MAP_AUTO = 0
            const val AMAP_PROVIDER = 1
            const val MAPLIBRE_PROVIDER = 2
            const val GOOGLE_PROVIDER = 3
        }
    }

    private fun createMapView(savedInstanceState: Bundle?) {
        val onMapReadyListener = MapWidget.OnMapReadyListener { map ->
            map.setMapType(DJIMap.MapType.NORMAL)
        }
        map_widget.initAMap(onMapReadyListener)

        map_widget.onCreate(savedInstanceState) //需要再init后调用否则Amap无法显示

//        map_widget.mapView
//        MapView mapView = null;
//        mapView = AMapView(context)
//        mMapView = (MapView) findViewById(R.id.map_widget);
//
//        val myLocationStyle: MyLocationStyle
//        myLocationStyle =
//            MyLocationStyle() //初始化定位蓝点样式类myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);//连续定位、且将视角移动到地图中心点，定位点依照设备方向旋转，并且会跟随设备移动。（1秒1次定位）如果不设置myLocationType，默认也会执行此种模式。
//
//        myLocationStyle.interval(2000) //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
//
//        map_widget.
//
//        map_widget.map.setMyLocationStyle(myLocationStyle) //设置定位蓝点的Style
//        map_widget.setMyLocationStyle(myLocationStyle) //设置定位蓝点的Style
//
////aMap.getUiSettings().setMyLocationButtonEnabled(true);设置默认定位按钮是否显示，非必需设置。
////aMap.getUiSettings().setMyLocationButtonEnabled(true);设置默认定位按钮是否显示，非必需设置。
//        map_widget.setMyLocationEnabled(true) // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。


    }

    override fun onPause() {
        super.onPause()
        map_widget.onPause()
    }

    override fun onResume() {
        super.onResume()
        map_widget.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map_widget.onDestroy()

        clearSharedPreferences() // 清空所有数据
        // 停止更新任务以避免内存泄漏
        updateJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        wayPointV3VM.cancelListenFlightControlState()
        wayPointV3VM.removeAllMissionStateListener()
        wayPointV3VM.clearAllWaylineExecutingInfoListener()
        wayPointV3VM.clearAllWaypointActionListener()

        mDisposable?.let {
            if (!it.isDisposed) {
                it.dispose()
            }
        }

    }

    fun getErroMsg(error: IDJIError): String {
        if (!TextUtils.isEmpty(error.description())) {
            return error.description();
        }
        return error.errorCode()
    }

    fun showMultiChoiceDialog(waylineids: List<Int>) {
        var items: ArrayList<String> = ArrayList()
        waylineids
            .filter {
                it >= 0
            }
            .map {
                items.add(it.toString())
            }

        val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
        builder.setTitle("Select Wayline")
        builder.setPositiveButton("OK", null)
        builder.setMultiChoiceItems(
            items.toTypedArray(),
            null,
            object : OnMultiChoiceClickListener {
                override fun onClick(p0: DialogInterface?, index: Int, isSelect: Boolean) {
                    if (isSelect) {
                        selectWaylines.add(index)
                    } else {
                        selectWaylines.remove(index)
                    }
                }
            }).create().show()

    }

    fun markWaypoints() {
        // version参数实际未用到
        var waypoints: ArrayList<WaylineExecuteWaypoint> = ArrayList<WaylineExecuteWaypoint>()
        val parseInfo = JNIWPMZManager.getWaylines("1.0.0", curMissionPath)
        var waylines = parseInfo.waylines
        waylines.forEach() {
            waypoints.addAll(it.waypoints)
            markLine(it.waypoints)
        }
        waypoints.forEach() {
            markWaypoint(DJILatLng(it.location.latitude, it.location.longitude), it.waypointIndex)
        }
    }

    fun markWaypoint(latlong: DJILatLng, waypointIndex: Int) : DJIMarker?{
        var markOptions = DJIMarkerOptions()
        markOptions.position(latlong)
        markOptions.icon(getMarkerRes(waypointIndex, 0f))
        markOptions.title(waypointIndex.toString())
        markOptions.isInfoWindowEnable = true
       return map_widget.map?.addMarker(markOptions)
    }

    fun markLine(waypoints: List<WaylineExecuteWaypoint>) {

        var djiwaypoints = waypoints.filter {
            true
        }.map {
            DJILatLng(it.location.latitude, it.location.longitude)
        }
        var lineOptions = DJIPolylineOptions()
        lineOptions.width(5f)
        lineOptions.color(Color.GREEN)
        lineOptions.addAll(djiwaypoints)
        map_widget.map?.addPolyline(lineOptions)
    }



    /**
     * Convert view to bitmap
     * Notice: recycle the bitmap after use
     */
    fun getMarkerBitmap(
        index: Int,
        rotation: Float,
    ): Bitmap? {
        // create View for marker
        @SuppressLint("InflateParams") val markerView: View =
            LayoutInflater.from(activity)
                .inflate(R.layout.waypoint_marker_style_layout, null)
        val markerBg = markerView.findViewById<ImageView>(R.id.image_content)
        val markerTv = markerView.findViewById<TextView>(R.id.image_text)
        markerTv.text = index.toString()
        markerTv.setTextColor(AndUtil.getResColor(R.color.blue))
        markerTv.textSize =
            AndUtil.getDimension(R.dimen.mission_waypoint_index_text_large_size)

        markerBg.setImageResource(R.mipmap.mission_edit_waypoint_normal)

        markerBg.rotation = rotation
        // convert view to bitmap
        markerView.destroyDrawingCache()
        markerView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)
        markerView.isDrawingCacheEnabled = true
        return markerView.getDrawingCache(true)
    }

    private fun getMarkerRes(
        index: Int,
        rotation: Float,
    ): DJIBitmapDescriptor? {
        return DJIBitmapDescriptorFactory.fromBitmap(
            getMarkerBitmap(index , rotation)
        )
    }

    fun showWaypoints(){
        var loction2D = showWaypoints.last().waylineWaypoint.location
        val waypoint =  DJILatLng(loction2D.latitude , loction2D.longitude)
       var pointMarker =  markWaypoint(waypoint , getCurWaypointIndex())
        pointMarkers.add(pointMarker)
    }

    fun getCurWaypointIndex():Int{
        if (showWaypoints.size <= 0) {
            return 0
        }
        return showWaypoints.size
    }
    private fun showWaypointDlg( djiLatLng: DJILatLng ,callbacks: CommonCallbacks.CompletionCallbackWithParam<WaypointInfoModel>) {
        val builder = AlertDialog.Builder(requireActivity())
        val dialog = builder.create()
        val dialogView = View.inflate(requireActivity(), R.layout.dialog_add_waypoint, null)
        dialog.setView(dialogView)

        val etHeight = dialogView.findViewById<View>(R.id.et_height) as EditText
        val etSpd = dialogView.findViewById<View>(R.id.et_speed) as EditText
        val viewActionType = dialogView.findViewById<View>(R.id.action_type) as DescSpinnerCell
        val btnLogin = dialogView.findViewById<View>(R.id.btn_add) as Button
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel) as Button

        btnLogin.setOnClickListener {
            var waypointInfoModel =  WaypointInfoModel()
            val waypoint = WaylineWaypoint()
            waypoint.waypointIndex = getCurWaypointIndex()
            val location = WaylineLocationCoordinate2D(djiLatLng.latitude , djiLatLng.longitude)
            waypoint.location = location
            waypoint.height = etHeight.text.toString().toDouble()
            // 根据坐标类型，如果为egm96 需要加上高程差
            waypoint.ellipsoidHeight = etHeight.text.toString().toDouble()
            waypoint.speed = etSpd.text.toString().toDouble()
            waypoint.useGlobalTurnParam = true
            waypointInfoModel.waylineWaypoint = waypoint
            val actionInfos: MutableList<WaylineActionInfo> = ArrayList()
            actionInfos.add(KMZTestUtil.createActionInfo(getCurActionType(viewActionType)))
            waypointInfoModel.waylineWaypoint = waypoint
            waypointInfoModel.actionInfos = actionInfos
            callbacks.onSuccess(waypointInfoModel)
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun getHeightMode(): HeightMode {
        return  when(heightmode.getSelectPosition()){
           0 -> HeightMode.WGS84
           1-> HeightMode.EGM96
           2 -> HeightMode.RELATIVE
            else -> {
                HeightMode.WGS84
            }
        }
    }
    private fun getResumeType(): RecoverActionType {
        return  when(resumeType.getSelectPosition()){
            0 -> RecoverActionType.GoBackToRecordPoint
            1 -> RecoverActionType.GoBackToNextPoint
            2 -> RecoverActionType.GoBackToNextNextPoint
            else -> {
                RecoverActionType.GoBackToRecordPoint
            }
        }
    }

    private fun getCurActionType(viewActionType: DescSpinnerCell): WaypointActionType? {
        return when (viewActionType.getSelectPosition()) {
            0 -> WaypointActionType.START_TAKE_PHOTO
            1 -> WaypointActionType.START_RECORD
            2 -> WaypointActionType.STOP_RECORD
            3 -> WaypointActionType.GIMBAL_PITCH
            else -> {
                WaypointActionType.START_TAKE_PHOTO
            }
        }
    }
    private  fun removeAllPoint(){
        pointMarkers.forEach{
            it?.let {
                it.remove()
            }
        }
    }
}