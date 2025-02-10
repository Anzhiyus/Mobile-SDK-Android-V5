package dji.sampleV5.aircraft.djicontroller

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {

    private const val TAG = "LogUtil"
    private const val LOG_FILE_PREFIX = "app_log_" // 日志文件前缀
    private const val LOG_FILE_EXTENSION = ".txt"  // 日志文件扩展名
    private var logFileDirectory: File? = null     // 日志文件存放目录

    // 初始化日志工具类
    final fun initialize(context: Context) {
        logFileDirectory = context.getExternalFilesDir(null) // 获取应用的外部存储目录
    }

    // 错误日志
    fun e(tag: String, message: String) {
        Log.e(tag, message)
        writeToFile("E", tag, message)
    }

    // 调试日志
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }

    // 信息日志
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }

    // 警告日志
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("W", tag, message)
    }

    // 详细日志
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        writeToFile("V", tag, message)
    }

    // 将日志写入文件
    private fun writeToFile(level: String, tag: String, message: String) {
        if (logFileDirectory == null) {
            Log.e(TAG, "Log file directory not initialized")
            return
        }

        // 获取当前日期，用于生成日志文件名
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val logFileName = "$LOG_FILE_PREFIX$currentDate$LOG_FILE_EXTENSION"

        // 创建或获取日志文件
        val logFile = File(logFileDirectory, logFileName)

        // 获取当前时间，用于日志内容的时间戳
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date())
        val logMessage = "$timestamp $level/$tag: $message\n"

        // 将日志写入文件
        try {
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing log to file", e)
        }
    }
}