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

    private static final String TAG = "MainActivity";
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1;
    private static final int MY_PERMISSIONS_ACTIVITY_RECOGNITION = 1;
    private FitnessOptions fitnessOptions;

    private OnDataPointListener mListener;


    TextView as, bs, cs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestActivityPermission();

        // 피트니스 객체 지정
        fitnessOptions = FitnessOptions.builder()
                //.addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                //.addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                //.addDataType(DataType.TYPE_ACTIVITY_SEGMENT, fitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, fitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_RATE_BPM, fitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_MOVE_MINUTES, fitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_MOVE_MINUTES, fitnessOptions.ACCESS_WRITE)
                .build();

        GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(this, fitnessOptions);

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this, // your activity
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                    account,
                    fitnessOptions);
        } else {

            accessHeartLate();

        }


    }

    // ACTIVITY READ 권한 요청
    private void requestActivityPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    1);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {

                accessHeartLate();

            }
        }
    }


    private void accessHeartLate() {


        // 출력을 위한 어레이리스트
        final ArrayList<String> heartLateList = new ArrayList<>();

        // 하루 동안의 심박수
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        final long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        final long startTime = cal.getTimeInMillis();


        Fitness.getHistoryClient(this,
                GoogleSignIn.getLastSignedInAccount(this))
                .readData(new DataReadRequest.Builder()
                        //.read(DataType.TYPE_STEP_COUNT_DELTA) // Raw 걸음 수
                        .read(DataType.TYPE_HEART_RATE_BPM)// 심박수
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build())
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse response) {
                        DataSet dataSet = response.getDataSet(DataType.TYPE_HEART_RATE_BPM);
                        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());


                        bs = findViewById(R.id.b);

                        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataPoints());

                        for (DataPoint dp : dataSet.getDataPoints()) {

                            SimpleDateFormat format = new SimpleDateFormat("a h:mm");

                            Log.i(TAG, "Data point:");
                            Log.i(TAG, "\tType: " + dp.getDataType().getName());
                            Log.i(TAG, "\tEnd: " + (dp.getEndTime(TimeUnit.MILLISECONDS)));


                            for (Field field : dp.getDataType().getFields()) {

                                heartLateList.add("\tTime: " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " Value: " + dp.getValue(field));

                                Log.i(TAG, "\tField: " + field.getName() + " Value: " + dp.getValue(field));
                                bs.setText("\t가장 최근 동기화된 시각: " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " Value: " + dp.getValue(field));
                            }

                        }
                    }
                });


        ListView printView = (ListView)findViewById(R.id.bpm);
        final ArrayAdapter<String> timeAdabtor = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, heartLateList);
        printView.setAdapter(timeAdabtor);


    }
}



