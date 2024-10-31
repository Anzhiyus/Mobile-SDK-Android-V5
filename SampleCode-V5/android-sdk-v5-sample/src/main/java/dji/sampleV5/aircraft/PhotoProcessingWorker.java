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
import java.util.Comparator;
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

        Log.d(TAG, "img1Bitmap分辨率宽: "+img1Bitmap.getWidth());
        Log.d(TAG, "img2Bitmap分辨率高: "+img2Bitmap.getHeight());
        double idw = processImageORB(img1Bitmap, img2Bitmap, FocalLength, baseLine, PixelDim);
        Log.d(TAG, "idw: "+idw);
        // 数据暂存缓存路径
        tempDataPath2 = saveImageToCacheDir(context,path1,"DroneFlyTemp");

        // 结果为0时改为上一个计算的数值
        if(idw == 0)
            idw = tempDataIdw;
        tempDataIdw = idw;

        // 第一次加权平滑结果
        calculateWeightSmooth(idwData_Smooth1, idw, 10,weights);
        // 第二次加权平滑结果
        smoothmean = calculateWeightSmooth(idwData_Smooth2, idwData_Smooth1.get(idwData_Smooth1.size()-1), 10,weights2);

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
        double[] angleDegress = new double[dmatchArray.length];

        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine /  Parallax
        for (int i = 0; i < dmatchArray.length; i++) {
            // 根据索引求匹配点坐标
            int Idx1=dmatchArray[i].queryIdx;
            int Idx2=dmatchArray[i].trainIdx;
            Point point1= new Point(keyPointArray1[Idx1].pt.x-img1.cols()/2,  keyPointArray1[Idx1].pt.y-img1.rows()/2);
            Point point2= new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2,  keyPointArray2[Idx2].pt.y-img2.rows()/2);

            // 确定航线方向
            angleDegress[i] = calculateAngle(point1, point2);

            // 视差
            double Parallax=Math.sqrt((point1.x - point2.x) *(point1.x - point2.x)+ (point1.y - point2.y)*(point1.y - point2.y));
            AviationHighPoints[i]=new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2, keyPointArray2[Idx2].pt.y-img2.rows()/2);
            // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
            AviationHigh[i]=FocalLength * BaseLine / (Parallax * PixelDim);

            // 阈值判断
            if (AviationHigh[i] < 60) {
                AviationHigh[i] = 60.0;
            }
            else if (AviationHigh[i] > 1200) {
                AviationHigh[i] = 1200.0;
            }
        }

        // 航线方向平均值
        double angleDegressAverage = calculateAverage(angleDegress);
        // 沿航线方向的边界点
        Point boundaryPoint = calculateBoundaryPoint(new Point(img2.cols()/2, img2.rows()/2), angleDegressAverage>180?angleDegressAverage-180:angleDegressAverage+180, img2.cols(), img2.rows());

        // 执行需要测量运行时间的代码块
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;

        // 航高过滤5%和95%
        filterDataInPlace(AviationHigh, AviationHighPoints);

        // 根据行高 AviationHigh 和坐标 AviationHighPoints 进行反距离加权
        double idw = calculateWeight(AviationHigh, AviationHighPoints);
        double weightedAviationHighGS = calculateWeightGS(AviationHigh, AviationHighPoints, boundaryPoint, img2.cols()/3);
        Log.d(TAG, "GS距离加权: "+weightedAviationHighGS);
        return idw;
    }

    /** 反距离加权平均
     *
     * @param values 数值
     * @param points 坐标
     * @return
     */
    public static double calculateWeight(double[] values, Point[] points) {
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

    public static double calculateWeightGS(double[] values, Point[] points, Point boundaryPoint, double radius) {


        if (values.length != points.length || values.length == 0) {
            return 0;
        }
        // double radius = width / 3.0; // 使用照片宽度的1/3作为半径
        double sigma = radius / 3.0; // 设置高斯分布的标准差，控制平滑程度

        double weightedSum = 0.0;
        double weightSum = 0.0;

        for (int i = 0; i < values.length; i++) {
            double distance = Math.sqrt(Math.pow(points[i].x - boundaryPoint.x, 2) + Math.pow(points[i].y - boundaryPoint.y, 2));
            // 使用高斯函数计算权重，超过radius范围的急剧降低
            double weight = (distance > radius) ? 0.1 : Math.exp(-Math.pow(distance, 2) / (2 * Math.pow(sigma, 2)));
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
    public double calculateWeightSmooth(List<Double> demData, double newData ,int beta, double[] weights) {
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
            smoothmean = calculateWeight(values, points);
            demData.set(demData.size() - 1, smoothmean);
        }
        return smoothmean;
    }

    // 重载方法，使用默认参数
    public static List<Double> applyKalmanFilter(List<Double> existingData, double newData) {
        return applyKalmanFilter(existingData, newData, 0.05, 0.5, 0.05, 0.5);
    }

    public static List<Double> applyKalmanFilter(List<Double> existingData, double newData,
                                                 double processNoise1, double measurementNoise1,
                                                 double processNoise2, double measurementNoise2) {
        // processNoise1越小越平滑，measurementNoise1相反，并且两者作用好像类似即只需要调一个参数即可
        // 如果已有数据为空，初始化第一个数据点
        if (existingData.isEmpty()) {
            existingData.add(newData);
            return existingData;
        }

        // 初始化第一次滤波的状态估计和误差协方差
        double estimatedValue1 = existingData.get(existingData.size() - 1);
        double estimatedError1 = 1.0;

        // 初始化第二次滤波的状态估计和误差协方差
        double estimatedValue2 = existingData.get(existingData.size() - 1);
        double estimatedError2 = 1.0;

        // 第一次滤波 - 预测阶段
        double predictedValue1 = estimatedValue1;
        double predictedError1 = estimatedError1 + processNoise1;

        // 第一次滤波 - 更新阶段
        double kalmanGain1 = predictedError1 / (predictedError1 + measurementNoise1);
        estimatedValue1 = predictedValue1 + kalmanGain1 * (newData - predictedValue1);
        estimatedError1 = (1 - kalmanGain1) * predictedError1;

        // 第二次滤波 - 预测阶段
        double predictedValue2 = estimatedValue2;
        double predictedError2 = estimatedError2 + processNoise2;

        // 第二次滤波 - 更新阶段
        double kalmanGain2 = predictedError2 / (predictedError2 + measurementNoise2);
        estimatedValue2 = predictedValue2 + kalmanGain2 * (estimatedValue1 - predictedValue2);
        estimatedError2 = (1 - kalmanGain2) * predictedError2;

        // 将二次滤波的结果添加到数据列表中
        existingData.add(estimatedValue2);
        return existingData;
    }



    // 计算点到中心的距离
    private static double calculateDistanceToCenter(Point point) {
        // 这里简化为点到原点的距离，你可以根据实际情况修改为其他距离计算方式
        return Math.sqrt(point.x * point.x + point.y * point.y);
    }

    // Step 1: 计算角度
    public static double calculateAngle(Point point1, Point point2) {
        double deltaX = point2.x - point1.x;
        double deltaY = point1.y - point2.y; // y轴反向
        double angleInDegrees = Math.toDegrees(Math.atan2(deltaY, deltaX));
        return (angleInDegrees < 0) ? angleInDegrees + 360 : angleInDegrees;
    }

    // Step 2: 计算沿该角度的边界点
    public static Point calculateBoundaryPoint(Point center, double angle, int width, int height) {
        // 转换为弧度
        double angleInRadians = Math.toRadians(angle);

        // 使用边界计算最大位移，宽高的最大半径
        double maxRadiusX = width / 2.0;
        double maxRadiusY = height / 2.0;

        // 计算位移到边界时的x, y坐标
        float boundaryX = (float) (center.x + maxRadiusX * Math.cos(angleInRadians));
        float boundaryY = (float) (center.y - maxRadiusY * Math.sin(angleInRadians)); // y轴向下

        return new Point(boundaryX, boundaryY);
    }

    // Step 3: 以边界点为中心的高斯加权
    public static double[][] generateWeightMatrix(int width, int height, Point boundaryPoint) {
        double[][] weightMatrix = new double[height][width];

        double radius = width / 3.0; // 使用照片宽度的1/3作为半径
        double sigma = radius / 3.0; // 设置高斯分布的标准差，控制平滑程度

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double distance = Math.sqrt(Math.pow(x - boundaryPoint.x, 2) + Math.pow(y - boundaryPoint.y, 2));

                // 使用高斯函数计算权重，超过radius范围的急剧降低
                double weight = (distance > radius) ? 0.1 : Math.exp(-Math.pow(distance, 2) / (2 * Math.pow(sigma, 2)));

                // 将权重值插入矩阵
                weightMatrix[y][x] = weight;
            }
        }

        return weightMatrix;
    }

    public static double calculateAverage(double[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }

        double sum = 0.0;
        for (double num : array) {
            sum += num;
        }

        return sum / array.length;
    }

    public static void filterDataInPlace(double[] aviationHigh, Point[] aviationHighPoints) {
        int n = aviationHigh.length;
        int numToRemove = (int) (n * 0.05);  // 5% 的元素数

        // 创建一个索引数组并初始化
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }

        // 按 aviationHigh 的值升序排序索引
        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                return Double.compare(aviationHigh[i1], aviationHigh[i2]);
            }
        });

        // 创建新的数组来存储保留的元素
        double[] filteredAviationHigh = new double[n - 2 * numToRemove];
        Point[] filteredAviationHighPoints = new Point[n - 2 * numToRemove];

        // 复制保留的元素到新的数组
        for (int i = numToRemove; i < n - numToRemove; i++) {
            filteredAviationHigh[i - numToRemove] = aviationHigh[indices[i]];
            filteredAviationHighPoints[i - numToRemove] = aviationHighPoints[indices[i]];
        }

        // 将结果复制回原数组中
        System.arraycopy(filteredAviationHigh, 0, aviationHigh, 0, filteredAviationHigh.length);
        System.arraycopy(filteredAviationHighPoints, 0, aviationHighPoints, 0, filteredAviationHighPoints.length);
    }

}
