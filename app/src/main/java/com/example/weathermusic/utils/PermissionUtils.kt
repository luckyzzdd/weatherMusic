package com.example.weathermusic.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build

object PermissionUtils {
    // 定义需要申请的音乐权限（根据系统版本适配）
    fun getAudioPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+：仅申请音频权限
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            // Android 12及以下：申请外部存储读取权限
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // 检查是否已获取音频权限
    fun hasAudioPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(permission)
    }

    // 显示权限拒绝提示弹窗（引导用户去设置页开启）
    fun showPermissionDeniedDialog(context: Activity) {
        AlertDialog.Builder(context)
            .setTitle("权限申请")
            .setMessage("需要读取本地音乐权限才能使用音乐功能，请前往设置开启！")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用权限设置页
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }


}