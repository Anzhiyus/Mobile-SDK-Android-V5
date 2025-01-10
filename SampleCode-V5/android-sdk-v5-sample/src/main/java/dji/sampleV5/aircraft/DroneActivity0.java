package dji.sampleV5.aircraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dji.sampleV5.aircraft.pages.MediaFragment;

public class DroneActivity0 extends AppCompatActivity {

    private static final String TAG = "OpencvpictureActivity";

    private static final String SHARED_PREFS_NAME = "WorkerData";

    MediaFragment myFragment = new MediaFragment();
    int i=0;



    // 判断OpenCV是否加载成功
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                // OpenCV加载成功
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_drone);
        setContentView(R.layout.activity_drone);

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV初始化失败", Toast.LENGTH_SHORT).show();
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        // 创建MyFragment实例
        if (savedInstanceState == null) {
            // 使用supportFragmentManager开始事务
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, myFragment)
                    .hide(myFragment) // 隐藏 myFragment
                    .commit();  // 提交事务
        }
//        takePhoto();

//        initTakePhoto();




    }

    private void initTakePhoto(){
        findViewById(R.id.btn_take_photo).setOnClickListener(v -> myFragment.take_photo());
        int a=0   ;
    }

    public void initDownloadPhoto(View view) {

//        Bitmap bitmapData = myFragment.download_photo();
//
//            byte[] bitmapBytes = myFragment.downloadPhotoByteArray();

//        clearSharedPreferences();


//        for (int i = 0; i < picturearray.length; i++) {
//
            String path1=picturearray[i];
            Log.d(TAG, "picturearray: "+path1);

            // 创建 WorkRequest
            WorkRequest photoProcessingWorkRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
                    .setInputData(new Data.Builder()
                            .putString("photo_path", path1)
                            .putDouble("photo_baseLine", BaseLine[i])
                            .build())
                    .build();

            // 获取 WorkManager 实例
            WorkManager workManager = WorkManager.getInstance(this);

            // 启动工作
            workManager.enqueue(photoProcessingWorkRequest);

            // 监听结果
            workManager.getWorkInfoByIdLiveData(photoProcessingWorkRequest.getId()).observe(this, workInfo -> {
                if (workInfo != null && workInfo.getState().isFinished()) {
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        Data outputData = workInfo.getOutputData();
                        double resultValue = outputData.getDouble("result_value", 0.0);
                        // 使用 resultValue
                        Log.d(TAG, "resultValue: "+resultValue);
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        // 处理失败情况
                    }
                }
            });

            i++;
//
//
//        }

//        WorkManager workManager = WorkManager.getInstance(this);
//
//// 创建初始任务
//        OneTimeWorkRequest.Builder firstWorkRequestBuilder = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class);
//        firstWorkRequestBuilder.setInputData(new Data.Builder()
//                .putString("photo_path", picturearray[0])
//                .putDouble("photo_baseLine", BaseLine[0])
//                .build());
//        OneTimeWorkRequest firstWorkRequest = firstWorkRequestBuilder.build();
//
//// 创建其他任务
//        List<OneTimeWorkRequest> workRequests = new ArrayList<>();
//        for (int i = 1; i < 5; i++) {
//            Log.d(TAG, "picturearray_i: "+i);
//            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
//                    .setInputData(new Data.Builder()
//                            .putString("photo_path", picturearray[i])
//                            .putDouble("photo_baseLine", BaseLine[i])
//                            .build())
//                    .build();
//            workRequests.add(workRequest);
//        }
//
//// 链接任务
//        workManager.beginWith(firstWorkRequest)
//                .then(workRequests)
//                .enqueue();
//
//        byte[] bitmapBytes = myFragment.downloadPhotoByteArray();
//
//
//            // 创建 WorkRequest
//            WorkRequest photoProcessingWorkRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
//                    .setInputData(new Data.Builder()
//                            .putByteArray("photo_bytes", bitmapBytes)
//                            .putByteArray("photo_bytes", bitmapBytes)
//                            .build())
//                    .build();

//        String bitmapBytes = "myFragment.downloadPhotoByteArray()";
//
//        // 创建 WorkRequest
//        WorkRequest photoProcessingWorkRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
//                .setInputData(new Data.Builder()
//                        .putString("photo_bytes", bitmapBytes)
//                        .build())
//                .build();

