package temp.check.app;

import android.app.Application;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;

public class App extends Application {
    public static CH34xUARTDriver driver;
    // 需要将CH34x的驱动类写在APP类下面，使得帮助类的生命周期与整个应用程序的生命周期是相同的

    private static Context context;
    private static boolean isOpen = false;
    private static int baudRate = 1500000;//1500000;       //波特率
    private static byte dataBit = 8;           //数据位
    private static byte stopBit = 1;           //停止位
    private static byte parity = 0;            //校验
    private static byte flowControl = 0;       //流控

    private static boolean readFlag = false;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        if (driver == null) {
            driver = new CH34xUARTDriver(
                    (UsbManager) (getSystemService(Context.USB_SERVICE)), getContext(),
                    "temp.usb.permission");
        }
        open();
        sendHeartBeat();
    }

    private static void sendHeartBeat() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d("App", "run: 000");
                write("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
            }
        }, 0, 1000);
    }

    public static Context getContext() {
        return context;
    }

    public static synchronized void open() {
        int reval = driver.ResumeUsbList();
        if (!isOpen) {
            if (reval == -1) {
                Toast.makeText(getContext(), "打开设备失败", Toast.LENGTH_SHORT).show();
                driver.CloseDevice();
            } else if (reval == 0) {
                if (!driver.UartInit()) {
                    Toast.makeText(getContext(), "设备初始化失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(getContext(), "打开设备成功", Toast.LENGTH_SHORT).show();
                isOpen = true;
                configSerialPort();
            }
        }
    }

    private static void configSerialPort() {
        if (driver.SetConfig(baudRate, dataBit, stopBit, parity, flowControl)) {
            Toast.makeText(getContext(), "串口配置成功", Toast.LENGTH_SHORT).show();
            sendHeartBeat();
        } else {
            Toast.makeText(getContext(), "串口配置失败", Toast.LENGTH_SHORT).show();
        }
    }

    public static void read(OTGCallBack callBack) {
        byte[] buffer = new byte[40960];
        new Thread(() -> {
            while (isOpen && readFlag) {
                int length = driver.ReadData(buffer, buffer.length);
                if (length > 0) {
                    String str;
                    str = byte2HexString(buffer);
                    callBack.testFinish(str);
                    if (str.startsWith("66cc0011")) {
                        String[] datas = str.split("66cc0011");
                        if (datas.length == 2 || datas.length == 1) {
                            String result = null;
                            if (datas[0].length() >= 34)
                                result = datas[0].substring(0, 34);
                            if (datas[1].length() >= 34)
                                result = datas[1].substring(0, 34);

                            if (result != null
                                    && result.endsWith(FrameUtil.getCheckSum(result.substring(0, result.length() - 2)))) {
                                callBack.readFinish(result);
                            } else {
                                Toast.makeText(context, "校验不通过", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            for (String temp : datas) {
                                try {
                                    if (!temp.startsWith("b10103000003")) continue;
                                    if (temp.endsWith(FrameUtil.getCheckSum(temp.substring(0, temp.length() - 2), "0011"))) {
                                        callBack.readFinish(temp);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }
                        }
                    }
                }
            }
        }).start();
    }

    public static synchronized void write(String data) {
        byte[] hexData = stringTobytes(data);
        driver.WriteData(hexData, hexData.length);
        Log.d("MyApp", "WriteData: " + byte2HexString(hexData));
    }

    public static String byte2HexString(byte[] arg) {
        StringBuilder result = new StringBuilder();
        for (byte b : arg) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result.append(hex);//result字符串长度为2的倍数
        }
        return result.toString();
    }

    public static byte[] stringTobytes(String hexString) {
        String stringProcessed = hexString.trim().replaceAll("0x", "");
        stringProcessed = stringProcessed.replaceAll("\\s+", "");
        byte[] data = new byte[stringProcessed.length() / 2];
        int i = 0;
        int j = 0;
        while (i <= stringProcessed.length() - 1) {
            byte character = (byte) Integer.parseInt(stringProcessed.substring(i, i + 2), 16);
            data[j] = character;
            j++;
            i += 2;
        }
        return data;
    }

    public interface OTGCallBack {
        void readFinish(String result);

        void testFinish(String result);
    }

    public static synchronized void close() {
        if (driver.isConnected()) {
            driver.CloseDevice();
        }
    }

    public static void setIsOpen(boolean isOpen1) {
        isOpen = isOpen1;
    }

    public static void setReadFlag(boolean flag) {
        readFlag = flag;
    }
}
