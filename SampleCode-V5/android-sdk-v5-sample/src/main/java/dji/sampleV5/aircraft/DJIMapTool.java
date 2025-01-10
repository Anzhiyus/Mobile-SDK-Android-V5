package dji.sampleV5.aircraft;

import static com.google.android.material.internal.ContextUtils.getActivity;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polygon;
import com.amap.api.maps.model.PolygonOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dji.sampleV5.aircraft.djicontroller.cpRPA;
import dji.sampleV5.aircraft.djicontroller.cpRPAOptions;
import dji.sdk.wpmz.jni.JNIWPMZManager;
import dji.sdk.wpmz.value.mission.Wayline;
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint;
import dji.v5.utils.common.AndUtil;
import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptorFactory;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygon;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygonOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolyline;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;

public class DJIMapTool {
    private DJIMap _amap;
    public static final int TOOL_DRAWAREA=2;
    private DJIMarker drawPointMarkerOK; //顶点marker  drawPointMarkerOK okMarker

    cpRPAOptions opts;

    //对航飞区域和航线的相关操作
    private List<DJIMarker> flightPointMarkers=new ArrayList<>();//区域顶点标注  flightPointMarkers polygon
    private List<DJIMarker> flightLineCenterPointMarkers=new ArrayList<>();//区域边界中心点标注 flightLineCenterPointMarkers centerPoints
    private DJIMarker flightCenterPointMarker;//区域中心点标注 flightCenterPointMarker centerMarker
    private DJIPolygon flightPolygon;//多边形边界图层 flightPolygon flyarea
    private DJIPolyline flightPolyline;//航线图层 flightPolyline flylines
    private DJIMarker startMarker=null;
    private DJIMarker endMarker=null;

    private Context _context;
    public DJIMapTool(DJIMap amap, Context context){
        _context=context;
        _amap=amap;
        opts=new cpRPAOptions();
        opts.aMap=_amap;
        opts.rotate=90; // 2023.12.27 AZY：设置自定义值
        opts.space=100; // 2023.12.27 AZY：设置自定义值
        bindEvents();
    }

    public void OpenTool(int toolType){
        if(toolType==TOOL_DRAWAREA){
            ClearAll(); // 2023.12.28 AZY：添加
            ClearMarker(flightPointMarkers);
            _amap.setOnMapClickListener(new DJIMap.OnMapClickListener() {
                @Override
                public void onMapClick(DJILatLng latLng) {
                    // flightPolygon drawPointMarkerOK 每次都要更新
                    if(flightPolygon!=null){
                        flightPolygon.remove();
                    }
                    if(drawPointMarkerOK!=null){
                        drawPointMarkerOK.remove();
                    }
                    // 区域顶点标志
                    DJIMarker marker = markPoint(R.mipmap.mission_edit_waypoint_normal, latLng, String.valueOf(0),true);
                    flightPointMarkers.add(marker);

                    // 区域边界
                    flightPolygon= _amap.addPolygon(new DJIPolygonOptions()
                            .addAll(getPointsFromMarkers(flightPointMarkers))
                            .fillColor(Color.argb(50, 0, 128, 0))//0,128,0
                            .strokeColor(Color.GREEN).strokeWidth(1));//绘制区域

                    // 规划确认标志
                    if(flightPointMarkers.size()>2){
                        drawPointMarkerOK=markPoint(R.mipmap.playback_ic_selected, latLng, "v",false);  // 对号确认图标
                    }
                }
            });
        }
    }

