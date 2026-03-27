package com.example.weathermusic.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.weathermusic.R
import com.example.weathermusic.data.repository.WeatherRepository
import com.example.weathermusic.databinding.ActivityMainBinding
import com.example.weathermusic.ui.fragment.CityFragment
import com.example.weathermusic.ui.fragment.MusicFragment
import com.example.weathermusic.ui.fragment.WeatherFragment
import com.example.weathermusic.utils.LogUtil
import com.example.weathermusic.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.jar.Manifest

/**
 * Activity 的职责不是 “只管视图”，而是 “管与 Android 系统交互的 UI 层逻辑”
 * 视图相关：渲染 UI、处理用户交互（按钮点击）、响应 ViewModel 的状态更新；
 * 系统交互：调用 Android 系统 API（权限申请、LocationManager、传感器、Intent 跳转等）—— 这些操作必须依赖 Context，且和 UI 生命周期强绑定，是 Activity 的 “天然职责”。
 */
class MainActivity : AppCompatActivity() {
    // ViewBinding：替代findViewById，MVVM核心UI绑定方式
    private lateinit var binding: ActivityMainBinding

    //维护三个List<fragment>
    private lateinit var listFragment: MutableList<Fragment>
    private val TAG: String = "Main_Activity_Yes"
    fun getBinding(): ActivityMainBinding {
        return binding
    }

    fun getFragmentList(): MutableList<Fragment> {
        return listFragment
    }

    //当前的
    private var currentFragment: Fragment? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initNavBottom()
        setupImmersiveMode()
    }

    private fun setupImmersiveMode() {

        // 方式1：适配 Android 11+ (API 30+) 推荐方案
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            window.insetsController?.apply {
                // 隐藏状态栏和导航栏
                hide(WindowInsets.Type.navigationBars())
                // 设置沉浸式交互模式：触摸屏幕边缘时临时显示系统栏，松手后自动隐藏
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun initNavBottom() {
        listFragment = mutableListOf()
        listFragment.apply {
            add(WeatherFragment())
            add(MusicFragment())
            add(CityFragment())
        }
        currentFragment = null
        binding.bottomNav.setSelectedItemId(R.id.nav_weather)
        //默认是天气
        showFragment(listFragment[0])
        //这里是监听触发，就是当你点击底部导航切换就会触发监听，进入showFragment，是fragment切换,然后出来返回true就切换底部选中状态
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_weather -> {
                    showFragment(listFragment[0])
                    true
                }

                R.id.nav_music -> {
                    showFragment(listFragment[1])
                    true
                }

                R.id.nav_city -> {
                    showFragment(listFragment[2])
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 要看是不是第一次展示
     * 1.是第一次，就先隐藏别的fragment,然后add
     * 2，不是第一次，还是要隐藏然后，show
     */
    fun showFragment(targetFragment: Fragment) {
        val ts = supportFragmentManager.beginTransaction()
        if (currentFragment == targetFragment) {
            return
        }
        listFragment.forEach { fragment ->
            if (fragment != targetFragment) {
                ts.hide(fragment)
            }
        }
        if (!targetFragment.isAdded) {
            //首次展示，，添加到容器中
            ts.add(R.id.fragment_container, targetFragment)
            ts.show(targetFragment)
        } else {
            ts.show(targetFragment)
        }
        ts.commit()
        currentFragment = targetFragment
    }

    /**
     * 不能在这里用这个，因为但这个手动更新，会触发前面的监听，会再次进入show->然后再次手动跟新
     */
    fun updateBottomNavSelection(targetFragment: Fragment) {
        when (targetFragment) {
            is WeatherFragment -> {
                binding.bottomNav.selectedItemId = R.id.nav_weather
            }

            is MusicFragment -> {
                binding.bottomNav.selectedItemId = R.id.nav_music
            }

            is CityFragment -> {
                binding.bottomNav.selectedItemId = R.id.nav_city
            }
        }
    }
}

