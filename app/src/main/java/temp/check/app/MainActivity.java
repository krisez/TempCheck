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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView mTips;
    private TextView mTempNo;
    private TextView mTempLight;
    private TextView mInfo;
    private TextView mUsbCan;

    private UsbManager mUsbManager;
    private String USB_PERMISSION = "temp.usb.permission";
    private PendingIntent mPrtPermissionIntent; //获取外设权限的意图
    private UsbSerialPort mUsbSerialPort;
    private UsbDeviceConnection mConnection;
    private List<UsbSerialDriver> mList = new ArrayList<>();
    private SerialInputOutputManager mIOManager;

    static {
        System.loadLibrary("jnidispatch");
        System.loadLibrary("USB2XXX");
        System.loadLibrary("usb");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTips = findViewById(R.id.tv_usb_status);
        mTempNo = findViewById(R.id.tv_temp_no);
        mTempLight = findViewById(R.id.tv_temp_light);
        mInfo = findViewById(R.id.tv_usb_info);
        mUsbCan = findViewById(R.id.tv_usb_can);
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
//        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
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
/*            if(action != null && action.equals(Intent.ACTION_SCREEN_ON)){
                LogUtil.d(action);
            }*/
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
                search();
                usb2Can();
            }
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
                    long value = 0;
                    value += parseFrame(8, 16, data);
                    value += parseFrame(24, 16, data);
                    value += parseFrame(40, 16, data);
                    value += parseFrame(56, 16, data);
                    if (value >= -20 && value <= 80) {
                        sendData(new byte[]{0x1, 0, 0, 0, 0, 0, 0, 0});
                    } else if (value <= 160) {
                        sendData(new byte[]{0x2, 0, 0, 0, 0, 0, 0, 0});
                    } else if (value <= 240) {
                        sendData(new byte[]{0x4, 0, 0, 0, 0, 0, 0, 0});
                    } else if (value <= 320) {
                        sendData(new byte[]{0x8, 0, 0, 0, 0, 0, 0, 0});
                    }
                    String d = ByteUtils.bytesToHexString(data);
                    LogUtil.i(d);
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

    int[] DevHandleArray = new int[20];
    int DevHandle;
    boolean isUsbCanRead = true;

    private void usb2Can() {
        int ret = 0;
        byte CANIndex = 0;
        boolean state;
        //扫描设备
        if (mConnection != null) {
            int fd = mConnection.getFileDescriptor();
            ret = USB_Device.INSTANCE.USB_ScanDevice(DevHandleArray, fd);
        }
        if (ret > 0) {
            DevHandle = DevHandleArray[0];
        } else {
            LogUtil.e("not found device!");
            return;
        }
        //打开设备
        state = USB_Device.INSTANCE.USB_OpenDevice(DevHandle);
        if (!state) {
            mUsbCan.append("Open device error");
            return;
        } else {
            mUsbCan.append("Open device success\n");
        }
        //初始化配置CAN
        USB2CAN.CAN_INIT_CONFIG CANConfig = new USB2CAN.CAN_INIT_CONFIG();
        CANConfig.CAN_Mode = 1;//环回模式
        CANConfig.CAN_ABOM = 0;//禁止自动离线
        CANConfig.CAN_NART = 1;//禁止报文重传
        CANConfig.CAN_RFLM = 0;//FIFO满之后覆盖旧报文
        CANConfig.CAN_TXFP = 1;//发送请求决定发送顺序
        //配置波特率,波特率 = 100M/(BRP*(SJW+BS1+BS2))
        CANConfig.CAN_BRP = 25;
        CANConfig.CAN_BS1 = 2;
        CANConfig.CAN_BS2 = 1;
        CANConfig.CAN_SJW = 1;
        ret = USB2CAN.INSTANCE.CAN_Init(DevHandle, CANIndex, CANConfig);
        if (ret != USB2CAN.CAN_SUCCESS) {
            mUsbCan.append("Config CAN failed!\n");
            return;
        } else {
            mUsbCan.append("Config CAN success!\n");
        }
        //配置过滤器，必须配置，否则可能无法收到数据
        USB2CAN.CAN_FILTER_CONFIG CANFilter = new USB2CAN.CAN_FILTER_CONFIG();
        CANFilter.Enable = 1;
        CANFilter.ExtFrame = 0;
        CANFilter.FilterIndex = 0;
        CANFilter.FilterMode = 0;
        CANFilter.MASK_IDE = 0;
        CANFilter.MASK_RTR = 0;
        CANFilter.MASK_Std_Ext = 0;
        ret = USB2CAN.INSTANCE.CAN_Filter_Init(DevHandle, CANIndex, CANFilter);
        if (ret != USB2CAN.CAN_SUCCESS) {
            mUsbCan.append("Config CAN filter failed!\n");
            return;
        } else {
            mUsbCan.append("Config CAN filter success!\n");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isUsbCanRead) {
                    final USB2CAN.CAN_MSG[] CanMsgBuffer = new USB2CAN.CAN_MSG[10];
                    int CanNum = USB2CAN.INSTANCE.CAN_GetMsg(DevHandle, (byte) 0, CanMsgBuffer);
                    if (CanNum > 0) {
                        for (int i = 0; i < CanNum; i++) {
                            final int finalI = i;
                            long value = 0;
                            value += parseFrame(8, 16, CanMsgBuffer[finalI].Data);
                            value += parseFrame(24, 16, CanMsgBuffer[finalI].Data);
                            value += parseFrame(40, 16, CanMsgBuffer[finalI].Data);
                            value += parseFrame(56, 16, CanMsgBuffer[finalI].Data);
                            if (value >= -20 && value <= 80) {
                                sendCanData(new byte[]{0x1, 0, 0, 0, 0, 0, 0, 0});
                            } else if (value <= 160) {
                                sendCanData(new byte[]{0x2, 0, 0, 0, 0, 0, 0, 0});
                            } else if (value <= 240) {
                                sendCanData(new byte[]{0x4, 0, 0, 0, 0, 0, 0, 0});
                            } else if (value <= 320) {
                                sendCanData(new byte[]{0x8, 0, 0, 0, 0, 0, 0, 0});
                            }
                            mUsbCan.post(new Runnable() {
                                @Override
                                public void run() {
                                    String d = ByteUtils.bytesToHexString(CanMsgBuffer[finalI].Data);
                                    LogUtil.i(d);
                                    mUsbCan.append(d + "\n");
                                }
                            });
                        }
                    }
                }
            }
        });
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
        usb2Can();//usb2can
    }

    private void sendData(byte[] bytes) {
        LogUtil.d("sendData:" + ByteUtils.bytesToHexString(bytes));
        boolean b = false;
        try {
            b = mUsbSerialPort.write(bytes, 100) > 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogUtil.i("send data " + b);
    }

    private void sendCanData(byte[] bytes) {
        USB2CAN.CAN_MSG canMsg = new USB2CAN.CAN_MSG();
        canMsg.ID = 0x180;
        canMsg.Data = bytes;
        USB_Device.INSTANCE.DEV_WriteUserData(DevHandle, 0, canMsg.Data, 8);
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
        isUsbCanRead = false;
        USB_Device.INSTANCE.USB_CloseDevice(DevHandle);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(usbReceiver);
        usbReceiver = null;
        disconnect();
        super.onDestroy();
    }

}
