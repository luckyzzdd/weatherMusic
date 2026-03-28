## 前置知识

### 架构模式

#### MVC

只要分成这种模块，不管是类也好，方法也好，都要分成model类型和View类型，然后activity只管调用，它这里不但跟新ui，还操作数据.

此时activity就是controller

#### MVVM

**M**（Model）. 这里的 model 层和 MVC 的一样，不碰 UI，核心负责数据的定义与获取，分为两层：

1. 定义承载数据的类：实体类（对应本地数据库表 / 网络接口返回结构）、Dao 类（数据库操作）、Database 类（数据库管理）；
2. Repository（仓库）：封装数据获取逻辑，统一管理本地（数据库）、远程（网络）等多数据源，对外提供统一的拿数据 / 改数据方法。

**V**（View）. 即 UI 层，包含 xml 布局、Activity/Fragment，核心只做两件事：

1. 观察监听 ViewModel 的响应式数据，当数据改变时，定义视图如何更新（比如列表刷新、文本变化）；
2. 处理与用户 / 系统的交互（比如按钮点击、权限申请、页面跳转），不直接处理业务逻辑、不直接操作数据。

**ViewModel**，数据和 UI 的桥梁，核心做两件事，且不持有 Activity/Fragment 引用（避免内存泄漏）：

1. 定义响应式数据（LiveData/Flow），供 View 层观察，数据变更自动通知 UI；
2. 定义操作和更改数据的方法：调用 Repository 的方法获取 / 修改数据，处理简单的业务逻辑（比如数据格式转换），不直接操作 Model 层（实体类 / Dao 等）。



|       分层        |                   职责边界（你落地的代码）                   |                           核心规范                           |
| :---------------: | :----------------------------------------------------------: | :----------------------------------------------------------: |
| UI 层（Activity） | 只做 2 件事：1. 展示数据（观察 LiveData）；2. 触发事件（点击按钮 / 跳转页面）；你在 MainActivity/CityActivity 中通过观察 LiveData 更新 UI，用 ActivityResultLauncher 传值 |               绝对不写业务逻辑 / 数据操作代码                |
|   ViewModel 层    | 承上启下：1. 接收 UI 层的事件（如 fetchRealtimeWeather/addCity）；2. 调用 Repository 获取数据；3. 用 LiveData 暴露数据给 UI | 不能持有 Activity 引用，避免内存泄漏；用 ViewModelProvider 初始化，不能 new |
|   Repository 层   | 统一管理数据源：1. 网络数据（WeatherRepository 调用 Retrofit）；2. 本地数据（CityRepository 调用 Room） |  解耦 ViewModel 和数据源，后续可无缝切换网络 / 本地数据来源  |







## 模块1，data模块

数据分为网络请求来的天气数据和本地扫描出的音乐数据，还有创建的收藏夹数据



### 先说网络数据，data/remote

有天气数据和城市数据，，这里只提天气数据

#### 1,定义响应类

封装了真实天气的响应体数据类

```
data class RealtimeWeather(
    val province: String?, // 省份
    var city: String?, // 城市
    val adcode: String?, // 城市编码
    val weather: String?, // 天气（晴/雨/多云等）
    ...
)
```



#### 2.网络请求(Retrofit框架)

1创建Retrofit类

.addConverterFactory(GsonConverterFactory.create())是自动JSON转换器

向高德天气接口发请求时：接口返回的是一串 JSON 格式的天气数据；

`GsonConverterFactory` 会自动把这串 JSON，按字段对应关系映射成 `WeatherResponse` 对象；

就是根据下面的返回值来映射对象，，，

```
App.kt

class App : Application() {
		...
 		 //在这个里初始化
    override fun onCreate() {
        super.onCreate()
        instance = this
       val retrofit1 = Retrofit.Builder()
            .baseUrl("https://restapi.amap.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
  weatherApi = retrofit1.create(WeatherApiService::class.java) 
        ...
        }

}


```



WeatherApiService

```
  @GET("v3/weather/weatherInfo")
    suspend fun getRealtimeWeather(
        @Query("key") key: String,
        @Query("city") cityCode: String,
        @Query("extensions") extensions: String = "base"
    ): WeatherResponse
    
    

    @POST("api/v3/responses")
    suspend fun classifySong(
        @Header("Authorization") token: String,
        @Body request: DouBaoRequest
    ): DouBaoResponse

```

#### 问题

**如果没有GsonConverterFactory该怎么写？？**

此时接口返回最原始的HTTP响应体，response.body()拿 “原始响应体”，，接口返回404/500错误时，响应体为空，返回null,string()方法是把二进制变成字符串

```
import com.google.gson.Gson

// 1. 构建 Retrofit（去掉 converterFactory）
val retrofit1 = Retrofit.Builder()
    .baseUrl("https://restapi.amap.com/")
    // 去掉 GsonConverterFactory.create()
    .build()

// 2. 创建接口实例
val weatherApi = retrofit1.create(WeatherApi::class.java)

// 3. 调用接口并手动解析（示例函数）
suspend fun fetchWeather(key: String, cityCode: String): WeatherResponse? {
    try {
        // 调用接口获取原始响应
        val response = weatherApi.getRealtimeWeather(key, cityCode)
        
        // 检查响应是否成功
        if (response.isSuccessful) {
            // 读取 JSON 字符串（核心：手动获取接口返回的 JSON）
            val jsonString = response.body()?.string() ?: return null
            
            // 手动用 Gson 解析 JSON 成 WeatherResponse 对象
            val gson = Gson()
            return gson.fromJson(jsonString, WeatherResponse::class.java)
        }
    } catch (e: Exception) {
        e.printStackTrace() // 实际开发中替换为日志记录
    }
    return null
}
```

本地的歌曲数据的拿到是歌曲的扫描，和内存里预定义的收藏夹数据

### 数据的存储(Room框架)

1实体类，，数据的定义

```
@Entity("music")
data class Music(...)
```

2Dao类,,数据的操作

```
 @Query("SELECT * FROM music")
    fun getAllMusics(): Flow<List<Music>>
```

3AppDatabase类，，Room 数据库的**核心管理类**

​	1.标记数据库的基础配置（@Database 注解）

​	2.提供 Dao 的访问入口（抽象方法）

​	3.管理数据库的单例实例

```
@Database(entities = [City::class,Music::class, MusicFolder::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    // 提供CityDao的实例（Room自动生成实现类）
    abstract fun cityDao(): CityDao

    abstract fun musicDao(): MusicDao
}
```

创建Room数据库

在这里创建

```
App.kt
class App : Application() {
		...
 //在这个里初始化
    override fun onCreate() {
        super.onCreate()
        instance = this
        ...
         appDatabase = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "weather_music_db"
        )
            .addCallback(FolderInitialCallback())
            .build()
        }
		
}
```

在模块里使用一般是使用Dao类，而Dao类，被定义到了AppDatabase这里，，它们这个抽象方法，抽象类会被room用代理类实现

```
 //获取CityDao实例
    private val cityDao: CityDao = App.instance.appDatabase.cityDao()
```



注意！！！

这个.addCallback(FolderInitialCallback())，是非常重要的，在这里可以一些数据库数据的初始化，它只要在第一次创建的时候才会调用，而不是在重新打开应用就会再次调用

```
  private inner class FolderInitialCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            //用协程插入
            CoroutineScope(Dispatchers.IO).launch {
                // 第二步：插入默认3个收藏夹
                MusicRepository.apply {
                    addFolder("晴天", "SUNNY")
                    addFolder("阴天", "CLOUDY")
                    addFolder("雨天", "RAINY")
                }
                //预制的歌
                initDefaultSongs()
            }
        }
    }

```







## 模块2，view和系统

### 1权限申请

启动器做权限申请，先声明

```
   //WeatherFragment.kt，，，因为所有功能，音乐，定位，通知都需要在fragment出来时进行，所以权限全放到了这个文件里
   private lateinit var cityActivityLauncher: ActivityResultLauncher<Intent>
   private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
   private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
   private lateinit var audioPermissionLauncher: ActivityResultLauncher<Array<String>>
```

启动器也可以做activity的跳转，这里不需要，但之前写了，还是贴上来

```
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

```

注意！！

两个权限不能同时申请，，必须等一个权限申请完，才能拉去下一个

这个先发起的是checkNotificationPermission()，，通知权限

因为当定位的时候，会直接执行weatherViewModel.handleLocationSuccess(latitude, longitude)，，它来给viewModel里的location的状态赋值，并且执行fetchForecastWeather(it)，fetchRealtimeWeather(it, address.city)发送请求，，，一开始就这里的天气数据改变，，然后观察到，ui改变。。此时应该发通知，但如果定位权限在前，通知权限在后，就会导致此处发不了通知

套路都是先检查有没有权限，没有的话，就用启动器来拉去请求，，并且处理结果回调

```
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
```



此时看通知启动器的回调里,有  getLatLng()，来发起定位权限的拉取

```
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
```

音频权限，在observeRealtimeWeather()里，能进入这个方法，肯定通知权限和定位权限全部处理完了，然后

在这个方法里发起音频权限的请求，并且用hasAutoPlayed来标注是否是第一次进入页面，因为不能反复的随机播放

```
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
```

### 2RecyclerView

**RecyclerView控件**，列表容器

**Adapter适配器** -----  ListAdapter，把数据 → 变成每一行 Item

负责创建视图和绑定数据

ViewHolder类

**如果父类有无参构造（空构造），子类就可以不用传参数！**

**如果父类没有无参构造，子类必须传参！**

```
inner class MusicViewHolder(val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root) {
		//这里定义一下列表项的数据绑定方法
        fun bind(...) {
        	...
        }
 }
```



