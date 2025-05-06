package dji.sampleV5.aircraft;

import static androidx.core.content.ContentProviderCompat.requireContext;

import static java.lang.Math.abs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Environment;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;
import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.DiskUtil;
import dji.sampleV5.aircraft.djicontroller.LogUtil;
public class PhotoProcessingWorker extends Worker {
    Context context = getApplicationContext(); // 获取 Context
    private static final String TAG = "OpencvpictureActivity";
    double smoothmean = 0;
    private static double estimatedError1 = 1.0;
    private static double estimatedError2 = 1.0;

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
        List<Double>  aviationHighMedian= jsonToDoubleList(sharedPreferences.getString("aviationHighMedian", "[]"));
        List<Double> idwData_Smooth1 = jsonToDoubleList(sharedPreferences.getString("idwData_Smooth1", "[]"));
        List<Double> idwData_Smooth2 = jsonToDoubleList(sharedPreferences.getString("idwData_Smooth2", "[]"));
        List<Double> idwData_KalmanFilter = jsonToDoubleList(sharedPreferences.getString("idwData_KalmanFilter", "[]"));

        // 获取传输数据
        String path1 = getInputData().getString("photo_path");
        double baseLine = getInputData().getDouble("photo_baseLine", 100);
        double FocalLength = getInputData().getDouble("photo_focallength", 0.04);
        double PixelDim = getInputData().getDouble("photo_pixeldim", 4.5/1000/1000);  // 米/像素

        LogUtil.INSTANCE.d(TAG, "当前照片路径: "+path1);
        LogUtil.INSTANCE.d(TAG, "上一照片路径: "+tempDataPath2);

        // 检查列表是否为空
        if (tempDataPath2 == "kong") {
            // 数据暂存缓存路径
//            tempDataPath2 = saveImageToCacheDir(context,path1,"DroneFlyTemp");
            tempDataPath2 = saveImageToCacheDir(context,path1,path1.substring(path1.lastIndexOf("/") + 1));
            // 更新并保存共享数据
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("tempDataPath2", tempDataPath2);
            editor.apply();
            // 如果列表为空，函数直接返回
            LogUtil.INSTANCE.d(TAG, "上一照片路径更新: "+tempDataPath2);
            System.out.println("The list is empty. Function completed.");
            // 返回结果
            Data outputData = new Data.Builder()
                    .putDouble("result_value", 0.0001)
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

        LogUtil.INSTANCE.d(TAG, "img1Bitmap分辨率宽: "+img1Bitmap.getWidth());
        LogUtil.INSTANCE.d(TAG, "img2Bitmap分辨率高: "+img2Bitmap.getHeight());


        double idw = processImageORB(img1Bitmap,img2Bitmap, FocalLength, baseLine, PixelDim);
//        double idw = processImageMultiTemplateMatch(img1Bitmap, img2Bitmap, FocalLength, baseLine, PixelDim);

        LogUtil.INSTANCE.d(TAG, "idw: "+Math.round(idw * 10) / 10.0 );
        // 数据暂存缓存路径
//        tempDataPath2 = saveImageToCacheDir(context,path1,"DroneFlyTemp");
        tempDataPath2 = saveImageToCacheDir(context,path1,path1.substring(path1.lastIndexOf("/") + 1));

        // 结果为0时改为上一个计算的数值
        if(idw == 0)
            idw = tempDataIdw;
        tempDataIdw = idw;

//        long startTime = System.nanoTime();
//        // 第一次加权平滑结果
//        calculateWeightSmooth(idwData_Smooth1, idw, 10,weights);
//        // 第二次加权平滑结果
//        smoothmean = calculateWeightSmooth(idwData_Smooth2, idwData_Smooth1.get(idwData_Smooth1.size()-1), 10,weights2);
//        LogUtil.INSTANCE.d(TAG, "smoothmean: "+Math.round(smoothmean * 10) / 10.0 );
//
//        // 执行需要测量运行时间的代码块
//        long endTime = System.nanoTime();
//        long elapsedTime = endTime - startTime;
//        LogUtil.INSTANCE.d(TAG, "smoothmean elapsedTime: "+elapsedTime );
        // 卡尔曼滤波
        LogUtil.INSTANCE.d(TAG, "KalmanFilter: "+applyKalmanFilter(idwData_KalmanFilter, idw));

        // 更新并保存共享数据
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("tempDataPath2", tempDataPath2);
        editor.putFloat("tempDataIdw", (float) tempDataIdw);
        aviationHighMedian.add(idw);
        editor.putString("aviationHighMedian",doubleListToJson(aviationHighMedian) );
        LogUtil.INSTANCE.d(TAG, "aviationHighMedian: "+aviationHighMedian);
//        editor.putString("idwData_Smooth1", doubleListToJson(idwData_Smooth1));
//        editor.putString("idwData_Smooth2", doubleListToJson(idwData_Smooth2));
        editor.putString("idwData_KalmanFilter", doubleListToJson(idwData_KalmanFilter));
        editor.apply();


        // 返回结果
        Data outputData = new Data.Builder()
//                .putDouble("result_value", idwData_KalmanFilter.get(idwData_KalmanFilter.size() - 1))
                .putDouble("result_value", idw)
//                .putDouble("result_value", Math.round(smoothmean * 10) / 10.0 )
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

    public static String saveBitmapToFile(Bitmap bitmap, String folderName) {
        // 获取当前时间并格式化为年月日时分秒
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String currentTime = dateFormat.format(new Date());

        // 创建保存路径
        String dirsPath = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), "/mediafile/" + folderName + "/" + currentTime);
        File dirs = new File(dirsPath);
        if (!dirs.exists()) {
            dirs.mkdirs();
        }