//        // 获取 WorkManager 实例
//        WorkManager workManager = WorkManager.getInstance(this);
//
//        // 启动工作
//        workManager.enqueue(photoProcessingWorkRequest);
//
//        // 监听结果
//        workManager.getWorkInfoByIdLiveData(photoProcessingWorkRequest.getId()).observe(this, workInfo -> {
//            if (workInfo != null && workInfo.getState().isFinished()) {
//                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
//                    Data outputData = workInfo.getOutputData();
//                    double resultValue = outputData.getDouble("result_value", 0.0);
//                    // 使用 resultValue
//                        Log.d(TAG, "resultValue: "+resultValue);
//                } else if (workInfo.getState() == WorkInfo.State.FAILED) {
//                    // 处理失败情况
//                }
//            }
//        });
    }

    private void clearSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear(); // 清除所有数据
        editor.apply(); // 或者使用 editor.commit();
    }

    public static byte[] readImageToByteArray(String path) {
        FileInputStream fis = null;
        byte[] imageBytes = null;

        try {
            File file = new File(path);
            imageBytes = new byte[(int) file.length()];
            fis = new FileInputStream(file);
            fis.read(imageBytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return imageBytes;
    }

//    private void initDownloadPhoto(){
//        findViewById(R.id.btn_download).setOnClickListener(v -> {
//            Bitmap bitmapData = myFragment.download_photo();
//
////            byte[] bitmapBytes = myFragment.downloadPhotoByteArray();
////
////            // 创建 WorkRequest
////            WorkRequest photoProcessingWorkRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
////                    .setInputData(new Data.Builder()
////                            .putByteArray("photo_bytes", bitmapBytes)
////                            .build())
////                    .build();
//
//            String bitmapBytes = "myFragment.downloadPhotoByteArray()";
//
//            // 创建 WorkRequest
//            WorkRequest photoProcessingWorkRequest = new OneTimeWorkRequest.Builder(PhotoProcessingWorker.class)
//                    .setInputData(new Data.Builder()
//                            .putString("photo_bytes", bitmapBytes)
//                            .build())
//                    .build();
//
//            // 获取 WorkManager 实例
//            WorkManager workManager = WorkManager.getInstance(this);
//
//            // 启动工作
//            workManager.enqueue(photoProcessingWorkRequest);
//
//            // 监听结果
//            workManager.getWorkInfoByIdLiveData(photoProcessingWorkRequest.getId()).observe(this, workInfo -> {
//                if (workInfo != null && workInfo.getState().isFinished()) {
//                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
//                        Data outputData = workInfo.getOutputData();
//                        double resultValue = outputData.getDouble("result_value", 0.0);
//                        // 使用 resultValue
////                        Log.d(TAG, "resultValue: "+resultValue);
//                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
//                        // 处理失败情况
//                    }
//                }
//            });
////
////            // 异步服务
////            Intent workIntent = new Intent(this, ORBJobIntentService.class);
////            workIntent.putExtra("selectedData", "selectedData");
////            // 根据下拉框所选数据进行相应操作，具体操作在 ORBJobIntentService.java 中
////            ORBJobIntentService.enqueueWork(this,workIntent);
////            // 异步服务
////            Intent workIntent = new Intent(this, ORBJobIntentService.class);
//////            workIntent.putExtra("bitmapData", bitmapData);
//////            workIntent.putExtra("BaseLine", BaseLine);
////            // 根据下拉框所选数据进行相应操作，具体操作在 ORBJobIntentService.java 中
////            ORBJobIntentService.enqueueWork(this,workIntent);
//
////            double FocalLength = 0.04;
////            double BaseLine = 100;
////            double PixelDim = 4.5/1000/1000; // 米/像素
////            if(midBitmap == null)
////                midBitmap = bitmap;
////            processImageORB(midBitmap, bitmap, FocalLength, BaseLine, PixelDim);
////            midBitmap = bitmap;
//        });
//    }

//    private Handler handler = new Handler(Looper.getMainLooper()) {
//        @Override
//        public void handleMessage(Message msg) {
//            // Extract data from the message and update the UI
//            double dataIdw = msg.getData().getDouble("dataIdw");
//            Log.d(TAG, "handleMessage: "+ dataIdw);
//
//        }
//    };

//    private Handler handler = new Handler(Looper.getMainLooper()) {
//        @Override
//        public void handleMessage(Message msg) {
//            // 处理接收到的数据
//            Bundle data = msg.getData();
//            // 从 Bundle 中获取数据并更新 UI
//            double result = data.getDouble("result_key");
////            updateUI(result);
//        }
//    };
//
    // 执行操作C
    String [] picturearray;
    double [] BaseLine;


    public void takePhoto(){
        picturearray=getMatchingFileNames(getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()+"/H1","^H1.*\\.(jpg|JPG)");
        Log.d(TAG, "takePhoto: "+picturearray.length);
        // 读取文件，计算基线距离
        BaseLine=latlonToBaseLine("H1架次CGCS2000、85高");
        Log.d(TAG, "H1架次CGCS2000: " + BaseLine);

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
        String filepath=getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()+"/"+filename+".txt";
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
        int size = pointStack.size();
        if (size <= 1) {
            throw new IllegalArgumentException("pointStack must contain more than 1 point to form a baseline.");
        }
        double[] BaseLine = new double[size - 1];
        for(int i=0; i<pointStack.size()-1; i++){
            BaseLine[i] = Math.sqrt((pointStack.get(i+1).x-pointStack.get(i).x)
                    * (pointStack.get(i+1).x-pointStack.get(i).x)
                    + (pointStack.get(i+1).y-pointStack.get(i).y)
                    * (pointStack.get(i+1).y-pointStack.get(i).y));
        }
        return BaseLine;
    }



//    /** 对相邻图像进行特征提取和计算航高
//     *
//     * @param img1Bitmap ：第一幅图像
//     * @param img2Bitmap ：第二幅图像
//     * @param FocalLength ：焦距
//     * @param BaseLine ：基线距离
//     * @param PixelDim ： 像元尺寸
//     */
//    private double processImageORB(Bitmap img1Bitmap, Bitmap img2Bitmap, double FocalLength, double BaseLine, double PixelDim){
//        long startTime = System.nanoTime();
//        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
//        // 图像转换
//        Mat img1 = new Mat();
//        Mat img2 = new Mat();
//        Utils.bitmapToMat(img1Bitmap, img1);    // convert original bitmap to Mat, R G B.
//        Utils.bitmapToMat(img2Bitmap, img2);    // convert original bitmap to Mat, R G B
//
//        // 图像的特征点和描述符
//        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
//        Mat descriptors1 = new Mat();
//        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
//        Mat descriptors2 = new Mat();
//
//        // 创建 ORB 特征检测器，和匹配器
//        ORB detector = ORB.create();
//        // 提取特征点和描述符
//        detector.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
//        detector.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);
//
//        // 创建特征匹配器
//        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT);
//
//        // 特征匹配
//        List<MatOfDMatch> matches = new LinkedList<>();
//        matcher.knnMatch(descriptors1, descriptors2, matches, 2);
//
//        // 使用比值测试来剔除错误匹配
//        float ratioThresh = 0.7f;
//        List<DMatch> goodMatchesList = new ArrayList<>();
//        for (int i = 0; i < matches.size(); i++) {
//            if (matches.get(i).rows() > 1) {
//                DMatch[] m = matches.get(i).toArray();
//                if (m[0].distance < ratioThresh * m[1].distance) {
//                    goodMatchesList.add(m[0]);
//                }
//            }
//        }
//
//        // 筛选好的匹配点的d
//        MatOfDMatch goodMatches = new MatOfDMatch();
//        goodMatches.fromList(goodMatchesList);
//        DMatch[] dmatchArray=goodMatches.toArray();
//        KeyPoint[] keyPointArray1 = keypoints1.toArray();
//        KeyPoint[] keyPointArray2 = keypoints2.toArray();
//
//        double[] AviationHigh = new double[dmatchArray.length];
//        Point[] AviationHighPoints = new Point[dmatchArray.length];
//
//        // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine /  Parallax
//        for (int i = 0; i < dmatchArray.length; i++) {
//            // 根据索引求匹配点坐标
//            int Idx1=dmatchArray[i].queryIdx;
//            int Idx2=dmatchArray[i].trainIdx;
//            Point point1= new Point(keyPointArray1[Idx1].pt.x-img1.cols()/2,  keyPointArray1[Idx1].pt.y-img1.rows()/2);
//            Point point2= new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2,  keyPointArray2[Idx2].pt.y-img2.rows()/2);
//            // 视差
//            double Parallax=Math.sqrt((point1.x - point2.x) *(point1.x - point2.x)+ (point1.y - point2.y)*(point1.y - point2.y));
//            // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
//            AviationHighPoints[i]=new Point(keyPointArray2[Idx2].pt.x-img2.cols()/2, keyPointArray2[Idx2].pt.y-img2.rows()/2);
//
//            AviationHigh[i]=FocalLength * BaseLine / (Parallax * PixelDim);
//            if (AviationHigh[i] < 200) {
//                AviationHigh[i] = 200.0;
//            }
//            // 如果元素大于max，则修正为max
//            else if (AviationHigh[i] > 1200) {
//                AviationHigh[i] = 1200.0;
//            }
//        }
//
//        // 执行需要测量运行时间的代码块
//        long endTime = System.nanoTime();
//        long elapsedTime = endTime - startTime;
//
//        // 根据行高 AviationHigh 和坐标 AviationHighPoints 进行反距离加权
//        double idw = calculateIDW(AviationHigh, AviationHighPoints);
//
//        // 结果为0时改为上一个计算的数值
//        if(idw == 0)
//            idw = idwData_Smooth2.get(idwData_Smooth2.size()-1);
//
//        // 第一次加权平滑结果
//        calculateIDWSmooth(idwData_Smooth1, idw, 10,weights);
//        // 第二次加权平滑结果
//        double smoothmean = calculateIDWSmooth(idwData_Smooth2, idwData_Smooth1.get(idwData_Smooth1.size()-1), 10,weights2);
//        return smoothmean;
//    }
//
//    /** 反距离加权平均
//     *
//     * @param values 数值
//     * @param points 坐标
//     * @return
//     */
//    public static double calculateIDW(double[] values, Point[] points) {
//        if (values.length != points.length || values.length == 0) {
//            return 0;
//        }
//
//        double weightedSum = 0.0;
//        double weightSum = 0.0;
//
//        for (int i = 0; i < values.length; i++) {
//            double distance = calculateDistanceToCenter(points[i]);
//            double weight = 1.0 / distance; // 反距离作为权重
//            weightedSum += values[i] * weight;
//            weightSum += weight;
//        }
//
//        if (weightSum == 0.0) {
//            throw new ArithmeticException("Cannot divide by zero. All point distances are zero.");
//        }
//        return weightedSum / weightSum;
//    }
//
//    // 计算点到中心的距离
//    private static double calculateDistanceToCenter(Point point) {
//        // 这里简化为点到原点的距离，你可以根据实际情况修改为其他距离计算方式
//        return Math.sqrt(point.x * point.x + point.y * point.y);
//    }
//
//
//    /** 滑动窗口反距离加权平均算法你
//     * 将newData数据加入到数组demData中，当数组数量超过beta个时，开始对newData数据进行加权计算
//     * 将newData数据之前的（包括newData）进行加权，得到的值赋给newData数据
//     * @param demData 存储数组
//     * @param newData 新增数据
//     * @param beta 滑动窗口大小
//     * @param weights 权重数组，自定义
//     * @return 加权计算结果
//     */
//    public double calculateIDWSmooth(List<Double> demData, double newData ,int beta, double[] weights) {
//        double smoothmean = newData;
//        demData.add(newData);
//
//        // 判断是否需要修正数据
//        if (demData.size() >= 10) {
//            // 截取最后beta个数据。
//            int size = Math.min(demData.size(), beta);
//            List<Double> subdemData=new ArrayList<>(demData.subList(demData.size() - size, demData.size()));
//            int dataSize = subdemData.size();
//            double[] values = new double[dataSize];
//            Point[] points = new Point[dataSize];
//            for (int i = 0; i < dataSize; i++) {
//                values[i] = subdemData.get(dataSize-1-i);  // 反向，最后的数据给的距离最短，权重最大
//                points[i] = new Point(weights[i],0);
//            }
//            smoothmean = calculateIDW(values, points);
//            demData.set(demData.size() - 1, smoothmean);
//        }
//        else{
//            smoothmean = demData.get(demData.size() -1);
//        }
//        return smoothmean;
//    }


}
