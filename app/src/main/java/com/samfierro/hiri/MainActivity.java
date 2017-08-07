package com.samfierro.hiri;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.webkit.WebViewClient;
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

    private TextView pm25; private TextView pm10;
    private TextView temp; private TextView hum;
    private TextView lat; private TextView lon;

    private Button connectButton;
    private Button visualizeButton;
    private TextView connectText;
    private WebView webView;
    private TouchyWebView visualizeView;
    private Button refreshButton;
    private Button continueDataButton;
    private Button databaseButton;
    private TextView databaseText;

    public Boolean connected = false;
    public Boolean paired = false;
    private Boolean collectData = false;

    private BluetoothAdapter BTAdapter;
    public BluetoothDevice myDevice;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private static final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public static int REQUEST_BLUETOOTH = 1;

    private double longitude;
    private double latitude;

    private String newDate;
    private String newTime;

    private LocationManager mLocationManager;
    private Location bestLocation;

    private static String database = "Default Base";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        setUpBluetooth();

        bestLocation = null;

        mLocationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);

        // Bluetooth Broadcast Receiver to detect changes in connection
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);


        try {mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);}
        catch (SecurityException e) {}

        try {mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);}
        catch (SecurityException e) {}

        pm25 = (TextView) findViewById(R.id.pm25Text);
        pm10 = (TextView) findViewById(R.id.pm10Text);
        temp = (TextView) findViewById(R.id.temp);
        hum = (TextView) findViewById(R.id.humidity);
        lat = (TextView) findViewById(R.id.lat);
        lon = (TextView) findViewById(R.id.lon);

        webView = (WebView) findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                stopData();
                linkFail();
            }
        });

        connectText = (TextView) findViewById(R.id.connectText);
        databaseText = (TextView) findViewById(R.id.databaseText);

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

        databaseButton = (Button) findViewById(R.id.databaseButton);
        databaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDatabase();
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

        refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshMap();
            }
        });

        databaseText.setText("Base de Datos: " + database);

    }


    // Detects changes in Bluetooth connection
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            //Device is now connected
                connected = true;
                connectButton.setText("Desconéctate");
                connectText.setText("Conectado" + " " + myDevice.getName().toString());
                continueDataButton.setEnabled(true);
            }

            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            //Device has disconnected
                connected = false;
            }
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        setUpBluetooth();
    }

    // Calls function to start or stop getting data constantly
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

    private void stopData() {
        continueDataButton.setText("Continuar Recibiendo Datos");
        collectData = false;
        handler.removeCallbacks(runSendData);
    }

    // Gets and sends data every 5 seconds
    // When connection is lost, checks for connection every 15 seconds
    Handler handler = new Handler();
    private final Runnable runSendData = new Runnable(){
        public void run(){
            try {

                // Connected
                if (connected) {
                    // Prepare and send the data here every 5 seconds
                    getData();
                    /*Time between retrievals is 1 second longer than shown,
                    reading the data from bluetooth has a 1 second delay*/
                    handler.postDelayed(this, 4000);
                }

                // Connection lost
                else {
                    try {
                        connectBluetooth();
                    } catch (Exception e) {}
                    // Waits 15 seconds in between connect attempts
                    handler.postDelayed(this, 15000);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Finds paired bluetooth device for sensor
     */

    // Sets up Bluetooth capability
    private void setUpBluetooth() {
        // Phone does not support Bluetooth so let the user know and exit.
        if (BTAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No compatible")
                    .setMessage("El dispositivo no es compatible con bluetooth.")
                    .setPositiveButton("Salir", new DialogInterface.OnClickListener() {
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
    }

    /**
     * Connects paired bluetooth sensor
     */
    private void bluetoothButton() {
        // No paired devices
        if (BTAdapter.getBondedDevices().size() == 0) {
            pairDialog();
        }
        // Connect
        else if (!connected) {
            chooseBluetooth();
        }
        // Disconnect
        else if (connected){
            disconnectBluetooth();
        }
    }

    // Connects bluetooth
    private void connectBluetooth() {
        connectText.setText("Conectando...");
        try {
            socket = myDevice.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
        } catch (IOException e) {connectText.setText("Error al conectarse");}

    }

    public void pickDatabase() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("El nombre de su base de datos");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                database = input.getText().toString();
                databaseText.setText("Base de Datos: " + database);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    // Disconnects bluetooth
    private void disconnectBluetooth() {
        connectButton.setText("Conéctate");
        connectText.setText("No Conectado");
        continueDataButton.setEnabled(false);
        try {socket.close();} catch (IOException e) {System.out.println(e);}
    }


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

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };


    /**
     * Reads data from bluetooth sensor and gets geocoordinate
     */
    private void getData() {

        // Gets location from all providers and finds the best
        try {

            List<String> providers = mLocationManager.getProviders(true);

            for (String provider : providers) {
                try{

                    Location l = mLocationManager.getLastKnownLocation(provider);
                    updateBestLocation(l);

                } catch (SecurityException e) {}
            }

            if (bestLocation != null) {
                longitude = bestLocation.getLongitude();
                latitude = bestLocation.getLatitude();
            }

            lon.setText("" + longitude);
            lat.setText("" + latitude);

        } catch (SecurityException e) {System.out.println(e);}


        // Reads bluetooth
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

            List<String> dataList = Arrays.asList(data.split("\n"));

            for (int i = 0; i < dataList.size(); i++) {
                String parsed = dataList.get(i);

                List<String> newData = Arrays.asList(parsed.split(","));

                // Determines how the data from the sensor is parsed

                if (newData.size() == 3) {

                    pm25.setText(newData.get(0));
                    temp.setText(newData.get(2));
                    hum.setText(newData.get(1));
                    sendData();
                }
            }

        } catch (IOException e) {
            System.out.println(e);
        }

    }

    private void getTime() {
        // Gets date and time
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

        // Need to wrap any data to send to the DB in single quotes

        String pm25String = "'" + pm25.getText().toString() + "'";
        String pm10String = "'" + pm10.getText().toString() + "'";
        String tempString = "'" + temp.getText().toString() + "'";
        String humString = "'" + hum.getText().toString() + "'";
        String sensString = "'" + myDevice.getName() + "'";


        String baseAddress = database.toLowerCase().replaceAll(" ", "_");
//          ################replace link with correct cartoDB user name and api key.################################
//          ################can change what values are sent depending on cartoDB table.#############################
        String link = "https://khunter.carto.com/api/v2/sql?q=INSERT INTO "+baseAddress+" (sens, pm_25, hum, temp, date, time, the_geom) VALUES ("+sensString+", "+pm25String+", "+humString+", "+tempString+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=6c0f6b8727acebc16c7492780ba5bbd7f73b32ca";
        webView.loadUrl(link);

        Toast.makeText(MainActivity.this,"Datos enviado",Toast.LENGTH_SHORT).show();

    }


    /**
     * Loads cartoDB map into web view and makes it visible
     */
    private void visualize() {
        if (visualizeButton.getText().equals("Visualizar")) {

            //Tried to work the google API but wasn't working, so put in an embed map instead
            visualizeView.loadUrl("https://www.google.com/maps/d/embed?mid=1zegu4s0LGfEe5KzLxvRfzIwWFqM&hl=en");
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


    // Bluetooth paired devices menu
    private void chooseBluetooth() {
        final Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();

        // Stores the name and addresses of the paired devices
        final List<String> deviceItemList = new ArrayList<String>();
        final List<String> deviceAddressList = new ArrayList<String>();

        for (BluetoothDevice device : pairedDevices) {
            deviceItemList.add(device.getName());
            deviceAddressList.add(device.getAddress());
        }

        final CharSequence[] devices = deviceItemList.toArray(new CharSequence[deviceItemList.size()]);

        // Menu
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Dispositivos emparejados");
        builder.setItems(devices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                // Chooses the device with the same address
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getAddress().equals(deviceAddressList.get(which))) {
                        paired = true;
                        myDevice = device;
                        connectText.setText("Conectando...");
                        break;
                    }
                }

            }
        });

        // Connects upon exit
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                connectBluetooth();
            }
        });

        builder.show();

    }

    private void pairDialog() {
        // Dialog appears the bluetooth device isn't paired, so press OK to go to bluetooth screen in settings!
        new AlertDialog.Builder(this)
                .setTitle("No hay dispositivo Bluetooth pareado.")
                .setMessage("Agrupe por favor el dispositivo en ajustes.")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intentOpenBluetoothSettings = new Intent();
                        intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(intentOpenBluetoothSettings);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // Dialog for a failed load of the link
    private void linkFail() {
        new AlertDialog.Builder(this)
                .setTitle("Datos no enviados")
                .setMessage("No se enviaron datos. Asegúrese de enviar a la base de datos correcta y verificar su conexión.")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}