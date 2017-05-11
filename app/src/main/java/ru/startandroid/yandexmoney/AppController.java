package ru.startandroid.yandexmoney;

import android.content.Context;
import android.content.SharedPreferences;

import com.yandex.money.api.net.clients.ApiClient;
import com.yandex.money.api.net.clients.DefaultApiClient;

public class AppController {

    public static final String PREFERENCES_FILE_NAME = "prefs";

    private static final ApiClient apiClient = new DefaultApiClient.Builder()
            .setClientId(Constants.CLIENT_ID).create();

    public static ApiClient getApiClient() {
        return apiClient;
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);

    }


}