    // 绑定点的点击和拖动事件。
    DJILatLng positionBeforeDrag;
    DJILatLng positionOnDrag;
    private void bindEvents(){
        // 点的点击
        _amap.setOnMarkerClickListener(new DJIMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(DJIMarker marker) {
                //点击的是顶点则删除这个点
                if(flightPointMarkers.contains(marker)){
                    if(flightPointMarkers.size()==3){
//                        Common.ShowQMUITipToast(_context,"多边形至少需要3个点", QMUITipDialog.Builder.ICON_TYPE_INFO,500);
                    }
                    else{
                        marker.remove();
                        flightPointMarkers.remove(marker);
                    }
                }
                //点击的是新增, 即点击中点, 将之增加为顶点并生成另外两个中点（最后由顶点重新生成边中点）
                else if(flightLineCenterPointMarkers.contains(marker)){
                    DJIMarker newmarker= markPoint(R.mipmap.mission_edit_waypoint_normal, marker.getPosition(), String.valueOf(0),true);
                    int i=flightLineCenterPointMarkers.indexOf(marker);//获取所在集合的索引
                    // 边界中点改为顶点，删除原标志
                    flightPointMarkers.add(i,newmarker);
                    marker.remove();
                }
                // 点击OK标志后结束画图
                else if(drawPointMarkerOK.getPosition().latitude == marker.getPosition().latitude && drawPointMarkerOK.getPosition().longitude == marker.getPosition().longitude){
                    // 停止画图，清除ok标志
                    _amap.removeOnMapClickListener(null);
                    drawPointMarkerOK.remove();
                }

                // 整体拖动，区域、标志和航线等都要更新
                List<DJILatLng> points= getPointsFromMarkers(flightPointMarkers);
                LoadArea(points);  // 更新边界图层flightPolygon
                LoadWayLines(points); // 更新航线图层flightPolyline
                LoadLineCenterPointMarkers(points);  // 更新边界和区域中点标志
                return true;
            }
        });

