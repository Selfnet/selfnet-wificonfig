/* Copyright 2013 Wilco Baan Hofman <wilco@baanhofman.nl>
 * Copyright 2018 Erik Zeiske <erik@selfnet.de>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.selfnet.wifisetup;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/* import android.util.Base64; */
// API level 18 and up

public class LogonScreen extends Activity {
    private static final String TAG = "LogonScree";
    protected static final int SHOW_PREFERENCES = 0;
    private Handler mHandler = new Handler();
    private EditText username;
    private EditText password;
    private Button btn;
    private Button qrScan;
    
    private static final String SSID = "Selfnet";
    private static final boolean HIDDEN_SSID = false;
    private static final int WLAN_PRIORITY = 40;
    private static final String ALTSUBJECT_MATCH = "DNS:radius-user.selfnet.de";
    private static final String DOMAIN_SUFFIX_MATCH = "radius-user.selfnet.de";
    private boolean busy = false;
    private Toast toast = null;
    private int logoclicks = 0;
    private String s_username;
    private String s_password;

    /**
     * Creates a toast for the given text.
     * If a toast is currently shown it gets replaced by this toast.
     * @param text The text content of the toast
     */
    private void toastText(final String text) {
        if (toast != null)
            toast.cancel();
        toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
        toast.show();
    }

	/*
     * Unfortunately, this returns false on a LOT of devices :(
	 *
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private boolean get5G() {
		WifiManager wifiManager = (WifiManager) this.getSystemService(WIFI_SERVICE);
		return wifiManager.is5GHzBandSupported();
	}
	*/