        // 获取当前时间并格式化为年月日时分秒
        dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        currentTime = dateFormat.format(new Date());

        // 创建文件路径并准备文件输出流
        String filePath = dirsPath + "/Picture_" + currentTime + ".jpg";
        File file = new File(filePath);

        try {
            FileOutputStream outputStream = new FileOutputStream(file, false);  // 覆盖模式
            BufferedOutputStream bos = new BufferedOutputStream(outputStream);

            // 将Bitmap压缩为JPEG格式并保存到文件
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            outputStream.close();

            LogUtil.INSTANCE.i("ImageUtils", "Image saved to " + filePath);

            // 返回保存的路径
            return filePath;

        } catch (IOException e) {
            LogUtil.INSTANCE.e("ImageUtils", "Error saving image: " + e.getMessage());
            return null;
        }
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

        int bitmapHeight = img1Bitmap.getHeight();
        int bitmapWidth = img1Bitmap.getWidth();

        // 对图像分割提取
        // processImageORB(getSubBitmap(img1Bitmap,4,4,0,2,1,2), getSubBitmap(img2Bitmap,4,4,0,2,1,2),
        // 75%重叠率，分为4x4格网，高取前75%，宽取100%
//        img1Bitmap = getSubBitmap(img1Bitmap,4,4,0,2,0,3);
//        img2Bitmap = getSubBitmap(img2Bitmap,4,4,0,2,0,3);

////        // 90%重叠率，分为10x10格网，高取前50%，宽取中间60%
//        img1Bitmap = getSubBitmap(img1Bitmap,10,10,0,4,2,7);
//        img2Bitmap = getSubBitmap(img2Bitmap,10,10,0,4,2,7);

//        ////        // 90%重叠率，分为10x10格网，高从上倒下取30~70%，宽取中间60%
//        img1Bitmap = getSubBitmap(img1Bitmap,10,10,2,6,2,7);
//        img2Bitmap = getSubBitmap(img2Bitmap,10,10,2,6,2,7);

        ////        // 90%重叠率，分为10x10格网，高从上倒下取30~70%，宽取中间40%
//        img1Bitmap = getSubBitmap(img1Bitmap,10,10,2,6,3,6);
//        img2Bitmap = getSubBitmap(img2Bitmap,10,10,2,6,3,6);

//        ////        // 90%重叠率，分为10x10格网，高取前50%，宽取中间40%
//        img1Bitmap = getSubBitmap(img1Bitmap,10,10,0,4,3,6);
//        img2Bitmap = getSubBitmap(img2Bitmap,10,10,0,4,3,6);

        ////        // 80%重叠率，分为10x10格网，高取前50%，宽取中间40%
        img1Bitmap = getSubBitmap(img1Bitmap,10,10,0,4,3,6);
        img2Bitmap = getSubBitmap(img2Bitmap,10,10,0,4,3,6);

//        preprocessImages(img1Bitmap,img2Bitmap);




        long startTime = System.nanoTime();
        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
        // 图像转换
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1);    // convert original bitmap to Mat, R G B.
        Utils.bitmapToMat(img2Bitmap, img2);    // convert original bitmap to Mat, R G B


//         1. 阴影抑制预处理
        img1 = removeShadows(img1);
        img2 = removeShadows(img2);
