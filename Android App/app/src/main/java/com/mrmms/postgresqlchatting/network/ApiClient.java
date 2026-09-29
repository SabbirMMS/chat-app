package com.mrmms.postgresqlchatting.network;

import android.content.Context;

import com.google.gson.Gson;
import com.mrmms.postgresqlchatting.network.models.ErrorResponse;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static ApiClient instance;
    private final Context appContext;
    private Retrofit retrofit;
    private ChatApiService apiService;
    private String lastBaseUrl;
    private final Gson gson = new Gson();

    private ApiClient(Context context) {
        this.appContext = context.getApplicationContext();
        initRetrofit();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    private synchronized void initRetrofit() {
        String baseUrl = PrefsManager.getInstance(appContext).getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.lastBaseUrl = baseUrl;

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder();

                String token = PrefsManager.getInstance(appContext).getToken();
                if (token != null && !token.trim().isEmpty()) {
                    builder.header("Authorization", "Bearer " + token.trim());
                }

                builder.header("Accept", "application/json");
                return chain.proceed(builder.build());
            }
        };

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build();

        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(ChatApiService.class);
    }

    public synchronized ChatApiService getApi() {
        String currentBaseUrl = PrefsManager.getInstance(appContext).getBaseUrl();
        if (!currentBaseUrl.endsWith("/")) {
            currentBaseUrl += "/";
        }
        if (!currentBaseUrl.equals(lastBaseUrl)) {
            initRetrofit();
        }
        return apiService;
    }

    public String parseError(retrofit2.Response<?> response) {
        if (response == null || response.errorBody() == null) {
            return "Unknown server error";
        }
        try {
            String errorJson = response.errorBody().string();
            ErrorResponse errorObj = gson.fromJson(errorJson, ErrorResponse.class);
            if (errorObj != null && errorObj.getError() != null) {
                return errorObj.getError();
            }
        } catch (Exception e) {
            // fallback
        }
        return "HTTP error " + response.code();
    }
}
