package dji.sampleV5.aircraft

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import dji.sampleV5.aircraft.djicontroller.FunctionType
import dji.sampleV5.aircraft.pages.WayPointV3Fragment

class DroneActivity : AppCompatActivity() {

    private val mainContent by lazy { findViewById<LinearLayout>(R.id.main_content) }
    private val fragmentContainer by lazy { findViewById<FrameLayout>(R.id.fragment_container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drone)

//        // 动态加载 WayPointV3Fragment
//        if (savedInstanceState == null) {  // 确保 Fragment 只会添加一次
//            supportFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, WayPointV3Fragment())
//                .commit()
//        }

        // 初始化按钮点击事件
        findViewById<Button>(R.id.btn_waypoint_planning).setOnClickListener {
            navigateToWayPointFragment(FunctionType.WAYPOINT_PLANNING)
        }

        findViewById<Button>(R.id.btn_waypoint_flight).setOnClickListener {
            navigateToWayPointFragment(FunctionType.WAYPOINT_FLIGHT)
        }

        findViewById<Button>(R.id.btn_manual_flight).setOnClickListener {
            navigateToWayPointFragment(FunctionType.MANUAL_FLIGHT)
        }

        findViewById<Button>(R.id.btn_indoor_sim).setOnClickListener {
            navigateToWayPointFragment(FunctionType.INDOOR_SIMULATION)
        }

//        // 文件管理使用单独的 Fragment
//        findViewById<Button>(R.id.btn_file_manage).setOnClickListener {
//            navigateToFragment(FileManageFragment())
//        }

    }

    private fun navigateToWayPointFragment(functionType: FunctionType) {
        val fragment = WayPointV3Fragment().apply {
            arguments = Bundle().apply {
                putSerializable("FUNCTION_TYPE", functionType)
            }
        }
        navigateToFragment(fragment)
    }

    private fun navigateToFragment(fragment: Fragment) {
        // 隐藏主界面，显示 Fragment 容器
        mainContent.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        // 加载 Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // 加入回退栈，按返回键可回到主界面
            .commit()
    }

    // 处理返回键逻辑
    override fun onBackPressed() {
        if (fragmentContainer.visibility == View.VISIBLE) {
            // 如果 Fragment 容器可见，先返回主界面
            showMainContent()
        } else {
            // 否则执行默认返回操作（退出 Activity）
            super.onBackPressed()
        }
    }

    private fun showMainContent() {
        // 如果回退栈中还有 Fragment，先弹出
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }

        // 显示主界面，隐藏 Fragment 容器
        mainContent.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
    }
}