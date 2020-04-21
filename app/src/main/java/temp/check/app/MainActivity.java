package temp.check.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView mTips;
    private TextView mTempNo;
    private TextView mTempLight;
    private TextView mInfo;

    private UsbManager mUsbManager;
    private String USB_PERMISSION = "temp.usb.permission";
    private PendingIntent mPrtPermissionIntent; //获取外设权限的意图
    private UsbSerialPort mUsbSerialPort;
    private UsbDeviceConnection mConnection;
    private List<UsbSerialDriver> mList = new ArrayList<>();
    private SerialInputOutputManager mIOManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTips = findViewById(R.id.tv_usb_status);
        mTempNo = findViewById(R.id.tv_temp_no);
        mTempLight = findViewById(R.id.tv_temp_light);
        mInfo = findViewById(R.id.tv_usb_info);
        registerBroadcast();
    }

    /**
     * 动态注册usb广播，拔插动作，注册动作
     */
    private void registerBroadcast() {
        //注册在此service下的receiver的监听的action
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
//        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(USB_PERMISSION);
        registerReceiver(usbReceiver, intentFilter);//注册receiver


        //通知监听外设权限注册状态
        //PendingIntent：连接外设的intent
        //ask permission
        mPrtPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(USB_PERMISSION), 0);
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogUtil.d("广播注册");
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            mTips.setText(action);
            // USB注册动作
            if (USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) != null) {
                            getUsbInfo(mUsbSerialPort.getDriver().getDevice());
                        } else {
                            mTips.setText(R.string.step_1);
                        }
                    } else {
                        mTips.setText(R.string.step_permission_failed);
                    }
                }
            }
            // USB拔插动作
            else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mTips.setText(R.string.step_2);
                LogUtil.d("USB 插入...");
            }
            search();
        }
    };

    //查询设备
    private void search() {
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mList.clear();
        mList.addAll(UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager));

        //try get enable printer dev
        if (mList.size() > 0) {
            UsbDevice device = mList.get(0).getDevice();
            requestNormalPermission(device);
        } else {
            LogUtil.e("not find device.");
            mTips.setText(R.string.step_connect_error);
        }
    }

    //检查权限
    private void requestNormalPermission(UsbDevice device) {
        if (!mUsbManager.hasPermission(device)) {
            LogUtil.d("木有权限");
            mTips.setText(R.string.step_request_permission);
            mUsbManager.requestPermission(device, mPrtPermissionIntent);
        } else {
            getUsbInfo(device);
        }
    }

    private void connect() {
        mConnection = mUsbManager.openDevice(mList.get(0).getDevice());
        if (mConnection == null) {
            mTips.setText(R.string.step_connect_error);
            return;
        }
        mUsbSerialPort = mList.get(0).getPorts().get(0); // Most devices have just one port (port 0)
        try {
            mUsbSerialPort.open(mConnection);
            mUsbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            mIOManager = new SerialInputOutputManager(mUsbSerialPort, new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    //解析data
                    String d = new String(data);
                    LogUtil.i(d);
                    mTips.setText(d);
                    switch (d){
                        case "Temp_10k_1":
                            sendData("LED_green");
                            break;
                        case "Temp_10k_2":
                            sendData("LED_yellow");
                            break;
                        case "Temp_10k_3":
                            sendData("LED_red");
                            break;
                        case "Temp_10k_4":
                            sendData("LED_white");
                            break;
                    }
                }

                @Override
                public void onRunError(Exception e) {
                    mTips.setText(getString(R.string.step_error) + e.getMessage());
                }
            });
            Executors.newSingleThreadExecutor().submit(mIOManager);
        } catch (IOException e) {
            e.printStackTrace();
            String s = getString(R.string.step_error) + e.getMessage();
            mTips.setText(s);
        }
        mTips.setText(R.string.step_3);
    }

    /**
     * 获得授权USB的基本信息
     * 1、USB接口，一般是第一个
     * 2、USB设备的输入输出端
     */
    private void getUsbInfo(UsbDevice usbDevice) {
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append(String.format("VID:%04X  PID:%04X  ManuFN:%s  PN:%s V:%s",
                    usbDevice.getVendorId(),
                    usbDevice.getProductId(),
                    usbDevice.getManufacturerName(),
                    usbDevice.getProductName(),
                    usbDevice.getVersion()
            ));
        } else if (Build.VERSION.SDK_INT >= 21) {
            sb.append(String.format("VID:%04X  PID:%04X  ManuFN:%s  PN:%s",
                    usbDevice.getVendorId(),
                    usbDevice.getProductId(),
                    usbDevice.getManufacturerName(),
                    usbDevice.getProductName()
            ));
        } else {
            sb.append(String.format("VID:%04X  PID:%04X",
                    usbDevice.getVendorId(),
                    usbDevice.getProductId()
            ));
        }

        mInfo.setText(sb.toString());
        connect();//连接
    }

    private void sendData(String s) {
        LogUtil.d("sendData:" + s);
        boolean b = false;
        try {
            b = mUsbSerialPort.write(s.getBytes(), 100) > 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogUtil.i("send data " + b);
    }

    /**
     * usb设备断开连接
     */
    private void disconnect() {
        LogUtil.e("usb disconnect...");
        if (mUsbSerialPort != null) {
            try {
                mUsbSerialPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mUsbSerialPort = null;
        }
        if(mIOManager != null){
            mIOManager.stop();
            mIOManager = null;
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(usbReceiver);
        usbReceiver = null;
        disconnect();
        super.onDestroy();
    }

}
