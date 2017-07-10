package com.samfierro.hiri;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private EditText pm25; private EditText pm10;
    private EditText temp; private EditText hum;
    private EditText lat; private EditText lon;

    private Button getButton;
    private Button eraseButton;
    private Button sendButton;
    private Button connectButton;
    private Button visualizeButton;
    private TextView connectText;
    private WebView webView;
    private TouchyWebView visualizeView;
    private Button refreshButton;
    private Button continueDataButton;

    public Boolean connected = false;
    public Boolean paired = false;
    private Boolean collectData = false;

    private BluetoothAdapter BTAdapter;
    private BluetoothDevice myDevice;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public static int REQUEST_BLUETOOTH = 1;

    private double longitude;
    private double latitude;
    //private String locationSource;

    private String newDate;
    private String newTime;

    private LocationManager mLocationManager;
    private Location bestLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        setUpBluetooth();

        bestLocation = null;

        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);

        try {mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);}
        catch (SecurityException e) {Log.e("GPS", "Security Error");}

        try {mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);}
        catch (SecurityException e) {Log.e("Network", "Security Error");}

        pm25 = (EditText) findViewById(R.id.pm25Text);
        pm10 = (EditText) findViewById(R.id.pm10Text);
        temp = (EditText) findViewById(R.id.temp);
        hum = (EditText) findViewById(R.id.humidity);
        lat = (EditText) findViewById(R.id.lat);
        lon = (EditText) findViewById(R.id.lon);

        webView = (WebView) findViewById(R.id.webView);
        connectText = (TextView) findViewById(R.id.connectText);

        continueDataButton = (Button) findViewById(R.id.keepGettingDataButton);
        continueDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                continueData();
            }
        });

        connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothButton();
            }
        });

        eraseButton = (Button) findViewById(R.id.eraseButton);
        eraseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eraseData();
            }
        });

        sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData();
            }
        });

        getButton = (Button) findViewById(R.id.getDataButton);
        getButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getData();
            }
        });

        visualizeButton = (Button) findViewById(R.id.visualizeButton);
        visualizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                visualize();
            }
        });

        //loads the cartodb map into a webview that is currently hidden until "visualize" is clicked
        visualizeView = (TouchyWebView) findViewById(R.id.vizWeb);
        visualizeView.getSettings().setJavaScriptEnabled(true);

