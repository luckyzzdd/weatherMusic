package com.example.weathermusic.viewmodel

import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.weathermusic.App
import com.example.weathermusic.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel : ViewModel(){
    val musicList= MusicRepository.observeAllMusics().asLiveData()

    val musicFolderList = MusicRepository.observeAllFolders().asLiveData()
    //新增：扫描并存储音乐
    fun scanAndSaveLocalMusic() {
        // 必须用viewModelScope启动协程（耗时操作不能在主线程）
        viewModelScope.launch {
            // 1. 扫描本地音乐
            val scannedMusics = MusicRepository.scanLocalMusic()
            // 2. 存储到Room
            MusicRepository.insertMusics(scannedMusics)

            withContext(Dispatchers.Main){
                val tipText = if (scannedMusics.isNotEmpty()) {
                    "扫描完成！共找到 ${scannedMusics.size} 首本地音乐"
                } else {
                    "未扫描到本地音乐"
                }
                Toast.makeText(
                    App.instance, // 替换成你的上下文（Activity/Fragment用this/requireContext()）
                    tipText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


}