ListAdapter+**DIffUtil**

```
class MusicListAdapter : ListAdapter<Music, ...>(MusicDiffCallback())
```

它在创建viewHolder时

**onCreateViewHolder方法**

```
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MusicViewHolder(binding)
    }
```

**onBindViewHolder方法**

getItem方法

```
override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = getItem(position)
        holder.bind(music, music.path == currentPlayingPath, isPlaying)
    }
```

**精准局部刷新**

```
notifyItemChanged(oldPosition)
notifyItemChanged(newPosition)
```

 ViewHolder视图持有者，，缓存Item里的控件

**LayoutManager 布局管理器**

-----`LinearLayoutManager` → 垂直 / 水平列表（你用的这个）

`GridLayoutManager` → 网格

`StaggeredGridLayoutManager` → 瀑布流



**普通 RecyclerView.Adapter VS ListAdapter（核心区别）**

原生的，，，手动维护一个列表

```
class NormalAdapter : RecyclerView.Adapter<VH>() {
    private var data: List<Data> = emptyList()

    // 你必须自己写这个方法！
    fun updateData(newData: List<Data>) {
        // 1. 手动创建回调
        val callback = MyDiffCallback(data, newData)
        // 2. 手动计算差异
        val result = DiffUtil.calculateDiff(callback)
        // 3. 更新数据源
        data = newData
        // 4. 手动分发刷新
        result.dispatchUpdatesTo(this)
    }
}
```

基础父类

**不含 DiffUtil**

你可以**自己手动调用 DiffUtil** 计算差异

你需要自己处理：

- 定义 List 数据集
- 手动执行 DiffUtil.calculateDiff ()
- 手动调用 dispatchUpdatesTo () 刷新



Listadpater

```
class MusicListAdapter : ListAdapter<Music, VH>(MusicDiffCallback()) {
    // 不用自己维护 list
    // 不用自己 calculateDiff
    // 不用自己 dispatchUpdatesTo
    // 只用这一句：
    //submitList(newList)
}
```

继承自 RecyclerView.Adapter

**内部已经封装好了 DiffUtil**

只需要：

- 写一个 Callback
- 调用 submitList ()



### 3底部导航栏和Fragment切换

底部导航栏和菜单menu.xml

需要记住的属性：

app:itemActiveIndicatorStyle="@null" 指示器取消
app:menu="@menu/menu",联系menu.xml

```
<!--    app:itemRippleColor="@android:color/transparent",水波纹-->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        app:itemActiveIndicatorStyle="@null"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toBottomOf="parent"
        app:menu="@menu/menu"
        app:itemTextColor="@color/nav_bottom_color"
        app:itemIconTint="@color/nav_bottom_color"
        app:itemRippleColor="@android:color/transparent"
        />
```

menu.xml

定义了菜单项的id,图标和文字标题，也就是底部导航栏的标识

```
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/nav_weather"
        android:icon="@drawable/weather"
        android:title="天气"
        />
    <item android:id="@+id/nav_music"
        android:icon="@drawable/music"
        android:title="音乐"
        />
    <item android:id="@+id/nav_city"
        android:icon="@drawable/city"
        android:title="城市"
        />
</menu>
```

此时已经实现了底部导航栏样式ui的切换和选中

接下来是fragment的切换

我直接new三个fragment，每次只做隐藏和显示

```
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
}        
```

展示Fragment的逻辑

```
/**
 * 要看是不是第一次展示
 * 1.是第一次，就先隐藏别的fragment,然后add
 * 2，不是第一次，还是要隐藏然后，show
 */
```

```
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
```



### 4通知

分为：

1**音乐通知 = 给 Service.startForeground () 用 → 前台通知**

它必须**绑定一个前台服务 (Service)**

有前台通知的应用不容易被系统杀死

2**天气通知 = 给 NotificationManager.notify () 用 → 普通通知**



通知，需要提前做权限请求

在manifest做标识，前面已经讲过动态请求

```
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

因为音乐通知是前台服务权限

```
<!-- 音乐前台服务权限（配合通知使用） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

#### 2. 权限说明

- **Android 13（API 33+）**：必须动态申请 `POST_NOTIFICATIONS` 权限，否则无法弹出通知
- **Android 8.0（API 26+）**：必须创建**通知渠道（Channel）**，否则通知不显示





先维护一个工具类：object NotificationHelper {}，全局复用，避免重复创建

#### 核心组成

1. **常量定义**：渠道 ID、通知 ID、行为标识
2. **创建通知渠道**：适配 Android 8.0+，系统必须调用
3. **构建通知样式**：图标、标题、文本、常驻、控制按钮
4. **PendingIntent 封装**：通知按钮点击 → 触发服务操作



创建通知渠道

参数是渠道ID,渠道名称，通知优先级

```
 private fun createMusicNotificationChannel(context: Context): String {
        val channelId = "MUSIC_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放通知"
            }
            val nm  =context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return channelId
    }
```

### 创建通知

**普通通知**

只是弹出提示，

```
fun sendWeatherNotification(context: Context, weather: RealtimeWeather){
    createNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.app_icon)
        .setContentTitle(title)
        .setContentText(content)
        .setAutoCancel(true)  // 点击自动消失
        .build()

    // 普通发送通知
    notificationManager.notify(NOTIFICATION_ID, notification)
}
```

**前台通知**

前台通知需要加按钮和点击事件

需要action行为标识

```
  fun getMusicNotification(context: Context, music: Music,isPlaying: Boolean): Notification {
        val channelId = createMusicNotificationChannel(context)
        val action = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause, // 暂停图标（确保资源存在）
                "暂停",
                getPausePendingIntent(context)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play, // 播放图标（确保资源存在）
                "播放",
                getPlayPendingIntent(context, music)
            )
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.music)
            .setContentTitle("正在播放")
            .setContentText(music.name)
            .setOngoing(true)
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
```

Action第三个参数是PendingIntent对象，意思是包装一个Intent，等你点击的时候才会执行

.addAction (action) 就是给通知添加一个「可点击的按钮」



先创建了一个intent，给它的action属性赋值

包装成一个PendingIntent对象

```
private fun getPausePendingIntent(context: Context): PendingIntent {
        val pauseIntent = Intent(context, MusicPlayService::class.java).apply {
            action = MusicPlayService.ACTION_PAUSE
        }
        return PendingIntent.getService(
            context,
            102, // 暂停按钮用固定值即可（只有1个暂停动作）
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

```

系统需要**区分不同的按钮事件**，102是它的区别码

和通知id不是一个维度的对象

**发通知的代码**

音乐通知 

```
startForeground(id, notification)
```

 普通通知

```
notify(id, notification)
```





### 流程

**1.Activity / Fragment 点击播放**

```
MusicPlayService.startPlay(context, music)
```

**2进入startPlay方法**，它是service提供给外部的入口,构建Intent，，，写在这里companion object {}

```
val intent = Intent(context, MusicPlayService::class.java).apply {
    action = ACTION_PLAY          // 标记：播放
    putExtra(EXTRA_MUSIC, music)   // 带歌曲对象
}
```

**3启动前台服务**

```
ContextCompat.startForegroundService(context, intent)
```

**4Service生命周期启动**

**onCreate()**

注册播放状态监听

**onStartCommand(intent)**

收到 Intent → 交给 `handleIntentAction`

**5,解析intent的action参数**

可能是播放，可能是暂停

```
ACTION_PLAY -> {
    val music = intent.getParcelableExtra(EXTRA_MUSIC)
    playMusic(music)
}
```

**6,执行MusicService的playMusic**

```
三种可能
1，同一首歌，暂停，需要恢复
2，同首歌，正在播放
3，不同歌，需要播放当前歌曲
MusicPlayerManager.playMusic(this, music) // 播放音乐
开启前台通知
startForeground(
    101,  // 通知ID = 101
    NotificationHelper.getMusicNotification(..., true)
)
```

**7点击通知的按钮**

pendingIntent被触发

系统帮忙发送Intent给Service

```
val pauseIntent = Intent(context, MusicPlayService::class.java).apply {
            action = MusicPlayService.ACTION_PAUSE
        }
PendingIntent.getService(
    context,
    102,        // 请求码 = 102（按钮ID，和通知无关）
    pauseIntent // action = ACTION_PAUSE
)
```

**8Service 收到 ACTION_PAUSE**

```
onStartCommand -> handleIntentAction -> ACTION_PAUSE
```

**9,执行togglePlayPause()**

```
updateMusicNotification(currentMusic, false)//暂停音乐
stopForeground(STOP_FOREGROUND_DETACH) // 取消前台，但保留通知，，，把服务从 “前台服务” 降级成普通后台服务
 updateMusicNotification(currentMusic, false)
```

**10刷新通知**

```
val newNotification = getMusicNotification(..., false)
nm.notify(101, newNotification) // 用同一个ID 101
系统看到 ID=101
→ 替换旧通知→ 按钮从 ⏸ 暂停 → ▶️ 播放
```

### 5dialog（模态）

dialog是对话框的意思，模态：弹窗出现后，用户必须先处理弹窗，才能操作后面的界面

后面的页面会变得：变灰（蒙层）不可点击     不可触摸    不可响应任何操作

我的三种dialog

#### 1AlertDialog

是系统原生弹窗

不依赖 Fragment

不依赖 DialogFragment

只要有 Context 就能弹

```
  	val loadingBinding = DialogLoadingBinding.inflate(layoutInflater)
        loadingBinding.tvLoadingMsg.text = "正在分析歌曲情感，匹配收藏夹..."
 		loadingDialog = AlertDialog.Builder(requireContext())
            .setView(loadingBinding.root)
            .setCancelable(false) // 不可取消
            .create()
     loadingDialog?.show()
```

