package ru.startandroid.yandexmoney;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import com.yandex.money.api.methods.OperationDetails;
import com.yandex.money.api.methods.OperationHistory;
import com.yandex.money.api.model.Operation;
import com.yandex.money.api.model.Scope;
import com.yandex.money.api.net.ApiRequest;
import com.yandex.money.api.net.clients.ApiClient;

import org.joda.time.DateTime;

import java.util.Collections;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_AUTH = 1;

    @BindView(R.id.status)
    TextView textViewStatus;

    @BindView(R.id.data)
    TextView textViewData;

    @BindView(R.id.get_data)
    Button buttonGetData;

    private ApiClient apiClient;
    private String next = "0";
    private StringBuilder data = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        ButterKnife.bind(this);

        apiClient = AppController.getApiClient();

        SharedPreferences preferences = AppController.getPreferences(MainActivity.this);
        String token = preferences.getString(Constants.EXTRA_TOKEN, "");
        apiClient.setAccessToken(token);
        checkStatus();
    }

    @OnClick(R.id.get_data)
    public void onGetDataClick() {
        if (apiClient.isAuthorized()) {
            data.setLength(0);
            showData("");
            next = "0";
            getData();
        } else {
            requestAuthorization();
        }
    }

    private void checkStatus() {
        textViewStatus.setText(getString(R.string.is_authorized, apiClient.isAuthorized()));
        buttonGetData.setText(apiClient.isAuthorized() ? R.string.get_data : R.string.authorize);
    }

    private void showData(String text) {
        textViewData.setText(text);
    }

    private void requestAuthorization() {
        AuthActivity.startAuthorization(this, REQUEST_CODE_AUTH, Scope.OPERATION_HISTORY, Scope.OPERATION_DETAILS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_AUTH) { // обработка ответа от AuthActivity
            checkStatus();
            if (resultCode == RESULT_CANCELED) {
                String error = "";
                if (data != null) {
                    error = data.getStringExtra(Constants.EXTRA_ERROR);
                }
                if (TextUtils.isEmpty(error)) {
                    error = getString(R.string.unknown_error);
                }
                showData(error);
            }
        }
    }


    // получение данных
    private void getData() {

        if (TextUtils.isEmpty(next)) {
            // если next пустой, значит все страницы получены, выходим
            return;
        }

        // создаем запрос на получение записей
        ApiRequest<OperationHistory> request = new OperationHistory.Request.Builder()
                .setRecords(100) // размер страницы - 100 записей
                .setStartRecord(next) // грузить записи страницы, которая начинается с next
                .setFrom(new DateTime(2017, 5, 1, 0, 0)) // грузить записи начиная с 1 мая 2017
                .create();

        // создаем Observable на основе запроса обернутого в Callable
        Observable.fromCallable(new HistoryRequest(request))

                // при получении каждой страницы данных (OperationHistory),
                // вытаскиваем из нее все записи - operationHistory.operations
                // и сохраняем номер записи, с которой начнется следующая страница
                .concatMap(new Func1<OperationHistory, Observable<Operation>>() {
                    @Override
                    public Observable<Operation> call(OperationHistory operationHistory) {
                        next = operationHistory.nextRecord;
                        return Observable.from(operationHistory.operations);
                    }
                })

                // для каждой операции отдельным запросом получаем детали
                .map(new Func1<Operation, OperationDetails>() {
                    @Override
                    public OperationDetails call(Operation operation) {
                        // создаем запрос деталей записи по ее operationId
                        ApiRequest<OperationDetails> request = new OperationDetails.Request(operation.operationId);
                        OperationDetails operationDetails = null;
                        try {
                            // выполняем запрос
                            operationDetails = apiClient.execute(request);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return operationDetails;
                    }
                })

                // все запросы должны выполняться в IO-потоке
                .subscribeOn(Schedulers.io())

                // а результат должен прийти в main-потоке
                .observeOn(AndroidSchedulers.mainThread())

                // подписываемся
                .subscribe(new Observer<OperationDetails>() {
                    @Override
                    public void onCompleted() {
                        // по завершению снова запускаем процесс получения данных
                        // чтобы загрузить следующую страницу
                        getData();
                    }

                    @Override
                    public void onError(Throwable e) {
                        showData(e.getMessage());
                    }

                    @Override
                    public void onNext(OperationDetails operationDetails) {
                        // получаем детали операции

                        if (operationDetails == null) {
                            return;
                        }

                        // выводим данные по операции на экран
                        Operation operation = operationDetails.operation;
                        data.append(operation.datetime.toString("yyyy-MM-dd HH:mm:ss")).append("\r\n")
                                .append(" ").append(operation.title)
                                .append(", ").append(operation.type)
                                .append("\r\n\r\n");
                        showData(data.toString());
                    }
                });
    }

    // Callable обертка выполнения запроса на получение записей
    private class HistoryRequest implements Callable<OperationHistory> {

        private final ApiRequest<OperationHistory> request;

        HistoryRequest(ApiRequest<OperationHistory> request) {
            this.request = request;
        }

        @Override
        public OperationHistory call() throws Exception {
            // выполнение запроса
            return apiClient.execute(request);
        }
    }
}
