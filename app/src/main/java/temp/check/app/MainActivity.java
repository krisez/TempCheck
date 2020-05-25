package temp.check.app;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;

public class MainActivity extends AppCompatActivity {

    private TextView mTips;
    private TextView mTempNo;
    private TextView mTempLight;
    private TextView mInfo;
    private TextView mData;

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
        mData = findViewById(R.id.tv_usb_data);
        mTempNo.setText(getString(R.string.temp, 0.0));
        App.driver = new CH34xUARTDriver(
                (UsbManager) getSystemService(Context.USB_SERVICE), this,
                USB_PERMISSION);
        registerBroadcast();
        findViewById(R.id.mock).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MockActivity.class));
            }
        });
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
        intentFilter.addAction(USB_PERMISSION);
        registerReceiver(usbReceiver, intentFilter);//注册receiver


        //通知监听外设权限注册状态
        //PendingIntent：连接外设的intent
        //ask permission
//        mPrtPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(USB_PERMISSION), 0);
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogUtil.d("广播注册");
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            // USB注册动作
/*            if (USB_PERMISSION.equals(action)) {
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
            }*/
            // USB拔插动作
            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                mTips.setText(R.string.step_2);
                LogUtil.d("USB 插入...");
//                search();
                int retval = App.driver.ResumeUsbPermission();
                if (retval == 0) {
                    //Resume usb device list
                    retval = App.driver.ResumeUsbList();
                    if (retval == -1)// ResumeUsbList方法用于枚举CH34X设备以及打开相关设备
                    {
                        mTips.setText(R.string.step_connect_error);
                        App.driver.CloseDevice();
                    } else if (retval == 0) {
                        if (App.driver.mDeviceConnection != null) {
                            if (!App.driver.UartInit()) {//对串口设备进行初始化操作
                                mTips.setText("初始化失败！");
                                return;
                            }
                            mTips.setText(R.string.step_3);
                            mReadThread.start();//开启读线程读取串口接收的数据
                        } else {
                            mTips.setText(R.string.step_connect_error);
                        }
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("未授权限");
                        builder.setMessage("确认退出吗？");
                        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        });
                        builder.setNegativeButton("返回", null);
                        builder.show();
                    }
                }
            }
        }
    };

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            mData.append( msg.obj + "\n");
            return true;
        }
    });

    private Thread mReadThread = new Thread(new Runnable() {
        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            while (true) {
                try {
                    Message msg = new Message();
                    int length = App.driver.ReadData(buffer, 4096);
                    if (length > 0) {
                        double value = 0;
                        for (int i = 0; i < length; i++) {
                            value += buffer[i];
                        }
                        value /= length;
                        msg.obj = ByteUtils.bytesToHexString2(buffer, length).substring(0,2);
                        mHandler.sendMessage(msg);
                        if (value > 0) {
                            if (value <= 60) {
                                LEVEL = 1;
                            } else if (value <= 70) {
                                LEVEL = 2;
                            } else if (value <= 80) {
                                LEVEL = 4;
                            } else {
                                LEVEL = 8;
                            }
                            sendData();
                            final double finalValue = value;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mTempNo.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mTempNo.setText(getString(R.string.temp, finalValue));
                                            if (finalValue <= 60) {
                                                mTempLight.setText(getString(R.string.light_status_green));
                                            } else if (finalValue <= 70) {
                                                mTempLight.setText(getString(R.string.light_status_yellow));
                                            } else if (finalValue <= 80) {
                                                mTempLight.setText(getString(R.string.light_status_red));
                                            } else {
                                                mTempLight.setText(getString(R.string.light_status_white));
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    });

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
                    long value = 0;
                    value += parseFrame(8, 16, data);
                    value += parseFrame(24, 16, data);
                    value += parseFrame(40, 16, data);
                    value += parseFrame(56, 16, data);
                    value /= 4;//取平均值

                    String d = ByteUtils.bytesToHexString(data);
                    LogUtil.i(d);
                    final long finalValue = value;
                    mTempNo.post(new Runnable() {
                        @Override
                        public void run() {
                            mTempNo.setText(getString(R.string.temp, finalValue));
                            if (finalValue <= 60) {
                                mTempLight.setText(getString(R.string.light_status_green));
                            } else if (finalValue <= 70) {
                                mTempLight.setText(getString(R.string.light_status_yellow));
                            } else if (finalValue <= 80) {
                                mTempLight.setText(getString(R.string.light_status_red));
                            } else {
                                mTempLight.setText(getString(R.string.light_status_white));
                            }
                        }
                    });
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

    private int LEVEL;
    private int PRE_LEVEL = 0;

    private void sendData() {
        if (PRE_LEVEL != LEVEL) {
            PRE_LEVEL = LEVEL;
            startTimer();
        }
    }

    private Timer mTimer;

    private void startTimer() {
        if (LEVEL != 8) {
            if (mTimer != null) {
                mTimer.cancel();
            }
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    //闪烁
                    byte[] bytes = new byte[]{0x1};
                    byte[] bytess = new byte[]{0x0};
                    //usb
                    boolean b = false;
                    try {
                        b = App.driver.WriteData(bytes, 1) > 0;
                        b = App.driver.WriteData(bytess, 1) > 0;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    LogUtil.i("send data " + b);
                }
            }, 0, 2000 / LEVEL);
        } else {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            //常亮
            byte[] bytes = new byte[]{0x1};
            //usb
            boolean b = false;
            try {
                b = App.driver.WriteData(bytes, 1) > 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
            LogUtil.i("send data " + b);
        }

    }

    private static long parseFrame(int start, int length, byte[] bytes) {
        int lsbbit = start & 7;
        int lsbbyte = start >> 3;
        int msbbyte = lsbbyte - ((lsbbit + length - 1) >> 3);
        long data = 0;
        for (int i = msbbyte; i < lsbbyte + 1; i++) {
            data += bytes[i] << ((lsbbyte - i) << 3);
        }
        return (data >> lsbbit) & ((1 << length) - 1);
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
        if (mIOManager != null) {
            mIOManager.stop();
            mIOManager = null;
        }
        App.driver.CloseDevice();
        if(mReadThread.isAlive()){
            mReadThread.interrupt();
        }
    }

    @Override
    public void onDestroy() {
//        unregisterReceiver(usbReceiver);
//        usbReceiver = null;
        disconnect();
        super.onDestroy();
    }

}
