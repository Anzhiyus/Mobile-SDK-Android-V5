package dji.sampleV5.aircraft.pages

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
import android.widget.*
import androidx.annotation.IntDef
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.amap.api.maps.model.LatLng
import com.dji.industry.mission.DocumentsUtils
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.common.data.Template
import com.dji.wpmzsdk.common.utils.kml.model.WaypointActionType
import com.dji.wpmzsdk.manager.WPMZManager
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
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptorFactory
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_add_waypoint.view.*
import kotlinx.android.synthetic.main.frag_virtual_stick_page.simulator_state_info_tv
import kotlinx.android.synthetic.main.frag_virtual_stick_page.widget_horizontal_situation_indicator
import kotlinx.android.synthetic.main.frag_waypointv3_page.*
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
import java.text.SimpleDateFormat
import java.util.*
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
class DroneFlyFragment : DJIFragment() {

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
    private val deviation: Double = 0.02

    var routePoints: List<LatLng> = mutableListOf()
    // 声明全局变量，用来存储无人机当前位置
    var droneCurrentLocation: LatLng? = null
    // 全局变量：地球半径（单位：米）
    val EARTH_RADIUS = 6371e3
    // 当前航点索引（持久化变量，可存储在文件、数据库或 SharedPreferences 中）
    var currentIndex: Int = 1
    // 全局变量：默认方位角
    var droneLastAzimuth: Double = 0.0
    var droneCurrentAzimuth: Double = 0.0
    var isActive:Boolean = true
    private var kmlSpeed: Double = 5.00
    private var kmlHeight: Double = 5.00
    private var kmlTime: Double = 0.00
    var gpsFileNamePath: String = ""

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
        return inflater.inflate(R.layout.spf_frag_waypointv3_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 虚拟摇杆
        widget_horizontal_situation_indicator.setSimpleModeEnable(true)
        initBtnClickListener()
        // 摇杆监听
        virtualStickVM.listenRCStick()
        simulatorVM.simulatorStateSb.observe(viewLifecycleOwner) {
            simulator_state_info_tv.text = it
        }

        // 在应用启动时，读取保存的数据，currentIndex：上次飞行航点索引
        val sharedPreferences = requireContext().getSharedPreferences("AppData", Context.MODE_PRIVATE)
        currentIndex = sharedPreferences.getInt("currentIndex", 0) // 默认从0开始

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(context, "OpenCV初始化失败", Toast.LENGTH_SHORT).show()
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        thumbnail_click_overlay.setOnClickListener {
            Log.d(TAG,"窗口点击事件")
            switchViews(main_window, thumbnail_window_left, thumbnail_window, isLeftThumbnail = false)
        }
        thumbnail_click_overlay_left.setOnClickListener {
            Log.d(TAG,"窗口点击事件")
            switchViews(main_window, thumbnail_window_left, thumbnail_window, isLeftThumbnail = true)
        }

        prepareMissionData()
        initView(savedInstanceState)
        initData()
        startTaskQueueConsumer()
        WPMZManager.getInstance().init(ContextUtil.getContext())

        mediaVM.init()

//        takePhoto() //注销
        clearSharedPreferences()
        i = 0

    }

    private fun initBtnClickListener() {
        // 开启虚拟摇杆
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
//            virtualStickVM.setRightPosition(200,0)
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
//            if (curMissionExecuteState == WaypointMissionExecuteState.READY) {
//                ToastUtils.showToast("Mission not start")
//                return@setOnClickListener
//            }
//            if (TextUtils.isEmpty(curMissionPath)){
//                ToastUtils.showToast("curMissionPath is Empty")
//                return@setOnClickListener
//            }
//            wayPointV3VM.stopMission(
//                FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
//                object : CommonCallbacks.CompletionCallback {
//                    override fun onSuccess() {
//                        ToastUtils.showToast("stopMission Success")
//                    }
//
//                    override fun onFailure(error: IDJIError) {
//                        ToastUtils.showToast("stopMission Failed " + getErroMsg(error))
//                    }
//                })

            // 取消当前任务
            if (isActive)
            {
                isActive = false
                Log.d(TAG, "当前任务已取消")
                btn_mission_stop.text = "恢复任务SPF"
            }else{
                isActive = true
                Log.d(TAG, "当前任务已开始")
                btn_mission_stop.text = "暂停任务SPF"
                // 启动任务
                currentTaskJob = lifecycleScope.launch {
                    enqueueTask {
                        performTask()
                    }
                }
            }

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
            mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("take photo success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("take photo failed")
                }
            })
        }

//        // 读取视频流中的数据：

        // 按钮点击事件：启动任务
        btn_download_photo_spf.setOnClickListener {

            // 更新全局变量
            val location = getAircraftLocation()
            droneCurrentLocation = LatLng(location.latitude, location.longitude)

            // 启动新的任务
            currentTaskJob = lifecycleScope.launch {
                enqueueTask {
                    performTask()
                }
            }
        }