#### 2BottomSheetDialogFragment

##### 是什么

- 从**屏幕底部滑出**的对话框
- 本质也是 DialogFragment
- 属于 Material Design 规范组件

```
class SongSelectorBottomSheet : BottomSheetDialogFragment() {
	private lateinit var binding: DialogSongSelectorBinding
    private lateinit var songAdapter: SongSelectAdapter
    
    
}
```

常用

dismiss()

show()

#### 3DialogFragment

##### 是什么??

- Google 推荐的**正式对话框组件**
- 拥有**生命周期**（`onCreate`/`onCreateView`/`onDestroy`）
- 屏幕旋转、分屏不会消失
- 可以当作一个轻量级 Fragment 使用

```
class AddFolderDialog : DialogFragment() {
    private lateinit var binding: DialogAddFolderBinding
     override fun onCreateView(){}
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}
    }
```



fragment弹窗怎么开启？？

```
先创建，new一个，或者调用入口方法，return一个，，对象
然后
fragment.show(manager, tag) // 必须交给系统管理
fragemnt.show(childFragmentManager,tag)
tag 弹窗的唯一标识
```

你的弹窗需要一个 “容器” 来存放

则：

- 如果在 **Activity** 中 → 使用 `supportFragmentManager`
- 如果在 **Fragment** 中 → 使用 `childFragmentManager`

#### 作用：

- 让弹窗 “寄生” 在当前页面
- 页面退出，弹窗自动销毁
- 系统自动管理生命周期



#### fragmentManager介绍

负责：

- 调度 Fragment
- 管理生命周期
- 管理回退栈
- 控制显示 / 隐藏

```
// 1. 拿管家
val manager = supportFragmentManager

// 2. 开启事务（安排表演）
val transaction = manager.beginTransaction()

// 3. 指定：把 fragment 放到 容器container 里
transaction.add(R.id.container, MyFragment())

// 4. 提交！管家开始执行
transaction.commit()
```



### 6用到的控件

**1.androidx.appcompat.widget.AppCompatButton**

```
<androidx.appcompat.widget.AppCompatButton
    android:id="@+id/btn_add_city"
    android:layout_width="wrap_content"
    android:layout_height="50dp"
    android:background="@drawable/bg_button"
    android:minWidth="0dp"
    android:paddingHorizontal="24dp"
    android:text="添加"
    android:textColor="@android:color/white"
    android:textSize="16sp" />
```

​	可以自定义Button的样式，，一般用selector来搞

**2.CardView**

app:cardCornerRadius="12dp"   卡片圆角半径（

app:cardElevation="4dp"    卡片阴影高度

android:padding="16dp"    卡片内部内容和卡片边缘的间距（

```
  <!-- 核心：新增容器（CardView，灰色背景+圆角） -->
    <androidx.cardview.widget.CardView
        android:id="@+id/weather_card"
        android:layout_width="320dp"
        android:layout_height="280dp"
        android:layout_marginTop="50dp"
        android:padding="16dp"
        app:cardBackgroundColor="@color/pink_soft"
        app:cardCornerRadius="12dp"
        app:cardElevation="4dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">
        //可以用Constraintlayout
    </androidx.cardview.widget.CardView> 
```

3.**根 Toolbar 控件**

`android:layout_height="?attr/actionBarSize"`：高度匹配系统默认标题栏（ActionBar）的高度，保证视觉统一；

`app:contentInsetStart/End="16dp"`：控制 Toolbar 内部内容和左右边缘的间距（避免内容贴边）；

`android:background="?attr/colorSurface"`：背景色沿用主题的 “表面色”（适配深色 / 浅色模式）。

```
 <Toolbar
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorSurface"
        app:contentInsetStart="16dp"
        app:contentInsetEnd="16dp">
        ...
         </Toolbar>
```

4.**分割线的一种写法**

```
 <View
        android:layout_width="match_parent"
        android:layout_height="0.5dp"
        android:background="?attr/colorOutline"/>
```

5**加载动画**

```
  <!-- 加载动画 -->
    <ProgressBar
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:indeterminate="true"
        android:layout_marginEnd="12dp"
        android:indeterminateTint="@color/white"
        />
```

6**复选框的动态生成**

定义布局容器

```
   <LinearLayout
        android:id="@+id/ll_checkbox_group"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        />

```

这个逻辑是只展示没有用到天气收藏夹

```
 private fun loadAvailableWeatherCheckBox() {
       lifecycleScope.launch(Dispatchers.IO) {
            // 第一步：查已存在的收藏夹名称
            val existingFolderNames = MusicRepository.getAllFolderNames()
            // 第二步：过滤预定义列表，只保留未创建的天气
            //ALL_WEATHER_NAMES这是一个包含 “键值对” 的集合,,listOf(pair(1,2))
            
            //filter { ... }，，作用是遍历集合中的每个元素，只保留大括号内条件为 true 的元素
			//filter，里面为false,就不要，为true就要
            val availableWeathers = WeatherConstants.ALL_WEATHER_NAMES.filter {
                !existingFolderNames.contains(it.first)
            }
			
            // 第三步：动态生成CheckBox（切回主线程操作UI）
            withContext(Dispatchers.Main) {
                availableWeathers.forEach { (weatherName, weatherTag) ->
                       // 存储名称和tag的映射（后续创建收藏夹用）
                    weatherTagMap[weatherName] = weatherTag
                    // 创建CheckBox！！！！
                    
                    val checkBox = MaterialCheckBox(requireContext()).apply {
                        text = weatherName
                        // 3. 设置文字大小（单位是 sp，直接写 16f 即可，Android 自动适配，，16f 对应 16sp）
                        textSize = 16f
                       //设置勾取按钮的颜色
                       buttonTintList =  ColorStateList.valueOf(requireContext().resources.getColor(R.color.blue_soft,null))
                            // 5. 设置布局参数（决定 CheckBox 在父布局中的显示方式）
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                   // 6. 把创建好的 CheckBox 添加到父布局（LinearLayout）中，界面才会显示
                    binding.llCheckboxGroup.addView(checkBox)
                }

                // 无可选天气时的提示
                if (availableWeathers.isEmpty()) {
                    binding.llCheckboxGroup.addView(
                        	androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
                            text = "已添加所有天气收藏夹，暂无可选"
                            textSize = 16f
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                    )
                    // 禁用确认按钮
                    binding.btnConfirm.isEnabled = false
                }
            }
        }
    }
```



### 7沉浸式

#### 什么是「沉浸式」？

**让你的 APP 布局顶满整个屏幕，延伸到系统的状态栏、导航栏，去掉系统默认的黑边 / 空白，界面更美观**。

#### 两者沉浸式

##### 模式 1：基础沉浸式（推荐！最常用）

✅ **效果**：布局延伸到状态栏，状态栏**透明 / 变色**，仍旧可以看见电量，不隐藏系统栏

✅ **适用**：首页、音乐播放页、详情页（你的音乐 APP 首选）

✅ **特点**：安全、全版本适配、不影响操作

```
private fun setupBaseImmersive() {
    // 1. 核心：布局延伸到状态栏（取消系统默认占位）
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // 2. 状态栏透明
    window.statusBarColor = Color.TRANSPARENT
    // 3. 状态栏文字黑色（浅色主题专用）
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
}
```

##### 模式 2：完全沉浸式（全屏模式）

✅ **效果**：**隐藏**状态栏 / 导航栏，触摸屏幕临时呼出

✅ **适用**：视频、游戏、全屏播放页

✅ **特点**：真正全屏，交互性强

```
private fun setupFullImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    val controller = WindowInsetsControllerCompat(window, window.decorView)
    // 隐藏 状态栏+导航栏
    controller.hide(WindowInsetsCompat.Type.systemBars())
    // 滑动临时显示，松手自动隐藏
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```

我的代码（能看见状态栏，导航栏隐藏，需要滑动显示）

仅仅适配安卓11

```
private fun setupImmersiveMode() {
    // 只适配 Android 11+ (API 30)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        // 布局全屏，无边界（等同于 setDecorFitsSystemWindows(false)）
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        // 状态栏文字改成黑色
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
        // 隐藏 底部导航栏
        window.insetsController?.apply {
            hide(WindowInsets.Type.navigationBars())
            // 滑动临时显示导航栏
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
```

## 模块3，功能模块

### 1定位功能

从拿到权限往后，开始做定位业务

先讲前置条件，我封装了一个密封类，里面是位置的状态

```
sealed class LocationState {
    object Loading: LocationState()
    data class Success(
        val latitude: String,
        val longitude: String
    ): LocationState()
    data class Error(val message: String) : LocationState() // 定位失败
    object PermissionDenied : LocationState() // 权限拒绝
}
```

密封类在最上面kotlin讲解过，总之是存数据和状态的

WeatherViewModel

定义定位状态，提供改变状态的方法。

这里状态改变，在fragment监听这个定位数据的就开始执行代码

```
WeatherViewModel.kt
class WeatherViewModel : ViewModel() {
 //监控状态，状态变化，吐司提示
    private val _locationState = MutableLiveData<LocationState>()
    val locationState: LiveData<LocationState> = _locationState
	//提供改变状态的方法
	handleLocationSuccess(latitude: String, longitude: String) {
	
	}
		//提供改变状态的方法
	fun handleLocationError(message: String) {
        _locationState.postValue(LocationState.Error(message))
        }
}


```

大致监听代码

```
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
```



**接着开始正式过流程**

这里一开始是通知权限完成后的回调里，提出位置权限申请

这个是检查有没有权限

```
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
```

