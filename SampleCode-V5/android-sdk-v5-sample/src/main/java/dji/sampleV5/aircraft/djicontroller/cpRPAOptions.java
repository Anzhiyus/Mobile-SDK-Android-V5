package dji.sampleV5.aircraft.djicontroller;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.LatLng;

import java.util.List;

import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJILatLng;


public class cpRPAOptions {
    public List<DJILatLng> polygonPoints;
    public int rotate;
    public double space;
    public DJIMap aMap;
}
