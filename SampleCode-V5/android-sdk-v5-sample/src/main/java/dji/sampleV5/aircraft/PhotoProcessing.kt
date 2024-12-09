package dji.sampleV5.aircraft

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.mapboxsdk.Mapbox.getApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList

class PhotoProcessing {
    companion object {
        var context: Context = getApplicationContext() // 获取 Context

        private const val TAG = "OpencvpictureActivity"
        var smoothmean = 0.0

        // 卡尔曼滤波初始化误差参数
        var estimatedError1 = 1.0
        var estimatedError2 = 1.0

//        // 平滑权重，weights为最近赋予2权重表示弱化当前数据，赋予1表示强化之前的数据，目的是使当前数据和之前趋势一样
//        // weights2则赋予当前数据最大权重1，为了体现当前数据的特征。
//        var weights = doubleArrayOf(2.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
//        var weights2 = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)

        private const val SHARED_PREFS_NAME = "WorkerData"
        private val gson = Gson()


        fun initDownloadPhoto(
            path1: String,
            baseLine: Double,
            focalLength: Double,
            pixelDim: Double
        ): Double {
            // 获取共享数据
            val sharedPreferences: SharedPreferences = getApplicationContext().getSharedPreferences(
                SHARED_PREFS_NAME, Context.MODE_PRIVATE
            )
            var tempDataPath2 = sharedPreferences.getString("tempDataPath2", "kong")
            var tempDataIdw = sharedPreferences.getFloat("tempDataIdw", 0.0f).toDouble()
//            val idwData_Smooth1: MutableList<Double> =
//                jsonToDoubleList(sharedPreferences.getString("idwData_Smooth1", "[]") ?: "[]")
//                    ?.filterNotNull()?.toMutableList() ?: mutableListOf()
//            val idwData_Smooth2: MutableList<Double> =
//                jsonToDoubleList(sharedPreferences.getString("idwData_Smooth2", "[]") ?: "[]")
//                    ?.filterNotNull()?.toMutableList() ?: mutableListOf()
            val idwData_KalmanFilter: MutableList<Double> =
                jsonToDoubleList(sharedPreferences.getString("idwData_KalmanFilter", "[]") ?: "[]")
                    ?.filterNotNull()?.toMutableList() ?: mutableListOf()

            Log.d(TAG, "photo_path: $path1")


            // 检查临时照片是否为空
            if (tempDataPath2 === "kong") {
                Log.d(TAG,"The list is empty. Function completed: $tempDataPath2")
                // 数据暂存缓存路径
                // tempDataPath2 = saveImageToCacheDir(context, path1, "DroneFlyTemp")
                tempDataPath2 =
                    saveImageToCacheDir(context, path1, path1.substring(path1.lastIndexOf("/") + 1))
                // 更新并保存共享数据
                val editor = sharedPreferences.edit()
                editor.putString("tempDataPath2", tempDataPath2)
                editor.apply()
                // 如果列表为空，函数直接返回
                Log.d(TAG,"The list is empty. Function completed: $tempDataPath2")
                // 返回结果
                return 0.01
            }

            // 提取相邻照片
            var img1Bitmap: Bitmap? = null
            var img2Bitmap: Bitmap? = null
            val options = BitmapFactory.Options()
            options.inScaled = false // 阻止 Android 自动缩放相片分辨率
            try {
                FileInputStream(path1).use { fis -> img1Bitmap = BitmapFactory.decodeStream(fis) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                FileInputStream(tempDataPath2).use { fis ->
                    img2Bitmap = BitmapFactory.decodeStream(fis)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var idw: Double = processImageORB(img1Bitmap!!, img2Bitmap!!, focalLength, baseLine, pixelDim)
            // 数据暂存缓存路径
            // 数据暂存缓存路径
            // tempDataPath2 = saveImageToCacheDir(context, path1, "DroneFlyTemp")
            tempDataPath2 =
                saveImageToCacheDir(context, path1, path1.substring(path1.lastIndexOf("/") + 1))

            // 结果为0时改为上一个计算的数值

            // 结果为0时改为上一个计算的数值
            if (idw == 0.0) idw = tempDataIdw
            tempDataIdw = idw



//            // 第一次加权平滑结果
//            calculateIDWSmooth(idwData_Smooth1, idw, 10, weights)
//            // 第二次加权平滑结果
//            // 第二次加权平滑结果
//            smoothmean = calculateIDWSmooth(
//                idwData_Smooth2,
//                idwData_Smooth1[idwData_Smooth1.size - 1], 10, weights2
//            )
//            Log.d(TAG, "smoothmean: $smoothmean")



            // 卡尔曼滤波
            Log.d(
                PhotoProcessing.TAG,
                "KalmanFilter: " + PhotoProcessing.applyKalmanFilter(
                    idwData_KalmanFilter,
                    idw
                )
            )
            // 更新并保存共享数据

            // 更新并保存共享数据
            val editor = sharedPreferences.edit()
            editor.putString("tempDataPath2", tempDataPath2)
            editor.putFloat("tempDataIdw", tempDataIdw.toFloat())
//            editor.putString("idwData_Smooth1", doubleListToJson(idwData_Smooth1))
//            editor.putString("idwData_Smooth2", doubleListToJson(idwData_Smooth2))
            editor.putString("idwData_KalmanFilter", doubleListToJson(idwData_KalmanFilter))
            editor.apply()

            return idwData_KalmanFilter.last()// 返回结果
        }

        // 重载方法，使用默认参数
        fun applyKalmanFilter(existingData: MutableList<Double>, newData: Double): List<Double>? {
            return applyKalmanFilter(existingData, newData, 0.05, 0.5, 0.05, 0.5)
        }

        fun applyKalmanFilter(
            existingData: MutableList<Double>, newData: Double,
            processNoise1: Double, measurementNoise1: Double,
            processNoise2: Double, measurementNoise2: Double
        ): List<Double>? {
            // processNoise1越小越平滑，measurementNoise1相反，并且两者作用好像类似即只需要调一个参数即可
            // 如果已有数据为空，初始化第一个数据点
            if (existingData.isEmpty()) {
                existingData.add(newData)
                return existingData
            }

            // 初始化第一次滤波的状态估计和误差协方差
            var estimatedValue1 = existingData[existingData.size - 1]

            // 初始化第二次滤波的状态估计和误差协方差
            var estimatedValue2 = existingData[existingData.size - 1]

            // 第一次滤波 - 预测阶段
            val predictedValue1 = estimatedValue1
            val predictedError1 = PhotoProcessing.estimatedError1 + processNoise1

            // 第一次滤波 - 更新阶段
            val kalmanGain1 = predictedError1 / (predictedError1 + measurementNoise1)
            estimatedValue1 = predictedValue1 + kalmanGain1 * (newData - predictedValue1)
            PhotoProcessing.estimatedError1 = (1 - kalmanGain1) * predictedError1

            // 第二次滤波 - 预测阶段
            val predictedValue2 = estimatedValue2
            val predictedError2 = PhotoProcessing.estimatedError2 + processNoise2

            // 第二次滤波 - 更新阶段
            val kalmanGain2 = predictedError2 / (predictedError2 + measurementNoise2)
            estimatedValue2 = predictedValue2 + kalmanGain2 * (estimatedValue1 - predictedValue2)
            PhotoProcessing.estimatedError2 = (1 - kalmanGain2) * predictedError2
            Log.d(
                PhotoProcessing.TAG,
                "estimatedError2: " + PhotoProcessing.estimatedError2
            )

            // 将二次滤波的结果添加到数据列表中
            existingData.add(Math.round(estimatedValue2 * 10) / 10.0)
            return existingData
        }

        private fun jsonToDoubleList(json: String): List<Double?>? {
            val listType = object : TypeToken<ArrayList<Double?>?>() {}.type
            return gson.fromJson(json, listType)
        }

        private fun doubleListToJson(list: List<Double>): String? {
            return gson.toJson(list)
        }

        /**
         * 将给定路径的 JPEG 图像复制到应用的缓存目录中，并保存为指定的路径名称。
         *
         * @param context 应用上下文
         * @param sourcePath 原图像路径
         * @param targetFileName 缓存目录下的目标文件名称（不包括扩展名）
         * @return 目标文件的路径，如果失败则返回 null
         */
        fun saveImageToCacheDir(
            context: Context,
            sourcePath: String?,
            targetFileName: String
        ): String? {
            val sourceFile = File(sourcePath)
            val cacheDir = context.cacheDir
            val targetFile = File(cacheDir, "$targetFileName.jpg")

            // 确保源文件存在
            if (!sourceFile.exists()) {
                return null
            }
            try {
                FileInputStream(sourceFile).use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
            return targetFile.absolutePath
        }

        /** 对相邻图像进行特征提取和计算航高
         *
         * @param img1Bitmap ：第一幅图像
         * @param img2Bitmap ：第二幅图像
         * @param FocalLength ：焦距
         * @param BaseLine ：基线距离
         * @param PixelDim ： 像元尺寸
         */
        private fun processImageORB(
            img1Bitmap: Bitmap,
            img2Bitmap: Bitmap,
            FocalLength: Double,
            BaseLine: Double,
            PixelDim: Double
        ): Double {
            val startTime = System.nanoTime()
            // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
            // 图像转换
            val img1 = Mat()
            val img2 = Mat()
            Utils.bitmapToMat(
                img1Bitmap,
                img1
            ) // convert original bitmap to Mat, R G B.
            Utils.bitmapToMat(
                img2Bitmap,
                img2
            ) // convert original bitmap to Mat, R G B

            // 图像的特征点和描述符
            val keypoints1 = MatOfKeyPoint()
            val descriptors1 = Mat()
            val keypoints2 = MatOfKeyPoint()
            val descriptors2 = Mat()

            // 创建 ORB 特征检测器，和匹配器
            val detector = ORB.create()
            // 提取特征点和描述符
            detector.detectAndCompute(img1, Mat(), keypoints1, descriptors1)
            detector.detectAndCompute(img2, Mat(), keypoints2, descriptors2)

            // 创建特征匹配器
            val matcher =
                DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT)

            // 特征匹配
            val matches: List<MatOfDMatch> = LinkedList()
            matcher.knnMatch(descriptors1, descriptors2, matches, 2)

            // 使用比值测试来剔除错误匹配
            val ratioThresh = 0.7f
            val goodMatchesList: MutableList<DMatch> =
                java.util.ArrayList()
            for (i in matches.indices) {
                if (matches[i].rows() > 1) {
                    val m = matches[i].toArray()
                    if (m[0].distance < ratioThresh * m[1].distance) {
                        goodMatchesList.add(m[0])
                    }
                }
            }

            // 筛选好的匹配点的d
            val goodMatches = MatOfDMatch()
            goodMatches.fromList(goodMatchesList)
            val dmatchArray = goodMatches.toArray()
            val keyPointArray1 = keypoints1.toArray()
            val keyPointArray2 = keypoints2.toArray()
            val AviationHigh = DoubleArray(dmatchArray.size)
            val AviationHighPoints =
                arrayOfNulls<Point>(dmatchArray.size)

            // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine /  Parallax
            for (i in dmatchArray.indices) {
                // 根据索引求匹配点坐标
                val Idx1 = dmatchArray[i].queryIdx
                val Idx2 = dmatchArray[i].trainIdx
                val point1 = Point(
                    keyPointArray1[Idx1].pt.x - img1.cols() / 2,
                    keyPointArray1[Idx1].pt.y - img1.rows() / 2
                )
                val point2 = Point(
                    keyPointArray2[Idx2].pt.x - img2.cols() / 2,
                    keyPointArray2[Idx2].pt.y - img2.rows() / 2
                )
                // 视差
                val Parallax =
                    Math.sqrt((point1.x - point2.x) * (point1.x - point2.x) + (point1.y - point2.y) * (point1.y - point2.y))
                // 距离=焦距*基线/视差   AviationHigh=FocalLength * BaseLine / (Parallax * PixelDim)
                AviationHighPoints[i] = Point(
                    keyPointArray2[Idx2].pt.x - img2.cols() / 2,
                    keyPointArray2[Idx2].pt.y - img2.rows() / 2
                )
                AviationHigh[i] = FocalLength * BaseLine / (Parallax * PixelDim)
                if (AviationHigh[i] < 200) {
                    AviationHigh[i] = 200.0
                } else if (AviationHigh[i] > 1200) {
                    AviationHigh[i] = 1200.0
                }
            }

            // 执行需要测量运行时间的代码块
            val endTime = System.nanoTime()
            val elapsedTime = endTime - startTime

            // 根据行高 AviationHigh 和坐标 AviationHighPoints 进行反距离加权
            return PhotoProcessingWorker.calculateWeight(AviationHigh, AviationHighPoints)
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
        fun calculateIDWSmooth(
            demData: MutableList<Double>,
            newData: Double,
            beta: Int,
            weights: DoubleArray
        ): Double {
            var smoothmean = newData
            demData.add(newData)

            // 判断是否需要修正数据
            if (demData.size >= 10) {
                // 截取最后beta个数据。
                val size = Math.min(demData.size, beta)
                val subdemData: List<Double> =
                    java.util.ArrayList(demData.subList(demData.size - size, demData.size))
                val dataSize = subdemData.size
                val values = DoubleArray(dataSize)
                val points = arrayOfNulls<Point>(dataSize)
                for (i in 0 until dataSize) {
                    values[i] = subdemData[dataSize - 1 - i] // 反向，最后的数据给的距离最短，权重最大
                    points[i] = Point(weights[i], 0.0)
                }
                smoothmean = PhotoProcessingWorker.calculateWeight(values, points)
                demData[demData.size - 1] = smoothmean
            }
            return smoothmean
        }
    }
}
