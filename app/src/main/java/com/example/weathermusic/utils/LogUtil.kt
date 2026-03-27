package com.example.weathermusic.utils

import android.util.Log

/**
 * 日志工具类封装
 * 核心特性：
 * 1. 可全局控制日志开关（上线时设为 false 关闭所有日志）
 * 2. 区分日志级别（VERBOSE/DEBUG/INFO/WARN/ERROR）
 * 3. 自动拼接 TAG（支持自定义），简化调用
 * 4. 捕获异常并打印完整堆栈
 */
object LogUtil {
    // 日志总开关：开发时=true，上线打包时=false（建议通过BuildConfig控制）
    private const val isLogEnable = true

    // 默认TAG（可自定义，也可在调用时指定）
    private const val DEFAULT_TAG = "Weather_Music"

    // ===================== 基础日志方法 =====================
    /**
     * Verbose 级别日志（最详细，用于调试）
     * @param msg 日志内容
     * @param tag 日志标签（默认使用DEFAULT_TAG）
     */
    fun v(msg: String, tag: String = DEFAULT_TAG) {
        if (isLogEnable) Log.v(tag, msg)
    }

    /**
     * Debug 级别日志（开发调试核心日志）
     * @param msg 日志内容
     * @param tag 日志标签
     */
    fun d(msg: String, tag: String = DEFAULT_TAG) {
        if (isLogEnable) Log.d(tag, msg)
    }

    /**
     * Info 级别日志（提示性信息）
     * @param msg 日志内容
     * @param tag 日志标签
     */
    fun i(msg: String, tag: String = DEFAULT_TAG) {
        if (isLogEnable) Log.i(tag, msg)
    }

    /**
     * Warn 级别日志（警告信息，需关注但不影响运行）
     * @param msg 日志内容
     * @param tag 日志标签
     */
    fun w(msg: String, tag: String = DEFAULT_TAG) {
        if (isLogEnable) Log.w(tag, msg)
    }

    /**
     * Error 级别日志（错误信息，必须修复）
     * @param msg 日志内容
     * @param tag 日志标签
     * @param tr 异常对象（可选，打印完整堆栈）
     */
    fun e(msg: String, tag: String = DEFAULT_TAG, tr: Throwable? = null) {
        if (isLogEnable) {
            if (tr != null) {
                Log.e(tag, msg, tr) // 打印日志+异常堆栈
            } else {
                Log.e(tag, msg)
            }
        }
    }


}