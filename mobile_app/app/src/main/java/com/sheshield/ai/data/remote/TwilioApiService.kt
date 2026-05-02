package com.sheshield.ai.data.remote

import com.sheshield.ai.Config
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Path

interface TwilioApiService {

    @FormUrlEncoded
    @POST("Accounts/{accountSid}/Messages.json")
    suspend fun sendSms(
        @Path("accountSid") accountSid: String,
        @Field("To") to: String,
        @Field("From") from: String,
        @Field("Body") body: String
    ): Response<Unit>

    companion object {
        fun create(): TwilioApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", Credentials.basic(Config.TWILIO_ACCOUNT_SID, Config.TWILIO_AUTH_TOKEN))
                        .build()
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(Config.TWILIO_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TwilioApiService::class.java)
        }
    }
}
