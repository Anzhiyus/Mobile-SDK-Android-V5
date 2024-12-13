package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.keyvalue.KeyValueDialogUtil
import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.util.Helper
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.virtualstick.OnScreenJoystick
import dji.sampleV5.aircraft.virtualstick.OnScreenJoystickListener
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.virtualstick.Stick
import dji.v5.utils.common.JsonUtil
import kotlinx.android.synthetic.main.frag_virtual_stick_page.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Class Description
 *
 * @author Hoker
 * @date 2021/5/11
 *
 * Copyright (c) 2021, DJI All Rights Reserved.
 */
class VirtualStickFragment : DJIFragment() {

    private val basicAircraftControlVM: BasicAircraftControlVM by activityViewModels()
    private val virtualStickVM: VirtualStickVM by activityViewModels()
    private val simulatorVM: SimulatorVM by activityViewModels()
    private val deviation: Double = 0.02

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.frag_virtual_stick_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        widget_horizontal_situation_indicator.setSimpleModeEnable(false)
        initBtnClickListener()
        initStickListener()
        virtualStickVM.listenRCStick()
        virtualStickVM.currentSpeedLevel.observe(viewLifecycleOwner) {
            updateVirtualStickInfo()
        }
        virtualStickVM.useRcStick.observe(viewLifecycleOwner) {
            updateVirtualStickInfo()
        }
        virtualStickVM.currentVirtualStickStateInfo.observe(viewLifecycleOwner) {
            updateVirtualStickInfo()
        }
        virtualStickVM.stickValue.observe(viewLifecycleOwner) {
            updateVirtualStickInfo()
        }
        virtualStickVM.virtualStickAdvancedParam.observe(viewLifecycleOwner) {
            updateVirtualStickInfo()
        }
        simulatorVM.simulatorStateSb.observe(viewLifecycleOwner) {
            simulator_state_info_tv.text = it
        }
    }

    private fun initBtnClickListener() {
        btn_enable_virtual_stick.setOnClickListener {
            virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("enableVirtualStick success.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("enableVirtualStick error,$error")
                }
            })
        }
        btn_disable_virtual_stick.setOnClickListener {
            virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("disableVirtualStick success.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("disableVirtualStick error,${error})")
                }
            })
        }
        btn_set_virtual_stick_speed_level.setOnClickListener {
            val speedLevels = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
            initPopupNumberPicker(Helper.makeList(speedLevels)) {
                virtualStickVM.setSpeedLevel(speedLevels[indexChosen[0]])
                resetIndex()
            }
        }
        btn_take_off.setOnClickListener {
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
        btn_landing.setOnClickListener {
            basicAircraftControlVM.startLanding(object :
                CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                override fun onSuccess(t: EmptyMsg?) {
                    ToastUtils.showToast("start landing onSuccess.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("start landing onFailure,$error")
                }
            })
        }
        btn_use_rc_stick.setOnClickListener {
            virtualStickVM.useRcStick.value = virtualStickVM.useRcStick.value != true
            if (virtualStickVM.useRcStick.value == true) {
                ToastUtils.showToast(
                    "After it is turned on," +
                            "the joystick value of the RC will be used as the left/ right stick value"
                )
            }
        }
        btn_set_virtual_stick_advanced_param.setOnClickListener {
            KeyValueDialogUtil.showInputDialog(
                activity, "Set Virtual Stick Advanced Param",
                JsonUtil.toJson(virtualStickVM.virtualStickAdvancedParam.value), "", false
            ) {
                it?.apply {
                    val param = JsonUtil.toBean(this, VirtualStickFlightControlParam::class.java)
                    if (param == null) {
                        ToastUtils.showToast("Value Parse Error")
                        return@showInputDialog
                    }
                    virtualStickVM.virtualStickAdvancedParam.postValue(param)
                }
            }
        }
        btn_send_virtual_stick_advanced_param.setOnClickListener {
//            virtualStickVM.virtualStickAdvancedParam.value?.let {
//                virtualStickVM.sendVirtualStickAdvancedParam(it)

//            // 启动一个协程
//            CoroutineScope(Dispatchers.Main).launch {
//                repeat(5) { iteration ->
//                    virtualStickVM.virtualStickAdvancedParam.value?.let {
//                        virtualStickVM.sendVirtualStickAdvancedParam(it)
//                        println("第 ${iteration + 1} 次发送参数：$it")
//                    }
//                    delay(200) // 间隔 200ms
//                }
//            }

//            // 启动一个协程
//            CoroutineScope(Dispatchers.Main).launch {
//                repeat(10*10-1) { iteration ->
//                    val param = VirtualStickFlightControlParam().apply {
//                        pitch = 5.0
//                        roll = 0.0
//                        yaw = 0.0
//                        verticalThrottle = 0.0
//                        verticalControlMode = VerticalControlMode.VELOCITY // 假设 0 表示 VELOCITY 模式
//                        rollPitchControlMode = RollPitchControlMode.VELOCITY // 假设 1 表示 ANGLE 模式
//                        yawControlMode = YawControlMode.ANGLE // 假设 1 表示 ANGLE 模式
//                        rollPitchCoordinateSystem = FlightCoordinateSystem.BODY // 假设 1 表示 BODY 坐标系
//                    }
//                    virtualStickVM.sendVirtualStickAdvancedParam(param)
//                    println("第 ${iteration + 1} 次发送参数：$it")
//                    delay(200) // 间隔 200ms
//                }
//            }

            virtualStickVM.virtualStickAdvancedParam.value?.let { itParam ->
                sendVirtualStickParameters(
                    durationInSeconds = 1,   // 持续时间 1 秒
                    pitch = itParam.pitch,   // 使用 itParam 中的 pitch 值
                    roll = itParam.roll,     // 使用 itParam 中的 roll 值
                    yaw = itParam.yaw,       // 使用 itParam 中的 yaw 值
                    verticalThrottle = itParam.verticalThrottle, // 使用 itParam 中的 verticalThrottle 值
                    verticalControlMode = itParam.verticalControlMode, // 使用 itParam 中的 verticalControlMode
                    rollPitchControlMode = itParam.rollPitchControlMode, // 使用 itParam 中的 rollPitchControlMode
                    yawControlMode = itParam.yawControlMode, // 使用 itParam 中的 yawControlMode
                    rollPitchCoordinateSystem = itParam.rollPitchCoordinateSystem, // 使用 itParam 中的 rollPitchCoordinateSystem
                    sendAction = {
                        // 直接使用 itParam
                        virtualStickVM.sendVirtualStickAdvancedParam(itParam)
                    }
                )
            }


        }
        btn_enable_virtual_stick_advanced_mode.setOnClickListener {
            virtualStickVM.enableVirtualStickAdvancedMode()
        }
        btn_disable_virtual_stick_advanced_mode.setOnClickListener {
            virtualStickVM.disableVirtualStickAdvancedMode()
        }
    }

    fun sendVirtualStickParameters(
        durationInSeconds: Int = 1,
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
        CoroutineScope(Dispatchers.Main).launch {
            repeat(durationInSeconds*5-1) { iteration ->
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
                println("第 ${iteration + 1} 次发送参数：$param")
                delay(200) // 每次间隔指定时间，即5Hz，每秒传递5次，大疆官方推荐
            }
        }
    }


    private fun initStickListener() {
        left_stick_view.setJoystickListener(object : OnScreenJoystickListener {
            override fun onTouch(joystick: OnScreenJoystick?, pX: Float, pY: Float) {
                var leftPx = 0F
                var leftPy = 0F

                if (abs(pX) >= deviation) {
                    leftPx = pX
                }

                if (abs(pY) >= deviation) {
                    leftPy = pY
                }

                virtualStickVM.setLeftPosition(
                    (leftPx * Stick.MAX_STICK_POSITION_ABS).toInt(),
                    (leftPy * Stick.MAX_STICK_POSITION_ABS).toInt()
                )
            }
        })
        right_stick_view.setJoystickListener(object : OnScreenJoystickListener {
            override fun onTouch(joystick: OnScreenJoystick?, pX: Float, pY: Float) {
                var rightPx = 0F
                var rightPy = 0F

                if (abs(pX) >= deviation) {
                    rightPx = pX
                }

                if (abs(pY) >= deviation) {
                    rightPy = pY
                }

                virtualStickVM.setRightPosition(
                    (rightPx * Stick.MAX_STICK_POSITION_ABS).toInt(),
                    (rightPy * Stick.MAX_STICK_POSITION_ABS).toInt()
                )
            }
        })
    }

    private fun updateVirtualStickInfo() {
        val builder = StringBuilder()
        builder.append("Speed level:").append(virtualStickVM.currentSpeedLevel.value)
        builder.append("\n")
        builder.append("Use rc stick as virtual stick:").append(virtualStickVM.useRcStick.value)
        builder.append("\n")
        builder.append("Is virtual stick enable:").append(virtualStickVM.currentVirtualStickStateInfo.value?.state?.isVirtualStickEnable)
        builder.append("\n")
        builder.append("Current control permission owner:").append(virtualStickVM.currentVirtualStickStateInfo.value?.state?.currentFlightControlAuthorityOwner)
        builder.append("\n")
        builder.append("Change reason:").append(virtualStickVM.currentVirtualStickStateInfo.value?.reason)
        builder.append("\n")
        builder.append("Rc stick value:").append(virtualStickVM.stickValue.value?.toString())
        builder.append("\n")
        builder.append("Is virtual stick advanced mode enable:").append(virtualStickVM.currentVirtualStickStateInfo.value?.state?.isVirtualStickAdvancedModeEnabled)
        builder.append("\n")
        builder.append("Virtual stick advanced mode param:").append(virtualStickVM.virtualStickAdvancedParam.value?.toJson())
        builder.append("\n")
        mainHandler.post {
            virtual_stick_info_tv.text = builder.toString()
        }
    }
}