有权限进入 getLocation()

原生定位方法

```
private fun getLocation() {
    // 🔒 防御性编程：二次检查权限（防止权限被动态撤回）
    if (ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    // 1. 标记ViewModel：定位加载中（同步UI状态）
    weatherViewModel.markLocationLoading()

    // 2. 按优先级获取定位：GPS > 网络
    val lastKnownLocation: Location? = when {
        // 优先GPS定位（精度最高）
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        // 无GPS → 用网络定位（基站/WiFi）
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        // 都未开启 → 提示用户
        else -> {
            Toast.makeText(context, "请开启GPS/网络定位", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // 3. 处理定位结果
    lastKnownLocation?.let {
        // 🟢 定位成功：提取经纬度，交给ViewModel处理
        val latitude = String.format("%.6f", it.latitude)   // 纬度
        val longitude = String.format("%.6f", it.longitude) // 经度
        weatherViewModel.handleLocationSuccess(latitude, longitude)
    } ?: run {
        // 🔴 定位失败：无位置信息
        weatherViewModel.handleLocationError("未获取到定位信息")
    }
}
```

#### 1.`LocationManager`

**官方定义**：Android 系统提供的**定位服务管理器**

```
// 拿到系统的定位大管家
locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
```

##### 2 .`LocationManager.GPS_PROVIDER`

**官方定义**：GPS 卫星定位提供者

##### `3.LocationManager.NETWORK_PROVIDER`

**官方定义**：网络定位提供者（基站 + WiFi）

4.isProviderEnabled(参数)

**官方定义**：检查指定的定位方式是否**已开启**

5.getLastKnownLocation(参数)

**官方定义**：获取设备**最后一次缓存的定位结果**



拿到经纬度，就可以发送逆地理编码的网络请求,拿到所在城市的地址，区编码等

```
val cityInfo = WeatherRepository.getCityInfoByLatLng(latitude, longitude)
```

然后发送天气请求

```
 fetchForecastWeather(it)
 fetchRealtimeWeather(it, address.city)
```



#### 注意，这里天气请求可能失败

我做了两层兜底

**1，重复请求三次，每次延迟500毫秒**

在WeatherRepository里写重复请求的业务逻辑

定义了一个带着泛型，和函数成员变量的方法

很明显这个函数成员变量就是网络请求

```
  private suspend fun <T> retryOnFailure(
        maxRetries: Int = MAX_RETRY_COUNT,
        block: suspend () -> T?
    ): T? {
        var retries = 0
        while (retries < maxRetries) {
            try {
                val result = block()//请求成功
                return result
            } catch (e: Exception) {
                retries++
                //请求失败
                LogUtil.e("请求失败，重试次数：$retries/$maxRetries，错误：${e.message}", TAG)
                // 达到最大重试次数，返回null
                if (retries >= maxRetries) {
                    e.printStackTrace()
                    return null
                }
                //延迟重试
                delay(RETRY_DELAY_MS)
            }
        }
        return null
    }

```



使用

```
  retryOnFailure {
                val response = App.instance.weatherApi.getRealtimeWeather(
                    key = AMAP_API_KEY,
                    cityCode = cityCode
                )
                LogUtil.d("实时天气返回：${response.lives}", TAG)
                val realtime =  response.lives?.firstOrNull()
                realtime?.let {
                    //缓存实时天气
                    WeatherDataStore.saveRealtimeWeather(App.instance, it)
                    // 缓存城市编码（供定时同步用）
                    WeatherDataStore.saveLastCityCode(App.instance, cityCode)
                }
				//返回的值，泛型应该会自动推断
                realtime
            }
```



**2缓存数据和失败时拿缓存数据**

看下个模块

### 2缓存数据功能

技术栈：**Jetpack Preferences DataStore**（键值对存储）

作用：**把数据存在手机本地，关机 / 重启 / 断网都不丢**（持久化缓存）

存**天气数据**，断网的时候能从本地读出来显示

**替代老旧的 SharedPreferences** 

1.创建单例

```
/**
 * DataStore：数据存储的核心类，负责读写数据；
 * Preferences：DataStore 专门用于存储键值对的类型
 * by preferencesDataStore(name = "weather_cache")
 * by：Kotlin 的委托属性关键字 —— 把 weatherDataStore 的创建、管理逻辑委托给 preferencesDataStore 这个函数；
 * preferencesDataStore(name = "weather_cache")：Google 提供的内置函数，作用是：
 * ① 创建一个 DataStore 实例；
 * ② 保证这个实例是单例（无论你调用多少次 context.weatherDataStore，都只会创建一个实例，避免重复创建导致的资源浪费）；
 * ③ name = "weather_cache"：指定这个 DataStore 的存储文件名（最终会在应用的私有目录下生成一个名为 weather_cache.preferences_pb 的文件，用来持久化存储数据）。
 */
// 给手机Context扩展一个属性：整个APP只用【一本】天气缓存笔记本
val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_cache")
```





```
object WeatherDataStore {
	 //定义缓存
    private val KEY_REALTIME_WEATHER = stringPreferencesKey("realtime_weather")
     //缓存实时天气
    suspend fun saveRealtimeWeather(context: Context, weather: RealtimeWeather){
       
       //pref是一个可编辑的键值对容器
       //里面放着键值对
       context.weatherDataStore.edit {
            pref->
            pref[KEY_REALTIME_WEATHER] = gson.toJson(weather)
        }
    }
       // 读取缓存的实时天气//异步读取
    suspend fun getRealtimeWeather(context: Context): RealtimeWeather? {
        //map不是键值对的意思，是对数据项做转换。。。data是数据流Flow<Pref>
        return context.weatherDataStore.data.map {
            pref->
            pref[KEY_REALTIME_WEATHER]?.let {
                json->
                gson.fromJson(json, RealtimeWeather::class.java)
            }
        }.first()
    }
}
```

Flow 设计时支持：0 个数据 / 1 个数据 / N 个持续更新的数据

咱们的场景：**明确只有 1 个数据**

所以用 `.first()`：

✅ 拿到唯一的数据

✅ 自动关闭 Flow（不占内存、不耗电）

✅ 语义清晰：我只需要一次缓存，不需要实时监听

那flow的目的就是为了做转化

把Flow<Pref>变成Flow<RealtimeWeather?>

然后.first()  取出流里唯一的数据：Flow<RealtimeWeather?> → RealtimeWeather?



#### 注意！！gson的使用

**序列化**：把 **Kotlin/Java 对象** → 转换成 **JSON 字符串**（用于存储、网络传输）

**反序列化**：把 **JSON 字符串** → 还原成 **Kotlin/Java 对象**（用于代码使用）

DataStore 只能存**字符串**，不能存 `RealtimeWeather`/`List<Casts>` 这种复杂对象

→ 存的时候：**对象 → JSON 字符串**

→ 取的时候：**JSON 字符串 → 对象**

```
// 1. 创建 Gson 实例（全局一个就行）
private val gson = Gson()

// 2. 序列化：对象 → JSON字符串
val json = gson.toJson(对象)

// 3. 反序列化：JSON字符串 → 对象
val 对象 = gson.fromJson(json, 对象类型)

第三种，单个普通对象可以如上
但是带泛型的集合不行
// 编译报错！泛型擦除，Gson 不知道 List 里装的是 Casts
gson.fromJson(json, List<Casts>::class.java)

必须用TypeToken

// 1. 获取【带泛型的完整类型】
val type = object : TypeToken<List<Casts>>() {}.type

// 2. 解析：告诉 Gson 要转成 List<Casts>
gson.fromJson<List<Casts>>(json, type)

```



### 3选择城市天气功能

#### 1.维护一个城市的列表的增删改查

一个editText输入框和按钮做增加

需要viewModel层，定义数据表的全部数据

```
 val allCities = CityRepository.getAllCities().asLiveData()
```

这个需要Room数据库在查询时，给返回值包裹一层Flow,具有响应式，当增删改的时候，这里会返回新数据，

把flow转成liveData，风格统一的监听

```
  @Query("SELECT * FROM city ORDER BY id DESC")
    fun getAllCities(): Flow<List<City>>
```

列表，，CityAdapter()

#### 2点击跳转天气并显示此城市的天气和长按删除城市

把点击事件和删除事件回调，传给CityFragment

```
class CityAdapter : ListAdapter<City, CityAdapter.CityViewHolder>(CityDiffCallback()) {
    //点击事件回调
    var onCityClick: ((City) -> Unit)? = null

    // 删除事件回调
    var onCityDelete: ((String) -> Unit)? = null
    ...
        //安全调用符 ?.，如果回调还是 null,返回null
         onCityClick?.invoke(city) 
}
```

CityFragment实现方法

```
  private fun initRecyclerView() {
        cityAdapter = CityAdapter()

        binding.rvCityList.apply {
            adapter = cityAdapter
            layoutManager = LinearLayoutManager(context)
//            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        cityAdapter.onCityClick = { city ->
            //改变当前和其他被选中的选中状态
            cityViewModel.selectCity(city.copy(isSelected = true))
            //查询选中城市的天气
            //先做网络请求
//        然后在隐藏和显示页面
            // 2. 直接请求天气（核心：WeatherFragment已订阅，会自动更新UI）
            city.cityCode?.let{
                weatherViewModel.fetchRealtimeWeather(it, null)
                weatherViewModel.fetchForecastWeather(it)
            }
//        切换回WeatherFragment,保证Fragment唯一性
            (activity as? MainActivity)?.apply {
                val weatherFragment = getFragmentList().firstOrNull()
                weatherFragment?.let {
                    showFragment(it)
                    updateBottomNavSelection(it)
                }
            }
        }

        cityAdapter.onCityDelete = {
            cityCode->
            cityViewModel.deleteCity(cityCode)
        }
    }


```

