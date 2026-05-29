package com.example.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class BackupLogPayload(
    val id: Long,
    val timestamp: Long,
    val cleanedBytes: Long,
    val scanDurationMs: Long,
    val junkDetailsJson: String,
    val status: String
)

data class BackupResponse(
    val url: String?,
    val origin: String?,
    val headers: Map<String, String>?
)

interface CloudBackupService {
    @POST("post")
    suspend fun backupLog(@Body payload: BackupLogPayload): Response<BackupResponse>

    companion object {
        private const val BASE_URL = "https://httpbin.org/"

        fun create(): CloudBackupService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(CloudBackupService::class.java)
        }
    }
}
