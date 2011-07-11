
package org.zakky.remocon;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.util.Map;
import java.util.Map.Entry;

public class InfoActivity extends Activity {

    private static final String TAG = "RemoCon";

    private static final String ACTION_USB_PERMISSION = InfoActivity.class.getPackage().getName()
            + ".USB_PERMISSION";

    private UsbManager mManager;

    private UsbDevice mTargetDevice;

    private final Handler mHandler = new Handler();

    private Thread mPollingThread;

    private TextView mKeycodeView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info);

        mKeycodeView = (TextView) findViewById(R.id.keycode);

        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Intent intent = getIntent();
        if (intent != null
                && intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) instanceof UsbDevice) {
            mTargetDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            mPollingThread = new Thread(new Poller());
            mPollingThread.start();
            return;
        }

        final Map<String, UsbDevice> deviceList = mManager.getDeviceList();
        if (deviceList.isEmpty()) {
            return;
        }
        final Entry<String, UsbDevice> device = deviceList.entrySet().iterator().next();

        int vendorId = device.getValue().getVendorId();
        int productId = device.getValue().getProductId();
        if (vendorId == 1211 && productId == 3854) {
            mTargetDevice = device.getValue();
        } else {
            mTargetDevice = null;
            return;
        }

        if (mManager.hasPermission(mTargetDevice)) {
            mPollingThread = new Thread(new Poller());
            mPollingThread.start();
        }

        final PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        mManager.requestPermission(mTargetDevice, pi);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPollingThread != null) {
            mPollingThread.interrupt();
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    final UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.i(TAG, "USB Permission denied");
                        InfoActivity.this.setResult(Activity.RESULT_CANCELED);
                        InfoActivity.this.finish();
                        return;
                    }

                    if (device == null) {
                        Log.i(TAG, "USB device is null in BroadcastReceiver");
                        InfoActivity.this.setResult(Activity.RESULT_CANCELED);
                        InfoActivity.this.finish();
                        return;
                    }
                    mTargetDevice = device;

                    mPollingThread = new Thread(new Poller());
                    mPollingThread.start();
                }
            }
        }
    };

    private final class Poller implements Runnable {

        @Override
        public void run() {
            final UsbDevice device = mTargetDevice;
            if (device == null) {
                return;
            }

            final UsbDeviceConnection conn = mManager.openDevice(mTargetDevice);
            UsbInterface interface0 = mTargetDevice.getInterface(0);
            conn.claimInterface(interface0, true);

            final UsbEndpoint endpoint = interface0.getEndpoint(0);
            final byte[] buffer = new byte[endpoint.getMaxPacketSize()];

            while (true) {
                if (Thread.interrupted()) {
                    return;
                }
                final int read = conn.bulkTransfer(endpoint, buffer, buffer.length, 10000);
                if (read < 0) {
                    continue;
                }

                //final String data = "len=" + read + ", data=" + Arrays.toString(buffer);
                // Log.i(TAG, data);
                if (read != 8) {
                    continue;
                }
                final int keycode = (buffer[5] & 0xFF);
                if (keycode < KEY_NAMES.length) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final String keyName = KEY_NAMES[keycode];
                            mKeycodeView.setText(keyName);
                        }
                    });
                }
            }
        }
    }

    private static final String[] KEY_NAMES = {
            "", //
            "Power", "unknown", "TV", "TVPlay", "Library", //
            "Program", "Data", "DisplaySize", "Voice", "Mute", // 10
            "Blue", "Red", "Green", "Yellow", "1", //
            "2", "3", "4", "5", "6", // 20
            "7", "8", "9", "10/*", "11/0", //
            "12/#", "Date", "Up", "DisplayMode", "Left", // 30
            "Enter", "Right", "OnAir", "Down", "Cancel", //
            "VolumeUp", "Menu", "ChannelUp", "VolumeDown", "Play", // 40
            "ChannelDown", "Rewind", "Pause", "Forward", "Previous", //
            "Stop", "Next", "Rec", "Subtitle", "Interactive", // 50
            "Skip"
    };
}