        // 中心点的拖动
        _amap.setOnMarkerDragListener(new DJIMap.OnMarkerDragListener() {  //拖拽图层的处理
            @Override
            public void onMarkerDragStart(DJIMarker marker) {
                positionOnDrag = marker.getPosition();
                positionBeforeDrag = marker.getPosition();
            }
            @Override
            public void onMarkerDrag(DJIMarker marker) {
                positionOnDrag = marker.getPosition();
                if(marker.equals(flightCenterPointMarker)) {//拖拽中心点平移
                    for (int i = 0; i < flightPointMarkers.size(); i++) {//平移顶点
                        DJILatLng position = flightPointMarkers.get(i).getPosition();
                        DJILatLng newposition = new DJILatLng
                                (position.latitude + (positionOnDrag.latitude - positionBeforeDrag.latitude),
                                        position.longitude + (positionOnDrag.longitude - positionBeforeDrag.longitude)
                                );
                        flightPointMarkers.get(i).setPosition(newposition);
                    }
                }
                // 整体拖动，区域、标志和航线等都要更新
                List<DJILatLng> points= getPointsFromMarkers(flightPointMarkers);
                LoadArea(points);  // 更新边界图层flightPolygon
                LoadWayLines(points); // 更新航线图层flightPolyline
                LoadLineCenterPointMarkers(points); // 更新边界和区域中点标志
                positionBeforeDrag=positionOnDrag;
            }
            @Override
            public void onMarkerDragEnd(DJIMarker marker) {
                resetCenterMoveMarker(getPointsFromMarkers(flightPointMarkers));
            }
        });
    }

    // 加载并更新多边形边界和图形中心的标志
    // 由画图时 flightPointMarkers 标志提取端点，画图
    public void LoadLineCenterPointMarkers(List<DJILatLng> rect){
        // 清除并更新图形中心标志
        resetCenterMoveMarker(rect); // flightCenterPointMarker ,更新中点

        // 清除并更新边界中心
        for (int i = 0; i < flightLineCenterPointMarkers.size(); i++) {
            flightLineCenterPointMarkers.get(i).remove();
        }
        flightLineCenterPointMarkers.clear();
        // 为每条边添加中点
        int size=rect.size();
        for(int i=0;i<size;i++){
            DJILatLng center;
            if(i==0){
                DJILatLng st=rect.get(size-1);
                DJILatLng ed=rect.get(i);
                center=new DJILatLng((st.latitude+ed.latitude)/2,(st.longitude+ed.longitude)/2);
            }
            else{
                DJILatLng st=rect.get(i-1);
                DJILatLng ed=rect.get(i);
                center=new DJILatLng((st.latitude+ed.latitude)/2,(st.longitude+ed.longitude)/2);
            }
//            DJIMarker markerPlus =  AddPoint(R.drawable.corner_add,center,false,null,null,0.5f,0.5f);//创建中心点
            DJIMarker centerMarker =  markPoint(R.mipmap.mission_edit_waypoint_normal, center,"+");  // 创建边界中心点
            flightLineCenterPointMarkers.add(centerMarker);
        }
    }

    //加载航线并根据航线角度和间距更新航线
    public void UpdateWayLines(List<DJILatLng> points, int rotate,double space){
        opts.rotate=rotate;
        opts.space=space;
        LoadWayLines(points);
    }

    //由航飞区域顶点标志获取航飞区域顶点，
    public List<DJILatLng> getPointsFromMarkers(List<DJIMarker> markers){
        List<DJILatLng> points = new ArrayList<DJILatLng>();
        for (int i = 0; i < markers.size(); i++) {
            points.add(markers.get(i).getPosition());
        }
        return points;
    }

    // 更新航线
    private void LoadWayLines(List<DJILatLng> points){
        if(flightPolyline!=null){
            flightPolyline.remove();
        }
        if(startMarker!=null){
            startMarker.remove();
        }
        if(endMarker!=null){
            endMarker.remove();
        }
        if(points.size()>0){
            // 创建航线
            // cpRPA.setOptions(opts) 生成opts航线角度和间距的点
            opts.polygonPoints=points;
            flightPolyline =_amap.addPolyline(new DJIPolylineOptions().addAll(cpRPA.setOptions(opts)).width(2).color(Color.GREEN));
            flightPolyline.setZIndex(10);
            List<DJILatLng> pnts= flightPolyline.getPoints();
            if(!pnts.isEmpty()){
                startMarker = markPoint(R.mipmap.mission_edit_waypoint_normal, pnts.get(0),"S");  // 无人机起始标志
                endMarker = markPoint(R.mipmap.mission_edit_waypoint_normal, pnts.get(pnts.size()-1),"E");  // 无人机结束标志
            }
        }
    }

    private void LoadArea(List<DJILatLng> points){
        if(flightPolygon!=null){
            flightPolygon.remove();
        }

        flightPolygon=_amap.addPolygon(new DJIPolygonOptions()//创建边界
                .addAll(points)
                .fillColor(Color.argb(50, 1, 1, 1))
                .strokeColor(Color.RED).strokeWidth(1));
    }


    //清空多边形和航线
    public void ClearAll(){
        for (int i = 0; i < flightPointMarkers.size(); i++) {
            flightPointMarkers.get(i).remove();
        }
        flightPointMarkers.clear();
        for (int i = 0; i < flightLineCenterPointMarkers.size(); i++) {
            flightLineCenterPointMarkers.get(i).remove();
        }
        flightLineCenterPointMarkers.clear();
        if(flightCenterPointMarker!=null){
            flightCenterPointMarker.remove();
        }
        if(flightPolygon!=null) {
            flightPolygon.remove();
        }
        if(flightPolyline!=null){
            flightPolyline.remove();
        }
        if(startMarker!=null){
            startMarker.remove();
        }
        if(endMarker!=null){
            endMarker.remove();
        }
    }

    private void ClearMarker(List<DJIMarker> markers){
        if(markers!=null){
            for(int i=0;i<markers.size();i++){
                markers.get(i).remove();//清除
            }
            markers.clear();
        }
    }

    private void resetCenterMoveMarker(List<DJILatLng> points){
        DJILatLng centerPoint = cpRPA.getCenterPoint(points);
        if(flightCenterPointMarker!=null){
            flightCenterPointMarker.remove();
        }
        flightCenterPointMarker = markPoint(R.mipmap.mission_edit_waypoint_normal, centerPoint, "*",true);  // 中心点
    }


    // 重载方法，默认 isDarggable 为 false
    public DJIMarker markPoint(int res, DJILatLng latlong, String waypointIndex) {
        return markPoint(res, latlong, waypointIndex, false);  // 默认 isDarggable 为 false
    }

    public DJIMarker markPoint(int res, DJILatLng latlong, String waypointIndex, boolean isDarggable) {
        DJIMarkerOptions markOptions = new DJIMarkerOptions();
        markOptions.position(latlong);
        markOptions.icon(getMarkerRes(res, waypointIndex, 0f));
        markOptions.anchor(0.5f, 0.5f) ; // 设置锚点位置
        markOptions.draggable(isDarggable);
        markOptions.title(String.valueOf(waypointIndex));
        markOptions.setInfoWindowEnable(true);
        return _amap.addMarker(markOptions);
    }

    private DJIBitmapDescriptor getMarkerRes(int res, String index, float rotation) {
        Bitmap bitmap = getMarkerBitmap(res, index, rotation);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false); // 缩放图标
        return DJIBitmapDescriptorFactory.fromBitmap(scaledBitmap);
    }

    /**
     * Convert view to bitmap
     * Notice: recycle the bitmap after use
     */
    private Bitmap getMarkerBitmap(int res, String index, float rotation) {
        // create View for marker
        @SuppressLint("InflateParams")
        View markerView = LayoutInflater.from(_context)
                .inflate(R.layout.waypoint_marker_style_layout, null);

        ImageView markerBg = markerView.findViewById(R.id.image_content);
        TextView markerTv = markerView.findViewById(R.id.image_text);

        markerTv.setText(index);
        markerTv.setTextColor(AndUtil.getResColor(R.color.blue));
        markerTv.setTextSize(AndUtil.getDimension(R.dimen.mission_waypoint_index_text_large_size));

        markerBg.setImageResource(res);

        markerBg.setRotation(rotation);

        // convert view to bitmap
        markerView.destroyDrawingCache();
        markerView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        markerView.layout(0, 0, markerView.getMeasuredWidth(), markerView.getMeasuredHeight());
        markerView.setDrawingCacheEnabled(true);

        return markerView.getDrawingCache(true);
    }

    //获取全部航点
    public List<DJILatLng> getPointsFlyLines(){
        if(flightPolyline!=null) {
            return flightPolyline.getPoints();
        }
        else {
            return null;
        }
    }

    public List<DJILatLng> generateIntermediatePoints(List<DJILatLng> points, double distance) {
        // points是航线边界点，result是航线边界加密得到的航点
        List<DJILatLng> result = new ArrayList<>();

        // 添加第一个点，第一个点位置不变
        result.add(points.get(0));

        for (int i = 1; i < points.size(); i++) {
            // lastResultPoint和currentPoint组成一条航线
            DJILatLng lastResultPoint = result.get(result.size() - 1); // result中最后一个点
            DJILatLng currentPoint = points.get(i); // points中的当前点

            if (i % 2 == 1) {
                // 平行航线。偶数索引到奇数索引：分割线段
                double segmentDistance = calculateFlatDistance(lastResultPoint, currentPoint);
                int numPoints = (int) Math.ceil(segmentDistance / distance);
                for (int j = 1; j <= numPoints; j++) {
                    double fraction = (double) j / numPoints;
                    DJILatLng interpolatedPoint = linearInterpolate(lastResultPoint, currentPoint, fraction);
                    // 航线加密，循环最后一个点为该航线终点，由于航线外扩，替代了currentPoint的位置。
                    result.add(interpolatedPoint);
                }
            } else {
                // 转向航线。奇数索引到偶数索引：根据距离和方位计算新点
                // 根据原始距离和方位，以及上一航线延申的新起点，计算新终点
                double segmentDistance = calculateFlatDistance(points.get(i - 1), currentPoint);
                double azimuth = calculateAzimuth(points.get(i - 1), currentPoint);
                // 新终点
                DJILatLng newPoint = calculateNewPoint(lastResultPoint, azimuth, segmentDistance);

                // 对新起点和终点进行分割。
                currentPoint = newPoint;
                int numPoints = (int) Math.floor(segmentDistance / distance);
                for (int j = 1; j <= numPoints-1; j++) {    // 最后一个点不需要，使用上述计算的新终点。
                    double fraction = (double) j / numPoints;
                    DJILatLng interpolatedPoint = linearInterpolate(lastResultPoint, currentPoint, fraction);
                    // 航线加密，由于航线不外扩，循环最后一个点不是该航线终点。
                    result.add(interpolatedPoint);
                }

                result.add(newPoint);
            }
        }

        return result;
    }


    // 计算总航线长度
    public double calculateTotalRouteLength(List<DJILatLng> routeLinePoints) {
        double totalDistance = 0.0;

        // 遍历相邻的坐标点，计算总距离
        for (int i = 0; i < routeLinePoints.size() - 1; i++) {
            DJILatLng startPoint = routeLinePoints.get(i);
            DJILatLng endPoint = routeLinePoints.get(i + 1);

            // 计算两点之间的平面距离并累加
            totalDistance += calculateFlatDistance(startPoint, endPoint);
        }

        return totalDistance; // 返回总距离
    }

    /**
     * 使用球面公式计算两点之间的距离
     */
    private double calculateFlatDistance(DJILatLng start, DJILatLng end) {
        double earthRadius = 6371000; // 地球半径，单位：米
        double dLat = Math.toRadians(end.latitude - start.latitude);
        double dLng = Math.toRadians(end.longitude - start.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(start.latitude)) * Math.cos(Math.toRadians(end.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    // 简单的线性插值（线性插值适用于平面距离计算）
    private DJILatLng linearInterpolate(DJILatLng start, DJILatLng end, double fraction) {
        double lat = start.latitude + (end.latitude - start.latitude) * fraction;
        double lng = start.longitude + (end.longitude - start.longitude) * fraction;
        return new DJILatLng(lat, lng);
    }
    /**
     * 计算两点之间的方位角
     */
    private double calculateAzimuth(DJILatLng start, DJILatLng end) {
        double startLat = Math.toRadians(start.latitude);
        double startLng = Math.toRadians(start.longitude);
        double endLat = Math.toRadians(end.latitude);
        double endLng = Math.toRadians(end.longitude);

        double dLng = endLng - startLng;
        double y = sin(dLng) * cos(endLat);
        double x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng);
        return (Math.toDegrees(atan2(y, x)) + 360) % 360; // 确保方位角为 0~360 度
    }

    /**
     * 根据起点、方位角和距离计算新点（基于球面距离公式）
     */
    private DJILatLng calculateNewPoint(DJILatLng start, double azimuth, double distance) {
        double earthRadius = 6371000; // 地球半径，单位：米

        double angularDistance = distance / earthRadius; // 距离转化为角度
        double azimuthRad = Math.toRadians(azimuth);     // 方位角转化为弧度

        double startLat = Math.toRadians(start.latitude);
        double startLng = Math.toRadians(start.longitude);

        // 计算新点的纬度
        double newLat = Math.asin(Math.sin(startLat) * Math.cos(angularDistance) +
                Math.cos(startLat) * Math.sin(angularDistance) * Math.cos(azimuthRad));

        // 计算新点的经度
        double newLng = startLng + Math.atan2(Math.sin(azimuthRad) * Math.sin(angularDistance) * Math.cos(startLat),
                Math.cos(angularDistance) - Math.sin(startLat) * Math.sin(newLat));

        // 转换为度数
        newLat = Math.toDegrees(newLat);
        newLng = Math.toDegrees(newLng);

        return new DJILatLng(newLat, newLng);
    }

    public void writeLatLngsToKML(OutputStream outputStream, Map<String, Object> kmlData) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        writer.write("<Document>\n");
        Double  height = (Double ) kmlData.get("height");
        Double  speed = (Double ) kmlData.get("speed");
        Double  time = (Double ) kmlData.get("time");
        Double  waypointDistance = (Double ) kmlData.get("waypointDistance");

        Toast.makeText(this._context, "height"+height+"speed"+speed+"time"+time+"waypointDistance"+waypointDistance, Toast.LENGTH_SHORT).show();


        List<DJILatLng> points = (List<DJILatLng>) kmlData.get("points");

        // 如果 height 和 speed 不为 null，写入 KML 文件
        if (height != null && speed != null) {
            writer.write("<ExtendedData>\n");
            writer.write("<Data name=\"height\">\n");
            writer.write("<height>" + height + "</height>\n");
            writer.write("</Data>\n");

            writer.write("<Data name=\"speed\">\n");
            writer.write("<speed>" + speed + "</speed>\n");
            writer.write("</Data>\n");

            writer.write("<Data name=\"time\">\n");
            writer.write("<time>" + time + "</time>\n");
            writer.write("</Data>\n");
            writer.write("</ExtendedData>\n");

            writer.write("<Data name=\"waypointDistance\">\n");
            writer.write("<waypointDistance>" + waypointDistance + "</waypointDistance>\n");
            writer.write("</Data>\n");
            writer.write("</ExtendedData>\n");
        }

        for (DJILatLng point : points) {
            writer.write("<Placemark>\n");
            writer.write("<Point>\n");
            writer.write("<coordinates>" + point.longitude + "," + point.latitude + "</coordinates>\n");
            writer.write("</Point>\n");
            writer.write("</Placemark>\n");
        }
        writer.write("</Document>\n");
        writer.write("</kml>\n");
        writer.flush();
        writer.close();
    }

}