//        // 按钮点击事件：取消任务
//        btn_cancel_task.setOnClickListener {
//            // 取消当前任务
//            currentTaskJob?.cancel()
//            currentTaskJob = null
//            Log.d("Task", "当前任务已取消")
//        }

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

//        btn_download_photo_spf.setOnClickListener {
//            Log.d(TAG, "Log：DJI开始")
//            lifecycleScope.launch {
//                val path = downloadPhotoFixedPath()
//                Log.d(TAG, "path: $path")
//                if (path != null) {
//                    val resultValue = downloadPhotoSuspend(path, 27.7, 0.01229, 3.3 / 1000 / 1000, requireContext())
//                    Log.d(TAG, "DJI回调返回的结果: $resultValue")
//                } else {
//                    Log.d(TAG, "DJI回调返回的结果: 下载失败，无法获取路径")
//                }
//            }
//        }


//        // 读取本地文件夹中的数据：
//        btn_download_photo_spf.setOnClickListener {
//            Log.d(TAG, "Log：DJI开始")
//            lifecycleScope.launch {
//                try {
//                    for (i in pictureArray.indices) {
//                        val path = pictureArray[i]
//                        if (path != null) {
//                            val resultValue = downloadPhotoSuspend(path, 27.7, 0.01229, 3.3 / 1000 / 1000, requireContext())
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
//        }
    }

    // 定义一个全局的 Job，用于控制任务取消
    private var currentTaskJob: Job? = null
    // 定义一个任务队列（Channel）
    private val taskQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
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
    // 添加任务到队列中
    fun enqueueTask(task: suspend () -> Unit) {
        lifecycleScope.launch {
            taskQueue.send(task)
        }
    }
    // 任务函数逻辑
    suspend fun performTask() {

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

        // 首先，调整高度
        val location = getAircraftLocation()
        var diffHeight = kmlHeight - location.altitude
        val time = (diffHeight / 5) // 将飞行时间转换为 Int
        // 发送虚拟杆参数：控制无人机向上飞行5m/s
        sendVirtualStickParameters(time, 0.0,0.0,0.0,5.0,
            sendAction = { param ->
                virtualStickVM.sendVirtualStickAdvancedParam(param)
            }
        )

        var flyTime = kmlTime + (routePoints.size-currentIndex)*25/60
        ToastUtils.showToast( "航线预计飞行时间min:"+flyTime*100/100, Toast.LENGTH_SHORT)
//        currentIndex= 1

        if (routePoints.isEmpty()) {
            Log.d(TAG, "航线点为空，无法执行任务")
            return
        }
        if (isActive) {
            Log.d(TAG, "航线正在运行：$isActive")
        }else{
            Log.d(TAG, "航线已经暂停：$isActive")
        }
        Log.d(TAG, "routePoints.size：${routePoints.size}")
//        kmlHeight=5.00
        Log.d(TAG, "kmlHeight：${kmlHeight}")
//        kmlSpeed=4.00
        Log.d(TAG, "kmlSpeed：${kmlSpeed}")

        while (currentIndex < routePoints.size && isActive) {
            Log.d(TAG, "所有航线点已完成飞行任务：$isActive")
            Log.d(TAG, "downloadPhotoFixedPath: start")

            var startTime = System.nanoTime()
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
                    27.7,
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
            resultValue = kmlHeight + 5.0  // 预设调整距离
            resultValue = resultValue - kmlHeight

            Log.d(TAG, "VirtualStick: start：$currentIndex")
            val start: LatLng
            val end: LatLng

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

            // 计算两点之间的距离和方位角
            var distance = calculateDistance(start, end)*100/100
            distance = (distance* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
            var azimuth = calculateBearing(start, end)
            Log.d(TAG, "azimuth：$azimuth")
            azimuth = (azimuth* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入
            if (azimuth > 180) {
                azimuth -= 360
            }
            Log.d(TAG, "azimuth2：$azimuth")

            // 计算飞行时间
            val time = (distance / kmlSpeed) // 将飞行时间转换为 Int
            var verticalSpeed = resultValue/time // 垂直速度分量
            verticalSpeed = (verticalSpeed* 10).roundToInt() / 10.0    //  保留一位小数，四舍五入

            // 判断 azimuth 与 globalAzimuth 的差值是否小于 1
            if (abs(azimuth - droneLastAzimuth) < 1) {
                // 差值小于 1，直接运行一次
                // 发送虚拟杆参数：azimuth控制无人机朝向，即镜头方向。northSpeed（东西）和eastSpeed（南北）控制路线（以镜头方向为正北方向）
                sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed,
                    sendAction = { param ->
                        virtualStickVM.sendVirtualStickAdvancedParam(param)
                    }
                )
            } else {
                // 发送虚拟杆参数，修改方位
                Log.d(TAG, "azimuth3：$azimuth")
                sendVirtualStickParameters(1.0, 0.0,0.0,azimuth,
                    sendAction = { param ->
                        virtualStickVM.sendVirtualStickAdvancedParam(param)
                    }
                )
                // 发送虚拟杆参数
                Log.d(TAG, "azimuth4：$azimuth")
                sendVirtualStickParameters(time, 0.0,kmlSpeed,azimuth,verticalSpeed,
                    sendAction = { param ->
                        virtualStickVM.sendVirtualStickAdvancedParam(param)
                    }
                )
            }
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

//        Log.d(TAG, "sendVirtualStickParameters: picth")
//        sendVirtualStickParameters(5.0, 0.0,5.0,0.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//
//        Log.d(TAG, "sendVirtualStickParameters: picth2")
//        sendVirtualStickParameters(5.0, 0.0,5.0,180.0,0.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//
//        Log.d(TAG, "sendVirtualStickParameters: picth3")
//        sendVirtualStickParameters(5.0, 0.0,5.0,90.0,0.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//
//        Log.d(TAG, "sendVirtualStickParameters: picth4")
//        sendVirtualStickParameters(5.0, 0.0,5.0,-45.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//
//        Log.d(TAG, "sendVirtualStickParameters: picth4")
//        sendVirtualStickParameters(5.0, 0.0,5.0,90.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//        Log.d(TAG,  "sendVirtualStickParameters: roll")
//        sendVirtualStickParameters(5.0, 0.0,5.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//        Log.d(TAG,  "sendVirtualStickParameters: picthroll")
//        sendVirtualStickParameters(5.0, 5.0,5.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
//
//        Log.d(TAG, "downloadPhotoFixedPath: start")
//        val path = try {
//            downloadPhotoFixedPath()
//        } catch (e: Exception) {
//            Log.e(TAG, "Download failed: ${e.message}")
//            null
//        }
//        Log.d(TAG, "downloadPhotoFixedPath: end")
//
//        Log.d("TaskQueue", "path: $path")
//        if (path != null) {
//            val resultValue = downloadPhotoSuspend(
//                path,
//                27.7,
//                0.01229,
//                3.3 / 1000 / 1000,
//                requireContext()
//            )
//            Log.d("TaskQueue", "DJI回调返回的结果: $resultValue")
//        } else {
//            Log.d("TaskQueue", "DJI回调返回的结果: 下载失败，无法获取路径")
//        }
//
//
//        Log.d(TAG, "sendVirtualStickParameters: picth2")
//        sendVirtualStickParameters(5, 5.0,5.0,
//            sendAction = { param ->
//                virtualStickVM.sendVirtualStickAdvancedParam(param)
//            }
//        )
    }



    // 计算两点之间的距离（单位：米）
    fun calculateDistance(start: LatLng, end: LatLng): Double {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)

        val dLat = endLat - startLat
        val dLng = endLng - startLng

        val a = sin(dLat / 2).pow(2) + cos(startLat) * cos(endLat) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

//    fun calculateDistance(point1: LatLng, point2: LatLng): Double {
//        val dx = point2.longitude - point1.longitude
//        val dy = point2.latitude - point1.latitude
//        return Math.sqrt(dx * dx + dy * dy) * 111000 // 简单近似，每经纬度差值约为111公里
//    }


    // 计算两点之间的方位角（相对于正北方向的顺时针角度，单位：度）
    fun calculateBearing(start: LatLng, end: LatLng): Double {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)

        val dLng = endLng - startLng
        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360 // 确保方位角为 0~360 度
    }

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
        sendAction: (VirtualStickFlightControlParam) -> Unit
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
//            println("第 ${iteration + 1} 次发送参数：$param")
            delay(200) // 每次间隔指定时间，即5Hz
        }
    }

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

    // 封装为函数，返回飞行器的位置
    fun getAircraftLocation(): LocationCoordinate3D {
        return KeyManager.getInstance().getValue(
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D),
            LocationCoordinate3D(0.0, 0.0, 0.0)  // 默认值
        )
    }

    // 封装为函数，返回云台的姿态
    // Attitude(pitch,roll,yaw)。pitch上下朝向0为水平，-90向下。yaw水平方向0为正北90为正东。
    fun getGimbalAttitude(): Attitude {
        return KeyManager.getInstance().getValue(
            KeyTools.createKey(GimbalKey.KeyGimbalAttitude),
            Attitude(0.0, 0.0, 0.0)  // 默认值
        )
    }


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


    var picturearray: Array<String> = arrayOf()  // 初始化为空数组
    var BaseLine: DoubleArray = doubleArrayOf()  // 初始化为空的 Double 数组