### 4天气切换和底部导航栏的天气图标同步小功能

位置，时机。。在observeRealtimeWeather()时，会执行

```
val weatherType = weather.weather ?: "" // 非空兜底，避免空指针
matchWeatherType(weatherType)
```

拿到天气类型：晴，阴，雨这些

```
然后开始匹配
fun matchWeatherType(weatherType: String) {
	 binding.apply {
	 	// 1. 清除上一次的颜色滤镜（关键：避免滤镜残留）
        ivWeather.clearColorFilter()
	 	val (iconResId, filterColor, navIconResId) = when {
    		    // ===== 晴系（最高优先级，避免被多云/阴覆盖）=====
                weatherType.contains("晴") -> Triple(
                    R.drawable.sun,
                    ContextCompat.getColor(context, R.color.yellow_mid), // 用你的黄色系
                    R.drawable.sun
                )
				...
    
    
    		}
    		
    		//开始设置天气图标+滤镜
    		ivWeather.setImageResource(iconResId)
    		filterColor?.let{
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
```

拿到 `MainActivity`

拿到底部导航栏

找到 `nav_weather` 这个菜单

把图标设置成和页面天气一样的图标

### 5动画功能



### 6扫描本地歌曲，下拉刷新添加功能

**MusicScanner**：**工具类** → 专门负责**扫描手机本地音乐**（只扫描，不存库）

**MusicViewModel**：**视图模型** → 调用扫描 → 把扫描结果**存进 Room 数据库** → 弹 Toast 提示

从本地扫描并存储音乐

```
MusicViewModel.kt
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
```

MusicRepository.scanLocalMusic()

用 MusicScanner工具类来扫描，相当于只是用MusicRepository包一层

因为是耗时操作，所以在io子线程里

```
suspend fun scanLocalMusic(): List<Music> = withContext(Dispatchers.IO) {
        MusicScanner.scanLocalMusic(App.instance)
    }
   
```



MusicScanner

```
 suspend fun scanLocalMusic(context: Context):List<Music> = withContext(Dispatchers.IO){
 	//1需要存储扫描到的歌曲列表
 	 val musicList = mutableListOf<Music>()
 	//2要查询的音乐字段（只查需要的，减少性能消耗）
 	 val projection = arrayOf(
            MediaStore.Audio.Media.DATA,// 文件路径（对应Music的path）
            MediaStore.Audio.Media.TITLE, // 歌曲名（对应name）
            MediaStore.Audio.Media.ARTIST, // 歌手名（对应singer）
            MediaStore.Audio.Media.DURATION, // 时长（毫秒，对应duration）
            MediaStore.Audio.Media.ALBUM, // 专辑名（对应album）
            MediaStore.Audio.Media.SIZE // 文件大小（对应size）
        )
    //3，筛选条件，定义字符串
    val selection  = "${MediaStore.Audio.Media.DURATION}>? AND AND ${MediaStore.Audio.Media.IS_MUSIC} = ?"
    val selectionArgs = arrayOf("100000", "1")
 	//4排序，按歌名升序
     val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
 	
 	//ContentResolver 就是安卓系统给你的「官方数据查询员」
 	 //用ContentResolver查询媒体库
     var cursor: Cursor?= null
 	 try {
            //其实是向 Android 的媒体库（本质是数据库）发起了一个查询请求
            //媒体库会返回所有符合条件的音乐数据
            // MediaStore.Audio.Media.EXTERNAL_CONTENT_URI是手机外部存储音乐文件
            cursor =context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            //查询结果的游标
      		//cursor相当于数据库查询返回的结果集
      		cursor?.let {
      		 // 获取各字段的索引（避免重复调用getColumnIndex，提升性能）
                val pathIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
      			...
      			  //一开始在0行，
                while (it.moveToNext()) {
                	val path = it.getString(pathIndex)?:continue // 路径为空则跳过
                    val name = it.getString(nameIndex) ?: "未知歌曲"
                    val singer = it.getString(singerIndex) ?: "未知歌手"
                    val duration = it.getLong(durationIndex) ?: 0L
                    val album = it.getString(albumIndex) ?: "未知专辑"
                    val size = it.getLong(sizeIndex) ?: 0L
                	构建Music实体
                	val music = Music(
                        path = path,
                        name = name,
                        singer = singer,
                        duration = duration,
                        album = album,
                        size = size
                    )
                    加入维护的本地扫描到的歌曲列表
                    musicList.add(music)
                }
      			
      		}
      		
      }catch(){
      	...
      
      }finally{
       // 关闭游标，释放资源（新手必做，避免内存泄漏）
            cursor?.close()
      }   
 }
```

### 7ai做歌曲选择收藏夹功能

用户的使用流程

**1点击ai按钮，看见一个底部弹窗，列表**

​	创建adpater时，需要注意，只加载未归属任何收藏夹的歌曲。

```
val unassignedSongs = MusicRepository.getUnassignedMusics()
songAdapter.submitList(unassignedSongs)
```

**2item有复选框，可以勾选歌曲**

​	维系一个private val selectedSongs = mutableListOf<Music>()

```
inner class ViewHolder(private var binding: ItemSongSelectBinding): RecyclerView.ViewHolder(binding.root){
        //绑定歌曲信息和勾选框
        fun bind(music: Music){
            binding.tvSongName.text = music.name
            binding.tvSinger.text = music.singer

            val checkBox = binding.cbSelect as MaterialCheckBox
            //「在当前弹窗生命周期内，保持勾选状态的临时记忆」
            checkBox.isChecked = selectedSongs.contains(music)
            checkBox.setOnCheckedChangeListener { button,isChecked ->
                onCheckChanged(music, isChecked)
            }
        }
    }
```

这里有勾取监听，checkBox.setOnCheckedChangeListener。如果勾取歌曲， 

调用回调函数onCheckChanged(music, isChecked)进去，是updateSelectedSongs

```
// 更新选中的歌曲列表
fun updateSelectedSongs(song: Music, isChecked: Boolean) {
    if (isChecked) {
        if (!selectedSongs.contains(song)) selectedSongs.add(song)
    } else {
        selectedSongs.remove(song)
    }
}
```

更新选中的歌曲列表（selectedSongs）

**3点击确定时，提交被选中的歌曲，判断是手动还是ai，是的话,发送给ai**

点击确定时，

触发binding.tvConfirm.setOnClickListener {}监听

```
 伪代码，只讲逻辑
 SongSelectorBottomSheet.kt
 先拿数据
 val selectedSongs = songAdapter.getSelectedSongs()
 //这个是传过来的参数
 if(folderId != 0){
 //手动添加
 然后// 批量更新歌曲的folderId（多对一核心逻辑）
 selectedSongs.forEach { song ->
                        //这里是正式的在数据库里改folderId
                        MusicRepository.assignMusicToFolder(song.path, folderId)
                    }
 
 }else{

 	  // AI分拣
                // 显示自定义加载弹窗
                showLoadingDialog()
                // 调用AI分拣（IO线程）
                lifecycleScope.launch(Dispatchers.IO) {
                    var successCount = 0
                    var failCount = 0
                    //获得所有收藏夹列表
                    val folderNames= MusicRepository.getAllFolderNames()
                    // 遍历选中的歌曲调用AI
                    selectedSongs.forEach { song ->
                        try {// 调用AI获取匹配的收藏夹名称
                            val matchFolderName = MusicRepository.aiClassifySingleSong(song, folderNames)
                            if (matchFolderName!=null){
                                val folderIdByName = MusicRepository.getFolderIdByName(matchFolderName)
                                //更新歌曲的folderId
                                MusicRepository.assignMusicToFolder(song.path,folderIdByName)
                                successCount++
                            }else{
                                failCount++
                                Log.e("AI分类", "歌曲《${song.name}》未匹配到任何收藏夹")
                            }
                        } catch (e: Exception) {
                            failCount++
                            Log.e("AI分类", "歌曲《${song.name}》分类失败：${e.message}", e)
                        }
                    }
                    // 切回主线程处理结果
                    withContext(Dispatchers.Main) {
                        // 关闭加载弹窗
                        dismissLoadingDialog()
                        // 显示分拣结果
                        val toastMsg = when {
                            successCount == selectedSongs.size -> "AI分拣完成：全部$successCount 首歌曲匹配成功✅"
                            successCount > 0 -> "AI分拣完成：成功$successCount 首，失败$failCount 首（失败歌曲可手动分配）"
                            else -> "AI分拣失败：$failCount 首歌曲未匹配到收藏夹❌"
                        }
                        Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_LONG).show()
                        // 关闭半模态框
                        dismiss()
                    }
 	
 }
```



这里因为收藏夹的数量不确定，所以需要动态的去查，而ai需要拿到当前所有的收藏夹来进行分配

```
 val folderNames= MusicRepository.getAllFolderNames()
```

真正的api请求在MusicRepository里

```
   suspend fun aiClassifySingleSong(music: Music, folderNames: List<String>): String? =
        withContext(
            Dispatchers.IO
        ) {
        1.写提示词
         val prompt = """
                     严格按以下规则匹配收藏夹名称，仅返回名称无其他内容：
                     1. 分析歌曲《${music.name}》（歌手：${music.singer}）的情感/风格；
                     2. 收藏夹列表：【${folderNames.joinToString("、")}】；
                     3. 情感匹配规则（参考）：
                        - 欢快/明朗 → 晴天；
                        - 沉闷/忧郁 → 阴天；
                        - 伤感/悲伤 → 雨天；
                        - 清冷/孤寂 → 雪天；
                      4. 必须从列表中选最接近的1个，仅返回名称（如“雨天”），无空格、无标点、无解释。
""".trimIndent()

		2.//构建请求体
         val douBaoRequest = DouBaoRequest(
                    model = App.MODEL_ID,
                    input = listOf(
                        InputItem(
                            role = "user",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = prompt
                                )
                            )
                        )
                    )
                )
        	//发起请求
        val response = App.instance.douBaoApi.classifySong(
                    token = "Bearer ${App.VOLC_API_KEY}",
                    request = douBaoRequest
                )
        处理错误...
        
        }
```

