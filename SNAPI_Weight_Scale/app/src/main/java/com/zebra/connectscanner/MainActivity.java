package com.zebra.connectscanner;

import static com.zebra.scannercontrol.DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DISABLE_SCALE;
import static com.zebra.scannercontrol.DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_ENABLE_SCALE;
import static com.zebra.scannercontrol.DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_RESET_SCALE;
import static com.zebra.scannercontrol.DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_ZERO_SCALE;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Xml;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.barcode.sdk.sms.ConfigurationUpdateEvent;
import com.zebra.connectscanner.entities.AvailableScanner;
import com.zebra.scannercontrol.BarCodeView;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Launching activity and main user interface of the sample project
 */
public class MainActivity extends AppCompatActivity implements IDcsSdkApiDelegate {
    public static SDKHandler sdkHandler;
    private FrameLayout barcodeDisplayArea;
    private RelativeLayout snapiLayout;
    private LinearLayout scaleLayout;
    private Switch scaleEnableSwitch, liveWeightEnableSwitch;
    private Button readWeightButton, zeroScaleButton, resetScaleButton;
    private TextView textViewNoScale;
    private LinearLayout linearLayoutScale;

    int currentConnectedScannerID;
    static AvailableScanner curAvailableScanner = null;

    static ConnectAsyncTask cmdExecTask = null;
    public static CustomProgressDialog progressDialog;
    private static ArrayList<DCSScannerInfo> mSNAPIList = new ArrayList<DCSScannerInfo>();
    private static ArrayList<DCSScannerInfo> mScannerInfoList;
    private boolean liveWeightEnable;
    public boolean isScaleAvailable = false;

    private int STATUS_SCALE_NOT_ENABLED = 0;
    private int STATUS_SCALE_NOT_READY = 1;
    private int STATUS_STABLE_WEIGHT_OVER_LIMIT = 2;
    private int STATUS_STABLE_WEIGHT_UNDER_ZERO = 3;
    private int STATUS_NON_STABLE_WEIGHT = 4;
    private int STATUS_STABLE_ZERO_WEIGHT = 5;
    private int STATUS_STABLE_NON_ZERO_WEIGHT = 6;

    // Xml tags.
    public static final String XMLTAG_SCANNER_ID = "<scannerID>";
    public static final String XMLTAG_ARGXML = "<inArgs>";
    // Error Messages.
    public static final String INVALID_SCANNER_ID_MSG = "Invalid Scanner ID";
    // Weight status.
    public static final String WEIGHT_XML_ELEMENT = "weight";
    public static final String WEIGHT_MODE_XML_ELEMENT = "weight_mode";
    public static final String WEIGHT_STATUS_XML_ELEMENT = "status";
    public static final String SCALE_STATUS_SCALE_NOT_ENABLED = "Scale Not Enabled";
    public static final String SCALE_STATUS_SCALE_NOT_READY = "Scale Not Ready";
    public static final String SCALE_STATUS_STABLE_WEIGHT_OVER_LIMIT = "Stable Weight OverLimit";
    public static final String SCALE_STATUS_STABLE_WEIGHT_UNDER_ZERO = "Stable Weight Under Zero";
    public static final String SCALE_STATUS_NON_STABLE_WEIGHT = "Non Stable Weight";
    public static final String SCALE_STATUS_STABLE_ZERO_WEIGHT = "Stable Zero Weight";
    public static final String SCALE_STATUS_STABLE_NON_ZERO_WEIGHT = "Stable NonZero Weight";


