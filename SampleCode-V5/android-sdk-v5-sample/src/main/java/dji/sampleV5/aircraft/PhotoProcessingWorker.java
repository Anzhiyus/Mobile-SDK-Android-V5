package dji.sampleV5.aircraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Data;
import androidx.annotation.NonNull;

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
import java.io.FileReader;
import java.io.IOException;
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

    private static final int JOB_ID = 1001;    // 用于启动服务的唯一标识符

    // 用于锁定的对象，程序运行过程中出现读取相片不按照顺序的情况，因此设置锁，让当前图像处理完后再处理下一张。因为是读取
    // 的图片数组，实际过程中应该是实时读取，可能不需要此过程。
    private static final Object lock = new Object();

    private double midChange = 0;
    Bitmap midBitmap = null;
    double smoothmean = 0;

    // 平滑权重，weights为最近赋予2权重表示弱化当前数据，赋予1表示强化之前的数据，目的是使当前数据和之前趋势一样
    // weights2则赋予当前数据最大权重1，为了体现当前数据的特征。
    double[] weights = {2, 1,  2, 3 , 4 , 5, 6, 7, 8, 9};
    double[] weights2 = {1,  2, 3 , 4 , 5, 6, 7, 8, 9,10};

    List<Double> idwData_Smooth1 = new ArrayList<>();
    List<Double> idwData_Smooth2 = new ArrayList<>();

    String [] picturearray;
    double [] BaseLine;
    double FocalLength;
    double PixelDim;
    Bitmap bitmap;

    public PhotoProcessingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String bitmapBytes = getInputData().getString("photo_bytes");

//        byte[] bitmapBytes = getInputData().getByteArray("photo_bytes");
//        if (bitmapBytes != null) {
//            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
//            if (bitmap == null) {
//                Log.e("PhotoProcessingWorker", "Failed to decode bitmap");
//                return Result.failure();
//            }
//        }

//        picturearray=getMatchingFileNames(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()+"/H1","^H1.*\\.(jpg|JPG)");
        // 读取文件，计算基线距离
        double[] baseLine=latlonToBaseLine("H1架次CGCS2000、85高");
//
        double FocalLength = 0.04;
        double BaseLine = 100;
        double PixelDim = 4.5/1000/1000; // 米/像素
        if(midBitmap == null)
            midBitmap = bitmap;
        double idw = processImageORB(midBitmap, bitmap, FocalLength, baseLine[0], PixelDim);
        midBitmap = bitmap;

        // 结果为0时改为上一个计算的数值
        if(idw == 0)
            idw = midChange;
        midChange = idw;

        // 第一次加权平滑结果
        calculateIDWSmooth(idwData_Smooth1, idw, 10,weights);
        // 第二次加权平滑结果
        smoothmean = calculateIDWSmooth(idwData_Smooth2, idwData_Smooth1.get(idwData_Smooth1.size()-1), 10,weights2);




        // 返回结果
        Data outputData = new Data.Builder()
                .putDouble("result_value", 42.0)
                .build();
        return Result.success(outputData);
    }

    private double processPhoto(Bitmap bitmap) {
        // 执行照片处理和计算逻辑
        return 0; // 示例返回值
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

    /** 列出当前文件夹内某一文件类型的文件名
     * @param folderPath
     * @param pattern
     * @return
     */
    public static String[] getMatchingFileNames(String folderPath, String pattern) {
        Pattern p = Pattern.compile(pattern); // ".+\\.txt"
        List<String> matchingFileNames = new ArrayList<>();
        File directory = new File(folderPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    Matcher m = p.matcher(file.getName());
                    if (file.isFile() && m.matches()) {
                        matchingFileNames.add(folderPath+"/"+file.getName());
                    }
                }
            }
        }
        Collections.sort(matchingFileNames);
        return matchingFileNames.toArray(new String[0]);
//        return Arrays.copyOfRange(matchingFileNames.toArray(new String[0]), 1201, 1260);
    }

    private double [] latlonToBaseLine(String filename){
        // 读取照片经纬度，用于计算变高的基线
        ArrayList<Point> pointStack = new ArrayList<>();
//        String path_QK3POS=getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()+"/QK3POS.txt";
        String filepath=context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()+"/"+filename+".txt";
        try {
            File file = new File(filepath);
            if (file.exists()) {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                String line;
                // 跳过第一行
                bufferedReader.readLine();
                while ((line = bufferedReader.readLine()) != null) {
                    // 使用空格分隔每一列
                    String[] columns = line.split("\t");
                    // 判断是否有足够的列
                    if (columns.length >= 3) {
                        // 读取第二列和第三列作为 double 类型数据
                        double column2 = Double.parseDouble(columns[1]);
                        double column3 = Double.parseDouble(columns[2]);
                        // 在这里可以使用 column2 和 column3 做进一步的处理
                        pointStack.add(new Point(column2,column3));
                    }
                }
                bufferedReader.close();
            } else {
                Log.e("File Error", "File does not exist");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        double [] BaseLine=new double[pointStack.size()-1];
        for(int i=0; i<pointStack.size()-1; i++){
            BaseLine[i] = Math.sqrt((pointStack.get(i+1).x-pointStack.get(i).x)
                    * (pointStack.get(i+1).x-pointStack.get(i).x)
                    + (pointStack.get(i+1).y-pointStack.get(i).y)
                    * (pointStack.get(i+1).y-pointStack.get(i).y));
        }
        return BaseLine;
    }

}
