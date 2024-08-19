package dji.sampleV5.aircraft

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import dji.sampleV5.aircraft.pages.MediaFragment
import dji.sampleV5.aircraft.util.Helper
import dji.v5.utils.common.StringUtils
import kotlinx.android.synthetic.main.activity_drone.btn_take_photo
import kotlinx.android.synthetic.main.activity_drone.*

class DroneActivity : AppCompatActivity() {
    val myFragment = MediaFragment()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drone)

        // 检查savedInstanceState是否为空，以防止Activity重新创建时多次添加Fragment
        if (savedInstanceState == null) {
            // 创建MyFragment实例
//            val myFragment = MediaFragment()

            // 使用supportFragmentManager开始事务
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, myFragment)
                .hide(myFragment) // 隐藏 myFragment
                .commit()  // 提交事务


        }

        btn_take_photo.setOnClickListener {
            myFragment.take_photo()
        }

        btn_download.setOnClickListener {
            myFragment.take_photo()
        }

    }






}