    /**
     * The method that initializes the SDK
     */
    private void initializeDcsSdk() {
        // Initializing sdk handler.
        if (sdkHandler == null) {
            sdkHandler = new SDKHandler(this, true);
        }

        sdkHandler.dcssdkSetDelegate(this);
        sdkHandler.dcssdkEnableAvailableScannersDetection(true);

        // SNAPI mode.
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
                        scaleLayout.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                // Try to connect available scanner.
                cmdExecTask = new ConnectAsyncTask(mSNAPIList.get(0));
                cmdExecTask.execute();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialization of UI components.
        barcodeDisplayArea = (FrameLayout) findViewById(R.id.scan_to_connect_barcode);
        snapiLayout = (RelativeLayout) findViewById(R.id.snapi_layout);
        scaleLayout = (LinearLayout) findViewById(R.id.scale_layout);

        // Change visibility on connection state change. Initially display the barcode.
        snapiLayout.setVisibility(View.VISIBLE);
        scaleLayout.setVisibility(View.GONE);

        readWeightButton = findViewById(R.id.read_weight_button);
        zeroScaleButton = findViewById(R.id.zero_scale_button);
        resetScaleButton = findViewById(R.id.reset_scale_button);
        liveWeightEnableSwitch = findViewById(R.id.live_weight_enable_switch);
        scaleEnableSwitch = findViewById(R.id.scale_enable_switch);
        textViewNoScale = (TextView) findViewById(R.id.txt_no_scale);
        linearLayoutScale = (LinearLayout) findViewById(R.id.layout_scale);
        scaleEnableSwitch.setChecked(true);

        liveWeightEnableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (liveWeightEnableSwitch.isChecked()) {
                readWeightButton.setEnabled(false);
                liveWeightEnable = true;
                liveWeight();
            } else {
                readWeightButton.setEnabled(true);
                liveWeightEnable = false;
            }
        });

        scaleEnableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (scaleEnableSwitch.isChecked()) {
                enableScale();
                readWeightButton.setEnabled(true);
                zeroScaleButton.setEnabled(true);
                resetScaleButton.setEnabled(true);
                liveWeightEnableSwitch.setEnabled(true);

            } else {
                disableScale();
                liveWeightEnableSwitch.setEnabled(false);
                readWeightButton.setEnabled(false);
                zeroScaleButton.setEnabled(false);
                resetScaleButton.setEnabled(false);
            }
        });

        initializeDcsSdk();

    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeDcsSdk();

        liveWeightEnable = false;
        if (isScaleAvailable) {
            linearLayoutScale.setVisibility(View.VISIBLE);
            textViewNoScale.setVisibility(View.INVISIBLE);
            textViewNoScale.setVisibility(View.GONE);


        } else {
            textViewNoScale.setVisibility(View.VISIBLE);
            linearLayoutScale.setVisibility(View.INVISIBLE);
            linearLayoutScale.setVisibility(View.GONE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getSnapiBarcode();
    }

    /**
     * Initial method that gets called to display the barcode.
     * once scan the barcode Scanner change the protocol to SNAPI mode.
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
     *
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

    public void liveWeight() {
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID + currentConnectedScannerID +XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            new ExecuteRSMAsyncLiveWeight(currentConnectedScannerID, DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT).execute(new String[]{in_xml});
        } else {
            Toast.makeText(this, INVALID_SCANNER_ID_MSG, Toast.LENGTH_SHORT).show();
        }
    }

    public void readWeight(View view) {
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID  + currentConnectedScannerID +XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            new ExecuteRSMAsync(currentConnectedScannerID, DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT).execute(new String[]{in_xml});
        } else {
            Toast.makeText(this, INVALID_SCANNER_ID_MSG, Toast.LENGTH_SHORT).show();
        }
    }

    public void zeroScale(View view) {
        StringBuilder sb = new StringBuilder();
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID  + currentConnectedScannerID + XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDK_ZERO_SCALE, in_xml, sb, currentConnectedScannerID);
        }
    }

    public void resetScale(View view) {
        StringBuilder sb = new StringBuilder();
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID  + currentConnectedScannerID + XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDK_RESET_SCALE, in_xml, sb, currentConnectedScannerID);
        }
    }

    public void enableScale() {
        clearText();
        ((TextView) findViewById(R.id.txtWeightMeasured)).setText("");
        StringBuilder sb = new StringBuilder();
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID  + currentConnectedScannerID + XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDK_ENABLE_SCALE, in_xml, sb, currentConnectedScannerID);
        }
    }

    public void disableScale() {
        clearText();
        StringBuilder sb = new StringBuilder();
        if (currentConnectedScannerID > 0) {
            String in_xml = XMLTAG_ARGXML+XMLTAG_SCANNER_ID  + currentConnectedScannerID + XMLTAG_SCANNER_ID+ XMLTAG_ARGXML;
            sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(DCSSDK_DISABLE_SCALE, in_xml, sb, currentConnectedScannerID);
        }
    }

    public void clearText() {
        ((TextView) findViewById(R.id.txtWeightMeasured)).setText("");
        ((TextView) findViewById(R.id.txtWeightUnit)).setText("");
        ((TextView) findViewById(R.id.txtWeightStatus)).setText("");
    }

    private class ExecuteRSMAsync extends AsyncTask<String, Integer, Boolean> {
        private int scannerId = 0;
        private CustomProgressDialog progressDialog;
        DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode;

        public ExecuteRSMAsync(int scannerId, DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode) {
            this.scannerId = scannerId;
            this.opcode = opcode;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new CustomProgressDialog(MainActivity.this, "Execute Command...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            StringBuilder sbOutXml = new StringBuilder();
            boolean result = executeCommand(opcode, strings[0], sbOutXml, scannerId);
            if (opcode == DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT && result) {

                try {

                    XmlPullParser parserForOutXml = Xml.newPullParser();

                    parserForOutXml.setInput(new StringReader(sbOutXml.toString()));
                    int event = parserForOutXml.getEventType();
                    String outXmlAttribute = null;
                    while (event != XmlPullParser.END_DOCUMENT) {
                        String outXmlElement = parserForOutXml.getName();
                        switch (event) {
                            case XmlPullParser.START_TAG:
                                break;
                            case XmlPullParser.TEXT:
                                outXmlAttribute = parserForOutXml.getText();
                                break;

                            case XmlPullParser.END_TAG:

                                if (outXmlAttribute != null) {

                                    if (outXmlElement.equals(WEIGHT_XML_ELEMENT)) {
                                        final String weight = outXmlAttribute.trim();
                                        runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightMeasured)).setText(weight));

                                    } else if (outXmlElement.equals(WEIGHT_MODE_XML_ELEMENT)) {
                                        final String weightMode = outXmlAttribute.trim();
                                        runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightUnit)).setText(weightMode));

                                    } else if (outXmlElement.equals(WEIGHT_STATUS_XML_ELEMENT)) {
                                        int status = Integer.parseInt(outXmlAttribute.trim());
                                        String scaleStatus = null;
                                        if (status == STATUS_SCALE_NOT_ENABLED) {
                                            scaleStatus = SCALE_STATUS_SCALE_NOT_ENABLED;
                                        } else if (status == STATUS_SCALE_NOT_READY) {
                                            scaleStatus = SCALE_STATUS_SCALE_NOT_READY ;
                                        } else if (status == STATUS_STABLE_WEIGHT_OVER_LIMIT) {
                                            scaleStatus = SCALE_STATUS_STABLE_WEIGHT_OVER_LIMIT ;
                                        } else if (status == STATUS_STABLE_WEIGHT_UNDER_ZERO) {
                                            scaleStatus = SCALE_STATUS_STABLE_WEIGHT_UNDER_ZERO;
                                        } else if (status == STATUS_NON_STABLE_WEIGHT) {
                                            scaleStatus = SCALE_STATUS_NON_STABLE_WEIGHT;
                                        } else if (status == STATUS_STABLE_ZERO_WEIGHT) {
                                            scaleStatus = SCALE_STATUS_STABLE_ZERO_WEIGHT;
                                        } else if (status == STATUS_STABLE_NON_ZERO_WEIGHT) {
                                            scaleStatus = SCALE_STATUS_STABLE_NON_ZERO_WEIGHT;
                                        }

                                        final String scale = scaleStatus;
                                        runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightStatus)).setText(scale));

                                    }

                                }
                                break;
                        }
                        event = parserForOutXml.next();
                    }
                } catch (Exception e) {

                }

            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean b) {
            super.onPostExecute(b);
            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();
        }
    }

    private class ExecuteRSMAsyncLiveWeight extends AsyncTask<String, Integer, Boolean> {
        private int scannerId = 0;
        DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode;

        public ExecuteRSMAsyncLiveWeight(int scannerId, DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode) {
            this.scannerId = scannerId;
            this.opcode = opcode;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... strings) {

            boolean result;
            do {
                StringBuilder sbOutXml = new StringBuilder();
                result = executeCommand(opcode, strings[0], sbOutXml, scannerId);
                if (opcode == DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT && result) {

                    try {

                        XmlPullParser parserForOutXml = Xml.newPullParser();
                        parserForOutXml.setInput(new StringReader(sbOutXml.toString()));
                        int event = parserForOutXml.getEventType();
                        String outXmlAttribute = null;
                        while (event != XmlPullParser.END_DOCUMENT) {
                            String outXmlElement = parserForOutXml.getName();
                            switch (event) {
                                case XmlPullParser.START_TAG:
                                    break;
                                case XmlPullParser.TEXT:
                                    outXmlAttribute = parserForOutXml.getText();
                                    break;

                                case XmlPullParser.END_TAG:

                                    if (outXmlAttribute != null) {

                                        if (outXmlElement.equals(WEIGHT_XML_ELEMENT)) {
                                            final String weight = outXmlAttribute.trim();
                                            runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightMeasured)).setText(weight));

                                        } else if (outXmlElement.equals(WEIGHT_MODE_XML_ELEMENT)) {
                                            final String weightMode = outXmlAttribute.trim();
                                            runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightUnit)).setText(weightMode));

                                        } else if (outXmlElement.equals("status")) {
                                            int status = Integer.parseInt(outXmlAttribute.trim());
                                            String scaleStatus = null;
                                            if (status == STATUS_SCALE_NOT_ENABLED) {
                                                scaleStatus = SCALE_STATUS_SCALE_NOT_ENABLED;
                                            } else if (status == STATUS_SCALE_NOT_READY) {
                                                scaleStatus = SCALE_STATUS_SCALE_NOT_READY ;
                                            } else if (status == STATUS_STABLE_WEIGHT_OVER_LIMIT) {
                                                scaleStatus = SCALE_STATUS_STABLE_WEIGHT_OVER_LIMIT ;
                                            } else if (status == STATUS_STABLE_WEIGHT_UNDER_ZERO) {
                                                scaleStatus = SCALE_STATUS_STABLE_WEIGHT_UNDER_ZERO;
                                            } else if (status == STATUS_NON_STABLE_WEIGHT) {
                                                scaleStatus = SCALE_STATUS_NON_STABLE_WEIGHT;
                                            } else if (status == STATUS_STABLE_ZERO_WEIGHT) {
                                                scaleStatus = SCALE_STATUS_STABLE_ZERO_WEIGHT;
                                            } else if (status == STATUS_STABLE_NON_ZERO_WEIGHT) {
                                                scaleStatus = SCALE_STATUS_STABLE_NON_ZERO_WEIGHT;
                                            }

                                            final String scale = scaleStatus;
                                            runOnUiThread(() -> ((TextView) findViewById(R.id.txtWeightStatus)).setText(scale));
                                        }
                                    }
                                    break;
                            }
                            event = parserForOutXml.next();
                        }
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();

                    } catch (IOException | XmlPullParserException ignored){

                    }
                }
            } while (liveWeightEnable);
            return result;
        }

        @Override
        protected void onPostExecute(Boolean b) {
            super.onPostExecute(b);
        }
    }

    public boolean executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE opCode, String inXML, StringBuilder outXML, int scannerID) {
        if (sdkHandler != null) {
            if (outXML == null) {
                outXML = new StringBuilder();
            }
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML, scannerID);
            if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS)
                return true;
            else if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_FAILURE)
                return false;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        liveWeightEnable = false;
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

        // Check weight scale availability on scanner.
        String in_xml = "<inArgs><scannerID>" + currentConnectedScannerID + "</scannerID></inArgs>";
        new AsyncTaskScaleAvailable(currentConnectedScannerID, DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT, MainActivity.this).execute(in_xml);

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
                scaleLayout.setVisibility(View.GONE);

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
     * Add scanner to scanner list
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
     * Asynchronously connect to the scanner
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
        protected void onPostExecute(Boolean isConneced) {
            super.onPostExecute(isConneced);
            if (!isFinishing()) {
                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
            }

            //not connect
            if (!isConneced) {
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
     * Custom Dialog to be shown when sending commands to the RFID Reader
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

    /**
     * scale availability check
     */
    private class AsyncTaskScaleAvailable extends AsyncTask<String, Integer, Boolean> {
        int scannerId;
        Context context;
        private CustomProgressDialog progressDialog;
        DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode;

        public AsyncTaskScaleAvailable(int scannerId, DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode, Context context) {
            this.scannerId = scannerId;
            this.opcode = opcode;
            this.context = context;

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            StringBuilder sb = new StringBuilder();
            boolean result = executeCommand(opcode, strings[0], sb, scannerId);
            if (opcode == DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_READ_WEIGHT) {
                if (result) {
                    return true;
                }
            }
            return false;
        }


        @Override
        protected void onPostExecute(Boolean scaleAvailability) {
            super.onPostExecute(scaleAvailability);
            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

           isScaleAvailable = scaleAvailability;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // change ui on connection
                    snapiLayout.setVisibility(View.GONE);
                    scaleLayout.setVisibility(View.VISIBLE);
                    if (isScaleAvailable) {
                        linearLayoutScale.setVisibility(View.VISIBLE);
                        textViewNoScale.setVisibility(View.INVISIBLE);
                        textViewNoScale.setVisibility(View.GONE);


                    } else {
                        textViewNoScale.setVisibility(View.VISIBLE);
                        linearLayoutScale.setVisibility(View.INVISIBLE);
                        linearLayoutScale.setVisibility(View.GONE);
                    }
                }
            });
        }
    }
}



