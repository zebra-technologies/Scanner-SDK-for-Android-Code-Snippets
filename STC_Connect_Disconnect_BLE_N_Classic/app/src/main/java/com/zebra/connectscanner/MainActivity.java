package com.zebra.connectscanner;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zebra.barcode.sdk.sms.ConfigurationUpdateEvent;
import com.zebra.scannercontrol.BarCodeView;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsScannerEventsOnReLaunch;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements IDcsScannerEventsOnReLaunch,IDcsSdkApiDelegate {
    public static SDKHandler sdkHandler;
    AlertDialog alertDialog;

    ArrayList<String> permissionsList;
    int permissionsCount = 0;
    private FrameLayout barcodeDisplayArea;
    private RelativeLayout stcLayout;
    private Button btnDisconnect;
    private TextView txtBluetoothMode;
    int currentConnectedScannerID;
    BluetoothAdapter mBluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;

    // User can select SSI_BT_LE protocol by uncommenting below line and commenting the SSI_BT_CRADLE_HOST protocol.
    //public static DCSSDKDefs.DCSSDK_BT_PROTOCOL btProtocol = DCSSDKDefs.DCSSDK_BT_PROTOCOL.SSI_BT_LE;
    public static DCSSDKDefs.DCSSDK_BT_PROTOCOL btProtocol = DCSSDKDefs.DCSSDK_BT_PROTOCOL.SSI_BT_CRADLE_HOST;

    private static final String[] BLE_PERMISSIONS = new String[]{
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
    };

    private static final String[] ANDROID_13_BLE_PERMISSIONS = new String[]{
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,

    };

    private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    new ActivityResultCallback<Map<String, Boolean>>() {
                        @RequiresApi(api = Build.VERSION_CODES.M)
                        @Override
                        public void onActivityResult(Map<String,Boolean> result) {
                            ArrayList<Boolean> list = new ArrayList<>(result.values());
                            permissionsList = new ArrayList<>();
                            permissionsCount = 0;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                for (int i = 0; i < list.size(); i++) {
                                    if (shouldShowRequestPermissionRationale(ANDROID_13_BLE_PERMISSIONS[i])) {
                                        permissionsList.add(ANDROID_13_BLE_PERMISSIONS[i]);
                                    } else if (!hasPermission(MainActivity.this, ANDROID_13_BLE_PERMISSIONS[i])) {
                                        permissionsCount++;
                                    }
                                }
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                                for (int i = 0; i < list.size(); i++) {
                                    if (shouldShowRequestPermissionRationale(ANDROID_12_BLE_PERMISSIONS[i])) {
                                        permissionsList.add(ANDROID_12_BLE_PERMISSIONS[i]);
                                    }else if (!hasPermission(MainActivity.this, ANDROID_12_BLE_PERMISSIONS[i])){
                                        permissionsCount++;
                                    }
                                }
                            } else {
                                for (int i = 0; i < list.size(); i++) {
                                    if (shouldShowRequestPermissionRationale(BLE_PERMISSIONS[i])) {
                                        permissionsList.add(BLE_PERMISSIONS[i]);
                                    }else if (!hasPermission(MainActivity.this, BLE_PERMISSIONS[i])){
                                        permissionsCount++;
                                    }
                                }
                            }
                            if (permissionsList.size() > 0) {
                                // Some permissions are denied and can be asked again.
                                askForPermissions(permissionsList);
                            } else if (permissionsCount > 0) {
                                // Show alert dialog.
                                showPermissionDialog();
                            } else {
                                // After all the required permissions are granted, the SDK should be initialized.
                                initializeDcsSdk();
                            }
                        }
                    });

    /**
     * Method to check permissions granted status
     * @param context
     * @param permissionStr
     * @return true if permission granted otherwise false
     */
    private boolean hasPermission(Context context, String permissionStr) {
        return ContextCompat.checkSelfPermission(context, permissionStr) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Method to request required permissions with the permission launcher
     * @param permissionsList list of permissions
     */
    private void askForPermissions(ArrayList<String> permissionsList) {
        String[] newPermissionStr = new String[permissionsList.size()];
        for (int i = 0; i < newPermissionStr.length; i++) {
            newPermissionStr[i] = permissionsList.get(i);
        }
        if (newPermissionStr.length > 0) {
            permissionsLauncher.launch(newPermissionStr);
        } else {
            showPermissionDialog();
        }
    }

    /**
     * Method to show dialog
     * to redirect to application settings if user denies the permissions
     */
    private void showPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission required")
                .setCancelable(false)
                .setMessage("Some permissions need to be allowed for the seamless operation of the App.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    openAppSettings();
                    dialog.dismiss();
                });
        if (alertDialog == null) {
            alertDialog = builder.create();
            if (!alertDialog.isShowing()) {
                alertDialog.show();
            }
        }
    }

    /**
     * Method to open application settings
     * User can grant the permission on the settings page as well
     */
    public void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", MainActivity.this.getPackageName(), null);
        intent.setData(uri);
        MainActivity.this.startActivity(intent);
    }

    /**
     * The method that initializes the SDK
     */

    @SuppressLint("MissingPermission")
    private void initializeDcsSdk() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Device bluetooth enable if it is disabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializing sdk handler.
        if (sdkHandler == null) {
            sdkHandler = new SDKHandler(this, true);
        }

        sdkHandler.dcssdkSetDelegate(this);
        sdkHandler.dcssdkEnableAvailableScannersDetection(true);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_LE);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);

        int notifications_mask = 0;

        // We would like to subscribe to all scanner available/not-available events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;

        // We would like to subscribe to all scanner connection events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;

        // We would like to subscribe to all barcode events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value;

        // Subscribe to events set in notification mask.
        sdkHandler.dcssdkSubsribeForEvents(notifications_mask);

        generatePairingBarcode();
    }

    @SuppressLint({"MissingInflatedId", "MissingPermission"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization of UI components.
        barcodeDisplayArea = (FrameLayout) findViewById(R.id.scan_to_connect_barcode);
        btnDisconnect = (Button) findViewById(R.id.btn_disconnect);
        stcLayout = (RelativeLayout) findViewById(R.id.stc_layout);
        txtBluetoothMode = (TextView) findViewById(R.id.bluetooth_mode);

        // Change visibility on connection state change. Initially display the barcode.
        stcLayout.setVisibility(View.VISIBLE);
        btnDisconnect.setVisibility(View.GONE);

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disconnect scanner on button click.
                disconnectScanner();
            }
        });

        requestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestPermission();
    }

    /**
     * requesting runtime permission
     */
    private void requestPermission(){
        permissionsList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.addAll(Arrays.asList(ANDROID_13_BLE_PERMISSIONS));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsList.addAll(Arrays.asList(ANDROID_12_BLE_PERMISSIONS));
        } else {
            permissionsList.addAll(Arrays.asList(BLE_PERMISSIONS));
        }
        askForPermissions(permissionsList);
    }

    /**
     * Initial method that gets called to display the barcode based on the android version of the device
     */
    private void generatePairingBarcode() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
        BarCodeView barCodeView;

        // Check the selected bluetooth protocol and generate barcode.
        if(btProtocol==DCSSDKDefs.DCSSDK_BT_PROTOCOL.SSI_BT_LE) {
            barCodeView = sdkHandler.dcssdkGetPairingBarcode(btProtocol, DCSSDKDefs.DCSSDK_BT_SCANNER_CONFIG.SET_FACTORY_DEFAULTS);
            if (barCodeView != null) {
                updateBarcodeView(layoutParams, barCodeView);
            }
            txtBluetoothMode.setText("Bluetooth Low Energy Mode");
        } else {
            // SDK was not able to determine Bluetooth MAC. So call the dcssdkGetPairingBarcode with BT Address.
            String btAddress = BluetoothAddressPopup.getInstance().getDeviceBTAddress(MainActivity.this);
            if (btAddress.equals("")) {
                barcodeDisplayArea.removeAllViews();
            } else {
                sdkHandler.dcssdkSetBTAddress(btAddress);
                barCodeView = sdkHandler.dcssdkGetPairingBarcode(btProtocol, DCSSDKDefs.DCSSDK_BT_SCANNER_CONFIG.SET_FACTORY_DEFAULTS, btAddress);
                if (barCodeView != null) {
                    updateBarcodeView(layoutParams, barCodeView);
                }
                txtBluetoothMode.setText("Bluetooth Classic Mode");
            }
        }
    }

    /**
     * Once the correct bluetooth address is received, this method proceed to display the barcode in the given frame layout
     * @param layoutParams
     * @param barCodeView
     */
    private void updateBarcodeView(LinearLayout.LayoutParams layoutParams, BarCodeView barCodeView) {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;

        int orientation = this.getResources().getConfiguration().orientation;
        int x = width * 9 / 10;
        int y = x / 3;

        if (getDeviceScreenSize() > 6) { // TODO: Check 6 is ok or not
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                x = width / 2;
            } else {
                x = width * 2 / 3;
            }
            y = x / 3;
        }
        barCodeView.setSize(x, y);
        barcodeDisplayArea.addView(barCodeView, layoutParams);
    }

    /**
     * get display size to utilize the ui
     * @return double
     */
    private double getDeviceScreenSize() {
        double screenInches = 0;
        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();

        int mWidthPixels;
        int mHeightPixels;

        try {
            Point realSize = new Point();
            Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
            mWidthPixels = realSize.x;
            mHeightPixels = realSize.y;
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            double x = Math.pow(mWidthPixels / dm.xdpi, 2);
            double y = Math.pow(mHeightPixels / dm.ydpi, 2);
            screenInches = Math.sqrt(x + y);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return screenInches;
    }

    /**
     * disconnect scanner
     */
    private void disconnectScanner(){
        sdkHandler.dcssdkTerminateCommunicationSession(currentConnectedScannerID);
    }

    @Override
    public boolean onLastConnectedScannerDetect(BluetoothDevice bluetoothDevice) {
        return false;
    }

    @Override
    public void onConnectingToLastConnectedScanner(BluetoothDevice bluetoothDevice) {

    }

    @Override
    public void onScannerDisconnect() {

    }

    @Override
    public void dcssdkEventScannerAppeared(DCSScannerInfo dcsScannerInfo) {

    }

    @Override
    public void dcssdkEventScannerDisappeared(int i) {

    }

    @Override
    public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo dcsScannerInfo) {
        // Keep scanner id on connection.
        currentConnectedScannerID = dcsScannerInfo.getScannerID();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Change ui on connection.
                stcLayout.setVisibility(View.GONE);
                btnDisconnect.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void dcssdkEventCommunicationSessionTerminated(int i) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stcLayout.setVisibility(View.VISIBLE);
                btnDisconnect.setVisibility(View.GONE);
            }
        });

    }

    @Override
    public void dcssdkEventBarcode(byte[] bytes, int i, int i1) {

    }

    @Override
    public void dcssdkEventImage(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventVideo(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventBinaryData(byte[] bytes, int i) {

    }

    @Override
    public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent firmwareUpdateEvent) {

    }

    @Override
    public void dcssdkEventAuxScannerAppeared(DCSScannerInfo dcsScannerInfo, DCSScannerInfo dcsScannerInfo1) {

    }

    @Override
    public void dcssdkEventConfigurationUpdate(ConfigurationUpdateEvent configurationUpdateEvent) {

    }
}



