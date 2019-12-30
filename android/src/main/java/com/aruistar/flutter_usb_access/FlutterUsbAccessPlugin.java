package com.aruistar.flutter_usb_access;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterUsbAccessPlugin
 */
public class FlutterUsbAccessPlugin implements FlutterPlugin, MethodCallHandler {
    private static Context context;

    private static final int VENDOR_ID = 0x0483;
    private static final int PRODUCT_ID = 0x5778;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int WRITE_TIMEOOUT = 1000;
    private static final int READ_TIMEOOUT = 1000;

    UsbManager usbManager;
    UsbDevice mydevice;
    UsbInterface intf;
    UsbDeviceConnection connection;
    UsbEndpoint epOut, epIn;
    PendingIntent mPermissionIntent;

    public FlutterUsbAccessPlugin() {
        initUsb();
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();

        final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "com.aruistar.flutter_usb_access");
        channel.setMethodCallHandler(new FlutterUsbAccessPlugin());
    }

    public static void registerWith(Registrar registrar) {
        context = registrar.context();

        final MethodChannel channel = new MethodChannel(registrar.messenger(), "com.aruistar.flutter_usb_access");
        channel.setMethodCallHandler(new FlutterUsbAccessPlugin());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "check":
                result.success(check());
                break;
            case "write": {
                List arguments = (List) call.arguments;
                result.success(write((byte[]) arguments.get(0)));
                break;
            }
            case "read":
                result.success(read());
                break;
            case "command": {
                List arguments = (List) call.arguments;
                result.success(command((byte[]) arguments.get(0)));
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }


    private void initUsb() {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            toast("获取USB服务失败。");
            return;
        }

        mPermissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(mUsebReceiver, filter);
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsebReceiver, usbDeviceStateFilter);

        searchUsb();
    }

    private final BroadcastReceiver mUsebReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            debug(action);
            if (action.equals(ACTION_USB_PERMISSION)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        mydevice = device;
                        debug(device.getDeviceName());
                        debug("usb permission granted.");
                        connectUsb();
                    } else {
                        debug("usb permission denied.");
                    }

                }
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                        mydevice = device;
                        toast("USB读卡设备已插入。");
                        searchUsb();
                    }
                }
            } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                        toast("USB读卡设备已拔出。");
                        release();
                    }
                }
            }
        }
    };

    private void searchUsb() {

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<String> keyIterator = deviceList.keySet().iterator();
        debug("usb devices is :");
        while (keyIterator.hasNext()) {
            debug(keyIterator.next());
        }
        debug("usb devices end.");
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            debug(String.format("设备属性：Vid:0x0%x,Pid:0x%x", device.getVendorId(), device.getProductId()));
            if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                debug("Find MF1 Reader");
                mydevice = device;
            }
        }
        if (mydevice == null) {
            toast("未找到USB读卡设备，请检查链接。");
            return;
        }

        if (usbManager.hasPermission(mydevice)) {
            connectUsb();
        } else {
            debug("请给USB读卡设备授权。");
            usbManager.requestPermission(mydevice, mPermissionIntent);
        }

    }

    private void connectUsb() {
        connection = usbManager.openDevice(mydevice);
        debug("usbManager.openDevice ok");
        debug("Interface Count = " + mydevice.getInterfaceCount());

        if (mydevice.getInterfaceCount() == 0) {
            toast("USB 设备链接不正常。");
            return;
        }

        intf = mydevice.getInterface(0);
        connection.claimInterface(intf, true);

        int cnt = intf.getEndpointCount();
        debug("intf.getEndpointCount =" + cnt);
        debug("start query endpoints:");

        for (int index = 0; index < cnt; index++) {
            UsbEndpoint ep = intf.getEndpoint(index);
            debug("ep type is " + ep.getType());
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                    debug("mydevice get EndPoint epOut");
                } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    epIn = ep;
                    debug("mydevice get EndPoint epIn");
                }
            }
        }

        if (check()) {
            toast("USB读卡设备已就绪，可以正常使用。");
        }
    }

    private synchronized byte[] command(byte[] bytes) {
        int cnt = write(bytes);
        if (cnt > 0) {
            return read();
        } else {
            return null;
        }
    }

    private synchronized int write(byte[] bytes) {
        int cnt = connection.bulkTransfer(epOut, bytes, bytes.length, WRITE_TIMEOOUT);
        debug("write cnt is :" + cnt);
        return cnt;
    }

    private synchronized byte[] read() {
        byte[] buffer = new byte[16];
        int cnt = connection.bulkTransfer(epIn, buffer, buffer.length, READ_TIMEOOUT);
        debug("read cnt is :" + cnt);
        if (cnt > 0) {
            debug("read message :" + showResult(buffer));
            return buffer;
        } else {
            return null;
        }
    }

    public String showResult(byte[] buffer) {
        StringBuffer s = new StringBuffer();
        int count = buffer.length;
        for (int i = 0; i < count; i++) {
            if ((buffer[i] <= 0xF) && (buffer[i] >= 0)) {
                s.append(String.format("0x0%x ", buffer[i]));
            } else {
                s.append(String.format("0x%x ", buffer[i]));
            }
        }
        return s.toString();
    }


    private boolean check() {
        return epIn != null && epOut != null;
    }


    private void release() {
        connection.releaseInterface(intf);
        mydevice = null;
        intf = null;
        epOut = null;
        epIn = null;
    }

    private void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    void debug(String msg) {
//        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        Log.e("ttt", msg);
    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }
}
