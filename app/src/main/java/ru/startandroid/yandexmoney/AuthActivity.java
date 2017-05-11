package ru.startandroid.yandexmoney;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.yandex.money.api.authorization.AuthorizationData;
import com.yandex.money.api.authorization.AuthorizationParameters;
import com.yandex.money.api.methods.Token;
import com.yandex.money.api.model.Scope;
import com.yandex.money.api.net.AuthorizationCodeResponse;
import com.yandex.money.api.net.clients.ApiClient;

import java.net.URISyntaxException;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class AuthActivity extends AppCompatActivity {

    @BindView(R.id.webView)
    WebView webView;

    ApiClient apiClient;

    // метод для запуска AuthActivity с указанием необходимых прав
    public static void startAuthorization(Activity activity, int requestCode, Scope... scopes) {
        AuthorizationParameters.Builder builder = new AuthorizationParameters.Builder()
                .setRedirectUri(Constants.REDIRECT_URI);
        for (Scope scope : scopes) {
            builder.addScope(scope);
        }
        AuthorizationParameters parameters = builder.create();

        AuthorizationData data = AppController.getApiClient().createAuthorizationData(parameters);
        String url = data.getUrl() + "?" + new String(data.getParameters());

        Intent intent = new Intent(activity, AuthActivity.class);
        intent.putExtra(Constants.EXTRA_URL, url);
        activity.startActivityForResult(intent, requestCode);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_activity);
        initView();
        apiClient = AppController.getApiClient();
        requestAuthorization();
    }

    private void requestAuthorization() {
        String url = getIntent().getStringExtra(Constants.EXTRA_URL);
        if (URLUtil.isValidUrl(url)) {
            webView.loadUrl(url);
        } else {
            returnError(getString(R.string.url_is_not_valid));
        }
    }

    private void initView() {
        ButterKnife.bind(this);
        webView.setWebViewClient(new AuthWebViewClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    private void returnError(String error) {
        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_ERROR, error);
        setResult(RESULT_CANCELED, data);
        finish();
    }

    private void returnSuccess() {
        setResult(RESULT_OK);
        finish();
    }

    private void getToken(String code) {
        Observable.fromCallable(new TokenRequest(code))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Token>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        returnError(e.getMessage());
                    }

                    @Override
                    public void onNext(Token token) {
                        if (token.error == null) {
                            // токен получен! сохраняем его в префы
                            SharedPreferences prefs = AppController.getPreferences(AuthActivity.this);
                            prefs.edit().putString(Constants.EXTRA_TOKEN, token.accessToken).apply();
                            // и отдаем в apiClient, чтобы он был авторизован
                            apiClient.setAccessToken(token.accessToken);
                            returnSuccess();
                        } else {
                            returnError(token.error.getCode());
                        }
                    }
                });
    }

    private class AuthWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String uri) {
            // если URL содержит REDIRECT_URI, значит это нам пришел код
            // и WebView не надо открывать этот URL

            if (uri.contains(Constants.REDIRECT_URI)) {
                // парсим URL, чтобы извлечь из него код
                AuthorizationCodeResponse response = null;
                try {
                    response = AuthorizationCodeResponse.parse(uri);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    returnError(e.getMessage());
                }
                if (response != null) {
                    if (response.error == null) {
                        // используя код получаем токен
                        getToken(response.code);
                    } else {
                        returnError(response.error.getCode() + ", "
                                + (response.errorDescription == null ? "" : response.errorDescription));
                    }
                }
                return true;
            }
            return false;
        }
    }

    // Callable обертка выполнения запроса на получение токена
    private class TokenRequest implements Callable<Token> {

        private final String code;

        public TokenRequest(String code) {
            this.code = code;
        }

        @Override
        public Token call() throws Exception {
            return apiClient.execute(new Token.Request(code, Constants.CLIENT_ID, Constants.REDIRECT_URI));
        }
    }
}
