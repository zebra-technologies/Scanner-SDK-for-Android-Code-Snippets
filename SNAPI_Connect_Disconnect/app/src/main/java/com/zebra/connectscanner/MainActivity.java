package com.zebra.connectscanner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.zebra.barcode.sdk.sms.ConfigurationUpdateEvent;
import com.zebra.connectscanner.entities.AvailableScanner;
import com.zebra.connectscanner.entities.Barcode;
import com.zebra.connectscanner.ui.adapters.BarcodeListAdapter;
import com.zebra.scannercontrol.BarCodeView;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;

/**
 * Launching activity and main user interface of the sample project
 */
public class MainActivity extends AppCompatActivity implements IDcsSdkApiDelegate {
    public static SDKHandler sdkHandler;

    private FrameLayout barcodeDisplayArea;
    private RelativeLayout snapiLayout;
    private RelativeLayout brcodeLayout;
    RecyclerView recyclerView;

    int currentConnectedScannerID;
    static AvailableScanner curAvailableScanner = null;

    private static ArrayList<DCSScannerInfo> mSNAPIList = new ArrayList<DCSScannerInfo>();
    private static ArrayList<DCSScannerInfo> mScannerInfoList;

    ArrayList<Barcode> listScannedBarcodes;
    BarcodeListAdapter barcodeListAdapter;

    static ConnectAsyncTask cmdExecTask = null;

    public static CustomProgressDialog progressDialog;


    /**
     * The method that initializes the SDK
     */
    @SuppressLint("MissingPermission")
    private void initializeDcsSdk() {
        // Initializing sdk handler.
        if (sdkHandler == null) {
            sdkHandler = new SDKHandler(this, true);
        }

        sdkHandler.dcssdkSetDelegate(this);
        sdkHandler.dcssdkEnableAvailableScannersDetection(true);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_SNAPI);

        int notifications_mask = 0;

        // We would like to subscribe to all scanner available/not-available events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;

        // We would like to subscribe to all scanner connection events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;

        // We would like to subscribe to all barcode events.
        notifications_mask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value;

        // Subscribe to events set in notification mask.
        sdkHandler.dcssdkSubsribeForEvents(notifications_mask);

