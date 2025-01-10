package dji.sampleV5.aircraft.djicontroller;

import com.amap.api.maps.model.LatLng;

import java.util.List;

import dji.v5.ux.mapkit.core.models.DJILatLng;


public class PolygonBounds {
    public DJILatLng center;
    public List<DJILatLng> latlngs;
    public double northLat;
}
