package com.example.bamachat.data.api

import com.example.bamachat.data.model.OllamaChatRequest
import com.example.bamachat.data.model.OllamaChatResponse
import com.example.bamachat.data.model.OllamaRequest
import com.example.bamachat.data.model.OllamaResponse
import com.example.bamachat.data.model.OllamaTagsResponse
import okhttp3.ResponseBody
import retrofit2.http.*

interface OllamaApiService {
    @Streaming
    @POST
    suspend fun chatStream(@Url url: String, @Body request: OllamaChatRequest): retrofit2.Response<ResponseBody>

    @POST
    suspend fun chat(@Url url: String, @Body request: OllamaChatRequest): OllamaChatResponse

    @POST
    suspend fun generate(@Url url: String, @Body request: OllamaRequest): OllamaResponse

    @GET
    suspend fun getTags(@Url url: String): OllamaTagsResponse
}
