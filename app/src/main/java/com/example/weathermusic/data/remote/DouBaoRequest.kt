package com.example.weathermusic.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// 1. 请求体（不变，和之前一致）
data class DouBaoRequest(
    val model: String,
    val input: List<InputItem>
)
data class InputItem(
    val role: String, // 固定为"user"
    val content: List<ContentItem>
)
data class ContentItem(
    val type: String, // 固定为"input_text"
    val text: String  // AI指令文本
)

// 2. 响应体（完全匹配实际返回结构）
data class DouBaoResponse(
    val output: List<OutputItem>, // 核心：替换choices为output
    val error: ErrorItem? = null
)
data class OutputItem(
    val type: String, // "reasoning"或"message"
    val role: String? = null, // "assistant"（仅message类型有）
    val content: List<ContentData>? = null, // 仅message类型有
    val status: String
)
data class ContentData(
    val type: String, // "output_text"
    val text: String  // 最终的收藏夹名称（如"阴天"）
)
data class ErrorItem(
    val code: String,
    val message: String
)

// 3. Retrofit接口（不变）
interface DouBaoApiService {
    @POST("api/v3/responses")
    suspend fun classifySong(
        @Header("Authorization") token: String,
        @Body request: DouBaoRequest
    ): DouBaoResponse
}