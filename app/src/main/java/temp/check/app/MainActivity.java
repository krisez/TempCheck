package temp.check.app;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Map<String, UsbDevice> usbMap = usbManager.getDeviceList();

    }
}