//    fun takePhoto() {
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
//    }

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

//    fun downloadPhotoFixedPath() : String? {
//        var bitmap: String? = null
//        // 获取文件列表
//        // 从摄像头中获取指定数量和从指定索引开始的媒体文件,mediaFileListData 会更新
//        mediaVM.pullMediaFileListFromCamera(-1, 1)
//
//        // 你可以在 mediaFileListData 更新后再处理选择逻辑，以确保数据已经更新
//        mediaVM.mediaFileListData.observe(viewLifecycleOwner) {
//            // 下载文件
//            val mediafiles: ArrayList<MediaFile> = ArrayList(mediaVM.mediaFileListData.value?.data!!)
//            bitmap = mediaVM.downloadMediaFileFixedPath(mediafiles)
//        }
//        Log.d(TAG,  "downloadPhotoFixedPath: String")
//        return bitmap
//    }

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

//        // 使用挂起函数等待 mediaFileListData 更新
//        val mediaFiles = suspendCancellableCoroutine<List<MediaFile>> { cont ->
//            mediaVM.mediaFileListData.observe(viewLifecycleOwner) { mediaFileList ->
//                if (!cont.isCompleted) {
//                    cont.resume(mediaFileList.data!!) // 返回获取到的数据
//                }
//            }
//        }

        // 转换为 ArrayList<MediaFile>
        val mediaFileList = ArrayList(mediaFiles)  // 将 mediaFiles 转换为 ArrayList<MediaFile>
        Log.d(dji.sampleV5.aircraft.models.TAG, "downloadPhotoFixedPath: mediaFileList.size：${mediaFileList.size}")

        // 下载文件
        var bitmap: String? = null
        bitmap = mediaVM.downloadMediaFileFixedPath(mediaFileList)
        Log.d(dji.sampleV5.aircraft.models.TAG, "downloadPhotoFixedPath: filePath：$bitmap")
        return bitmap
    }

    // onDownloadComplete回调
