package dji.sampleV5.aircraft.pages


import android.annotation.SuppressLint
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
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.activityViewModels
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.dji.industry.mission.DocumentsUtils
import com.dji.wpmzsdk.common.data.HeightMode
import com.dji.wpmzsdk.common.data.Template
import com.dji.wpmzsdk.common.utils.kml.model.WaypointActionType
import com.dji.wpmzsdk.manager.WPMZManager
import dji.sampleV5.aircraft.PhotoProcessingWorker
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.MediaVM
import dji.sampleV5.aircraft.models.WayPointV3VM
import dji.sampleV5.aircraft.util.DialogUtil
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.utils.KMZTestUtil
import dji.sampleV5.aircraft.utils.KMZTestUtil.createWaylineMission
import dji.sampleV5.aircraft.utils.wpml.WaypointInfoModel
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
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
import kotlinx.android.synthetic.main.frag_waypointv3_page.*
import kotlinx.android.synthetic.main.view_mission_setting_home.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*
import java.util.regex.Pattern
import kotlin.math.pow


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



    private val showWaypoints : ArrayList<WaypointInfoModel> = ArrayList()
    private val pointMarkers : ArrayList<DJIMarker?> = ArrayList()
    var curMissionPath = ""
    val rootDir = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), WAYPOINT_SAMPLE_FILE_DIR)
    var validLenth: Int = 2
    var curMissionExecuteState: WaypointMissionExecuteState? = null
    var selectWaylines: ArrayList<Int> = ArrayList()

    private val mediaVM: MediaVM by activityViewModels()
    private var isFullScreen = false

    private val TAG = "OpencvpictureActivity"
    private val SHARED_PREFS_NAME = "WorkerData"

    var i = 0

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

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(context, "OpenCV初始化失败", Toast.LENGTH_SHORT).show()
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }



        // 设置点击事件来切换视图
        widget_primary_fpv.setOnClickListener { switchToFullScreen(widget_primary_fpv, map_widget) }

        prepareMissionData()
        initView(savedInstanceState)
        initData()
        WPMZManager.getInstance().init(ContextUtil.getContext())

        mediaVM.init()

//        takePhoto()
        clearSharedPreferences()
        i = 0

    }

    private fun switchToFullScreen(fullScreenView: View, thumbnailView: View) {
        val constraintLayout = fpv_holder
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        if (isFullScreen) {
            // 当前是全屏模式，切换为缩略模式
            constraintSet.constrainWidth(fullScreenView.id, 400)
            constraintSet.constrainHeight(fullScreenView.id, 400)
            constraintSet.connect(fullScreenView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            constraintSet.connect(fullScreenView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.connect(fullScreenView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)


            constraintSet.constrainWidth(thumbnailView.id,ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(thumbnailView.id, 900)
            constraintSet.connect(thumbnailView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
//            constraintSet.connect(thumbnailView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
            constraintSet.connect(thumbnailView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            constraintSet.connect(thumbnailView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)

            fullScreenView.bringToFront()
        } else {
            // 当前是缩略模式，切换为全屏模式
            constraintSet.constrainWidth(fullScreenView.id,2200)
            constraintSet.constrainHeight(fullScreenView.id, 900)
            constraintSet.connect(fullScreenView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
//            constraintSet.connect(fullScreenView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
            constraintSet.connect(fullScreenView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
//            constraintSet.connect(fullScreenView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)

            constraintSet.constrainWidth(thumbnailView.id, 400)
            constraintSet.constrainHeight(thumbnailView.id, 400)
            constraintSet.connect(thumbnailView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            constraintSet.connect(thumbnailView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
            constraintSet.connect(thumbnailView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)

            // 将 thumbnailView 提到前台
            thumbnailView.bringToFront()
        }

        constraintSet.applyTo(constraintLayout)

        isFullScreen = !isFullScreen
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
            mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("take photo success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("take photo failed")
                }
            })
        }

        btn_download_photo_spf.setOnClickListener {
//            var bitmap: ByteArray? = downloadPhotoByteArray()
            var path1: String? = downloadPhotoFixedPath()
//            val path1: String = picturearray.get(i)
            Log.d(TAG, "picturearray: $path1")

            var resultValue: Double = 0.0

            if (path1 != null) {
                initDownloadPhoto(path1, 20.0, 0.04, 4.5 / 1000 / 1000) { result ->
                    resultValue = result
                    Log.d(TAG, "回调返回的结果: $resultValue")
                    // 这里可以使用 resultValue 变量做进一步处理
                }
            }

        }

    }

    // 任务队列
    private val taskQueue = LinkedList<() -> Unit>()

    // 当前是否正在执行任务
    private var isRunning = false

    fun initDownloadPhoto(
        path1: String,
        BaseLine: Double,
        FocalLength: Double,
        PixelDim: Double,
        callback: (Double) -> Unit
    ) {
        // 将任务添加到队列中
        taskQueue.add {

            // 创建 WorkRequest
            val photoProcessingWorkRequest: WorkRequest = OneTimeWorkRequest.Builder(PhotoProcessingWorker::class.java)
                .setInputData(
                    Data.Builder()
                        .putString("photo_path", path1)
                        .putDouble("photo_baseLine", BaseLine)
                        .putDouble("photo_focallength", FocalLength)
                        .putDouble("photo_pixeldim", PixelDim)
                        .build()
                )
                .build()

            // 获取 WorkManager 实例
            val workManager = WorkManager.getInstance(requireContext())

            // 启动工作
            workManager.enqueue(photoProcessingWorkRequest)

            // 监听结果
            workManager.getWorkInfoByIdLiveData(photoProcessingWorkRequest.id).observe(
                viewLifecycleOwner
            ) { workInfo: WorkInfo? ->
                if (workInfo != null && workInfo.state.isFinished) {
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        val outputData = workInfo.outputData
                        val resultValue = outputData.getDouble("result_value", 0.0)
                        // 使用 resultValue
                        Log.d(TAG, "resultValue: $resultValue")
                        callback(resultValue) // 调用回调函数
                    } else if (workInfo.state == WorkInfo.State.FAILED) {
                        // 处理失败情况
                        callback(1200.0) // 处理失败时也调用回调函数返回一个默认值
                    }
                    // 任务完成后重置标志，并处理下一个任务
                    isRunning = false
                    processNextTask()
                }
            }


            i++

        }

        // 如果当前没有任务在运行，处理队列中的任务
        if (!isRunning) {
            processNextTask()
        }

    }

    // 处理队列中的下一个任务
    private fun processNextTask() {
        if (taskQueue.isNotEmpty()) {
            isRunning = true
            val task = taskQueue.poll()
            task?.invoke()
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

    fun downloadPhotoFixedPath() : String? {
        var bitmap: String? = null
        // 获取文件列表
        // 从摄像头中获取指定数量和从指定索引开始的媒体文件,mediaFileListData 会更新
        mediaVM.pullMediaFileListFromCamera(-1, 1)

        // 你可以在 mediaFileListData 更新后再处理选择逻辑，以确保数据已经更新
        mediaVM.mediaFileListData.observe(viewLifecycleOwner) {
            // 下载文件
            val mediafiles: ArrayList<MediaFile> = ArrayList(mediaVM.mediaFileListData.value?.data!!)
            bitmap = mediaVM.downloadMediaFileFixedPath(mediafiles)
        }
        return bitmap
    }

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