//      #############replace with correct embedded map.##########################################################
        // Annoying map pop-up
        //visualizeView.loadUrl("https://samfierro.cartodb.com/viz/2942f7d2-3980-11e6-9d7a-0ecfd53eb7d3/embed_map");

        refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshMap();
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        setUpBluetooth();
    }

    //calls function to start or stop getting data constantly
    private void continueData() {
        if (!collectData) {
            collectData = true;
            continueDataButton.setText("No Continuar Recibiendo Datos");
            handler.post(runSendData);

        } else {
            continueDataButton.setText("Continuar Recibiendo Datos");
            collectData = false;
            handler.removeCallbacks(runSendData);
        }
    }

    //gets and sends data every 4 seconds
    Handler handler = new Handler();
    private final Runnable runSendData = new Runnable(){
        public void run(){
            try {
                //prepare and send the data here..
                getData();
                sendData();
//###############scheduled to run every 5 seconds. change the 5 to another number to change the seconds.
//###############for example, 30000 would be 30 seconds.
                handler.postDelayed(this, 4000);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Finds paired bluetooth device for sensor
     */

    // Changed so that this method only sets up Bluetooth capability
    private void setUpBluetooth() {
        // Phone does not support Bluetooth so let the user know and exit.
        if (BTAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your device does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        // Device supports Bluetooth
        if (!BTAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, REQUEST_BLUETOOTH);
        }

        // Gets list of all paired Bluetooth devices
//        Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();

//        if (pairedDevices.size() > 0) {
//            for (BluetoothDevice device : pairedDevices) {
////              ########################## BLUETOOTH DEVICE NAME ####################
//                // Hardcoded - doesn't allow the user to select a device
//                if (device.getAddress().equals("98:D3:32:30:BC:5E")) {
//                    paired = true;
//                    myDevice = device;
//                    break;
//                }
//            }
//        }
    }

    /**
     * Connects paired bluetooth sensor
     */
    private void bluetoothButton() {
        // no paired devices
        if (BTAdapter.getBondedDevices().size() == 0) {
            pairDialog();
        }
        // disconnected -> connected
        else if (!connected) {
            if (!paired) {
                chooseBluetooth();
            }
            else if (paired) {
                connectBluetooth();
            }
        // connected -> disconnected
        } else if (connected){
            disconnectBluetooth();
        }
    }

    // connects bluetooth
    private void connectBluetooth() {
        connectText.setText("Conectando...");
        try {
            socket = myDevice.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
        } catch (IOException e) {System.out.println(e);}
        connected = true;
        connectButton.setText("Desconéctate");
        connectText.setText("Conectado" + " " + myDevice.getName().toString());
        getButton.setEnabled(true);
        continueDataButton.setEnabled(true);
    }

    // disconnects bluetooth
    private void disconnectBluetooth() {
        try {socket.close();} catch (IOException e) {System.out.println(e);}
        connected = false;
        connectButton.setText("Conéctate");
        connectText.setText("No Conectado");
        getButton.setEnabled(false);
        continueDataButton.setEnabled(false);
    }

//    private Location getLastKnownLocation() {
//        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
//        List<String> providers = mLocationManager.getProviders(true);
//        Location bestLocation = null;
//        for (String provider : providers) {
//            try{
//                Location l = mLocationManager.getLastKnownLocation(provider);
//                if (l == null) {
//                    continue;
//                }
//                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
//                    // Found best last known location: %s", l);
//                    bestLocation = l;
//                }} catch (SecurityException e) {}
//        }
//        return bestLocation;
//    }

    /**By Kate**/
    /** Determines whether one Location reading is better than the current Location fix**/
    private void updateBestLocation(Location l) {
        //1 minute
        int duration = 1000 * 60;

        if (l == null) {
            return;
        }
        if (bestLocation == null) {
            // A new location is always better than no location
            bestLocation = l;
            return;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = l.getTime() - bestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > duration;
        boolean isSignificantlyOlder = timeDelta < -duration;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved

        if (isSignificantlyNewer) {
            bestLocation = l;
            return;
        }

        // If the new location is more than two minutes older, it must be worse
        else if (isSignificantlyOlder) {
            return;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (l.getAccuracy() - bestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(l.getProvider(), bestLocation.getProvider());

            // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            bestLocation = l;
            return;
        } else if (isNewer && !isLessAccurate) {
            bestLocation = l;
            return;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            bestLocation = l;
            return;
        }
        return;
    }

/** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    // Define a listener that responds to location updates
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            updateBestLocation(location);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {}
    };

    //time stamp
    /**
     * Reads data from bluetooth sensor and gets geocoordinate
     */
    private void getData() {
        try {
            String data = "";
            inputStream = socket.getInputStream();
            long end = System.currentTimeMillis() + 1000;
            while (System.currentTimeMillis() < end) {
                int byteCount = inputStream.available();
                if (byteCount > 0) {
                    byte[] rawBytes = new byte[byteCount];
                    inputStream.read(rawBytes);
                    final String dataString = new String(rawBytes, "UTF-8");
                    data += dataString;
                }
            }

            List<String> dataList = Arrays.asList(data.split(","));

            // Temporary fix for data input size
            if (dataList.size() == 3) {
                pm25.setText(dataList.get(0));
                temp.setText(dataList.get(2));
                hum.setText(dataList.get(1));
            }
            else if (dataList.size() > 3) {
                pm25.setText(dataList.get(dataList.size() - 3).substring(3));
                temp.setText(dataList.get(dataList.size() - 1));
                hum.setText(dataList.get(dataList.size() - 2));
            }


        } catch (IOException e) {
            System.out.println(e);
        }
        try {
            //gets location
//            Location location = getLastKnownLocation();
            List<String> providers = mLocationManager.getProviders(true);
            //Log.e("Providers", providers.toString());

            for (String provider : providers) {
                try{

                    Location l = mLocationManager.getLastKnownLocation(provider);
                    updateBestLocation(l);

                } catch (SecurityException e) {}
            }

            if (bestLocation != null) {
                longitude = bestLocation.getLongitude();
                latitude = bestLocation.getLatitude();
                //send the location source to cartoDB to run tests
            }
            lon.setText("" + longitude);
            lat.setText("" + latitude);
        } catch (SecurityException e) {
            System.out.println(e);
        }
    }

    private void getTime() {
        //gets date and time
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        newDate = dateFormat.format(date);
        List<String> dateTime = Arrays.asList(newDate.split(" "));
        newDate = dateTime.get(0);
        newDate = "'" + newDate + "'";
        newTime = dateTime.get(1);
        newTime = "'" + newTime + "'";
        //####TO SEND A STRING TO CARTODB YOU NEED TO ADD WRAP IT IN SINGLE QUOTES LIKE THIS: 'myString' ####
    }

    /**
     * Sends the data to cartoDB
     */
    private void sendData() {
        getTime();
        String lat_coord = lat.getText().toString();
        String long_coord = lon.getText().toString();

        //Need to wrap any data to send to the DB in single quotes
        String pm25String = pm25.getText().toString();
        String pm10String = pm10.getText().toString();
        String tempString = temp.getText().toString();
        String humString = hum.getText().toString();
        String sensString = "'" + myDevice.getName() + "'";

        if (lat_coord.equals("") && long_coord.equals("") && pm25String.equals("")
                && pm10String.equals("") && tempString.equals("") && humString.equals("")) {
            sendDialog();
        } else {
//          ################replace link with correct cartoDB user name and api key.################################
//          ################can change what values are sent depending on cartoDB table.#############################
            //String link = "https://samfierro.cartodb.com/api/v2/sql?q=INSERT INTO test (pm_25, date, time, the_geom) VALUES ("+pm25String+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=02e8c4a7c19b20c6dd81015ea2af533aeadf19de";
            String link = "https://khunter.carto.com/api/v2/sql?q=INSERT INTO test (sens, pm_25, hum, temp, date, time, the_geom) VALUES ("+sensString+", "+pm25String+", "+humString+", "+tempString+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=6c0f6b8727acebc16c7492780ba5bbd7f73b32ca";
            webView.loadUrl(link);
            Toast.makeText(MainActivity.this,"Datos enviado",Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Loads cartoDB map into web view and makes it visible
     */
    private void visualize() {
        if (visualizeButton.getText().equals("Visualizar")) {
            visualizeView.setVisibility(View.VISIBLE);
            refreshMap();
            visualizeButton.setText("No Visualizar");
            refreshButton.setVisibility(View.VISIBLE);
        }
        else {
            visualizeView.setVisibility(View.GONE);
            visualizeButton.setText("Visualizar");
            refreshButton.setVisibility(View.GONE);
        }
    }

    private void refreshMap() {
        visualizeView.reload();
    }

    private void eraseData() {
        pm25.setText("");
        pm10.setText("");
        temp.setText("");
        hum.setText("");
        lat.setText("");
        lon.setText("");
    }

    // Created by Kate - bluetooth menu
    private void chooseBluetooth() {
        final Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
        final List<String> deviceItemList = new ArrayList<String>();
        final List<String> deviceAddressList = new ArrayList<String>();

        for (BluetoothDevice device : pairedDevices) {
            deviceItemList.add(device.getName());
            deviceAddressList.add(device.getAddress());
        }

        final CharSequence[] devices = deviceItemList.toArray(new CharSequence[deviceItemList.size()]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Conexiones Disponibles");
        builder.setItems(devices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress().equals(deviceAddressList.get(which))) {
                        paired = true;
                        myDevice = device;
                        break;
                    }
                }
            }
        });
        builder.show();
    }

    private void pairDialog() {
        //dialog appears the bluetooth device isn't paired, so press OK to go to bluetooth screen in settings!
        new AlertDialog.Builder(this)
                .setTitle("No Bluetooth Device Paired")
                .setMessage("Please pair device in settings")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intentOpenBluetoothSettings = new Intent();
                        intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(intentOpenBluetoothSettings);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void sendDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No datos para enviar")
                .setMessage("Por favor ponga datos antes de enviar")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

}