//    fun downloadPhotoFixedPath(onDownloadComplete: (String?) -> Unit) {
//        // 获取文件列表
//        mediaVM.pullMediaFileListFromCamera(-1, 1)
//
//        // 监听 mediaFileListData 的变化
//        mediaVM.mediaFileListData.observe(viewLifecycleOwner) {
//            // 确保数据更新后再处理
//            val mediafiles: ArrayList<MediaFile> = ArrayList(mediaVM.mediaFileListData.value?.data!!)
//
//            // 下载文件并在下载完成后通过回调返回结果
//            mediaVM.downloadMediaFileFixedPath(mediafiles) { filepath ->
//                onDownloadComplete(filepath)  // 文件路径或 null
//            }
//        }
//    }

//    suspend fun downloadPhotoFixedPath(): String? {
//        // 获取文件列表
//        mediaVM.pullMediaFileListFromCamera(-1, 1)
//
//        // 等待 mediaFileListData 的更新
//        val mediafiles = suspendCoroutine<List<MediaFile>?> { continuation ->
//            mediaVM.mediaFileListData.observe(viewLifecycleOwner) { mediaFileList ->
//                val files = mediaFileList.data
//                if (files != null) {
//                    continuation.resume(files) // 数据更新后返回列表
//                } else {
//                    continuation.resume(null)  // 处理为空的情况
//                }
//            }
//        }
//
//        // 确保 mediafiles 不为空并进行下载
//        return if (!mediafiles.isNullOrEmpty()) {
//            mediaVM.downloadMediaFileFixedPath(ArrayList(mediafiles))
//        } else {
//            null  // 当文件列表为空时，返回 null
//        }
//    }


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

                        routePoints = kmlData["points"] as? List<LatLng>?: emptyList()
                        kmlHeight = kmlData["height"] as? Double?:5.0
                        kmlSpeed = kmlData["speed"] as? Double?:5.0
                        kmlTime = kmlData["speed"] as? Double?:0.0
                        Log.d(TAG, "kmlSpeed：${kmlSpeed}")
                        Log.d(TAG, "kmlSpeed：${kmlData["speed"]}")
                        Log.d(TAG, "kmlHeight：${kmlData["height"]}")
                        Log.d(TAG, "kmlTime：${kmlData["time"]}")

                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showToast( "无法导入 KML 文件", Toast.LENGTH_SHORT)
                }
            }
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

        // 获取外部存储路径中的某一文件夹中的满足某一条件文件名的文件名数组。需手动放置数据
        // 路径示例：Android/data/cas.igsnrr.dronefly/files/Pictures/DJI/DJI***.jpg
        pictureArray = getMatchingFileNames(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/DJI_20241025",
            "^DJI_20241025.*\\.(jpg|JPG)"
        )
        ToastUtils.showToast( "DJI开始：${requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/DJI_20241025"}")
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