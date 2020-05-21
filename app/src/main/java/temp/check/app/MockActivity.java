package temp.check.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

public class MockActivity extends AppCompatActivity {

    private TextView mTemp;
    private TextView mColor;
    private long temp = 0;
    private int FLAG = 100;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock);
        mTemp = findViewById(R.id.tv_temp_no);
        mColor = findViewById(R.id.tv_temp_light);
        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FLAG = 0;
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FLAG = 1;
            }
        });
        findViewById(R.id.rest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FLAG = -1;
                temp = 0;
                mTemp.setText(getString(R.string.temp, temp));
                mColor.setText(getString(R.string.light_status_green));
            }
        });
        startTimer();
        mTemp.setText(getString(R.string.temp, temp));
    }

    private Timer mTimer;

    private void startTimer() {
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (FLAG == 0) {
                    temp++;
                    mHandler.sendEmptyMessage(0);
                }
            }
        }, 0, 100);
    }

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            mTemp.setText(getString(R.string.temp, temp));
            mTemp.setText(getString(R.string.temp, temp));
            if (temp <= 60) {
                mColor.setText(getString(R.string.light_status_green));
            } else if (temp <= 70) {
                mColor.setText(getString(R.string.light_status_yellow));
            } else if (temp <= 80) {
                mColor.setText(getString(R.string.light_status_red));
            } else {
                mColor.setText(getString(R.string.light_status_white));
            }
            return false;
        }
    });
}
