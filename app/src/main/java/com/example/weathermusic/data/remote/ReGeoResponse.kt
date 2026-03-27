package com.example.weathermusic.data.remote

data class ReGeoResponse(
    val status: String?,
    val info: String?,
    val regeocode:ReGeoCode?
)

data class ReGeoCode (
    val addressComponent:AddressComponent?
)

data class AddressComponent (
    val country: String?,
    val province: String?,
    val citycode: String?,
    val adcode: String?,
    val city: String?,
    val district: String?
)
