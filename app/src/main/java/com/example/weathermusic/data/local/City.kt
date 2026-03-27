package com.example.weathermusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity("city")
data class City (
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 主键，自增
    val cityName: String?, // 城市名（如“北京”）
    val cityCode: String?, // 城市编码（如“110100”）
    //有默认值的参数必须放在无默认值参数的后面
    val isSelected: Boolean = false // 是否选中（默认未选中）
    )
