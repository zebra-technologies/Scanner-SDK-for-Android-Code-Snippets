package com.zebra.connectscanner;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zebra.scannercontrol.DCSSDKDefs;

/**
 * BluetoothAddressPopup class use for separate ui part of bluetooth address providing popup on bluetooth classic mode
 * this class in not mandatory to implement the connection on classic mode connectivity.
 * User needs to be provide device bluetooth address as same as return value of getDeviceBTAddress() method.
 */
public class BluetoothAddressPopup {

    public static BluetoothAddressPopup bluetoothAddressPopup;
    Dialog dialogBTAddress;
    static String userEnteredBluetoothAddress;
    public static final String BLUETOOTH_ADDRESS_VALIDATOR = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
    private static final int MAX_ALPHANUMERIC_CHARACTERS = 12;
    private static final int MAX_BLUETOOTH_ADDRESS_CHARACTERS = 17;
    private static final String DEFAULT_EMPTY_STRING = "";
    private static final String COLON_CHARACTER = ":";
    Context context;

    public static BluetoothAddressPopup getInstance(){
        if(bluetoothAddressPopup == null){
            bluetoothAddressPopup = new BluetoothAddressPopup();
        }
        return bluetoothAddressPopup;
    }

    /**
     * Ui popup to enter bluetooth address
     * @param context
     * @return String - device bluetooth address
     */
    public String getDeviceBTAddress(Context context) {
        this.context = context;
        SharedPreferences prefs = context.getSharedPreferences("ConnectScanner",0);
        String bluetoothMAC = prefs.getString("PREF_BT_ADDRESS", "");
        if (bluetoothMAC.equals("")) {
            if (dialogBTAddress == null) {
                dialogBTAddress = new Dialog(context);
                dialogBTAddress.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialogBTAddress.setContentView(R.layout.dialog_get_bt_address);

                final TextView cancelContinueButton = (TextView) dialogBTAddress.findViewById(R.id.cancel_continue);
                final TextView abtPhoneButton = (TextView) dialogBTAddress.findViewById(R.id.abt_phone);

                abtPhoneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent statusSettings = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
                        context.startActivity(statusSettings);
                    }

                });
                cancelContinueButton.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("ApplySharedPref")
                    @Override
                    public void onClick(View view) {
                        // Cancel button will be change to continue button on correctly entered the bluetooth address.
                        if (cancelContinueButton.getText().equals(context.getResources().getString(R.string.cancel))) {
                            // Click on "Cancel" Button.
                            if (dialogBTAddress != null) {
                                dialogBTAddress.dismiss();
                                // Restore to ble on user canceled bt address entering.
                                MainActivity.btProtocol = DCSSDKDefs.DCSSDK_BT_PROTOCOL.SSI_BT_LE;
                                restartMainActivity();
                            }

                        } else {
                            //on click continue
                            MainActivity.sdkHandler.dcssdkSetSTCEnabledState(true);
                            //save entered bluetooth address on shared preference
                            SharedPreferences.Editor settingsEditor =prefs.edit();
                            settingsEditor.putString("PREF_BT_ADDRESS", userEnteredBluetoothAddress).commit();// Commit is required here. So suppressing warning.
                            if (dialogBTAddress != null) {
                                dialogBTAddress.dismiss();
                                dialogBTAddress = null;
                            }
                            restartMainActivity();
                        }
                    }
                });


                final EditText editTextBluetoothAddress = (EditText) dialogBTAddress.findViewById(R.id.text_bt_address);
                editTextBluetoothAddress.addTextChangedListener(new TextWatcher() {
                    String previousMac = null;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String enteredMacAddress = editTextBluetoothAddress.getText().toString().toUpperCase();
                        String cleanMacAddress = clearNonMacCharacters(enteredMacAddress);
                        String formattedMacAddress = formatMacAddress(cleanMacAddress);

                        int selectionStart = editTextBluetoothAddress.getSelectionStart();
                        formattedMacAddress = handleColonDeletion(enteredMacAddress, formattedMacAddress, selectionStart);
                        int lengthDiff = formattedMacAddress.length() - enteredMacAddress.length();

                        setMacEdit(cleanMacAddress, formattedMacAddress, selectionStart, lengthDiff);

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        userEnteredBluetoothAddress = s.toString();
                        if (userEnteredBluetoothAddress.length() > MAX_BLUETOOTH_ADDRESS_CHARACTERS)
                            return;

                        if (isValidBTAddress(userEnteredBluetoothAddress)) {

                            Drawable dr = context.getResources().getDrawable(R.drawable.tick);
                            dr.setBounds(0, 0, dr.getIntrinsicWidth(), dr.getIntrinsicHeight());
                            editTextBluetoothAddress.setCompoundDrawables(null, null, dr, null);
                            cancelContinueButton.setText(context.getResources().getString(R.string.continue_txt));

                        } else {
                            editTextBluetoothAddress.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                            cancelContinueButton.setText(context.getResources().getString(R.string.cancel));

                        }
                    }

                    /**
                     * Strips all characters from a string except A-F and 0-9
                     * (Keep Bluetooth address allowed characters only).
                     *
                     * @param inputMacString User input string.
                     * @return String containing bluetooth MAC-allowed characters.
                     */
                    private String clearNonMacCharacters(String inputMacString) {
                        return inputMacString.replaceAll("[^A-Fa-f0-9]", DEFAULT_EMPTY_STRING);
                    }

                    /**
                     * Adds a colon character to an unformatted bluetooth MAC address after
                     * every second character (strips full MAC trailing colon)
                     *
                     * @param cleanMacAddress Unformatted MAC address.
                     * @return Properly formatted MAC address.
                     */
                    private String formatMacAddress(String cleanMacAddress) {
                        int groupedCharacters = 0;
                        String formattedMacAddress = DEFAULT_EMPTY_STRING;

                        for (int i = 0; i < cleanMacAddress.length(); ++i) {
                            formattedMacAddress += cleanMacAddress.charAt(i);
                            ++groupedCharacters;

                            if (groupedCharacters == 2) {
                                formattedMacAddress += COLON_CHARACTER;
                                groupedCharacters = 0;
                            }
                        }

                        // Removes trailing colon for complete MAC address
                        if (cleanMacAddress.length() == MAX_ALPHANUMERIC_CHARACTERS)
                            formattedMacAddress = formattedMacAddress.substring(0, formattedMacAddress.length() - 1);

                        return formattedMacAddress;
                    }

                    /**
                     * Upon users colon deletion, deletes bluetooth MAC character preceding deleted colon as well.
                     *
                     * @param enteredMacAddress     User input MAC.
                     * @param formattedMacAddress   Formatted MAC address.
                     * @param selectionStartPosition MAC EditText field cursor position.
                     * @return Formatted MAC address.
                     */
                    private String handleColonDeletion(String enteredMacAddress, String formattedMacAddress, int selectionStartPosition) {
                        if (previousMac != null && previousMac.length() > 1) {
                            int previousColonCount = colonCount(previousMac);
                            int currentColonCount = colonCount(enteredMacAddress);

                            if (currentColonCount < previousColonCount) {
                                try {
                                    formattedMacAddress = formattedMacAddress.substring(0, selectionStartPosition - 1) + formattedMacAddress.substring(selectionStartPosition);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                String cleanMacAddress = clearNonMacCharacters(formattedMacAddress);
                                formattedMacAddress = formatMacAddress(cleanMacAddress);
                            }
                        }
                        return formattedMacAddress;
                    }

                    /**
                     * Gets bluetooth MAC address current colon count.
                     *
                     * @param formattedMacAddress Formatted MAC address.
                     * @return Current number of colons in MAC address.
                     */
                    private int colonCount(String formattedMacAddress) {
                        return formattedMacAddress.replaceAll("[^:]", DEFAULT_EMPTY_STRING).length();
                    }

                    /**
                     * Removes TextChange listener, sets MAC EditText field value,
                     * sets new cursor position and re-initiates the listener.
                     *
                     * @param cleanMacAddress       Clean MAC address.
                     * @param formattedMacAddress   Formatted MAC address.
                     * @param selectionStartPosition MAC EditText field cursor position.
                     * @param characterDifferenceLength     Formatted/Entered MAC number of characters difference.
                     */
                    private void setMacEdit(String cleanMacAddress, String formattedMacAddress, int selectionStartPosition, int characterDifferenceLength) {
                        editTextBluetoothAddress.removeTextChangedListener(this);
                        if (cleanMacAddress.length() <= MAX_ALPHANUMERIC_CHARACTERS) {
                            editTextBluetoothAddress.setText(formattedMacAddress);

                            editTextBluetoothAddress.setSelection(selectionStartPosition + characterDifferenceLength);
                            previousMac = formattedMacAddress;
                        } else {
                            editTextBluetoothAddress.setText(previousMac);
                            editTextBluetoothAddress.setSelection(previousMac.length());
                        }
                        editTextBluetoothAddress.addTextChangedListener(this);
                    }

                });

                dialogBTAddress.setCancelable(false);
                dialogBTAddress.setCanceledOnTouchOutside(false);
                dialogBTAddress.show();
                Window window = dialogBTAddress.getWindow();
                if (window != null)
                    window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                bluetoothMAC = prefs.getString("PREF_BT_ADDRESS", "");
            } else {
                dialogBTAddress.show();
            }
        }
        return bluetoothMAC;
    }

    public boolean isValidBTAddress(String text) {
        return text != null && text.length() > 0 && text.matches(BLUETOOTH_ADDRESS_VALIDATOR);
    }

    // Restart MainActivity.
    private void restartMainActivity() {
        Intent i = new Intent(context, MainActivity.class);
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }
}
