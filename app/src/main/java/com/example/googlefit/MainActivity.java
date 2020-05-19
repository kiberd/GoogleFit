package com.example.googlefit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.SessionsApi;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.fitness.result.SessionReadResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.text.DateFormat.getTimeInstance;

public class MainActivity extends AppCompatActivity {

    // 시작 끝 시간 임의 설정 2019년 5월 19일 수면기록
    private String startTime = "20200519004000"; // start 타임
    private String endTime = "20200519073200"; // end 타임

    private static final int ACC = 0;

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1;
    private FitnessOptions fitnessOptions;

    // 최근 7일 동안의 평균 심박수 및 최저 심박수
    private float avg_bpm = 0;
    private float min_bpm = 0;

    // 0 = 기상, 1 = 얕은수면, 2 = 깊은수면
    private int sleepStage[] = new int [3];

    TextView as, bs, cs;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Case of Android 10
        requestActivityPermission();

        // 피트니스 객체 지정
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_HEART_RATE_BPM, fitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, fitnessOptions.ACCESS_WRITE)
                .build();

        GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(this, fitnessOptions);


        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this, // your activity
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                    account,
                    fitnessOptions);
        } else {

            setHeartLate(); // 최근 7일동안 평균심박수, 최저심박수 구하는 method
            setSleepStage(); // 수면단계 구분 method
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {

                setHeartLate();// 최근 7일동안 평균심박수, 최저심박수 구하는 method
                setSleepStage(); // 수면단계 구분 method
            }
        }
    }


    // ACTIVITY READ 권한 요청 (Case of Android 10)
    private void requestActivityPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    1);
        }
    }


    // 수면단계 결정 ( getTimeInMillis() 형식 스타트,엔드 )
    private void setSleepStage(){

        // Range : 지난 7일동안
        Calendar start = Calendar.getInstance();
        start.set(Calendar.YEAR, 2019);
        start.set(Calendar.MONTH, Calendar.MAY );
        start.set(Calendar.DATE, 19);

        start.set(Calendar.HOUR_OF_DAY, 00);
        start.set(Calendar.MINUTE, 40);
        start.set(Calendar.SECOND, 00);

        final long startTime = start.getTimeInMillis();


        Calendar end = Calendar.getInstance();
        end.set(Calendar.YEAR, 2019);
        end.set(Calendar.MONTH, Calendar.MAY );
        end.set(Calendar.DATE, 19);

        end.set(Calendar.HOUR_OF_DAY, 07);
        end.set(Calendar.MINUTE, 32);
        end.set(Calendar.SECOND, 00);

        final long endTime = end.getTimeInMillis();

        // 심박수 데이터 getHistoryClient (2020년 5월 19일 수면기록 : 00시 40분 ~ 07시 32분)
        Fitness.getHistoryClient(this,
                GoogleSignIn.getLastSignedInAccount(this))
                .readData(new DataReadRequest.Builder()
                        .read(DataType.TYPE_HEART_RATE_BPM)// 심박수
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build())
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse response) {

                        float G = avg_bpm - min_bpm; // 평상시 심박수 평균 - 최저 심박수 (최근 7일동안)
                        float S; // 현재 심박수 - 최저 심박수 (최근 7일동안)


                        SimpleDateFormat format = new SimpleDateFormat("d일:h시:mm분");
                        bs = findViewById(R.id.b);
                        bs.setText(format.format(endTime - startTime) + " " + format.format(endTime));


                        DataSet dataSet = response.getDataSet(DataType.TYPE_HEART_RATE_BPM);

                        for (DataPoint dp : dataSet.getDataPoints()) {

                            for (Field field : dp.getDataType().getFields()) {

                                S = Float.valueOf(String.valueOf(dp.getValue(field))) - min_bpm;

                                if(S > (0.5 * G)){ // 기상 상태
                                    sleepStage[0] += 1;
                                }
                                else{

                                        if(S < (0.2 * G)){ // 깊은 수면
                                            sleepStage[2] += 1;
                                        }
                                        else{ // 얕은 수면
                                            sleepStage[1] += 1;
                                        }
                                    }

                                }
                            }
                        cs = findViewById(R.id.c);
                        cs.setText("기상 : " + sleepStage[0] +", 얕은수면 : " + sleepStage[1] + ", 깊은수면 : " + sleepStage[2]);
                    }
                });


    }


    // 최근 7일동안 평균심박수 및 최저심박수 설정
    private void setHeartLate() {

        // 출력을 위한 어레이리스트
        final ArrayList<String> heartLateList = new ArrayList<>();

        // Range : 지난 7일동안
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        final long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        final long startTime = cal.getTimeInMillis();


        // 심박수 데이터 getHistoryClient
        Fitness.getHistoryClient(this,
                GoogleSignIn.getLastSignedInAccount(this))
                .readData(new DataReadRequest.Builder()
                        .read(DataType.TYPE_HEART_RATE_BPM)// 심박수
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build())
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse response) {

                        as = findViewById(R.id.a);

                        float count = 0, total = 0;
                        DataSet dataSet = response.getDataSet(DataType.TYPE_HEART_RATE_BPM);

                        for (DataPoint dp : dataSet.getDataPoints()) {

                            SimpleDateFormat format = new SimpleDateFormat("d일:h시:mm분");
                            for (Field field : dp.getDataType().getFields()) {

                                // 첫번째 값일떄
                                if(count ==0){
                                    min_bpm = Float.valueOf(String.valueOf(dp.getValue(field)));
                                }
                                else{
                                    if(Float.valueOf(String.valueOf(dp.getValue(field))) < min_bpm){
                                        min_bpm = Float.valueOf(String.valueOf(dp.getValue(field)));
                                    }
                                }
                                count++;
                                total += Float.valueOf(String.valueOf(dp.getValue(field)));

                                heartLateList.add(format.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " Value: " + dp.getValue(field));
                                //bs.setText("\t가장 최근 동기화된 시각: " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " Value: " + dp.getValue(field));
                            }
                            avg_bpm = total / count;
                            as.setText("data수 : " + count + ", AVG : " + Math.round(avg_bpm) + ", MIN : " + Math.round(min_bpm));
                        }
                    }
                });

        ListView printView = (ListView)findViewById(R.id.bpm);
        final ArrayAdapter<String> timeAdabtor = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, heartLateList);
        printView.setAdapter(timeAdabtor);

    }
}



