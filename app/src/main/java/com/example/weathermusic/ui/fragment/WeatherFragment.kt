package com.example.weathermusic.ui.fragment

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.weathermusic.R
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.databinding.FragmentWeatherBinding
import com.example.weathermusic.service.MusicPlayService

import com.example.weathermusic.ui.main.LocationState
import com.example.weathermusic.ui.main.MainActivity
import com.example.weathermusic.utils.LogUtil
import com.example.weathermusic.utils.NotificationHelper
import com.example.weathermusic.utils.PermissionUtils
import com.example.weathermusic.viewmodel.MusicViewModel

import com.example.weathermusic.viewmodel.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WeatherFragment : Fragment() {
    private lateinit var binding: FragmentWeatherBinding

    private lateinit var cityActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>


    private lateinit var audioPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var musicViewModel: MusicViewModel // 新增：音乐VM，用于扫描音乐

    private lateinit var weatherViewModel: WeatherViewModel
    private val TAG: String = "Weather_Fragment"

    // 新增：标记是否已经自动播放过（默认false，仅首次播放）
    private var hasAutoPlayed = false
    //定位核心管理器
    private lateinit var locationManager: LocationManager
    private lateinit var context: Context
    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.context = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWeatherBinding.inflate(inflater, container, false)

        weatherViewModel = ViewModelProvider(requireActivity())[WeatherViewModel::class.java]
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // 绑定UI事件+观察数据


        observeLocationState()
        observeRealtimeWeather()
        observeLoadingState()
        observeForecast()
        observeIcon()
        initLauncher()
        getLocation()
        return binding.root
    }



    private fun initLauncher() {
        cityActivityLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val cityCode = result.data?.getStringExtra("selected_city_code")
                    if (cityCode != null) {
                        // 重新请求选中城市的天气
                        weatherViewModel.fetchRealtimeWeather(cityCode, null)
                    }
                }
            }
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    //授权成功-》获取经纬度
                    getLocation()
                } else {
                    // 授权失败 → 提示用户
                    Toast.makeText(context, "需要定位权限才能获取当前城市天气", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        //可以传入权限
        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    LogUtil.d("通知权限申请成功", TAG)
                } else {
                    Toast.makeText(context, "关闭通知权限将无法收到天气提醒", Toast.LENGTH_SHORT)
                        .show()
                }
                //提出位置权限申请
                getLatLng()
            }

        // ===== 重写：音频多权限Launcher（仿MusicFragment）=====
        audioPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                // 检查所有音频权限是否都授予（和MusicFragment逻辑一致）
                val isGranted = permissions.all { it.value }
                if (isGranted) {
                    // 音频权限成功 → 先扫描本地音乐 → 再播放
                    lifecycleScope.launch {
                        try {
                            // 复用MusicViewModel的扫描方法（和MusicFragment一致）
                            musicViewModel.scanAndSaveLocalMusic()
                            // 扫描完成后，执行播放逻辑
                            playWeatherFolderMusic()
                        } catch (e: Exception) {
                            Toast.makeText(context, "扫描音乐失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // 权限拒绝 → 提示（和MusicFragment逻辑一致）
                    PermissionUtils.showPermissionDeniedDialog(requireActivity())
                }
            }


        //刚进页面， 检查并申请通知权限（Android 13+）
        checkNotificationPermission()
    }

    // 新增：抽离播放逻辑（复用）
    private fun playWeatherFolderMusic() {
        val weather = weatherViewModel.realtimeWeather.value
        weather?.let {
            val weatherType = it.weather ?: ""
            val folderName = when {
                weatherType.contains("晴") -> "晴天"
                weatherType.contains("雨") && !weatherType.contains("雷阵雨") -> "雨天"
                weatherType.contains("雷阵雨") -> "雷阵雨"
                weatherType.contains("阴") -> "阴天"
                weatherType.contains("雪") -> "雪天"
                weatherType.contains("云") -> "多云"
                weatherType.contains("大风") -> "大风"
                weatherType.contains("雾") || weatherType.contains("霾") -> "雾天"
                else -> null
            }

            folderName?.let { name ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        // 修复点1：直接获取Int类型的folderId，重点做空值判断
                        val folderId: Int? = MusicRepository.getFolderIdByName(name)

                        // 空值兜底：folderId为null时直接提示并退出
                        val safeFolderId = folderId ?: run {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "未找到「$name」收藏夹", Toast.LENGTH_SHORT).show()
                            }
                            return@launch // 无有效ID，终止协程
                        }

                        // 修复点2：歌曲列表做空值+空列表双重判断（核心防崩溃）
                        val songs = MusicRepository.getMusicsByFolderIdDirectly(safeFolderId)
                        if (!songs.isNullOrEmpty()) {
                            val randomSong = songs.random()
                            withContext(Dispatchers.Main) {
                                MusicPlayService.startPlay(context, randomSong)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "「$name」收藏夹暂无歌曲", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        // 修复点3：全局异常捕获，避免协程崩溃
                        LogUtil.e("播放天气音乐失败：${e.message}", TAG)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "播放失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } ?: run {
            // 修复点4：weather数据为空时的兜底提示
            Toast.makeText(context, "天气数据为空，无法匹配音乐", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationPermission() {
        // 仅Android 13+需要申请
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }



    /**
     * 拿到经纬度
     */
    private fun getLocation() {
        LogUtil.d("getLocation",TAG)
// 二次检查权限（防御性编程）
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        //改变响应式数据的方法
        weatherViewModel.markLocationLoading()
        val lastKnownLocation: Location? = when {
            // 优先用GPS定位（精度高）
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            // 没有GPS则用网络定位（基站/Wi-Fi）
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            else -> {
                Toast.makeText(context, "请开启GPS/网络定位", Toast.LENGTH_SHORT).show()
                null
            }
        }
        lastKnownLocation?.let {
            // 成功获取经纬度
            val latitude = String.format("%.6f", it.latitude)   // 纬度
            val longitude = String.format("%.6f", it.longitude) // 经度
            weatherViewModel.handleLocationSuccess(latitude, longitude)
        } ?: run {
            weatherViewModel.handleLocationError("未获取到定位信息")
        }
    }

    /**
     * 位置权限检查（系统交互逻辑），和调用getLocation方法
     */
    private fun getLatLng() {
        LogUtil.d("getLaLng",TAG)
        //检查定位权限
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //没有授权-》动态申请,传入请求
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        // 已有授权 → 直接获取经纬度
        getLocation()
    }

    /**
     * 观察天气
     */
    private fun observeRealtimeWeather() {
        weatherViewModel.realtimeWeather.observe(viewLifecycleOwner, Observer { weather ->
            if (weather != null) {
                binding.apply {
                    wenDu.text = "${weather.temperature}"
                    tvWeather.text = "天气：${weather.weather}"
                    tvHumidity.text = "湿度：${weather.humidity}%"
                    tvCity.text = "城市：${weather.city}"
                    tvReporttime.text = "更新时间：${weather.reporttime}"
                    // 2. 核心：天气类型匹配（关键词匹配，覆盖90%场景）
                    val weatherType = weather.weather ?: "" // 非空兜底，避免空指针
                    matchWeatherType(weatherType)

                    lifecycleScope.launch {
                        // 1. 天气匹配收藏夹名称（覆盖你8个收藏夹）
                        val folderName = when {
                            weatherType.contains("晴") -> "晴天"
                            weatherType.contains("雨") && !weatherType.contains("雷阵雨") -> "雨天"
                            weatherType.contains("雷阵雨") -> "雷阵雨"
                            weatherType.contains("阴") -> "阴天"
                            weatherType.contains("雪") -> "雪天"
                            weatherType.contains("云") -> "多云"
                            weatherType.contains("大风") -> "大风"
                            weatherType.contains("雾") || weatherType.contains("霾") -> "雾天"
                            else -> null
                        }
                        folderName?.let { name ->
                            // 检查音频权限（仿MusicFragment的PermissionUtils）
                            if (!hasAutoPlayed) {
                                if (!PermissionUtils.hasAudioPermission(requireContext())) {
                                    // 无权限 → 申请多权限（此时通知/定位已处理完，绝对串行）
                                    audioPermissionLauncher.launch(PermissionUtils.getAudioPermissions())
                                } else {
                                    // 有权限 → 先扫描（确保有歌曲）→ 再播放
                                    musicViewModel.scanAndSaveLocalMusic()
                                    playWeatherFolderMusic()
                                }
                                hasAutoPlayed = true
                            }
                        }

                    }

                    //拿到天气后发送通知
                    //先检查有没有拿到权限
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    if (hasPermission) {
                        NotificationHelper.sendWeatherNotification(context, weather)
                    }

                }
            } else {
                // 数据为空，提示失败
                binding.tvCity.text = "天气请求失败，请检查Key或网络"
                Toast.makeText(context, "请求失败", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun observeForecast() {
        weatherViewModel.forecastList.observe(viewLifecycleOwner) { forecast ->
            if (forecast.isNullOrEmpty()) return@observe
            binding.apply {
                forecast.getOrNull(0)?.let { item ->
                    forecastItem1.ivForecastWeather.setImageResource(R.drawable.today)
                    forecastItem1.tvDate.text = "星期${item.week ?: "未知"}"
                    forecastItem1.tvContent.text =
                        "日：${item.dayweather ?: "无"},夜：${item.nightweather ?: "无"}"
                    forecastItem1.tvTempRange.text =
                        "${item.nighttemp ?: "0"}℃~${item.daytemp ?: "0"}℃"
                }
                forecast.getOrNull(1)?.let { item ->
                    forecastItem2.tvDate.text = "星期${item.week ?: "未知"}"
                    forecastItem2.tvContent.text =
                        "日：${item.dayweather ?: "无"},夜：${item.nightweather ?: "无"}"
                    forecastItem2.tvTempRange.text =
                        "${item.nighttemp ?: "0"}℃~${item.daytemp ?: "0"}℃"
                }
                forecast.getOrNull(2)?.let { item ->
                    forecastItem3.tvDate.text = "星期${item.week ?: "未知"}"
                    forecastItem3.tvContent.text =
                        "日：${item.dayweather ?: "无"},夜：${item.nightweather ?: "无"}"
                    forecastItem3.tvTempRange.text =
                        "${item.nighttemp ?: "0"}℃~${item.daytemp ?: "0"}℃"
                }
                forecast.getOrNull(3)?.let { item ->
                    forecastItem4.tvDate.text = "星期${item.week ?: "未知"}"
                    forecastItem4.tvContent.text =
                        "日：${item.dayweather ?: "无"},夜：${item.nightweather ?: "无"}"
                    forecastItem4.tvTempRange.text =
                        "${item.nighttemp ?: "0"}℃~${item.daytemp ?: "0"}℃"
                }
            }
        }
    }

    //匹配天气
    fun matchWeatherType(weatherType: String) {
        binding.apply {
            // 1. 清除上一次的颜色滤镜（关键：避免滤镜残留）
            ivWeather.clearColorFilter()

            // 2. 按「精准→宽泛」优先级匹配，覆盖90%+高德天气场景
            // 新增：增加导航图标映射（Triple替代Pair，第三个值是导航栏图标ResId）
            val (iconResId, filterColor, navIconResId) = when {
                // ===== 晴系（最高优先级，避免被多云/阴覆盖）=====
                weatherType.contains("晴") -> Triple(
                    R.drawable.sun,
                    ContextCompat.getColor(context, R.color.yellow_mid), // 用你的黄色系
                    R.drawable.sun
                )

                // ===== 雾/霾系（整合雾、霾、扬沙等）=====
                weatherType.contains("雾") || weatherType.contains("霾") ||
                        weatherType.contains("扬沙") || weatherType.contains("浮尘") -> Triple(
                    R.drawable.wu,
                    ContextCompat.getColor(context, R.color.gray_light), // 浅灰适配雾/霾视觉
                    R.drawable.wu,
                )

                // ===== 雨系（细分+整合暴雨到大雨）=====
                weatherType.contains("暴雨") || weatherType.contains("大暴雨") -> Triple(
                    R.drawable.rain_3, // 暴雨复用大雨图标
                    ContextCompat.getColor(context, R.color.blue_deep), // 深蓝适配暴雨/大雨
                    R.drawable.rain_3
                )

                weatherType.contains("大雨") -> Triple(
                    R.drawable.rain_3,
                    ContextCompat.getColor(context, R.color.blue_deep),
                    R.drawable.rain_3,
                )

                weatherType.contains("中雨") -> Triple(
                    R.drawable.rain_2,
                    ContextCompat.getColor(context, R.color.blue_mid), // 中蓝适配中雨
                    R.drawable.rain_2,
                )

                weatherType.contains("小雨") || weatherType.contains("阵雨") ||
                        weatherType.contains("雷阵雨") -> Triple(
                    // 雷阵雨/阵雨归到小雨
                    R.drawable.rain_1,
                    ContextCompat.getColor(context, R.color.blue_light), // 浅蓝适配小雨
                    R.drawable.rain_1,
                )

                // ===== 雪系（整合雨夹雪、暴雪等）=====
                weatherType.contains("暴雪") -> Triple(
                    R.drawable.snow_2, // 暴雪复用大雪图标
                    ContextCompat.getColor(context, R.color.white), // 白色适配雪天
                    R.drawable.snow_2,
                )

                weatherType.contains("大雪") -> Triple(
                    R.drawable.snow_2,
                    ContextCompat.getColor(context, R.color.white),
                    R.drawable.snow_2,
                )

                weatherType.contains("中雪") -> Triple(
                    R.drawable.snow, // 中雪复用小雪图标（若没有中雪图标）
                    ContextCompat.getColor(context, R.color.white),
                    R.drawable.snow,
                )

                weatherType.contains("小雪") || weatherType.contains("阵雪") -> Triple(
                    R.drawable.snow,
                    ContextCompat.getColor(context, R.color.white),
                    R.drawable.snow,
                )

                weatherType.contains("雨夹雪") -> Triple(
                    R.drawable.rain_snow,
                    ContextCompat.getColor(context, R.color.blue_light), // 浅蓝+白色视觉（滤镜仅设浅蓝）
                    R.drawable.snow,
                )

                // ===== 多云/阴系（整合多云、阴天）=====
                weatherType.contains("云") || weatherType.contains("阴") -> Triple(
                    R.drawable.duo_yun, // 多云图标复用为阴天图标
                    ContextCompat.getColor(context, R.color.gray_mid), // 中灰适配多云/阴
                    R.drawable.duo_yun // 导航栏同步显示多云图标
                )

                // ===== 兜底逻辑（剩余10%场景：冰雹、雷暴、沙尘等）=====
                else -> Triple(
                    R.drawable.mo_ren,
                    null, // 默认图标不设滤镜，保持原图样式
                    R.drawable.weather // 导航栏用默认天气图标
                )
            }

            // 3. 设置界面天气图标+滤镜（你的原有逻辑，无改动）
            ivWeather.setImageResource(iconResId)
            filterColor?.let {
                ivWeather.setColorFilter(it, PorterDuff.Mode.SRC_IN) // 标准着色模式，不破坏图标形状
            }

            // ===== 新增：同步更新底部导航栏的天气图标 =====
            // 步骤1：获取底部导航栏实例（替换成你项目中BottomNavigationView的实际ID）
            val mainActivity = activity as? MainActivity
            val bottomNav = mainActivity?.getBinding()?.bottomNav
            // 步骤2：找到导航栏的“天气”项，替换图标
            bottomNav?.menu?.findItem(R.id.nav_weather)?.let { weatherMenuItem ->
                LogUtil.d("开始设置导航图标", TAG)
                // 设置导航图标
                weatherMenuItem.icon = ContextCompat.getDrawable(context, navIconResId)
                // 可选：给导航图标加和界面图标一致的颜色滤镜（视觉统一）
            }
        }
    }

    /**
     * 观察加载状态（可选，新手先占位）
     */
    private fun observeLoadingState() {
        weatherViewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
            if (isLoading) {
                binding.tvCity.text = "数据正在加载中"
                binding.ivSlideIcon.visibility = View.GONE
                binding.isLoading.visibility = View.VISIBLE
            } else {
                binding.isLoading.visibility = View.GONE
                binding.ivSlideIcon.visibility = View.VISIBLE
                startSlideAnimation()
            }
        })
    }

    /**
     * 观察位置状态
     */
    private fun observeLocationState() {
        weatherViewModel.locationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LocationState.Loading -> {
                    binding.tvCity.text = "正在定位中..."
                }
                is LocationState.Success -> {
                    // 仅展示定位结果（无业务逻辑）
//                    Toast.makeText(
//                        context,
//                        "原生定位成功",
//                        Toast.LENGTH_LONG
//                    ).show()
                }
                is LocationState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                LocationState.PermissionDenied -> {
                    Toast.makeText(context, "需要定位权限才能获取当前城市天气", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    /**
     * 开启动画
     */
    private fun startSlideAnimation() {
        val slideDistance = dp2px(170f) // 滑动50dp（可自行调整）

        val slideAnimator = ObjectAnimator.ofFloat(
            binding.ivSlideIcon,
            "translationX",
            -slideDistance,
            slideDistance,
            -slideDistance
        ) // 新增：统计滑动次数（初始0）
        var slideCount = 0
        var iconList = listOf(
            R.drawable.weather,
            R.drawable.rain_1,
            R.drawable.water,
            R.drawable.duo_yun,
            R.drawable.city,
            R.drawable.mo_ren,
            R.drawable.rain_snow,
            R.drawable.sun,
            R.drawable.snow_2,
            R.drawable.rain_3,
            R.drawable.rain_2,
            R.drawable.today,
            R.drawable.music
        )

        slideAnimator.duration = 4000
        slideAnimator.repeatCount = ValueAnimator.INFINITE
        slideAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}

            // 核心：每重复一次（滑完一个来回），次数+1
            override fun onAnimationRepeat(animation: Animator) {
                slideCount++
                // 每5次更新响应式图标数据
                if (slideCount % 2 == 0) {
                    val currentIcon = weatherViewModel.slideIconRes.value
                        ?: iconList[0]
                    var newIcon = currentIcon
                    while (newIcon == currentIcon) {

                        newIcon = iconList.random() // Kotlin自带的random()，一行随机选！
                    }
                    // 更新响应式数据 → UI自动换图标
                    weatherViewModel.updateSlideIcon(newIcon)
                }
            }
        })
        slideAnimator.start()
    }

    private fun dp2px(dp: Float): Float {
        // 系统API：把dp转换成px，适配不同手机屏幕
        return dp * resources.displayMetrics.density
    }

    private fun observeIcon() {
        weatherViewModel.slideIconRes.observe(viewLifecycleOwner) { iconRes ->
            binding.ivSlideIcon.setImageResource(iconRes) // 数据变，图标自动变
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // 停止所有动画，避免内存泄漏
        binding.ivSlideIcon.clearAnimation()
    }

}