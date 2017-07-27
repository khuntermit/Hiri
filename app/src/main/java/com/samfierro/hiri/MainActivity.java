package com.samfierro.hiri;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
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

        pm25 = (EditText) findViewById(R.id.pm25Text);
        pm10 = (EditText) findViewById(R.id.pm10Text);
        temp = (EditText) findViewById(R.id.temp);
        hum = (EditText) findViewById(R.id.humidity);
        lat = (EditText) findViewById(R.id.lat);
        lon = (EditText) findViewById(R.id.lon);

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

//        eraseButton = (Button) findViewById(R.id.eraseButton);
//        eraseButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                eraseData();
//            }
//        });

//        sendButton = (Button) findViewById(R.id.sendButton);
//        sendButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                sendData();
//            }
//        });

//        getButton = (Button) findViewById(R.id.getDataButton);
//        getButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                getData(false);
//            }
//        });

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
        // Map pop-up
        // visualizeView.loadUrl("https://samfierro.cartodb.com/viz/2942f7d2-3980-11e6-9d7a-0ecfd53eb7d3/embed_map");

        refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshMap();
            }
        });

        databaseText.setText("Base de Datos: " + database);

    }

    // Created by Kate - detects changes in Bluetooth connection
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
                getButton.setEnabled(true);
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

    // Gets and sends data every 4 seconds
    // When connection is lost, checks for connection every 15 seconds
    Handler handler = new Handler();
    private final Runnable runSendData = new Runnable(){
        public void run(){
            try {

                // Connected
                if (connected) {
                    // Prepare and send the data here..
                    getData(true);
                    handler.postDelayed(this, 4000);
                }

                // Connection lost
                else {
                    // To cut system connect delay
                    long end = System.currentTimeMillis() + 4000;
                    while (System.currentTimeMillis() < end) {
                        try {
                            connectBluetooth();
                        } catch (Exception e) {
                        }
                    }
                    handler.postDelayed(this, 10000);
                }

//###############scheduled to run every 5 seconds. change the 5 to another number to change the seconds.
//###############for example, 30000 would be 30 seconds.

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
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
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

    // disconnects bluetooth
    private void disconnectBluetooth() {
        connectButton.setText("Conéctate");
        connectText.setText("No Conectado");
        getButton.setEnabled(false);
        continueDataButton.setEnabled(false);
        try {socket.close();} catch (IOException e) {System.out.println(e);}
    }


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

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };


    //time stamp
    /**
     * Reads data from bluetooth sensor and gets geocoordinate
     */
    private void getData(boolean send) {

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
                // Change around the numbers after "get" to reorder the data

                if (newData.size() == 3) {

                    pm25.setText(newData.get(0));
                    temp.setText(newData.get(2));
                    hum.setText(newData.get(1));
                    // Send data only when you specify
                    if (send) {
                        sendData();
                    }
                }

                // How to parse when pm10 is incorporated

                else if (newData.size() == 4) {

                    pm25.setText(newData.get(0));
                    pm10.setText(newData.get(1));
                    temp.setText(newData.get(3));
                    hum.setText(newData.get(2));
                    // Send data only when you specify
                    if (send) {
                        sendData();
                    }
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


//        if (lat_coord.equals("") && long_coord.equals("") && pm25String.equals("")
//                && pm10String.equals("") && tempString.equals("") && humString.equals("")) {
        if (lat_coord.equals("") && long_coord.equals("") && pm25String.equals("") && tempString.equals("") && humString.equals("")) {
            sendDialog();
        } else {
            String baseAddress = database.toLowerCase().replaceAll(" ", "_");
//          ################replace link with correct cartoDB user name and api key.################################
//          ################can change what values are sent depending on cartoDB table.#############################
            //String link = "https://samfierro.cartodb.com/api/v2/sql?q=INSERT INTO test (pm_25, date, time, the_geom) VALUES ("+pm25String+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=02e8c4a7c19b20c6dd81015ea2af533aeadf19de";
            String link = "https://khunter.carto.com/api/v2/sql?q=INSERT INTO "+baseAddress+" (sens, pm_25, hum, temp, date, time, the_geom) VALUES ("+sensString+", "+pm25String+", "+humString+", "+tempString+", "+newDate+", "+newTime+", ST_SetSRID(ST_Point("+long_coord+", "+lat_coord+"),4326))&api_key=6c0f6b8727acebc16c7492780ba5bbd7f73b32ca";
            webView.loadUrl(link);

            Toast.makeText(MainActivity.this,"Datos enviado",Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Loads cartoDB map into web view and makes it visible
     */
    private void visualize() {
        if (visualizeButton.getText().equals("Visualizar")) {
            //String apiKey = "AIzaSyD1Wiza9tr_q_B0A05GdMKFYMBXkq9x5WI";
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

//    private void eraseData() {
//        pm25.setText("");
//        pm10.setText("");
//        temp.setText("");
//        hum.setText("");
//        lat.setText("");
//        lon.setText("");
//    }

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
        builder.setTitle("Dispositivos emparejados");
        builder.setItems(devices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

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
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

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

    private void sendDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No datos para enviar.")
                .setMessage("Por favor ponga datos antes de enviar.")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}