//
//        // 2. 三种预处理方式
//        // 方式一：直方图均衡化
//        img1 = equalizeHistogram(img1);
//        img2 = equalizeHistogram(img2);
//
//        // 方式二：高斯模糊
//        img1 = gaussianBlur(img1);
//        img2 = gaussianBlur(img2);

        // 方式三：边缘增强
        img1 = enhanceEdges(img1);
        img2 = enhanceEdges(img2);

        // 图像的特征点和描述符
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors2 = new Mat();

        // 创建 ORB 特征检测器，和匹配器
        ORB detector = ORB.create();
        detector.setMaxFeatures(1000);  // 增加特征点的数量，默认500，增加到1000
        detector.setScaleFactor(1.5f);  // 调整图像缩放因子 1.379f 1.5f
        detector.setNLevels(8);        // 使用8层金字塔来提高多尺度特征提取
        detector.setEdgeThreshold(31); // 增大阈值，避免边缘处提取特征
        // 提取特征点和描述符
        detector.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
        detector.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);

        // 创建特征匹配器
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT);

        // 特征匹配
        List<MatOfDMatch> matches = new LinkedList<>();
        matcher.knnMatch(descriptors1, descriptors2, matches, 2);

        // 使用比值测试来剔除错误匹配
        float ratioThresh = 1.0f;
        List<DMatch> goodMatchesList = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).rows() > 1) {
                DMatch[] m = matches.get(i).toArray();

                KeyPoint kp1 = keypoints1.toList().get(m[0].queryIdx);  // 图片1的特征点
                KeyPoint kp2 = keypoints2.toList().get(m[0].trainIdx);  // 图片2的特征点

                // 图片1的y坐标要大于图片2，x坐标的差值不大于宽度的10%
                double y1 = kp1.pt.y;
                double y2 = kp2.pt.y;
                double x1 = kp1.pt.x;
                double x2 = kp2.pt.x;
                // ORB提取两对特征点进行筛选
                boolean b0 = m[0].distance < ratioThresh * m[1].distance;

                // 1000像素下，基线差3m，高程差10m
//                // 图像坐标系: y 坐标沿垂直方向向下增加。
//                // 当前无人机向正北飞，即同一点在当前照片y1的坐标大于上一张照片y2
//                boolean b1 = (y1-y2) > 0;;   // >0
//                // 航线重叠率为75%，特征点距离应该为25%。
//                boolean b2 = (y1-y2) < bitmapHeight*0.3;
//                // 当前无人机向正北飞，特征点东西方向应无距离
//                boolean b3 = abs(x1-x2)<bitmapWidth*0.05;

                // 图像坐标系: y 坐标沿垂直方向向下增加。
                // 当前无人机向正北飞，即同一点在当前照片y1的坐标大于上一张照片y2
//                // 航线重叠率为90%，特征点距离应该为10%。允许误差是0.5%（20像素）
//                boolean b1 = (y1-y2) > bitmapHeight*(0.1-0.05);
//                boolean b2 = (y1-y2) < bitmapHeight*(0.1+0.05);
                // 航线重叠率为80%，特征点距离应该为20%。允许误差是1%（20像素）
                boolean b1 = (y1-y2) > bitmapHeight*(0.2-0.05);
                boolean b2 = (y1-y2) < bitmapHeight*(0.2+0.1);
                // 当前无人机向正北飞，特征点东西方向应无距离允许误差是0.4%（20像素）
                boolean b3 = abs(x1-x2)<bitmapWidth*0.04;

                if ( b0 && b1 && b2 && b3) {
                    goodMatchesList.add(m[0]);
                }
            }
        }

        // 筛选好的匹配点的d
        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(goodMatchesList);


        // 使用drawMatches绘制两幅图像和匹配点
        Mat outputImg = new Mat();
        Features2d.drawMatches(img1, keypoints1, img2, keypoints2, goodMatches, outputImg, new Scalar(0, 255, 0), new Scalar(255, 0, 0), new MatOfByte(), Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS);
        // 将Mat转为Bitmap显示在UI中
        Bitmap outputBitmap = Bitmap.createBitmap(outputImg.cols(), outputImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImg, outputBitmap);
        // 保存图像到文件
        saveBitmapToFile(outputBitmap, "ProcessedImages");


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
//            LogUtil.INSTANCE.d(TAG, "Parallax: "+ Parallax*10.0 / 10.0+"AviationHigh[i]: "+ AviationHigh[i]*10.0 / 10.0);

//            // 阈值判断
//            if (AviationHigh[i] < 60) {
//                AviationHigh[i] = 60.0;
//            }
//            else if (AviationHigh[i] > 1200) {
//                AviationHigh[i] = 1200.0;
//            }
        }

        // 航线方向平均值
//        double angleDegressAverage = calculateAverage(angleDegress);
        // 沿航线方向的边界点
//        Point boundaryPoint = calculateBoundaryPoint(new Point(img2.cols()/2, img2.rows()/2), angleDegressAverage>180?angleDegressAverage-180:angleDegressAverage+180, img2.cols(), img2.rows());

        // 执行需要测量运行时间的代码块
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;


        // 根据行高 AviationHigh 和坐标 AviationHighPoints 进行反距离加权