        // Connect to scanner.
        connectToScanner();

    }

    /**
     * Get available scanner and connect.
     */
    private void connectToScanner(){
        mScannerInfoList = new ArrayList<>();
        mSNAPIList.clear();
        updateScannersList();
        for (DCSScannerInfo device : mScannerInfoList) {
            if (device.getConnectionType() == DCSSDKDefs.DCSSDK_CONN_TYPES.DCSSDK_CONNTYPE_USB_SNAPI) {
                mSNAPIList.add(device);
            }
        }

        if (mSNAPIList.isEmpty()) {
            // No SNAPI Scanners.
            getSnapiBarcode();
        } else if (mSNAPIList.size() <= 1) {
            // Only one SNAPI scanner available.
            if (mSNAPIList.get(0).isActive()) {
                // Available scanner is active. Navigate to active scanner.
                currentConnectedScannerID = mSNAPIList.get(0).getScannerID();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Change ui on connection.
                        snapiLayout.setVisibility(View.GONE);
                        brcodeLayout.setVisibility(View.VISIBLE);
                    }
                });
                // Initializing barcode list, ui adapter.
                // Set adapter to recycle view to display scanned barcode.
                listScannedBarcodes = new ArrayList<>();
                barcodeListAdapter = new BarcodeListAdapter(listScannedBarcodes);
                recyclerView.setAdapter(barcodeListAdapter);
            } else {
                // Try to connect available scanner.
                cmdExecTask = new ConnectAsyncTask(mSNAPIList.get(0));
                cmdExecTask.execute();
            }
        }
    }

    @SuppressLint({"MissingInflatedId", "MissingPermission"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization of UI components.
        barcodeDisplayArea = (FrameLayout) findViewById(R.id.scan_to_connect_barcode);
        snapiLayout = (RelativeLayout) findViewById(R.id.snapi_layout);
        brcodeLayout = (RelativeLayout) findViewById(R.id.barcode_layout);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Change visibility on connection state change. Initially display the barcode.
        snapiLayout.setVisibility(View.VISIBLE);
        brcodeLayout.setVisibility(View.GONE);

        initializeDcsSdk();

    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeDcsSdk();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getSnapiBarcode();
    }

    /**
     * Initial method that gets called to display the barcode.
     * Once scan the barcode Scanner change the protocol to SNAPI mode.
     */
    private void getSnapiBarcode() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
        BarCodeView barCodeView = sdkHandler.dcssdkGetUSBSNAPIWithImagingBarcode();
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
                y = x / 3;
            } else {
                x = width * 2 / 3;
                y = x / 3;
            }
        }
        barCodeView.setSize(x, y);
        barcodeDisplayArea.addView(barCodeView, layoutParams);
    }

    /**
     * Get display size to utilize the ui.
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
     * "Device Arrival" notification informs about appearance of a particular available scanner.
     * @param dcsScannerInfo Object representing an appeared available scanner.
     */
    @Override
    public void dcssdkEventScannerAppeared(DCSScannerInfo dcsScannerInfo) {

    }

    /**
     * "Device Disappeared" notification informs about disappearance of a particular available scanner.
     * @param scannerID Unique identifier of a disappeared available scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventScannerDisappeared(int scannerID) {

    }

    /**
     * "Session Established" notification informs about appearance of a particular active scanner.
     * @param dcsScannerInfo Object representing an appeared active scanner.
     */
    @Override
    public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo dcsScannerInfo) {
        // Keep scanner id on connection.
        currentConnectedScannerID = dcsScannerInfo.getScannerID();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Change ui on connection.
                snapiLayout.setVisibility(View.GONE);
                brcodeLayout.setVisibility(View.VISIBLE);
            }
        });
        // Initializing barcode list, ui adapter.
        // Set adapter to recycle view to display scanned barcode.
        listScannedBarcodes = new ArrayList<>();
        barcodeListAdapter = new BarcodeListAdapter(listScannedBarcodes);
        recyclerView.setAdapter(barcodeListAdapter);
    }

    /**
     * "Session Terminated" notification informs about disappearance of a particular active scanner
     * @param scannerID Unique identifier of a disappeared active scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventCommunicationSessionTerminated(int scannerID) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Change ui on disconnection.
                snapiLayout.setVisibility(View.VISIBLE);
                getSnapiBarcode();
                brcodeLayout.setVisibility(View.GONE);
            }
        });
    }

    /**
     * "Barcode Event" notification informs about reception of a particular barcode of a particular type from a particular active scanner.
     * @param barcodeData Object representing ASCII data of scanned barcode.
     * @param barcodeType Barcode type of the scanned barcode. Values of bar code data types are available in Table 3-6
     *                      of the Motorola Scanner SDK For Windows Developer’s Guide (72E-149784-02).
     * @param fromScannerID Unique identifier of a particular active scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventBarcode(byte[] barcodeData, int barcodeType, int fromScannerID) {
        // Create a barcode object onBarcodeScan event and add it barcode to list.
        Barcode barcode = new Barcode(barcodeData,barcodeType,fromScannerID);
        listScannedBarcodes.add(barcode);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Refreshing recycle view.
                barcodeListAdapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * "Image Event" notification is triggered when an active imaging scanner captures images in image mode.
     * @param imageData Object representing raw data of the received image.
     * @param fromScannerID Unique identifier of a particular active scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventImage(byte[] imageData, int fromScannerID) {

    }

    /**
     * "Video Event" notification is triggered when an active imaging scanner captures video in video mode
     * @param videoFrame Object representing raw data of the received video frame.
     * @param fromScannerID Unique identifier of a particular active scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventVideo(byte[] videoFrame, int fromScannerID) {

    }

    /**
     * "Binary Data Event" notification is triggered when an active imaging scanner captures Intelligent Document Capture(IDC) data in IDC Operating Mode.
     * @param binaryData Object representing raw data of the received Intelligent Document Capture(IDC) data.
     * @param fromScannerID Unique identifier of a particular active scanner assigned by SDK.
     */
    @Override
    public void dcssdkEventBinaryData(byte[] binaryData, int fromScannerID) {

    }

    /**
     * "Firmware Update Event" notification informs about status in firmware update process
     * @param firmwareUpdateEvent
     */
    @Override
    public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent firmwareUpdateEvent) {

    }

    /**
     * Notification to inform that new Aux scanner has been appeared
     * @param newTopology   Device tree that change has occurred
     * @param auxScanner    New Aux scanner
     */
    @Override
    public void dcssdkEventAuxScannerAppeared(DCSScannerInfo newTopology, DCSScannerInfo auxScanner) {

    }

    /**
     * "Configuration Push Event" notification informs about status in configuration update process
     * @param configurationUpdateEvent
     */
    @Override
    public void dcssdkEventConfigurationUpdate(ConfigurationUpdateEvent configurationUpdateEvent) {

    }

    /**
     * update scanner list with Available and Active scanners
     */
    private void updateScannersList() {
        if (sdkHandler != null) {
            mScannerInfoList.clear();
            ArrayList<DCSScannerInfo> scannerTreeList = new ArrayList<DCSScannerInfo>();
            sdkHandler.dcssdkGetAvailableScannersList(scannerTreeList);
            sdkHandler.dcssdkGetActiveScannersList(scannerTreeList);
            for (DCSScannerInfo s : scannerTreeList) {
                addToScannerList(s);
            }
        }
    }

    /**
     * Add scanner to scanner list.
     * @param dcsScannerInfo
     */
    private void addToScannerList(DCSScannerInfo dcsScannerInfo) {
        mScannerInfoList.add(dcsScannerInfo);
        if (dcsScannerInfo.getAuxiliaryScanners() != null) {
            for (DCSScannerInfo aux :
                    dcsScannerInfo.getAuxiliaryScanners().values()) {
                addToScannerList(aux);
            }
        }
    }

    /**
     * Asynchronously connect to the scanner.
     */
    private class ConnectAsyncTask extends AsyncTask<Void, DCSScannerInfo, Boolean> {
        private DCSScannerInfo scanner;

        public ConnectAsyncTask(DCSScannerInfo scn) {
            this.scanner = scn;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!isFinishing()) {
                progressDialog = new CustomProgressDialog(MainActivity.this, "Connecting To scanner. Please Wait...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            }
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            DCSSDKDefs.DCSSDK_RESULT result = connect(scanner.getScannerID());
            if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isConnected) {
            super.onPostExecute(isConnected);
            if (!isFinishing()) {
                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
            }

            // Not connected.
            if (!isConnected) {
                Toast.makeText(getApplicationContext(), "Unable to communicate with scanner", Toast.LENGTH_SHORT).show();
                getSnapiBarcode();
            }
        }
    }

    public DCSSDKDefs.DCSSDK_RESULT connect(int scannerId) {
        if (sdkHandler != null) {
            if (curAvailableScanner != null) {
                sdkHandler.dcssdkTerminateCommunicationSession(curAvailableScanner.getScannerId());
            }
            return sdkHandler.dcssdkEstablishCommunicationSession(scannerId);
        } else {
            return DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_FAILURE;
        }
    }

    /**
     * Custom Dialog to be shown when sending commands to the RFID Reader.
     */
    public class CustomProgressDialog extends ProgressDialog {
        private static final String MESSAGE = "Saving Settings....";

        /**
         * Constructor to handle the initialization
         *
         * @param context - Context to be used
         */
        public CustomProgressDialog(Context context, String message) {
            super(context, ProgressDialog.STYLE_SPINNER);
            setTitle(null);
            if (message != null)
                setMessage(message);
            else
                setMessage(MESSAGE);
            setCancelable(true);
        }
    }

}



