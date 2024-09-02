package dji.sampleV5.aircraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;
import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opencv.android.Utils;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhotoProcessingWorker extends Worker {
    Context context = getApplicationContext(); // 获取 Context
    private static final String TAG = "OpencvpictureActivity";
    double smoothmean = 0;

    // 平滑权重，weights为最近赋予2权重表示弱化当前数据，赋予1表示强化之前的数据，目的是使当前数据和之前趋势一样
    // weights2则赋予当前数据最大权重1，为了体现当前数据的特征。
    double[] weights = {2, 1,  2, 3 , 4 , 5, 6, 7, 8, 9};
    double[] weights2 = {1,  2, 3 , 4 , 5, 6, 7, 8, 9,10};

    private static final String SHARED_PREFS_NAME = "WorkerData";
    private static final Gson gson = new Gson();

    public PhotoProcessingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 获取共享数据
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String tempDataPath2 = sharedPreferences.getString("tempDataPath2", "kong");
        double tempDataIdw = sharedPreferences.getFloat("tempDataIdw", 0.0f);
        List<Double> idwData_Smooth1 = jsonToDoubleList(sharedPreferences.getString("idwData_Smooth1", "[]"));
        List<Double> idwData_Smooth2 = jsonToDoubleList(sharedPreferences.getString("idwData_Smooth2", "[]"));
        String bitmapPath1 = getInputData().getString("photo_path");

        // 获取传输数据

        String path1 = getInputData().getString("photo_path");
        double baseLine = getInputData().getDouble("photo_baseLine", 100);
        double FocalLength = getInputData().getDouble("photo_focallength", 0.04);
        double PixelDim = getInputData().getDouble("photo_pixeldim", 4.5/1000/1000);  // 米/像素

        Log.d(TAG, "photo_path: "+path1);

        // 检查列表是否为空
        if (tempDataPath2 == "kong") {
            Log.d(TAG, "The list is empty. Function completed: "+tempDataPath2);
            // 数据暂存缓存路径
            tempDataPath2 = saveImageToCacheDir(context,path1,"DroneFlyTemp");
            // 更新并保存共享数据
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("tempDataPath2", tempDataPath2);
            editor.apply();
            // 如果列表为空，函数直接返回
            Log.d(TAG, "The list is empty. Function completed: "+tempDataPath2);
            System.out.println("The list is empty. Function completed.");
            // 返回结果
            Data outputData = new Data.Builder()
                    .putDouble("result_value", 0)
                    .build();
            return Result.success(outputData);
        }

        Bitmap img1Bitmap=null;
        Bitmap img2Bitmap=null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;  // 阻止 Android 自动缩放相片分辨率
        try (FileInputStream fis=new FileInputStream(path1)){
            img1Bitmap = BitmapFactory.decodeStream(fis);
        } catch(Exception e){
            e.printStackTrace();
        }
        try (FileInputStream fis=new FileInputStream(tempDataPath2)){
            img2Bitmap = BitmapFactory.decodeStream(fis);
        } catch(Exception e){
            e.printStackTrace();
        }

//        double FocalLength = 0.04;
//        double PixelDim = 4.5/1000/1000; // 米/像素
        double idw = processImageORB(img1Bitmap, img2Bitmap, FocalLength, baseLine, PixelDim);
        // 数据暂存缓存路径
        tempDataPath2 = saveImageToCacheDir(context,path1,"DroneFlyTemp");

        // 结果为0时改为上一个计算的数值
        if(idw == 0)
            idw = tempDataIdw;
        tempDataIdw = idw;

        // 第一次加权平滑结果
        calculateIDWSmooth(idwData_Smooth1, idw, 10,weights);
        // 第二次加权平滑结果
        smoothmean = calculateIDWSmooth(idwData_Smooth2, idwData_Smooth1.get(idwData_Smooth1.size()-1), 10,weights2);

        Log.d(TAG, "smoothmean: "+smoothmean);

        // 更新并保存共享数据
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("tempDataPath2", tempDataPath2);
        editor.putFloat("tempDataIdw", (float) tempDataIdw);
        editor.putString("idwData_Smooth1", doubleListToJson(idwData_Smooth1));
        editor.putString("idwData_Smooth2", doubleListToJson(idwData_Smooth2));
        editor.apply();

        // 返回结果
        Data outputData = new Data.Builder()
                .putDouble("result_value", smoothmean)
                .build();
        return Result.success(outputData);
    }

    /**
     * 将给定路径的 JPEG 图像复制到应用的缓存目录中，并保存为指定的路径名称。
     *
     * @param context 应用上下文
     * @param sourcePath 原图像路径
     * @param targetFileName 缓存目录下的目标文件名称（不包括扩展名）
     * @return 目标文件的路径，如果失败则返回 null
     */
    public String saveImageToCacheDir(Context context, String sourcePath, String targetFileName) {
        File sourceFile = new File(sourcePath);
        File cacheDir = context.getCacheDir();
        File targetFile = new File(cacheDir, targetFileName + ".jpg");

        // 确保源文件存在
        if (!sourceFile.exists()) {
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(sourceFile);
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return targetFile.getAbsolutePath();
    }

    private List<Double> jsonToDoubleList(String json) {
        Type listType = new TypeToken<ArrayList<Double>>() {}.getType();
        return gson.fromJson(json, listType);
    }

    private String doubleListToJson(List<Double> list) {
        return gson.toJson(list);
    }

    /** 对相邻图像进行特征提取和计算航高
     *
     * @param img1Bitmap ：第一幅图像
     * @param img2Bitmap ：第二幅图像
     * @param FocalLength ：焦距
     * @param BaseLine ：基线距离
     * @param PixelDim ： 像元尺寸
     */
    private double processImageORB(Bitmap img1Bitmap, Bitmap img2Bitmap, double FocalLength, double BaseLine, double PixelDim){
        long startTime = System.nanoTime();
        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
        // 图像转换
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1);    // convert original bitmap to Mat, R G B.
        Utils.bitmapToMat(img2Bitmap, img2);    // convert original bitmap to Mat, R G B

        // 图像的特征点和描述符
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors2 = new Mat();

        // 创建 ORB 特征检测器，和匹配器
        ORB detector = ORB.create();
        // 提取特征点和描述符
        detector.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
        detector.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);

        // 创建特征匹配器
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT);

        // 特征匹配
        List<MatOfDMatch> matches = new LinkedList<>();
        matcher.knnMatch(descriptors1, descriptors2, matches, 2);

        // 使用比值测试来剔除错误匹配
        float ratioThresh = 0.7f;
        List<DMatch> goodMatchesList = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).rows() > 1) {
                DMatch[] m = matches.get(i).toArray();
                if (m[0].distance < ratioThresh * m[1].distance) {
                    goodMatchesList.add(m[0]);
                }
            }
        }

        // 筛选好的匹配点的d
        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(goodMatchesList);
        DMatch[] dmatchArray=goodMatches.toArray();
        KeyPoint[] keyPointArray1 = keypoints1.toArray();
        KeyPoint[] keyPointArray2 = keypoints2.toArray();

        double[] AviationHigh = new double[dmatchArray.length];
        Point[] AviationHighPoints = new Point[dmatchArray.length];

        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine /  Parallax
        for (int i = 0; i < dmatchArray.length; i++) {
            // 根据索引求匹配点坐标
            int Idx1=dmatchArray[i].queryIdx;
            int Idx2=dmatchArray[i].trainIdx;
            Point point1= new Point(keyPointArray1[Idx1].pt.x-img1.cols()/2,  keyPointArray1[Idx1].pt.y-img1.rows()/2);
            Point point2= new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2,  keyPointArray2[Idx2].pt.y-img2.rows()/2);
            // 视差
            double Parallax=Math.sqrt((point1.x - point2.x) *(point1.x - point2.x)+ (point1.y - point2.y)*(point1.y - point2.y));
            // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
            AviationHighPoints[i]=new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2, keyPointArray2[Idx2].pt.y-img2.rows()/2);

            AviationHigh[i]=FocalLength * BaseLine / (Parallax * PixelDim);
            if (AviationHigh[i] < 200) {
                AviationHigh[i] = 200.0;
            }
            // 如果元素大于max，则修正为max
            else if (AviationHigh[i] > 1200) {
                AviationHigh[i] = 1200.0;
            }
        }

        // 执行需要测量运行时间的代码块
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

        // 根据行高 AviationHigh 和坐标 AviationHighPoints 进行反距离加权
        double idw = calculateIDW(AviationHigh, AviationHighPoints);
        return idw;
    }

    /** 反距离加权平均
     *
     * @param values 数值
     * @param points 坐标
     * @return
     */
    public static double calculateIDW(double[] values, Point[] points) {
        if (values.length != points.length || values.length == 0) {
            return 0;
        }

        double weightedSum = 0.0;
        double weightSum = 0.0;

        for (int i = 0; i < values.length; i++) {
            double distance = calculateDistanceToCenter(points[i]);
            double weight = 1.0 / distance; // 反距离作为权重
            weightedSum += values[i] * weight;
            weightSum += weight;
        }

        if (weightSum == 0.0) {
            throw new ArithmeticException("Cannot divide by zero. All point distances are zero.");
        }
        return weightedSum / weightSum;
    }

    /** 滑动窗口反距离加权平均算法你
     * 将newData数据加入到数组demData中，当数组数量超过beta个时，开始对newData数据进行加权计算
     * 将newData数据之前的（包括newData）进行加权，得到的值赋给newData数据
     * @param demData 存储数组
     * @param newData 新增数据
     * @param beta 滑动窗口大小
     * @param weights 权重数组，自定义
     * @return 加权计算结果
     */
    public double calculateIDWSmooth(List<Double> demData, double newData ,int beta, double[] weights) {
        double smoothmean = newData;
        demData.add(newData);

        // 判断是否需要修正数据
        if (demData.size() >= 10) {
            // 截取最后beta个数据。
            int size = Math.min(demData.size(), beta);
            List<Double> subdemData=new ArrayList<>(demData.subList(demData.size() - size, demData.size()));
            int dataSize = subdemData.size();
            double[] values = new double[dataSize];
            Point[] points = new Point[dataSize];
            for (int i = 0; i < dataSize; i++) {
                values[i] = subdemData.get(dataSize-1-i);  // 反向，最后的数据给的距离最短，权重最大
                points[i] = new Point(weights[i],0);
            }
            smoothmean = calculateIDW(values, points);
            demData.set(demData.size() - 1, smoothmean);
        }
        return smoothmean;
    }

    // 计算点到中心的距离
    private static double calculateDistanceToCenter(Point point) {
        // 这里简化为点到原点的距离，你可以根据实际情况修改为其他距离计算方式
        return Math.sqrt(point.x * point.x + point.y * point.y);
    }


}