//        double idw = calculateWeight(AviationHigh, AviationHighPoints);

        double idw = calculateMedian(AviationHigh);
        LogUtil.INSTANCE.d(TAG, "均值: "+ calculateAverage(AviationHigh)+ "  中值："+calculateMedian(AviationHigh));


        // CSV 文件路径
        String csvFilePath = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/DJI_20250106/output.csv";

        try (FileWriter writer = new FileWriter(csvFilePath)) {
            // 写入 CSV 头部
            writer.append("AviationHigh,AviationHighPointX,AviationHighPointY\n");

            // 写入数据行
            for (int i = 0; i < AviationHigh.length; i++) {
                writer.append(String.valueOf(AviationHigh[i])) // AviationHigh
                        .append(',')
                        .append(String.valueOf(AviationHighPoints[i].x)) // Point X
                        .append(',')
                        .append(String.valueOf(AviationHighPoints[i].y)) // Point Y
                        .append('\n');
            }
            System.out.println("CSV file saved as: " + csvFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }


//        double weightedAviationHighGS = calculateWeightGS(AviationHigh, AviationHighPoints, boundaryPoint, img2.cols()/3);
//        LogUtil.INSTANCE.d(TAG, "GS距离加权: "+ Math.round(weightedAviationHighGS * 10) / 10.0);
        LogUtil.INSTANCE.d(TAG, "GS距离加权: "+ AviationHigh.toString());
        return idw;
    }

    private double processImageTemplateMatch(Bitmap img1Bitmap, Bitmap img2Bitmap, double FocalLength, double BaseLine, double PixelDim) {
        // 获取图像尺寸
        int bitmapHeight = img1Bitmap.getHeight();
        int bitmapWidth = img1Bitmap.getWidth();

        // 裁剪图像中心区域作为模板（可根据实际情况调整裁剪区域）
        int templateWidth = bitmapWidth / 4;
        int templateHeight = bitmapHeight / 4;
        int xOffset = (bitmapWidth - templateWidth) / 2;
        int yOffset = (bitmapHeight - templateHeight) / 2;

        // 裁剪模板区域（从第一张图像）
        Bitmap templateBitmap = Bitmap.createBitmap(img1Bitmap, xOffset, yOffset, templateWidth, templateHeight);

        // 转换为OpenCV Mat格式
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Mat template = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1);
        Utils.bitmapToMat(img2Bitmap, img2);
        Utils.bitmapToMat(templateBitmap, template);

        // 转换为灰度图像
        Mat grayImg1 = new Mat();
        Mat grayImg2 = new Mat();
        Mat grayTemplate = new Mat();
        Imgproc.cvtColor(img1, grayImg1, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(img2, grayImg2, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(template, grayTemplate, Imgproc.COLOR_RGB2GRAY);

        // 创建结果矩阵
        int resultCols = grayImg2.cols() - grayTemplate.cols() + 1;
        int resultRows = grayImg2.rows() - grayTemplate.rows() + 1;
        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);

        // 执行模板匹配（使用归一化相关系数匹配法）
        Imgproc.matchTemplate(grayImg2, grayTemplate, result, Imgproc.TM_CCOEFF_NORMED);

        // 找到最佳匹配位置
        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
        Point matchLoc = mmr.maxLoc;

        // 计算位移向量
        Point templateCenter = new Point(xOffset + templateWidth/2.0, yOffset + templateHeight/2.0);
        Point matchedCenter = new Point(matchLoc.x + templateWidth/2.0, matchLoc.y + templateHeight/2.0);

        // 计算位移（以图像中心为原点）
        Point displacement = new Point(
                matchedCenter.x - templateCenter.x,
                matchedCenter.y - templateCenter.y
        );

        // 计算视差（像素单位）
        double parallax = Math.sqrt(displacement.x * displacement.x + displacement.y * displacement.y);
        LogUtil.INSTANCE.i(TAG, "parallax " + parallax);

        // 计算航高
        double aviationHigh = FocalLength * BaseLine / (parallax * PixelDim);

        // 可视化结果（可选）
        Mat outputImg = new Mat();
        img2.copyTo(outputImg);

        // 绘制模板区域和匹配区域
        Imgproc.rectangle(outputImg,
                new Point(matchLoc.x, matchLoc.y),
                new Point(matchLoc.x + template.cols(), matchLoc.y + template.rows()),
                new Scalar(0, 255, 0), 3);

        Imgproc.rectangle(outputImg,
                new Point(xOffset, yOffset),
                new Point(xOffset + template.cols(), yOffset + template.rows()),
                new Scalar(255, 0, 0), 3);

        // 绘制位移向量
        Imgproc.arrowedLine(outputImg,
                templateCenter,
                matchedCenter,
                new Scalar(0, 0, 255), 3, Imgproc.LINE_AA, 0, 0.1);

        // 保存结果图像
        Bitmap outputBitmap = Bitmap.createBitmap(outputImg.cols(), outputImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImg, outputBitmap);
        saveBitmapToFile(outputBitmap, "TemplateMatchResult");

        return aviationHigh;
    }

    private double processImageMultiTemplateMatch(Bitmap img1Bitmap, Bitmap img2Bitmap, double FocalLength, double BaseLine, double PixelDim) {
        ////        // 80%重叠率，分为10x10格网，高取前50%，宽取中间40%
        img1Bitmap = getSubBitmap(img1Bitmap,10,10,0,4,3,6);
        img2Bitmap = getSubBitmap(img2Bitmap,10,10,0,4,3,6);

        // 转换为OpenCV Mat格式
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1);
        Utils.bitmapToMat(img2Bitmap, img2);

        // 转换为灰度图像
        Mat grayImg1 = new Mat();
        Mat grayImg2 = new Mat();
        Imgproc.cvtColor(img1, grayImg1, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(img2, grayImg2, Imgproc.COLOR_RGB2GRAY);

        // 定义多个模板区域（可根据实际情况调整）
        List<Rect> templateRegions = new ArrayList<>();
        int templateSize = Math.min(img1.width(), img1.height()) / 4;

        // 中心区域
        templateRegions.add(new Rect(
                img1.width()/2 - templateSize/2,
                img1.height()/2 - templateSize/2,
                templateSize, templateSize));

        // 四个角落区域
        templateRegions.add(new Rect(50, 50, templateSize, templateSize));
        templateRegions.add(new Rect(img1.width()-50-templateSize, 50, templateSize, templateSize));
        templateRegions.add(new Rect(50, img1.height()-50-templateSize, templateSize, templateSize));
        templateRegions.add(new Rect(img1.width()-50-templateSize, img1.height()-50-templateSize, templateSize, templateSize));

        List<Double> parallaxValues = new ArrayList<>();
        Mat outputImg = new Mat();
        img2.copyTo(outputImg);

        for (Rect rect : templateRegions) {
            // 提取模板
            Mat template = new Mat(grayImg1, rect);

            // 创建结果矩阵
            int resultCols = grayImg2.cols() - template.cols() + 1;
            int resultRows = grayImg2.rows() - template.rows() + 1;
            Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);

            // 执行模板匹配
            Imgproc.matchTemplate(grayImg2, template, result, Imgproc.TM_CCOEFF_NORMED);

            // 找到最佳匹配位置
            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            Point matchLoc = mmr.maxLoc;

            // 计算位移向量
            Point templateCenter = new Point(rect.x + rect.width/2.0, rect.y + rect.height/2.0);
            Point matchedCenter = new Point(matchLoc.x + rect.width/2.0, matchLoc.y + rect.height/2.0);

            // 计算视差
            double parallax = Math.sqrt(
                    Math.pow(matchedCenter.x - templateCenter.x, 2) +
                            Math.pow(matchedCenter.y - templateCenter.y, 2));

            parallaxValues.add(parallax);

            // 绘制匹配结果（可视化）
            Imgproc.rectangle(outputImg,
                    new Point(rect.x, rect.y),
                    new Point(rect.x + rect.width, rect.y + rect.height),
                    new Scalar(255, 0, 0), 2);

            Imgproc.rectangle(outputImg,
                    matchLoc,
                    new Point(matchLoc.x + template.cols(), matchLoc.y + template.rows()),
                    new Scalar(0, 255, 0), 2);

            Imgproc.arrowedLine(outputImg,
                    templateCenter,
                    matchedCenter,
                    new Scalar(0, 0, 255), 2, Imgproc.LINE_AA, 0, 0.1);
        }

        // 计算中值视差
        Collections.sort(parallaxValues);
        double medianParallax = parallaxValues.get(parallaxValues.size() / 2);

        // 保存结果图像
        Bitmap outputBitmap = Bitmap.createBitmap(outputImg.cols(), outputImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImg, outputBitmap);
        saveBitmapToFile(outputBitmap, "MultiTemplateMatchResult");

        // 计算航高
        return FocalLength * BaseLine / (medianParallax * PixelDim);
    }

    private Bitmap preprocessImages(Bitmap img1Bitmap, Bitmap img2Bitmap) {
        // 转换为OpenCV Mat格式
        Mat img1 = new Mat();
        Mat img2 = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1);
        Utils.bitmapToMat(img2Bitmap, img2);

        // 1. 阴影抑制预处理
        Mat img1NoShadow = removeShadows(img1);
        Mat img2NoShadow = removeShadows(img2);

        // 2. 三种预处理方式
        // 方式一：直方图均衡化
        Mat img1Eq = equalizeHistogram(img1NoShadow);
        Mat img2Eq = equalizeHistogram(img2NoShadow);

        // 方式二：高斯模糊
        Mat img1Blur = gaussianBlur(img1NoShadow);
        Mat img2Blur = gaussianBlur(img2NoShadow);

        // 方式三：边缘增强
        Mat img1Edge = enhanceEdges(img1NoShadow);
        Mat img2Edge = enhanceEdges(img2NoShadow);

        // 将结果拼接为一张大图用于显示（实际使用时选择一种预处理方式即可）
        Mat result = combineResults(img1, img1Eq, img1Blur, img1Edge,
                img2, img2Eq, img2Blur, img2Edge);

        // 转换为Bitmap返回
        Bitmap resultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, resultBitmap);
        saveBitmapToFile(resultBitmap, "preprocessImages3");

        return resultBitmap;
    }

    // ========== 阴影抑制 ==========
    private Mat removeShadows(Mat src) {
        // 使用Retinex算法抑制阴影
        Mat fImg = new Mat();
        src.convertTo(fImg, CvType.CV_32F);

        // 分离通道处理彩色图像
        List<Mat> channels = new ArrayList<>();
        Core.split(fImg, channels);

        for (int i = 0; i < channels.size(); i++) {
            Mat channel = channels.get(i);
            Core.log(channel, channel);

            // 大核高斯模糊模拟光照分量
            Mat blur = new Mat();
            Imgproc.GaussianBlur(channel, blur, new Size(201, 201), 0);
            Core.log(blur, blur);

            // 减去光照分量
            Core.subtract(channel, blur, channel);

            // 归一化
            Core.normalize(channel, channel, 0, 255, Core.NORM_MINMAX);
            channel.convertTo(channel, CvType.CV_8U);
        }

        Mat dst = new Mat();
        Core.merge(channels, dst);
        return dst;
    }

    // ========== 直方图均衡化 ==========
    private Mat equalizeHistogram(Mat src) {
        Mat dst = new Mat();

        if (src.channels() > 1) {
            // 彩色图像使用CLAHE
            List<Mat> channels = new ArrayList<>();
            Core.split(src, channels);

            CLAHE clahe = Imgproc.createCLAHE();
            clahe.setClipLimit(3.0);

            for (int i = 0; i < channels.size(); i++) {
                clahe.apply(channels.get(i), channels.get(i));
            }

            Core.merge(channels, dst);
        } else {
            // 灰度图像直接均衡化
            Imgproc.equalizeHist(src, dst);
        }

        return dst;
    }

    // ========== 高斯模糊 ==========
    private Mat gaussianBlur(Mat src) {
        Mat dst = new Mat();

        // 先进行小核模糊去噪
        Imgproc.GaussianBlur(src, dst, new Size(3, 3), 0);

        // 保持边缘的内部模糊（双边滤波）
        Imgproc.bilateralFilter(dst, dst, 9, 75, 75);

        return dst;
    }

    // ========== 边缘增强 ==========
    private Mat enhanceEdges(Mat src) {
        Mat gray = new Mat();
        if (src.channels() > 1) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            gray = src.clone();
        }

        // Sobel边缘检测
        Mat gradX = new Mat(), gradY = new Mat();
        Mat absGradX = new Mat(), absGradY = new Mat();

        Imgproc.Sobel(gray, gradX, CvType.CV_16S, 1, 0, 3);
        Imgproc.Sobel(gray, gradY, CvType.CV_16S, 0, 1, 3);

        Core.convertScaleAbs(gradX, absGradX);
        Core.convertScaleAbs(gradY, absGradY);

        // 合并梯度
        Mat edges = new Mat();
        Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0, edges);

        // 增强边缘：原图 + 边缘
        Mat enhanced = new Mat();
        Core.addWeighted(gray, 1.0, edges, 0.7, 0, enhanced);

        return enhanced;
    }

    // ========== 结果拼接 ==========
    private Mat combineResults(Mat img1, Mat img1Eq, Mat img1Blur, Mat img1Edge,
                               Mat img2, Mat img2Eq, Mat img2Blur, Mat img2Edge) {
        // 调整大小一致
        Size size = new Size(300, 200);
        Imgproc.resize(img1, img1, size);
        Imgproc.resize(img1Eq, img1Eq, size);
        Imgproc.resize(img1Blur, img1Blur, size);
        Imgproc.resize(img1Edge, img1Edge, size);
        Imgproc.resize(img2, img2, size);
        Imgproc.resize(img2Eq, img2Eq, size);
        Imgproc.resize(img2Blur, img2Blur, size);
        Imgproc.resize(img2Edge, img2Edge, size);

        // 添加标签
        img1 = putText(img1, "Original 1");
        img1Eq = putText(img1Eq, "HistEqual");
        img1Blur = putText(img1Blur, "GaussBlur");
        img1Edge = putText(img1Edge, "EdgeEnhance");
        img2 = putText(img2, "Original 2");
        img2Eq = putText(img2Eq, "HistEqual");
        img2Blur = putText(img2Blur, "GaussBlur");
        img2Edge = putText(img2Edge, "EdgeEnhance");

        // 水平拼接
        Mat row1 = new Mat(), row2 = new Mat();
        List<Mat> list1 = Arrays.asList(img1, img1Eq, img1Blur, img1Edge);
        List<Mat> list2 = Arrays.asList(img2, img2Eq, img2Blur, img2Edge);
        Core.hconcat(list1, row1);
        Core.hconcat(list2, row2);

        // 垂直拼接
        Mat result = new Mat();
        Core.vconcat(Arrays.asList(row1, row2), result);

        return result;
    }

    private Mat putText(Mat src, String text) {
        Mat dst = src.clone();
        Imgproc.putText(dst, text, new Point(10, 30),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7,
                new Scalar(255, 255, 255), 2);
        return dst;
    }

    /**
     * 对相邻图像进行特征提取和计算航高（完整无省略版）
     * @param img1Bitmap     第一幅图像（必须为正下拍摄，无天空区域）
     * @param img2Bitmap     第二幅图像（与img1Bitmap为连续帧，75%重叠率）
     * @param FocalLength    相机焦距（单位：mm）
     * @param BaseLine       基线距离（两拍摄位置的实际距离，单位：m）
     * @param PixelDim       像元尺寸（单位：mm/像素）
     * @return               计算的航高（单位：m）
     */
    private double processImageORBNew(Bitmap img1Bitmap, Bitmap img2Bitmap,
                                   double FocalLength, double BaseLine, double PixelDim) {
        LogUtil.INSTANCE.d(TAG, "新 processImageORB: ");
        // === 1. 初始化及图像预处理 ===
        long startTime = System.nanoTime();

        // 转换为OpenCV Mat对象（保留原始图像）
        Mat img1Original = new Mat();
        Mat img2Original = new Mat();
        Utils.bitmapToMat(img1Bitmap, img1Original);
        Utils.bitmapToMat(img2Bitmap, img2Original);

        // 显式转换为灰度图（确保ORB处理一致性）
        Mat gray1Original = new Mat();
        Mat gray2Original = new Mat();
        Imgproc.cvtColor(img1Original, gray1Original, Imgproc.COLOR_RGB2GRAY);
        Imgproc.cvtColor(img2Original, gray2Original, Imgproc.COLOR_RGB2GRAY);

        // 降采样处理（平衡速度与精度）
        double scaleFactor = 0.5; // 缩放比例
        double originalHeight = gray1Original.rows(); // 原始图像高度（用于后续重叠率计算）
        Mat gray1 = new Mat();
        Mat gray2 = new Mat();
        Imgproc.resize(gray1Original, gray1, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_AREA);
        Imgproc.resize(gray2Original, gray2, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_AREA);

        LogUtil.INSTANCE.d(TAG, "新 2. ORB特征提取: ");

        // === 2. ORB特征提取 ===
        // 配置ORB参数（针对无人机场景优化）
        ORB orb = ORB.create();
        orb.setMaxFeatures(2000);       // 增加特征点数量
        orb.setScaleFactor(1.2f);       // 金字塔缩放因子
        orb.setEdgeThreshold(15);       // 边缘阈值
        orb.setPatchSize(31);           // 特征描述符区域大小

        // 提取特征点和描述符
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors2 = new Mat();
        orb.detectAndCompute(gray1, new Mat(), keypoints1, descriptors1);
        orb.detectAndCompute(gray2, new Mat(), keypoints2, descriptors2);


        LogUtil.INSTANCE.d(TAG, "新 3. 特征匹配: ");
        // === 3. 特征匹配 ===
        // 使用汉明距离的暴力匹配器
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptors1, descriptors2, knnMatches, 2); // k=2的最近邻匹配

        // 应用比值测试（Lowe's ratio test）筛选优质匹配
        float ratioThresh = 0.7f;
        List<DMatch> goodMatchesList = new ArrayList<>();
        for (MatOfDMatch matOfDMatch : knnMatches) {
            DMatch[] matches = matOfDMatch.toArray();
            if (matches.length < 2) continue;

            // 比值测试：最优匹配距离需显著小于次优匹配
            if (matches[0].distance < ratioThresh * matches[1].distance) {
                goodMatchesList.add(matches[0]);
            }
        }

        // 转换为MatOfDMatch格式（用于后续可视化）
        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(goodMatchesList);
        LogUtil.INSTANCE.d(TAG, "新 4. 运动模型拟合: ");
        // === 4. 运动模型拟合 ===
        // 准备匹配点坐标（降采样图像坐标系）
        MatOfPoint2f pts1 = new MatOfPoint2f();
        MatOfPoint2f pts2 = new MatOfPoint2f();
        List<Point> pts1List = new ArrayList<>();
        List<Point> pts2List = new ArrayList<>();
        for (DMatch m : goodMatchesList) {
            pts1List.add(keypoints1.toList().get(m.queryIdx).pt);
            pts2List.add(keypoints2.toList().get(m.trainIdx).pt);
        }
        pts1.fromList(pts1List);
        pts2.fromList(pts2List);

        // 使用RANSAC拟合相似变换模型（旋转+平移+缩放）
        // 转换 MatOfPoint2f 到 Mat（部分Android版本需要）
        Mat pts1Mat = new Mat();
        Mat pts2Mat = new Mat();
        pts1.convertTo(pts1Mat, CvType.CV_32F);
        pts2.convertTo(pts2Mat, CvType.CV_32F);

        // 使用最简参数版本（兼容所有OpenCV Android版本）
        Mat affine = Calib3d.estimateAffinePartial2D(
                pts1Mat,  // 输入点集1（Mat类型）
                pts2Mat,  // 输入点集2（Mat类型）
                new Mat(), // 内点标记（可选）
                Calib3d.RANSAC,
                2.0,      // 最大重投影误差（像素）
                2000      // 最大迭代次数
        );

        // 检查结果有效性
        if (affine.empty()) {
            Log.e("OpenCV", "运动估计失败");
            return -1;
        }

        // 安全获取位移参数
        double dyScaled = affine.get(1, 2)[0];

        LogUtil.INSTANCE.d(TAG, "新 5. 位移还原与航高计算: ");
        // === 5. 位移还原与航高计算 ===
        // 关键步骤：将位移还原到原始分辨率
        double dyOriginal = dyScaled / scaleFactor;

        // 计算实际重叠率（基于原始图像高度）
        double actualOverlap = 1 - (Math.abs(dyOriginal) / originalHeight);

        // 动态基线校正（若实际重叠率与标称值75%偏差>15%，则使用标称基线）
        double effectiveBaseline = Math.abs(actualOverlap - 0.75) > 0.15 ?
                BaseLine : BaseLine * (0.75 / actualOverlap);

        // 航高计算公式：H = (f * B) / (视差 * 像元尺寸)
        double modelBasedHeight = FocalLength * effectiveBaseline / (Math.abs(dyOriginal) * PixelDim);
        LogUtil.INSTANCE.d(TAG, "新 6. 中值验证（备用方案）: ");
        // === 6. 中值验证（备用方案） ===
        // 为每个优质匹配点计算独立航高
        List<Double> individualHeights = new ArrayList<>();
        for (DMatch m : goodMatchesList) {
            Point p1 = keypoints1.toList().get(m.queryIdx).pt;
            Point p2 = keypoints2.toList().get(m.trainIdx).pt;
            double parallax = Math.abs((p1.y - p2.y) / scaleFactor); // 还原到原始分辨率
            individualHeights.add(FocalLength * effectiveBaseline / (parallax * PixelDim));
        }

        // 计算中值航高
        Collections.sort(individualHeights);
        double medianHeight = individualHeights.get(individualHeights.size() / 2);

        // 最终决策：若模型拟合与中值差异>10%，使用中值结果
        double finalHeight = Math.abs(modelBasedHeight - medianHeight) > 0.1 * medianHeight ?
                medianHeight : modelBasedHeight;
        LogUtil.INSTANCE.d(TAG, "新 7. 日志与调试输出 : ");
        // === 7. 日志与调试输出 ===
        long endTime = System.nanoTime();
        double processTimeMs = (endTime - startTime) / 1e6;

        Log.d("航高计算", String.format(
                "匹配点数量: %d | 降采样位移: %.2fpx | 原始位移: %.2fpx\n" +
                        "模型航高: %.2fm | 中值航高: %.2fm | 最终航高: %.2fm\n" +
                        "耗时: %.2fms",
                goodMatchesList.size(), dyScaled, dyOriginal,
                modelBasedHeight, medianHeight, finalHeight,
                processTimeMs
        ));

        // === 8. 可视化调试（可选） ===
        // 绘制匹配结果（降采样图像坐标系）
        Mat outputImg = new Mat();
        Features2d.drawMatches(
                gray1, keypoints1, gray2, keypoints2, goodMatches, outputImg,
                new Scalar(0, 255, 0), // 匹配线颜色（绿色）
                new Scalar(255, 0, 0), // 特征点颜色（红色）
                new MatOfByte(),
                Features2d.DrawMatchesFlags_NOT_DRAW_SINGLE_POINTS
        );

        Bitmap outputbitmap = Bitmap.createBitmap(outputImg.cols(), outputImg.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outputImg, outputbitmap);
        // 保存可视化结果到文件（调试用）
        saveBitmapToFile(outputbitmap, "ProcessedImages");

        if (Double.isInfinite(finalHeight)) {
            Log.w("HeightCal", "无效航高（视差为零），返回0");
            return 0;
        }

        if (Double.isNaN(finalHeight)) {
            Log.w("HeightCal", "非数字航高，返回0");
            return 0;
        }

        if (finalHeight <= 0) {
            Log.w("HeightCal", "负航高值，返回0");
            return 0;
        }

        return finalHeight;
    }

    /** 保存调试图像到文件 */
    private void saveDebugImage(Mat mat, String prefix) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        // 实现保存逻辑（需处理Android文件权限）
        // ...
    }

    /** 计算数值中值 */
    private double calculateMedian(List<Double> values) {
        Collections.sort(values);
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(middle);
        } else {
            return (values.get(middle - 1) + values.get(middle)) / 2.0;
        }
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
        return Math.round( weightedSum / weightSum * 10) / 10.0;
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
        //若两级滤波，可设第一级processNoise1>processNoise2，第二级measurementNoise2>measurementNoise1以分层过滤不同频段的噪声。
        // 如果已有数据为空，初始化第一个数据点
        if (existingData.isEmpty()) {
            existingData.add(newData);
            return existingData;
        }

        // 初始化第一次滤波的状态估计和误差协方差
        double estimatedValue1 = existingData.get(existingData.size() - 1);
