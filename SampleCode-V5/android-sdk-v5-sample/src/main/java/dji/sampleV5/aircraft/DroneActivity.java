package dji.sampleV5.aircraft;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

//import dji.sampleV5.aircraft.pages.DroneFlyFragment;
import dji.sampleV5.aircraft.pages.WayPointV3Fragment;

public class DroneActivity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drone);

        // 动态加载 FlyFragment
        if (savedInstanceState == null) {  // 确保 Fragment 只会添加一次
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new WayPointV3Fragment()) // 假设有一个容器 view 来放置 Fragment
                    .commit();
        }

    }

}