    /**
     * Called when the activity is created.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logon_screen);

        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);
        btn = (Button) findViewById(R.id.button1);

        ImageView img = (ImageView) findViewById(R.id.logo);
        //Easter egg for clicking on the logo
        img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logoclicks++;
                if (logoclicks == 4) {
                    toastText("You're cute!");
                }
                if (logoclicks == 6) {
                    toastText("Stop that!");
                }
            }
        });
    }

    /**
     * Gets called by the button to create the connection entry
     * @param _v the calling view
     */
    public void createConnectionEntryClick(View _v) {
        if (busy) {
            return;
        }

        // Reject trailing whitespaces
        if (username.getText().toString().endsWith(" ")) {
            toastText(getString(R.string.email_ends_with_whitespace));
            return;
        }

        busy = true;
        btn.setClickable(false);

        // Most of this stuff runs in the background
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    s_username = username.getText().toString();
                    s_password = password.getText().toString();

                    if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        saveWifiConfigPreQ();
                    } else {
                        saveWifiConfigPostP();
                    }
                    resultStatus(true, getString(R.string.success_explain_text));
                } catch (RuntimeException e) {
                    resultStatus(false, String.format(getString(R.string.error_explain_text), e.getMessage()));
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                busy = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        btn.setClickable(true);
                    }

                    ;
                });
            }
        };
        t.start();
    }

    /**
     * Sets the wifi config before API Version 29
     */
    @TargetApi(Build.VERSION_CODES.P)
    private void saveWifiConfigPreQ() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);

        WifiConfiguration currentConfig = new WifiConfiguration();

        List<WifiConfiguration> configs = null;
        for (int i = 0; i < 10 && configs == null; i++) {
            configs = wifiManager.getConfiguredNetworks();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                continue;
            }
        }

        if (s_username.equals("") || s_password.equals("")) {
            resultStatus(false, getString(R.string.no_credentials_provided));
            return;
        }


        // Use the existing Selfnet profile if it exists.
        boolean ssidExists = false;
        int oldNetworkId = -1;
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.SSID.equals(surroundWithQuotes(SSID))) {
                    oldNetworkId = config.networkId;
                    ssidExists = true;
                    break;
                }
            }
        }

        currentConfig.SSID = surroundWithQuotes(SSID);
        currentConfig.hiddenSSID = HIDDEN_SSID;
        currentConfig.priority = WLAN_PRIORITY;
        currentConfig.status = WifiConfiguration.Status.DISABLED;

        currentConfig.allowedKeyManagement.clear();
        currentConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);

        // GroupCiphers (Allow most ciphers)
        currentConfig.allowedGroupCiphers.clear();
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        currentConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);


        // PairwiseCiphers (CCMP = WPA2 only)
        currentConfig.allowedPairwiseCiphers.clear();
        currentConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

        // Authentication Algorithms (OPEN)
        currentConfig.allowedAuthAlgorithms.clear();
        currentConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);

        // Protocols (RSN/WPA2 only)
        currentConfig.allowedProtocols.clear();
        currentConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

        currentConfig.enterpriseConfig = getAndroid43EnterpriseSettings();

        if (ssidExists) {
            wifiManager.removeNetwork(oldNetworkId);
            wifiManager.saveConfiguration();
        }
        int networkId = wifiManager.addNetwork(currentConfig);
        wifiManager.enableNetwork(networkId, false);
        wifiManager.saveConfiguration();
    }


    @TargetApi(Build.VERSION_CODES.M)
    private WifiEnterpriseConfig getAndroid43EnterpriseSettings() {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream in = getResources().openRawResource(R.raw.cacert);
            // InputStream in = new ByteArrayInputStream(Base64.decode(ca.replaceAll("-----(BEGIN|END) CERTIFICATE-----", ""), 0));
            X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(in);

            WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
            enterpriseConfig.setPhase2Method(Phase2.PAP);
            enterpriseConfig.setAnonymousIdentity("anonymous@email.service");
            enterpriseConfig.setEapMethod(Eap.TTLS);

            enterpriseConfig.setCaCertificate(caCert);
            enterpriseConfig.setIdentity(s_username);
            enterpriseConfig.setPassword(s_password);
            enterpriseConfig.setAltSubjectMatch(ALTSUBJECT_MATCH);
            enterpriseConfig.setDomainSuffixMatch(DOMAIN_SUFFIX_MATCH);
            return enterpriseConfig;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't create certificate for Enterprise Settings.");
        }
    }

    /**
     * Save the the wifi configuration as suggestion for All API Versions since Q = 29
     */
    @TargetApi(Build.VERSION_CODES.Q)
    private void saveWifiConfigPostP() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Clear networks if possible
            wifiManager.removeNetworkSuggestions(wifiManager.getNetworkSuggestions());
        }

        final WifiNetworkSuggestion suggestion =
                new WifiNetworkSuggestion.Builder()
                        .setSsid(SSID)
                        .setIsHiddenSsid(HIDDEN_SSID)
                        .setPriority(WLAN_PRIORITY)
                        .setWpa2EnterpriseConfig(getAndroid43EnterpriseSettings())
                        .build();
        final List<WifiNetworkSuggestion> suggestionsList = new ArrayList<>();
        suggestionsList.add(suggestion);
        int status = wifiManager.addNetworkSuggestions(suggestionsList);
        Log.i(TAG, "Adding network suggestion returned: " + status);
    }

    /**
     * Related to the menu on the top right corner
     * @param menu the menu of this activity
     * @return true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    /**
     * Gets called when there an options menu item is clicked.
     * Will take the according actions
     * @param item
     * @return true if the event was processed by this function
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                PackageInfo pi = null;
                try {
                    pi = getPackageManager().getPackageInfo(getClass().getPackage().getName(), 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                showDialog( getString(R.string.ABOUT_TITLE),
                        getString(R.string.ABOUT_CONTENT) +
                        "\n\n" + pi.packageName + "\n" +
                        "V" + pi.versionName +
                        "C" + pi.versionCode + "-equi");

                return true;
            case R.id.help:
                showDialog(getString(R.string.HELP_TITLE), getString(R.string.HELP_TEXT));
                return true;

            case R.id.privacy:
                showDialog(getString(R.string.PRIVACY_TITLE), getString(R.string.PRIVACY_AGREEMENT));
                return true;

            case R.id.exit:
                System.exit(0);
        }
        return false;
    }

    private void showDialog(String title, String content){
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setPositiveButton(getString(android.R.string.ok), null);
        builder.show();
    }

    /**
     * Switches to the result screen displaying the given text
     * @param success
     * @param text
     */
    protected void resultStatus(final boolean success, final String text) {
        Intent intent = new Intent(this, ResultScreen.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(ResultScreen.TITLE, success?getString(R.string.success_title):getString(R.string.error_title));
        intent.putExtra(ResultScreen.DESC, text);
        startActivity(intent);
    }

    /**
     * removes the quotes from the given string if present
     * @param str
     * @return
     */
    static String removeQuotes(String str) {
        int len = str.length();
        if ((len > 1) && (str.charAt(0) == '"') && (str.charAt(len - 1) == '"')) {
            return str.substring(1, len - 1);
        }
        return str;
    }

    /**
     * Surrounds the given string with quotes
     * @param string
     * @return
     */
    static String surroundWithQuotes(String string) {
        return "\"" + string + "\"";
    }
}