//        double estimatedError1 = 1.0;

        // 初始化第二次滤波的状态估计和误差协方差
        double estimatedValue2 = existingData.get(existingData.size() - 1);
//        double estimatedError2 = 1.0;

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

        LogUtil.INSTANCE.d(TAG, "estimatedError2: "+estimatedError2);

        // 将二次滤波的结果添加到数据列表中
        existingData.add(Math.round(estimatedValue2 * 100) / 100.0 );
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
            LogUtil.INSTANCE.d(TAG, "calculateAverage : Array cannot be null or empty ");
            return 0.0;
        }

        double sum = 0.0;
        for (double num : array) {
            sum += num;
        }

        return sum / array.length;
    }

    public static double calculateMedian(double[] array) {
        if (array == null || array.length == 0) {
            LogUtil.INSTANCE.d(TAG, "calculateMedian : Array cannot be null or empty ");
            return 0.0;
        }

        // 排序数组
        Arrays.sort(array);

        int length = array.length;
        if (length % 2 == 1) {
            // 如果数组长度是奇数，返回中间的元素
            return array[length / 2];
        } else {
            // 如果数组长度是偶数，返回中间两个元素的平均值
            return (array[length / 2 - 1] + array[length / 2]) / 2.0;
        }
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


    /**
     * 对输入的Bitmap进行AxB分割，并提取指定范围内的块。
     *
     * @param inputBitmap 输入的Bitmap图像
     * @param A 行数，将图像分割为A行
     * @param B 列数，将图像分割为B列
     * @param rowStart 起始行索引（包含）
     * @param rowEnd 结束行索引（包含）
     * @param colStart 起始列索引（包含）
     * @param colEnd 结束列索引（包含）
     * @return 返回指定范围内的多个小块Bitmap
     */
    public static Bitmap getSubBitmap(Bitmap inputBitmap, int A, int B, int rowStart, int rowEnd, int colStart, int colEnd) {
        // 获取输入图像的宽度和高度
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();

        // 计算每个小块的宽度和高度
        int blockWidth = width / B;   // 每列的宽度
        int blockHeight = height / A; // 每行的高度

        // 计算目标区域的宽高
        int subWidth = (colEnd - colStart + 1) * blockWidth;
        int subHeight = (rowEnd - rowStart + 1) * blockHeight;

        // 计算目标区域的左上角坐标
        int left = colStart * blockWidth;
        int top = rowStart * blockHeight;

        // 返回指定区域的Bitmap
        return Bitmap.createBitmap(inputBitmap, left, top, subWidth, subHeight);
    }

}
