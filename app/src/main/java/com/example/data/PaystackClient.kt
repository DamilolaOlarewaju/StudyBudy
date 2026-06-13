package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SupabaseInitializeRequest(
    @Json(name = "email") val email: String,
    @Json(name = "amount") val amount: Int? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseInitializeData(
    val authorization_url: String? = null,
    val reference: String? = null,
    val access_code: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseInitializeResponse(
    val status: Boolean? = null,
    val message: String? = null,
    val data: SupabaseInitializeData? = null,
    val authorization_url: String? = null,
    val reference: String? = null
) {
    val finalAuthorizationUrl: String?
        get() = authorization_url ?: data?.authorization_url

    val finalReference: String?
        get() = reference ?: data?.reference
}

@JsonClass(generateAdapter = true)
data class SupabaseVerifyRequest(
    val reference: String
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyData(
    val status: String? = null,
    val reference: String? = null,
    val amount: Int? = null,
    val gateway_response: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseVerifyResponse(
    val status: Boolean? = null,
    val message: String? = null,
    val data: SupabaseVerifyData? = null
) {
    val isSuccessful: Boolean
        get() = (status == true && data?.status?.lowercase() == "success") || data?.status?.lowercase() == "success"
}

interface PaystackApiService {
    @POST("initialize-payment")
    suspend fun initializeTransaction(
        @Body request: SupabaseInitializeRequest
    ): SupabaseInitializeResponse

    @POST("verify-payment")
    suspend fun verifyTransaction(
        @Body request: SupabaseVerifyRequest
    ): SupabaseVerifyResponse
}

@JsonClass(generateAdapter = true)
data class ReferralDbRow(
    @Json(name = "code") val code: String,
    @Json(name = "email") val email: String
)

interface SupabaseDbService {
    @GET("referrals")
    suspend fun checkReferralCode(
        @Query("code") code: String
    ): List<ReferralDbRow>

    @Headers("Prefer: return=representation")
    @POST("referrals")
    suspend fun registerReferralCode(
        @Body referral: ReferralDbRow
    ): List<ReferralDbRow>
}

object PaystackClient {
    private const val BASE_URL = "https://pjptyztoaeowhvauwixk.supabase.co/functions/v1/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            val key = com.example.BuildConfig.SUPABASE_ANON_KEY
            if (key.isNotEmpty() && key != "your_supabase_anon_public_key_here") {
                builder.addHeader("apikey", key)
                builder.addHeader("Authorization", "Bearer $key")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: PaystackApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PaystackApiService::class.java)
    }

    val supabaseDbService: SupabaseDbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://pjptyztoaeowhvauwixk.supabase.co/rest/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseDbService::class.java)
    }
}