豆包网络请求的接口

```
interface DouBaoApiService {
    @POST("api/v3/responses")
    suspend fun classifySong(
        @Header("Authorization") token: String,
        @Body request: DouBaoRequest
    ): DouBaoResponse
}
```



### 8音乐播放和通知功能

#### 核心组件

##### 1MusicPlayerManager（播放核心单例）

##### 作用

全局唯一的音乐播放控制器，封装`MediaPlayer`所有操作，对外提供统一调用接口。

##### 核心功能

- 播放 / 暂停 / 恢复 / 停止音乐
- 三种播放模式逻辑
- 进度条实时更新（协程）
- 多监听器管理（避免内存泄漏）
- 资源自动释放

##### 2. MusicPlayService（前台播放服务）

##### 作用

- 让音乐**后台播放不被系统杀死**
- 绑定**前台通知**，符合 Android 后台权限规范
- 接收播放指令，调用 Manager 执行操作

##### 关键知识点

1. **前台服务 (Foreground Service)**：Android 8.0 + 必须配合通知使用，`startForeground()`
2. **START_STICKY**：服务被杀死后自动重启
3. **生命周期管理**：`onDestroy`释放资源，避免内存泄漏
4. **Intent 动作分发**：通过 Action 区分播放 / 暂停 / 恢复

##### 3. MusicListAdapter（列表适配器）

##### 作用

展示音乐列表，同步播放状态，处理列表项交互。

##### 关键知识点

1. **ListAdapter + DiffUtil**：高效刷新列表，仅更新变化项，避免卡顿
2. **ViewBinding**：视图绑定，替代 findViewById
3. **局部刷新**：`notifyItemChanged()` 只刷新播放 / 暂停的项
4. **动画优化**：ViewHolder 初始化加载动画，杜绝空指针



##### 4.NotificationHelper（通知工具类）

##### 作用

统一管理**天气通知**和**音乐播放通知**，兼容 Android O + 通知渠道。

##### 关键知识点

1. **通知渠道 (Notification Channel)**：Android 8.0 + 强制要求，必须创建后才能发通知
2. **PendingIntent**：跨组件安全意图，用于通知按钮控制音乐
3. **FLAG_IMMUTABLE**：Android 12+ 安全标志，避免意图篡改
4. **动态通知更新**：播放 / 暂停时切换通知按钮图标



##### 5. FolderDetailFragment（UI 交互页面）

##### 作用

用户交互入口，监听播放状态、处理点击事件、管理列表。

##### 关键知识点

1. **生命周期感知**：`onDestroyView` 移除监听器，防止内存泄漏
2. **协程收集 Flow**：`lifecycleScope` 安全收集进度流
3. **ViewModel**：共享数据，跨组件通信



#### 功能

##### **1播放/恢复/暂停**

```
用户点击列表项 → Fragment调用MusicPlayService → Service调用MusicPlayerManager →
MediaPlayer执行播放/暂停 → 触发OnPlayStateChangeListener → Adapter更新UI/通知更新
```

**1点击列表项**

调用adapter回调

判断点击的歌是不是当前的歌

1是的话，判断播放状态，暂停就恢复，播放就暂停

2不是的当前的歌，要么第一次点歌之前为空，要么切歌，要重新开启

```
override fun onMusicPlayClick(music: Music) {
        if (MusicPlayerManager.getCurrentMusic()?.path == music.path) {
            if (MusicPlayerManager.isPlaying()) MusicPlayService.pausePlay(requireContext())
            else MusicPlayService.resumePlay(requireContext())
        } else {
            MusicPlayService.startPlay(requireContext(), music)
        }
    }
```

不管哪种都进入MusicPlayService

**2调用MusicPlayerService**

这个服务是为了开启通知...

每个播放/暂停/恢复都先从这里包裹一层通知，然后调用MusicPlayerManager做真正的播放

都要来开启服务，但是它们是不同的服务，需要用inent的action来区分不同的行为，context.startService(intent)/ContextCompat.startForegroundService(context, intent)

```
const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
```

这三种行为

```
fun startPlay(context: Context, music: Music) {
            val intent = Intent(context, MusicPlayService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_MUSIC, music)
            }
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun pausePlay(context: Context) = startService(context, ACTION_PAUSE)
        fun resumePlay(context: Context) = startService(context, ACTION_RESUME)
private fun startService(context: Context, action: String) {
            val intent = Intent(context, MusicPlayService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
```



第一次开服务需要创建

此时实现MusicPlayerManager的回调方法

```
interface OnPlayStateChangeListener{
	//同一首歌状态改变时调用
    fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music?)
    //切歌时，第一次算切歌
    fun onMusicChanged(newMusic: Music?)
    //歌曲列表播放时
    fun onPlayListEnd()
}
定义在Manager的接口
```





```
 override fun onCreate() {
        super.onCreate()
        // 注册服务监听器，和UI监听器共存
        MusicPlayerManager.addOnPlayStateChangeListener(stateListener)
    }
    
    
    
 //更新通知   
 private val stateListener = object : OnPlayStateChangeListener {
        override fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music? ) {
            currentMusic?.let { updateNotification(it, isPlaying) }
        }
        override fun onMusicChanged(newMusic: Music?) {}
        override fun onPlayListEnd() {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

```



当播放状态切换时Manager会调用这些方法

比如恢复播放时

```
 fun resumePlay(): Boolean {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying && currentMusic != null) {
            mediaPlayer?.start()
            startProgressUpdate()
            playStateListeners.forEach { it.onPlayStateChanged(true, currentMusic) }
            return true
        }
        return false
    }
```

此时通知会更新



接着往下

**创建完成，你会进入onStartCommand里**

刚刚在intent做的action标识在这里用when分别处理

```
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val music = intent.getParcelableExtra<Music>(EXTRA_MUSIC)
                music?.let {
                    MusicPlayerManager.playMusic(applicationContext, it)
                    startForeground(NOTIFICATION_ID, NotificationHelper.getMusicNotification(this, it, true))
                }
            }
            ACTION_PAUSE -> {
                MusicPlayerManager.pausePlay()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            ACTION_RESUME -> {
                MusicPlayerManager.resumePlay()
                val music = MusicPlayerManager.getCurrentMusic()
                music?.let {
                    startForeground(NOTIFICATION_ID, NotificationHelper.getMusicNotification(this, it, true))
                }
            }
        }
        return START_STICKY
    }
```

对于不同的action,就调用不同的MusicPlayerManager,做最底层的音乐实际的播放/暂停/恢复

##### **2暂停，播放图标切换**

MediaPlayerManager的回调，也被在DetailFragment里实现了

```
  private fun updateListAdapter() {
        playStateListener = object: OnPlayStateChangeListener{
            override fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music?) {
                adapter.updatePlayState(currentMusic?.path, isPlaying)
            }
            override fun onMusicChanged(newMusic: Music?) {}
            override fun onPlayListEnd() {
                //此时关闭Manager
                MusicPlayerManager.stopPlay()
                //刷新ui
                adapter.updatePlayState(null,false)
            }
        }
        // 注册监听器
        MusicPlayerManager.addOnPlayStateChangeListener(playStateListener)
    }
```

此时当MediaPlayerManager执行播放/暂停方法时，调用OnPlayStateChangeListener（），会   adapter.updatePlayState(currentMusic?.path, isPlaying)更新ui

```
fun updatePlayState(path: String?, playing: Boolean) {
        // 1. 找到【上一首正在播放的音乐】在列表里的位置
        val oldPos = currentList.indexOfFirst { it.path == currentPlayingPath }
        // 2. 找到【现在要播放的音乐】在列表里的位置
        val newPos = currentList.indexOfFirst { it.path == path }

        // 3. 更新适配器内部记录的：当前播放音乐路径 + 播放状态
        currentPlayingPath = path
        isPlaying = playing

        // 4. 如果上一首有播放的，刷新这一行（让它变回暂停状态）
        if (oldPos != -1) notifyItemChanged(oldPos)
        // 5. 如果新的音乐有效，刷新这一行（让它显示播放状态/动画/进度条）
        if (newPos != -1) notifyItemChanged(newPos)
    }
```

也就是触发重新 onBindViewHolder

然后bind()方法

```
触发
   // 修复：使用你项目原有的图片名，无资源报错
                if (isPlaying) {
                    binding.ivPlay.setImageResource(R.drawable.play)
                    binding.ivPlay.startAnimation(pulseAnim)
                } else {
                    binding.ivPlay.setImageResource(R.drawable.pause)
                    binding.ivPlay.clearAnimation()
                }
这些图标的切换
```



这里有个图标放大缩小的播放逻辑

```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
   >
    <scale
        android:duration="800"
        android:fromXScale="1.0"
        android:fromYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:toXScale="1.2"
        android:repeatCount="infinite"
        android:repeatMode="reverse"
        android:toYScale="1.2"/>
</set>

 private val pulseAnim = AnimationUtils.loadAnimation(itemView.context, R.anim.anim_play_pulse)
 ...
  if (isPlaying) {
                    binding.ivPlay.setImageResource(R.drawable.play)
                    binding.ivPlay.startAnimation(pulseAnim)
                }
```









