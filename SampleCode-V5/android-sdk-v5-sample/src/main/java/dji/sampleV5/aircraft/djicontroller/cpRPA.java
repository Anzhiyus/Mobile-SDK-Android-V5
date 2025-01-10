package dji.sampleV5.aircraft.djicontroller;

import android.graphics.Point;

import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.utils.DJIGpsUtils;

//import visiontek.djicontroller.models.PolygonBounds;
//import visiontek.djicontroller.models.latline;

/**
 * Created by Administrator on 2017/12/7.
 */

public class cpRPA {
    public static DJILatLng getCenterPoint(List<DJILatLng> mPoints) {
        double latitude = (getMinLatitude(mPoints) + getMaxLatitude(mPoints)) / 2;
        double longitude = (getMinLongitude(mPoints) + getMaxLongitude(mPoints)) / 2;
        return new DJILatLng(latitude, longitude);
    }

    public static double getMinLatitude(List<DJILatLng> mPoints) {
        double lat=mPoints.get(0).latitude;
        for(int i=0;i<mPoints.size();i++){
            if(lat>mPoints.get(i).latitude){
                lat=mPoints.get(i).latitude;
            }
        }
        return lat;
    }
    public static double getMinLongitude(List<DJILatLng> mPoints) {
        double lon=mPoints.get(0).longitude;
        for(int i=0;i<mPoints.size();i++){
            if(lon>mPoints.get(i).longitude){
                lon=mPoints.get(i).longitude;
            }
        }
        return lon;
    }
    public static double getMaxLatitude(List<DJILatLng> mPoints) {
        double lat=mPoints.get(0).latitude;
        for(int i=0;i<mPoints.size();i++){
            if(lat<mPoints.get(i).latitude){
                lat=mPoints.get(i).latitude;
            }
        }
        return lat;
    }
    public static double getMaxLongitude(List<DJILatLng> mPoints) {
        double lon=mPoints.get(0).longitude;
        for(int i=0;i<mPoints.size();i++){
            if(lon<mPoints.get(i).longitude){
                lon=mPoints.get(i).longitude;
            }
        }
        return lon;
    }
    public static PolygonBounds createPolygonBounds(List<DJILatLng> mPoints) {
        PolygonBounds bounds=new PolygonBounds();
        bounds.center=getCenterPoint(mPoints);
        List<DJILatLng> list=new ArrayList<>();
        list.add(new DJILatLng(getMaxLatitude(mPoints),getMinLongitude(mPoints)));
        list.add(new DJILatLng(getMaxLatitude(mPoints),getMaxLongitude(mPoints)));
        list.add(new DJILatLng(getMinLatitude(mPoints),getMaxLongitude(mPoints)));
        list.add(new DJILatLng(getMinLatitude(mPoints),getMinLongitude(mPoints)));
        bounds.northLat=getMaxLatitude(mPoints);
        bounds.latlngs=list;
        return bounds;
    }
    public static Point transform(int x,int y,int tx,int ty,int deg1) {
        double deg = deg1 * Math.PI / 180;
        int resx= (int)((x - tx) * Math.cos(deg) - (y - ty) * Math.sin(deg)) + tx;
        int resy= (int)((x - tx) * Math.sin(deg) + (y - ty) * Math.cos(deg)) + ty;
        return new Point(resx,resy);
    }
    public static List<DJILatLng> createRotatePolygon(List<DJILatLng> latlngs, PolygonBounds bounds, int rotate, DJIMap amap) {
        List<DJILatLng> res=new ArrayList<>();
        Point a; Point b;
        Point c = amap.getProjection().toScreenLocation(bounds.center);
        for (int i = 0; i < latlngs.size(); i++) {
            a = amap.getProjection().toScreenLocation(latlngs.get(i));
            b = transform(a.x, a.y, c.x, c.y,rotate);
            res.add(amap.getProjection().fromScreenLocation(b));
        }
        return res;
    }
    public static latline createLats(PolygonBounds bounds, double space) {
        DJILatLng nw = bounds.latlngs.get(0);
        DJILatLng sw = bounds.latlngs.get(3);
//        float distance= AMapUtils.calculateLineDistance(nw,sw);//计算两点之间的距离
        double distance= DJIGpsUtils.distance(nw,sw);//计算两点之间的距离
        int steps = (int)(distance / space );//(int)(distance / space / 2);
        double lats = (nw.latitude - sw.latitude) / steps;
        latline res= new latline();
        res.lat=lats;
        res.len=steps;
        return res;
    }
    public static DJILatLng createInlinePoint(DJILatLng p1,DJILatLng p2,double y) {
        double s = p1.latitude - p2.latitude;
        double x =(y - p1.latitude) * (p1.longitude - p2.longitude) / s +p1.longitude;
        if(Double.isNaN(x) || Double.isInfinite(x)){
            return  null;
        }
        if (x > p1.longitude && x > p2.longitude) {
            return null;
        }
        if (x < p1.longitude && x <p2.longitude) {
            return null;
        }
        return new DJILatLng(y,x);
    }
    public static int si(int i,int l) {
        if (i > l - 1) {
            return i - l;
        }
        if (i < 0) {
            return l + i;
        }
        return i;
    }
    public  static List<DJILatLng> setOptions(cpRPAOptions opt) {
        PolygonBounds bounds = createPolygonBounds(opt.polygonPoints);
        List<DJILatLng> rPolygon = createRotatePolygon(opt.polygonPoints, bounds, -opt.rotate,opt.aMap);
        PolygonBounds rBounds = createPolygonBounds(rPolygon);
        latline latline = createLats(rBounds, opt.space);
        List<DJILatLng> line =new ArrayList<>();
        DJILatLng check = null;
        List<DJILatLng> polyline =new ArrayList<>();
        for (int i = 0; i < latline.len; i++) {
            line.clear();
            for (int j = 0; j < rPolygon.size(); j++) {
                int nt = si(j + 1, rPolygon.size());
                check = createInlinePoint(rPolygon.get(j), rPolygon.get(nt),rBounds.northLat - i * latline.lat);
                if (check!=null) {
                    line.add(check);
                }
            }
            if (line.size() < 2) {
                continue;
            }
            if (line.get(0).longitude == line.get(1).longitude) {
                continue;
            }
            if (i % 2>0) {
                DJILatLng start=new DJILatLng(line.get(0).latitude,Math.max(line.get(0).longitude, line.get(1).longitude));
                DJILatLng end=new DJILatLng(line.get(0).latitude,Math.min(line.get(0).longitude, line.get(1).longitude));
                polyline.add(start);
                polyline.add(end);
            } else {
                DJILatLng start=new DJILatLng(line.get(0).latitude,Math.min(line.get(0).longitude, line.get(1).longitude));
                DJILatLng end=new DJILatLng(line.get(0).latitude,Math.max(line.get(0).longitude, line.get(1).longitude));
                polyline.add(start);
                polyline.add(end);
            }
        }
        return  createRotatePolygon(polyline, bounds, opt.rotate,opt.aMap);
    }
}
