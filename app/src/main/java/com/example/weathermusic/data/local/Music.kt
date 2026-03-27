package com.example.weathermusic.data.local

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 能被 Room 数据库存储（本地存音乐信息）；
 * 能在 Fragment/Service 之间传递（比如把选中的歌传给播放 Service）。
 */
@Entity("music")
data class Music (
    // 主键：用音乐文件的绝对路径做唯一标识（避免重复扫描），也可以用自增ID
    @PrimaryKey var path: String,
// 歌曲名称
    var name: String,
// 歌手名称
    var singer: String,
// 歌曲时长（毫秒）
    var duration: Long,
// 专辑名称（可选，新手可先留空）
    var album: String = "",
    var size: Long = 0L,
    var folderId :Int? = null
): Parcelable{

    // 3. Parcelable核心：从Parcel读取数据（顺序必须和writeToParcel一致）
    constructor(parcel: Parcel) : this(
        path = parcel.readString() ?: "", // 非空兜底，避免空指针
        name = parcel.readString() ?: "",
        singer = parcel.readString() ?: "",
        duration = parcel.readLong(),
        album = parcel.readString() ?: "",
        size = parcel.readLong(),
        folderId = parcel.readValue(Int::class.java.classLoader) as? Int // 可空Int的读取方式
    )

    override fun describeContents(): Int {
        return 0
    }
    // // 6. 把Music对象"打包"进Parcel（新手重点：顺序和上面解包一致！）
    override fun writeToParcel( parcel: Parcel, p1: Int) {
        parcel.writeString(path)
        parcel.writeString(name)
        parcel.writeString(singer)
        parcel.writeLong(duration)
        parcel.writeString(album)
        parcel.writeLong(size)
        parcel.writeValue(folderId) // 可空Int用writeValue
    }

//    Parcelable的"工厂"，用来创建Music对象
    companion object CREATOR : Parcelable.Creator<Music> {
        // 从Parcel创建单个对象
        override fun createFromParcel(parcel: Parcel): Music {
            return Music(parcel)
        }

        // 创建对象数组（固定写法）
        override fun newArray(size: Int): Array<Music?> {
            return arrayOfNulls(size)
        }
    }
}