##### **3进度条显示**

进度条的跟新，肯定是MusicPlayerManager，这个最接近歌曲，能拿到歌曲相关信息的类来做。

```
   private var mediaPlayer: MediaPlayer? = null
    private val _progressFlow = MutableStateFlow(0 to 0)
    val progressFlow: StateFlow<Pair<Int, Int>> = _progressFlow.asStateFlow()

    // 协程定时更新播放进度
    private fun startProgressUpdate() {
        progressJob = mainScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                delay(500)
                val pos = mediaPlayer?.currentPosition ?: 0
                val dur = mediaPlayer?.duration ?: 0
                _progressFlow.emit(pos to dur) // 发送进度
            }
        }
    }
```

stateFlow是热流，可以存储状态，所有每次更新进度条，用协程发送进度，然后stateFlow变量就改变。

然后在DetailFragment里做收集最新的当前位置和歌曲总时长，来更新歌曲项

直接调用adapter里刷新列表项的方法，notifyItemChanged（）

```
private fun observeProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
            MusicPlayerManager.progressFlow.collect { (pos, dur) ->
                adapter.notifyItemChanged(adapter.currentList.indexOfFirst { it.path == MusicPlayerManager.getCurrentMusic()?.path })
            }
        }
    }
```

**调用 `notifyItemChanged(位置)` 触发刷新，本质就是让**对应位置的条目**重新执行 `onBindViewHolder` 方法**

```
在holder.bind(getItem(position))方法里
  // 同步进度条
binding.sbMusicProgress.max = MusicPlayerManager.getCurrentDuration()
binding.sbMusicProgress.progress = MusicPlayerManager.getCurrentPosition()


  // 仅播放中的Item设置监听
binding.sbMusicProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
     override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            // ✅ 双重判空：只有播放中、用户手动拖动才执行
            if (fromUser&& MusicPlayerManager.getCurrentMusic() != null) {
                 MusicPlayerManager.seekTo(progress)
                }
      }
     override fun onStartTrackingTouch(seekBar: SeekBar?) {}
     override fun onStopTrackingTouch(seekBar: SeekBar?) {}
       }
    )

```

MusicPlayerManager.seekTo(progress)执行这个是会导致当前的播放时间改变到滑动的位置，然后等flow流到点了，就会把这个进度发过去，再次更新列表

stateFlow和flow的用法上没有区别，，第一次我已经播放有进度然后退出，我第二次进fragment,是因为stateFlow直接有值，然后直接被收集，而flow需要等500ms再次发射才能收集到值





##### **4列表，单曲，随机播放**

自定义的view，点击时切换图标并且切换播放模式

```
class SimpleCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {


    // 播放模式
    enum class Mode {
        LIST_LOOP,   // 列表循环
        SINGLE_LOOP, // 单曲循环
        RANDOM       // 随机播放
    }
    // 当前模式
    private var currentMode = Mode.LIST_LOOP

    // 画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 图标资源
    private val listLoopIcon: Bitmap
    private val singleLoopIcon: Bitmap
    private val randomIcon: Bitmap



    // ====================== 固定尺寸（你直接改这两个数） ======================
    private val CIRCLE_DP = 50   // 圆形大小 180dp
    private val ICON_DP = 30     // 图标大小 100dp
    var onModeChanged: ((Mode) -> Unit)? = null
    init {
        // 初始化你的图标
        listLoopIcon = getBitmap(R.drawable.ic_loop_list)
        singleLoopIcon = getBitmap(R.drawable.ic_loop_single)
        randomIcon = getBitmap(R.drawable.ic_shuffle)

        // 白色圆形背景
        bgPaint.color = Color.WHITE
        bgPaint.style = Paint.Style.FILL
    }

    // 读取图片
    private fun getBitmap(resId: Int): Bitmap {
        return (resources.getDrawable(resId, null) as BitmapDrawable).bitmap
    }

    // dp转px（核心：安卓必须转像素才能用）
    private fun dp2px(dpValue: Int): Int {
        val scale = resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = dp2px(CIRCLE_DP) / 2f

        // 1. 画白色正圆
        canvas.drawCircle(centerX, centerY, radius, bgPaint)

        // 2. 获取当前图标
        val icon = when (currentMode) {
            Mode.LIST_LOOP -> listLoopIcon
            Mode.SINGLE_LOOP -> singleLoopIcon
            Mode.RANDOM -> randomIcon
        }

        // 3. 图标强制缩放到 100dp，居中绘制
        val iconSize = dp2px(ICON_DP)
        val left = centerX - iconSize / 2f
        val top = centerY - iconSize / 2f

        canvas.drawBitmap(
            icon,
            null,
            RectF(left, top, left + iconSize, top + iconSize),
            iconPaint
        )
    }

    // 点击切换模式
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            currentMode = when (currentMode) {
                Mode.LIST_LOOP -> Mode.RANDOM
                Mode.RANDOM -> Mode.SINGLE_LOOP
                Mode.SINGLE_LOOP -> Mode.LIST_LOOP
            }
            onModeChanged?.invoke(currentMode)
            invalidate()

        }
        return true
    }

    // ====================== 修复：控件固定180dp ======================
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp2px(CIRCLE_DP)
        setMeasuredDimension(size, size)
    }

    // 回收图片
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
```



在DetailFragment里

对这个自定义view进行监听，来更换设置模式

```
// 播放模式切换监听 + Toast提示
        binding.circleView.onModeChanged = { mode ->
            // 1. 设置播放模式
            MusicPlayerManager.setMode(mode)
            // 2. 弹出对应Toast提示
            val tip = when (mode) {
                SimpleCircleView.Mode.LIST_LOOP -> "列表循环"
                SimpleCircleView.Mode.RANDOM -> "随机播放"
                SimpleCircleView.Mode.SINGLE_LOOP -> "单曲循环"
            }
            // 弹Toast（Fragment中用 requireContext()）
            android.widget.Toast.makeText(requireContext(), tip, android.widget.Toast.LENGTH_SHORT).show()
        }
```



模式的逻辑

 1设置模式，MusicPlayerManager.setMode(mode)

2在playNextMusic里不同的mode，做不同的逻辑

```
fun playNextMusic(context: Context) {
    if (playList.isEmpty()) {
        playStateListeners.forEach { it.onPlayListEnd() }
        return
    }

    when (mode) {
        SimpleCircleView.Mode.SINGLE_LOOP -> {
            // 不切换索引
        }
        SimpleCircleView.Mode.LIST_LOOP -> {
            //播放继续播放
            currentPlaylistIndex = (currentPlaylistIndex + 1) % playList.size
        }
        SimpleCircleView.Mode.RANDOM -> {

            // 1. 只有列表大于1首歌才随机
            if (playList.size > 1) {
                var randomIndex = currentPlaylistIndex
                // 2. while循环：随机到和当前索引不同为止
                while (randomIndex == currentPlaylistIndex) {
                    randomIndex = (0 until playList.size).random()
                }
                // 3. 赋值新索引
                currentPlaylistIndex = randomIndex
            }
        }
    }

    playMusic(context, playList[currentPlaylistIndex])
}
```



##### **5播放的实现**（**关键**）

fun playMusic(context: Context, music: Music): Boolean

**作用**：播放**指定的音乐文件**，是所有播放操作的最终执行方法

**参数**：上下文`Context` + 要播放的音乐`Music`（包含路径、歌名、歌手等）

**返回值**：`Boolean` → 播放成功 / 失败

**核心逻辑**：分**两种情况**处理播放

- 情况 1：**重复点击当前正在播放的歌** → 直接继续播放
- 情况 2：**播放新的歌曲** → 销毁旧播放器，创建新播放器，加载歌曲播放



用注释的形式过逻辑

```
 fun playMusic(context: Context, music: Music): Boolean {
        try {
            // 停止之前的进度条更新协程（防止多个协程同时发进度）
            stopProgressUpdate()
            if (currentMusic?.path == music.path) {
                // 判断：要播放的歌 = 正在播放的歌
                mediaPlayer?.start() // 继续播放（暂停→播放）
                startProgressUpdate()   // 重启进度更新协程
                //开启回调，播放状态改为播放种，1更新通知，2更新ui
                playStateListeners.forEach { it.onPlayStateChanged(true, music) }
                return true
            }
            //不是同一首歌，销毁上一首歌的MediaPlayer，释放内存，避免卡顿/杂音
            releasePlayer()

            mediaPlayer = MediaPlayer().apply {
                reset() // 重置播放器状态（必须！MediaPlayer有严格状态机）

                //处理要播放的歌曲路径
                // 判断路径类型：
                // 1. content://  → 手机系统媒体库的音乐（最常见）
                // 2. 普通路径    → 本地文件
                val uri = if (music.path.startsWith("content://")) {
                    Uri.parse(music.path)
                } else {
                    Uri.fromFile(File(music.path))
                }
                //转成正确的uri，设置播放源
                setDataSource(context.applicationContext, uri)
                //设置错误监听（播放失败处理）
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    currentMusic = null
                    //通知监听器：播放失败，状态为暂停
                    playStateListeners.forEach { it.onPlayStateChanged(false, null) }
                    true
                }
                //设置准备完成监听（异步加载完成→播放）
                setOnPreparedListener {
                    start()               // 开始播放！
                    currentMusic = music  // 记录：当前正在播放的歌曲
                    // 记录歌曲在列表中的位置（用于切歌）
                    currentPlaylistIndex = playList.indexOfFirst { it.path == music.path }
                    startProgressUpdate() // 启动进度条更新协程
                    // 通知监听器：歌曲切换了
                    playStateListeners.forEach { it.onMusicChanged(music) }
                    // 通知监听器：播放状态变为【播放中】
                    playStateListeners.forEach { it.onPlayStateChanged(true, music) }
                }
                //设置播放完成监听（自动下一首）
                setOnCompletionListener {
                    stopProgressUpdate() // 停止进度更新
                    playNextMusic(context) // 自动播放下一首歌（根据播放模式：列表/随机/单曲）
                }
                //异步加载歌曲（不卡主线程）
                prepareAsync()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            releasePlayer()
            return false
        }
    }
```

 **MediaPlayer 状态机（为什么代码这么写？）**

Android 的`MediaPlayer`是**有严格状态的**，必须按顺序执行：

```
重置 → 设置数据源 → 异步准备 → 准备完成 → 播放
```

**为什么用异步`prepareAsync()`？**

加载音乐文件需要时间，同步加载会**卡住界面（ANR）**，异步加载后台执行，丝滑流畅。

**状态监听的作用**

`playStateListeners` 是**观察者模式**：

- 播放器状态改变 → 通知 Fragment/Service/Adapter 更新 UI
- 不用手动到处调用刷新，自动同步

**资源管理**

每次播放新歌，都会`releasePlayer()`销毁旧播放器，**避免内存泄漏、杂音、多播放器冲突**。





##### 6**通知的方法**

这里不一样的点，是需要给通知多添加一个Action

```
fun getMusicNotification(context: Context, music: Music,isPlaying: Boolean): Notification {
        val channelId = createMusicNotificationChannel(context)


        val action = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause, // 暂停图标（确保资源存在）
                "暂停",
                getPausePendingIntent(context)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play, // 播放图标（确保资源存在）
                "播放",
                getPlayPendingIntent(context, music)
            )
        }
// 强制折叠态显示图标（关键）

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.music)
            .setContentTitle("正在播放")
            .setContentText(music.name)
            .setOngoing(true)
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
```



Action里的属性，图标，标题，intent

这个intent很重要，本质是一个「延迟触发的播放指令」，**当用户点击通知栏里的【播放按钮】时，系统会自动通过这个指令，让音乐服务播放指定的歌曲**。

1.先创建一个发送服务的intent，分出暂停和恢复的action

2.创建PendingIntent.Service(...)

```
/**
 * 创建【通知栏播放按钮】对应的延迟指令
 * @param context 上下文
 * @param music 要播放的歌曲（核心：点击后播哪首歌）
 * @return 可被通知栏调用的延迟指令
 */
private fun getPlayPendingIntent(context: Context, music: Music): PendingIntent {
    // 1. 创建一个发给【音乐播放服务】的指令
    val playIntent = Intent(context, MusicPlayService::class.java).apply {
        action = MusicPlayService.ACTION_PLAY  // 指令类型：播放
        putExtra(MusicPlayService.EXTRA_MUSIC, music)  // 携带要播放的歌曲数据
    }

    // 2. 关键优化：用歌曲路径的哈希值作为唯一请求码
    // 每首歌的路径唯一 → 每首歌的请求码都不一样
    val requestCode = music.path.hashCode() 

    // 3. 获取【延迟执行的服务指令】
    return PendingIntent.getService(
        context,        // 上下文
        requestCode,    // 唯一标识（区分不同歌曲的播放指令）
        playIntent,     // 要执行的指令
        // 标志位：更新旧指令 + 安卓12+必须的不可变标志
        PendingIntent.FL_UPDATE_CURRENT or PendingIntent.FL_IMMUTABLE
    )
}
```

,,当你点击按钮时，本来是暂停的通知，系统会发送播放的延迟指令来开启服务...

也就是PendingIntent的本质不是发通知，而是启动服务，，服务来发通知









#### MediaPlayer的多线程问题

##### **Android 的 MediaPlayer 是 非线程安全 的！**

1. **在哪里创建，就必须在哪里操作**（你的代码创建在**主线程**，所有操作都只能在主线程）

  2.**跨线程调用 start/pause/seekTo/release → 直接崩溃 / 无声音 / 状态错乱**



用了prepareAsync 加载，会自动的切到系统的子线程

其他线程不能去操作





### 9定时保存功能（workManager）

**安卓后台任务调度框架**,专门用来执行 **「不需要立即执行、但必须保证能执行」** 的后台任务（定时、延迟、同步数据）。

我用它做 **「循环定时后台同步天气」**

→ 每过一段时间，自动更新天气数据，不用用户手动刷新。



任务

**`CoroutineWorker`**：官方提供的**支持协程的后台任务类**（你用协程请求网络，必须用它）

**`doWork()`**：**任务的核心**，后台要干啥全写这里

**返回值**：

- `Result.success()`：任务做完了
- `Result.failure()`：任务失败
- `Result.retry()`：出错了，让系统重新试一次

```
class WeatherSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
override suspend fun doWork(): Result {
        // 👇 这里面全是：APP在后台时，要自动执行的代码
        // 1. 拿缓存的城市编码
        // 2. 有网 → 请求最新天气 → 存缓存 → 发通知
        // 3. 没网 → 读缓存天气 → 发通知
        // 4. 报错 → 让系统重试
        return Result.success()
    }
}

```





```
private fun initWeatherSyncTask() {
	// 1. 创建任务：延迟1分钟执行
    val syncRequest = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
        .setInitialDelay(1, TimeUnit.MINUTES).build()
    
    // 2. 交给WorkManager执行（唯一任务，不重复）
    WorkManager.getInstance(this).enqueueUniqueWork(
        "WeatherSyncTask", ExistingWorkPolicy.REPLACE, syncRequest
    )

    // 3. 监听：任务成功 → 再安排一次 = 循环执行
    WorkManager.getInstance(this).getWorkInfoByIdLiveData(syncRequest.id)
        .observeForever { workInfo ->
            if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                initWeatherSyncTask()
            }
        }	
}
```

**创建一次性任务**：1 分钟后，执行 `WeatherSyncWorker`

**交给 WorkManager 管理**：系统保证这个任务一定会运行

**循环逻辑**：

任务跑完 → 立刻再安排下一个任务

→ **实现「每 1 分钟同步一次天气」**

#### 为什么这么写？

官方的循环任务**最小间隔 15 分钟**，你这么写可以**自定义任意时间**，更灵活！



|      东西       |     干什么的      |        你的项目里用来干啥        | 能不能用在天气同步？ |
| :-------------: | :---------------: | :------------------------------: | :------------------: |
|   **Service**   |  长期在后台运行   |    **播放音乐**（必须一直跑）    | ❌ 不行，会被系统杀死 |
| **WorkManager** | 定时 / 延迟跑一次 | **定时同步天气**（1 分钟跑一次） |   ✅ 完美，官方推荐   |



## 小知识点

### 1.Intent传递数据时，不能直接传对象

对象必须先**序列化（变成一串二进制数据）**

才能在组件（Activity → Service）之间传递。

##### 两种序列化方式：

1. **Serializable**（Java 老式，慢）
2. **Parcelable**（Android 专用，快，官方推荐）



### 2通知的生命周期，什么时候消失？

**通知还在状态栏里，还能点击，生命周期 = 直到你手动删除它，不会自动消失！**

消失的时机：

**你调用 `nm.cancel(101)`**

**你调用 `stopForeground(STOP_FOREGROUND_REMOVE)`**

**用户手动划掉通知（但音乐通知一般不可划掉）**



### 3前台通知，前台服务，服务的关系

**startForegroundService () = 只是允许你变成前台，还不是**

**startForeground () 开启通知= 真正变成前台服务**

**通知 = 前台服务必须带的 “身份证”**

通知必须由前台服务通过 startForeground () 开启，才叫前台通知



### 4处理fragment传参的方式

1SongSelectorBottomSheet------弹窗选歌的fragment

2MusicFragment在初始化FolderListAdapter时候，往列表项里传入点击事件

```
folderListAdapter = FolderListAdapter(
            onManualAddClick = { folderId, folderName ->
                val sheet = SongSelectorBottomSheet.instance(folderId, folderName)
                sheet.show(childFragmentManager, "SongSelectorBottomSheet")
            },
         )
```

注意这里涉及fragment的传参

SongSelectorBottomSheet.kt

```
 companion object {
        fun instance(folderId: Int?, folderName: String?): SongSelectorBottomSheet {
            val fragment = SongSelectorBottomSheet()
            val args = Bundle()
            folderId?.let { args.putInt("folderId", it) }
            args.putString("folderName", folderName)
            fragment.arguments = args
            return fragment
        }
    }
```

也就是被传入参数的SongSelectorBottomSheet提前写了伴生对象，也就是提供了入口，让你可以调用创建实例的接口来传参，而封装成Bundle()和拿参数的逻辑，全在被传入参数的SongSelectorBottomSheet里处理。



### 5fragment和service何时启动生命周期？？

**Fragment** ！！

**new 时 = 只创建对象，不启动生命周期**

**add /replace/show 提交后 = 正式启动生命周期**

触发流程：

1. val fragment = XxxFragment()

   → 只是对象，无生命周期

2. fragment.show(manager, tag)或 transaction.add(...)

   → 系统开始执行：onCreate → onCreateView → ...

一句话总结：

**Fragment 必须被 “添加到页面” 才会启动生命周期！**

Service!!!

**startService /startForegroundService 调用时 = 立即启动**

1. startService(intent)

   → 系统立刻创建服务

2. 执行：`onCreate` → `onStartCommand`

- **只需调用一次 startService**
- 后续再次调用只会走 `onStartCommand`，不会重建

Service 只要被启动，就立刻走完整生命周期！

