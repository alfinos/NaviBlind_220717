package com.example.salfino.naviblind_110217;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.indooratlas.android.sdk.IAGeofenceEvent;
import com.indooratlas.android.sdk.IAGeofenceListener;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.R.attr.id;

// Main Class which extends AppCompatActivity. This class is the main controller of the MVC architecture
// Class contains three inner classes: DebugGesture, speechListener and DownloadXML
// All listeners have been implemented as part of the "Controller"

public class MainActivity extends AppCompatActivity {

    private int[] audioCommands = {//Array of sounds
            R.raw.alert,//0
            R.raw.tick,//1
            R.raw.metal_metronome,//2
    };

    private double currentDistance = 10000d;//Initialize current distance to a ridiculously large number
    private final int MY_CODE_PERMISSIONS = 1;
    private static final int PERMISSION_REQUEST_COARSE_BL = 2;
    private long DEFAULT_INTERVAL; //Initialize default values
    private float DEFAULT_DISPLACEMENT;
    private double DEFAULT_ACCURACY;
    private double DEFAULT_CURRENT_DISTANCE;
    private float DEFAULT_SPEECH_RATE;
    private float DEFAULT_PITCH;
    private double DEFAULT_FF_LATITUDE;
    private double DEFAULT_FF_LONGITUDE;
    private float DEFAULT_FLOOR_CERTAINTY;
    private int DEFAULT_FF_FLOOR;
    private float DEFAULT_FF_ACCURACY;
    private IALocationManager mIALocationManager;
    private MediaPlayer mPlayer;
    private SpeechRecognizer mSR;
    private BluetoothAdapter mBTAdapter;
    private TextView mLogging;
    private TextView mTextView;
    private ScrollView mScrollView;
    private static final String TAG = "IndoorAtlas";
    private long mRequestStartTime;
    private boolean calibrationOK = false;
    private boolean statusOK = false;
    private boolean permissionOK = false;

    private boolean startOfRoute = true;
    private boolean duringRoute = false;
    private boolean startTextFlag = false;
    private boolean firstTextFlag = false;
    private boolean secondTextFlag = false;
    private boolean specialEvent = false;
    private boolean firstConfirmFlag = false;
    private boolean secondConfirmFlag = false;

    private boolean endFlag = false;

    private BluetoothLeScanner scanner;
    private ScanSettings scanSettings;
    private boolean mScanning;
    private String dName = "";
    private String macAddress = "";
    private double rssiGR = -17;
    private double rssiSR = -17;
    private double rssiSG = -17;
    private double metersGR = 10000;//Initialize current distance to a ridiculously large number
    private double metersSR = 10000;//Initialize current distance to a ridiculously large number
    private double metersSG = 10000;//Initialize current distance to a ridiculously large number
    private GestureDetectorCompat gestureObject;
    private TextToSpeech t1;
    private Bundle params = new Bundle();
    private ArrayList<RouteEntry> routeData;
    private ArrayList<WayPointEntry> waypointData;
    private ArrayList<TextEntry> textData;
    private ArrayList<ConfigEntry> configData;
    private ArrayList<RoomEntry> roomData;
    private ArrayList<EventEntry> eventData;
    private ArrayList<RoomWayPointEntry> roomwaypointData;
    private String xmlFile;
    private String introutterance = "";
    public String confirmationutterance = "";
    private String locationutterance = "";
    private String menuutterance = "";
    private String roomutterance = "";
    private String waypointutterance = "";
    private String repatutterance = "";
    private String serviceutterance = "";
    private String routeutterance = "";
    private String norouteutterance = "";
    private String resumeutterance = "";
    private String mytempLocation = "";
    private String currentId = "";
    private int chosenRouteIndex = 0;



    //// Accepts a message, font and colour and modifies the main text view accordingly
    // @param String msg, double font size and hex
    // @return nothing
    public void logText(String msg, int fontsize, int hexcolour ) {
        mLogging.setText(String.format(Locale.UK, "\n %s", msg));
        mLogging.setTextSize(fontsize);
        mLogging.setTextColor(hexcolour);
        mScrollView.smoothScrollBy(0, mLogging.getBottom());
    }

    //// Method for setting up for BLE scanning
    // @param none
    // @return nothing
    public void initialiseBLE(){

        final BluetoothManager bluetoothManager =  (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBTAdapter = bluetoothManager.getAdapter();//Get the Bluetooth Adapter first
        //Create the scan settings
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        //Set scan latency mode. Lower latency, faster device detection/more battery and resources consumption
        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        //Wrap settings together and save on a settings var (declared globally).
        scanSettings = scanSettingsBuilder.build();
        //Get the BLE scanner from the BT adapter (var declared globally)
        scanner = mBTAdapter.getBluetoothLeScanner();
    }

    //// Method that accepts the floor plan id and gets the standard text utterance from the TextEntry object
    //// Calls method checkSpecialEvent() to check if any special events should be announced
    // @param String id, which is the floorplan id retreived automatically through region change listener
    // @return Initial text to be spoken on start of the app
    public String initializeTTS(String id){

        String initString = "";
        String eventsString = "";

        TextEntry myTextObject = textData.get(0);
        introutterance = myTextObject.getIntrotext();
        confirmationutterance = myTextObject.getConfirmationtext();
        locationutterance = myTextObject.getLocationtext();
        menuutterance = myTextObject.getMenutext();
        roomutterance = myTextObject.getRoomtext();
        waypointutterance = myTextObject.getWaypointtext();
        repatutterance = myTextObject.getRepeattext();
        serviceutterance = myTextObject.getServicetext();
        routeutterance = myTextObject.getRoutetext();
        norouteutterance = myTextObject.getNoroutetext();
        resumeutterance = myTextObject.getResumetext();

        eventsString = checkSpecialEvents(id);

        //String id = "85b435a5-971f-47c3-8d33-23831680cca0";
        for (int i = 0; i < roomData.size();i++){
            RoomEntry myObject = roomData.get(i);
            String currentplanid = myObject.getFloorplanid();
            if (currentplanid.equals(id)){
                String floorutterance = myObject.getFloordescription();
                initString = introutterance + floorutterance + "... " + "... " + eventsString + "... Please wait for location update... ";
                break;
            }
        }
        return initString;
    }

    //// Method accepts spoken string converted by speech synthesizer and checks if chosen destination is configured
    // @param String myInput, converted spoken string
    // @return match flag
    public boolean roomwaypointVoiceMatch(String myInput) {

        boolean match = false;

        for (int i = 0; i < roomwaypointData.size();i++){//Iterate through both Room and Waypoint data
            RoomWayPointEntry myObject = roomwaypointData.get(i);
            String currentName = myObject.getName();
            currentName = currentName.trim();
            if (myInput.equalsIgnoreCase(currentName)){
                match = true;
            }
        }
        return match;
    }
    public void fixLocation(){

        IALocation location = new IALocation.Builder().withLatitude(DEFAULT_FF_LATITUDE)
                        .withLongitude(DEFAULT_FF_LONGITUDE)
                        .withAccuracy(DEFAULT_FF_ACCURACY)
                        .withFloorLevel(DEFAULT_FF_FLOOR).build();
                mIALocationManager.setLocation(location);//Explicitly set the the initial fix as specified in configuration file
    }

    //// Method uses floorplan Id to check if any special events have been recorded for particular floor
    // @param String id, floorplanID
    // @return String utterance describing the event(s)
    public String checkSpecialEvents(String id){
        String eventString = "";
        String allDescription = "";
        boolean noEventsFlag = false;

        for (int i = 0; i < eventData.size();i++){
            EventEntry myObject = eventData.get(i);
            String currentplanid = myObject.getFloorplanid();
            if (currentplanid.equals(id)){
                String currentName = myObject.getName();
                if (currentName.equals("None")){
                    noEventsFlag = true;
                    break;
                }else {
                    String currentDescription = myObject.getEventdescription();
                    allDescription = allDescription + currentDescription + "... " ;
                }
            }
        }

        if(noEventsFlag){
            eventString = "There are no special events on this floor today... ";
        }else {
            eventString = "There are the following events on the floor today... " + allDescription;
        }

        return eventString;
    }

    //// Method to announce the main options before the start of the route, calls texttospeech() method with "Menu" state
    // @param String currentLocation, name of current location of the user
    // @return nothing
    public void menu(String currentLocation){
        String allLocations = "";

        for (int i = 0; i < roomwaypointData.size();i++){
            RoomWayPointEntry myObject = roomwaypointData.get(i);
            String currentFloorPlanId = myObject.getFloorplanid();
            if (currentFloorPlanId.equals(currentId)){
                String currentName = myObject.getName();
                String currentDescription = myObject.getDescription();
                //String fullDescription = currentName + " which is " + currentDescription;
                String fullDescription = currentName;
                allLocations = fullDescription + "... " + allLocations;
            }
        }
        texttospeech(menuutterance + "... " + roomutterance +"... " + waypointutterance+"... " + allLocations,"Menu",currentLocation);
    }

    //// Method called when destination is chosen by user.
    // @param Strings containin the names of start position and end position
    // @return nothing - sets a chosenRouteIndex and calls method texttospeech with "OnNoRouteConfigured" or "OnChoosingRoute" states
    public void chooseRoute (String startname,String endname ){

        boolean routeFoundFlag = false;//Assume at the start that the chosen route is not in the XML file

        for (int i = 0; i < routeData.size();i++){
            RouteEntry myObject = routeData.get(i);
            String currentStartName = myObject.getStartpointname().trim();
            String currentEndName = myObject.getEndpointname().trim();

            if (currentStartName.equalsIgnoreCase(startname.trim())& currentEndName.equalsIgnoreCase(endname.trim())){
                chosenRouteIndex = i;
                routeFoundFlag = true;
            }

            if(routeFoundFlag){break;}
        }

        if(!routeFoundFlag){
            String inputString = norouteutterance;
            texttospeech(inputString,"OnNoRouteConfigured",startname);

        }else {

            RouteEntry myObject = routeData.get(chosenRouteIndex);
            String currentDescription = myObject.getRoutedescription();

            String inputString = routeutterance + currentDescription;
            texttospeech(inputString,"OnChoosingRoute",startname);
        }
    }

    //// Method that launches method texttospeech() with "OnStartText" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void launchRoute(String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String startText = myObject.getStartpointtext();
        texttospeech(startText,"OnStartText",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnFirstConfirm" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void firstConfirm (String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String firstConfirmText = myObject.getFirstconfirmation();
        texttospeech(firstConfirmText + "... " + resumeutterance,"OnFirstConfirm",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnFirstText" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void launchFirstText(String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String firstText = myObject.getFirstwaypointtext();
        texttospeech(firstText,"OnFirstText",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnSecondConfirm" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void secondConfirm(String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String secondConfirmText = myObject.getSecondconfirmation();
        texttospeech(secondConfirmText + "... " + resumeutterance,"OnSecondConfirm",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnSecondText" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void launchSecondText(String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String secondText = myObject.getSecondwaypointtext();
        texttospeech(secondText,"OnSecondText",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnEndRoute" state
    // @param String currentLocation - name of current location of user
    // @return nothing
    public void launchEndText(String currentLocation){
        RouteEntry myObject = routeData.get(chosenRouteIndex);
        String endText = myObject.getEndpointtext() + "... " + resumeutterance;
        texttospeech(endText,"OnEndRoute",currentLocation);
    }

    //// Method that launches method texttospeech() with "OnTimeEstimate" state
    // @param String currentLocation - name of current location of user and double routeDuration in milli seconds
    // @return nothing
    public void estimateTime (String currentLocation, double routeDuration) {
        String timeText = "Your estimated route time was... " + routeDuration + "... seconds";
        texttospeech(timeText,"OnTimeEstimate",currentLocation);
    }

    //// Method that starts BLE scanning and launches the Scan Callback
    // @param boolean enable - whether the scan should be enabled i.e. true or not i.e. false
    // @return nothing - launches startScan or stopScan methods
    public void startLeScan(boolean enable) {
        if (enable) {
            mScanning = true;//Start BLE Scan
            scanner.startScan(null, scanSettings, mScanCallback);
        }else{
            //Stop BLE scan
            mScanning = false;
            scanner.stopScan(mScanCallback);
        }
    }

    public double getDistance(double rssi, String location) {//RSSI (dBm) = -10n log10(d) + A and n = 2 for free space and A is average RSSI at 1m
        double A = -60.0;
        if (location.equals("GC")){
            A = -60.0;// average RSSI for beacon installed in George Roussos office
        } else if (location.equals("SR")) {
            A =  -50.0; // average RSSI for beacon isntalled in Staff Room, next to lifts
        } else if (location.equals("SG")) {
            A = -62.0; // average RSSI for beacon isntalled in Systems Group Area
        }
        return Math.pow(10.0,((rssi-(A))/-25.0));//-60dBm is average RSSI at 1m distance i.e. A
    }

    ///Finding BLE Devices - BLE scan listener with Callback methods
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            //String advertisingString = byteArrayToHex(result.getScanRecord().getBytes());
            dName = result.getDevice().getName();
            if (dName != null){
                dName = (result.getDevice().getName()).trim();
            }
            macAddress = result.getDevice().getAddress();
            if (macAddress != null){
                macAddress = (result.getDevice().getAddress()).trim();
            }

            if (macAddress != null && macAddress.equals("F4:46:EA:8F:C2:2D")) {
                rssiGR = result.getRssi();
                metersGR = getDistance(rssiGR,"GC");

            } else if (macAddress != null && macAddress.equals("C3:4E:E7:D1:2E:3A")) {
                rssiSR = result.getRssi();
                metersSR = getDistance(rssiSR, "SR");

            } else if (macAddress != null && macAddress.equals("C7:96:98:06:4C:64")) {
                rssiSG = result.getRssi();
                metersSG = getDistance(rssiSG,"SG");
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    //// Method for initializing Bluetooth
    // @param none
    // @return nothing
    public void initialiseBluetooth(){

        //Check if device does support BT by hardware
        if (!getBaseContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            //Toast shows a message on the screen for a LENGTH_SHORT period
            Toast.makeText(this, "BLUETOOTH NOT SUPPORTED!", Toast.LENGTH_SHORT).show();
            finish();
        }
        //Check if device does support BT Low Energy by hardware. Else close the app(finish())!
        if (!getBaseContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //Toast shows a message on the screen for a LENGTH_SHORT period
            Toast.makeText(this, "BLE NOT SUPPORTED!", Toast.LENGTH_SHORT).show();
            finish();
        }else {
            //If BLE is supported, get the BT adapter. Preparing for use!
            mBTAdapter = BluetoothAdapter.getDefaultAdapter();
            //If getting the adapter returns error, close the app with error message!
            if (mBTAdapter == null) {
                Toast.makeText(this, "ERROR GETTING BLUETOOTH ADAPTER!", Toast.LENGTH_SHORT).show();
                finish();
            }else{
                //Check if BT is enabled! This method requires BT permissions in the manifest.
                if (!mBTAdapter.isEnabled()) {
                    //If it is not enabled, ask user to enable it with default BT enable dialog! BT enable response will be received in the onActivityResult method.
                    Intent enableBTintent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBTintent, PERMISSION_REQUEST_COARSE_BL);
                }
            }
        }
    }

    //// Method accepts text-to-speech generated to file in internal memory, plays the audio and controls
    ///  the state machine based on unique utterance ids
    // @param String currentLocation - name of current location of user, File file - TTS converted to file which
    // is saved in internal memory, String utteranceID - unique ID that is used to control the state-machine
    // @return nothing
    // Error checking for the media player is included
    public void mediaPlayer (final File file, final String utteranceID, final String currentLocationName){

       try {
           Uri myuri = Uri.parse(file.getPath());
           mPlayer = MediaPlayer.create(this,myuri);
           AudioAttributes myAttributes = new AudioAttributes.Builder()
                   .setUsage(AudioAttributes.USAGE_MEDIA)
                   .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                   .build();
           mPlayer.setAudioAttributes(myAttributes);
           mPlayer.setLooping(false);
           mPlayer.start();

           mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
               @Override
               public void onCompletion(MediaPlayer mp) {
                   mp.stop();
                   mp.release();
                   mPlayer = null;
                   file.delete();

                   switch (utteranceID) {
                       case "OnInitialization":
                           //fixLocation();//Fix location before getting location updates
                           mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                           break;
                       case "OnLocationChanged":
                           mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                           break;
                       case "OnLocationChangedStart":
                           startOfRoute = false;
                           menu(currentLocationName);
                           break;
                       case "Menu":
                           mytempLocation = currentLocationName;
                           startVoiceRecognitionActivity();
                           break;
                       case "Repeat":
                           startOfRoute = true;
                           duringRoute = false;
                           firstTextFlag = false;
                           secondTextFlag = false;
                           mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                           break;
                       case "OnChoosingRoute":
                           launchRoute(currentLocationName);
                           break;
                       case "OnStartText":
                           mRequestStartTime = SystemClock.elapsedRealtime();
                           startOfRoute = false;
                           duringRoute = true;
                           startTextFlag = true;
                           firstTextFlag = false;
                           secondTextFlag = false;
                           mIALocationManager.removeLocationUpdates(mIALocationListener);
                           firstConfirm(currentLocationName);
                           break;
                       case "OnFirstText":
                           startOfRoute = false;
                           duringRoute = true;
                           startTextFlag = false;
                           firstTextFlag = true;
                           secondTextFlag = false;
                           mIALocationManager.removeLocationUpdates(mIALocationListener);
                           secondConfirm(currentLocationName);
                           break;
                       case "OnSecondText":
                           startOfRoute = false;
                           duringRoute = true;
                           startTextFlag = false;
                           firstTextFlag = false;
                           secondTextFlag = true;
                           mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                           break;
                       case "OnEndRoute":
                           startOfRoute = false;
                           duringRoute = false;
                           firstTextFlag = false;
                           secondTextFlag = false;
                           mIALocationManager.removeLocationUpdates(mIALocationListener);
                           mIALocationManager.unregisterRegionListener(mRegionListener);
                           endFlag = true;
                           playSound(2);
                           double duration = mRequestStartTime != 0
                                  ? (SystemClock.elapsedRealtime() - mRequestStartTime) / 1e3
                                   : 0d;
                           Toast.makeText(MainActivity.this, "Elapsed Time :" + duration, Toast.LENGTH_SHORT).show();
                           estimateTime(currentLocationName,duration);
                           break;
                       case "OnNoRouteConfigured":
                           startOfRoute = false;
                           menu(currentLocationName);
                           break;
                       case "OnFirstConfirm":
                           firstConfirmFlag = true;
                           secondConfirmFlag = false;
                           break;
                       case "OnSecondConfirm":
                           secondConfirmFlag = true;
                           firstConfirmFlag = false;
                           break;
                       case "OnTimeEstimate":
                           //Do nothing
                           break;
                   }
               }
           });

           mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
               @Override
               public boolean onError(MediaPlayer mp, int what, int extra) {
                   Toast.makeText(MainActivity.this, "ERROR!!!!!!", Toast.LENGTH_SHORT).show();
                   switch (what) {
                       case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                           switch (extra) {
                               case MediaPlayer.MEDIA_ERROR_IO:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_MALFORMED:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                   break;
                           }
                           logText("ERROR: " + "What Code: " + what + "Extra Code: " +extra,25,0xFFFF4046);
                           break;
                       case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                           switch (extra) {
                               case MediaPlayer.MEDIA_ERROR_IO:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_MALFORMED:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                                   break;
                               case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                   break;
                           }
                           logText("ERROR: " + "What Code: " + what + "Extra Code: " +extra,25,0xFFFF4046);
                           break;
                   }
                   return false;
               }
           });
       } catch (Exception e) {
           Toast.makeText(MainActivity.this, "ERROR!!!", Toast.LENGTH_LONG).show();
       }
   }
    //// Method that uses the MediaPlayer object to play sounds
    // @param int audioCommandIndex - array index to choose from 3 different sounds
    // @return nothing
   public void playSound (final int audioCommandIndex) {

        try {
            mPlayer = MediaPlayer.create(this,audioCommands[audioCommandIndex]);
            AudioAttributes myAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mPlayer.setAudioAttributes(myAttributes);
            mPlayer.setLooping(false);
            mPlayer.start();

            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.stop();
                    mp.release();
                    mPlayer = null;
                }
            });

            mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(MainActivity.this, "ERROR!!!!!!", Toast.LENGTH_SHORT).show();
                    switch (what) {
                        case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                            switch (extra) {
                                case MediaPlayer.MEDIA_ERROR_IO:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                    break;
                            }
                            logText("ERROR: " + "What Code: " + what + "Extra Code: " +extra,25,0xFFFF4046);
                            break;
                        case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                            switch (extra) {
                                case MediaPlayer.MEDIA_ERROR_IO:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                                    break;
                                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                                    break;
                            }
                            logText("ERROR: " + "What Code: " + what + "Extra Code: " +extra,25,0xFFFF4046);
                            break;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "ERROR!!!", Toast.LENGTH_LONG).show();
        }
    }
    //// Method that uses the TTS engine and saves the text utterance retreived from server to a file in internal memory
    // @param String currentLocation - name of current location of user, String text - text to be spoken, String - unique utterance id to be passed to the media player
    // @return nothing
    // Runs on a separate thread
    private void texttospeech(final String text, final String utteranceId,final String currentLocationName){

        mLogging.setText("");
        mLogging.setText(text);
        mLogging.setTextSize(35);
        mLogging.setTextColor(0xFFFFFFFF);
        mScrollView.smoothScrollBy(0, mLogging.getBottom());
        final File file = new File(this.getFilesDir(),"tempfile");

        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {//Speech rate and pitch can be configured from the XML configuration file
                if(status != TextToSpeech.ERROR){
                    t1.setLanguage(Locale.UK);
                    t1.setSpeechRate(DEFAULT_SPEECH_RATE);
                    t1.setPitch(DEFAULT_PITCH);
                    t1.synthesizeToFile(text,params,file,utteranceId);
                }
            }
        });

        t1.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {

            }

            @Override
            public void onDone(final String utteranceId) {
                if (utteranceId.equals("OnInitialization")){
                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });

                } else if (utteranceId.equals("OnLocationChanged")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });

                } else if (utteranceId.equals("OnLocationChangedStart")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("Menu")){
                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                } else if (utteranceId.equals("Repeat")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnChoosingRoute")){

                            MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                                @Override
                                public void run() {
                                    mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnStartText")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnFirstText")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnSecondText")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnEndRoute")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }else if (utteranceId.equals("OnNoRouteConfigured")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }

                else if (utteranceId.equals("OnFirstConfirm")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }

                else if (utteranceId.equals("OnSecondConfirm")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }

                else if (utteranceId.equals("OnTimeEstimate")){

                    MainActivity.this.runOnUiThread(new Runnable() {//Media player needs to run on main UI Thread
                        @Override
                        public void run() {
                            mediaPlayer(file,utteranceId,currentLocationName);
                        }
                    });
                }

            }

            @Override
            public void onError(String utteranceId) {
                //TODO Implement error checking if required

            }
        });
    }

    @Override // Callback method invoked when the screen is touched - gesture object invoked as below based on gestures
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureObject.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    ///Gesture class with onDoubleTap and onFling methods
    private class DebugGesture extends GestureDetector.SimpleOnGestureListener{//Inner class for Gesture control

        @Override
        public boolean onDoubleTap(MotionEvent e) {//Double tap in middle of device screen
            if(!endFlag & !firstConfirmFlag & !secondConfirmFlag){//If during the route - enable seek from the start of the audio
                if(mPlayer != null && mPlayer.isPlaying()){
                    mPlayer.seekTo(0);
                }
            }else if (endFlag & !firstConfirmFlag & !secondConfirmFlag){//Else if end of route - re-initialize the state machine for subsequent route selections
                playSound(0);//Play intro sound
                new CountDownTimer(2000, 1000){// 2 seconds count down timer
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
                        startOfRoute = true;//Reset state flags
                        endFlag = false;
                        mIALocationManager.registerRegionListener(mRegionListener);//Listen for location first
                        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                        //String initString = initializeTTS(id);//Initialize text to speech
                        //texttospeech(initString,"OnInitialization","");//Launch "OnInitialization" state
                    }
                }.start();

            }else if (!endFlag & firstConfirmFlag & !secondConfirmFlag){
                firstConfirmFlag = false;
                mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);

            }else if (!endFlag & !firstConfirmFlag & secondConfirmFlag){
                secondConfirmFlag = false;
                mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
            }
            return super.onDoubleTap(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            if(e2.getX() > e1.getX()){
                if(mPlayer != null && mPlayer.isPlaying())//Action when swiping to the RIGHT
                {
                   mPlayer.pause();//Pause audio when user swipes to the Right
                }

            } else if (e2.getX() < e1.getX()){//Action when swiping to the LEFT

                if(mPlayer != null)
                {
                   mPlayer.start();//Resume audio when user swipes to the left
                }
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    @Override//Main onCreate callback method called as soon as the application is launched
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] neededPermissions = {//ALL required permissions for this application
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.RECORD_AUDIO
        };

        ActivityCompat.requestPermissions( this, neededPermissions, MY_CODE_PERMISSIONS);//Request permissions
        setContentView(R.layout.activity_main);

        mLogging = (TextView) findViewById(R.id.mytextView);//Text view for displaying large font text
        mTextView = (TextView) findViewById(R.id.coordinates);//Text view for displaying location and other data for debug
        mTextView.setTextSize(15);//Small text for debug data
        mTextView.setTextColor(0xFFFF4046);
        mScrollView = (ScrollView) findViewById(R.id.myscrollView);//Scrollable view for the large font text

        Log.d(TAG, "onCreate: starting Asynctask");
        DownloadXML downloadData = new DownloadXML();//Creating an instance of class DownloadXML which extends AsynchTask
        downloadData.execute("http://naviblind.000webhostapp.com/ia_config.xml");//Calling execute method with specified XML Url
        Log.d(TAG, "onCreate: done");

        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"");//Initialize text to speech engine Bundle of params

        gestureObject = new GestureDetectorCompat(this, new DebugGesture());//Create a new instance of Gesture class

        //Create a new instance of IALocationManager using its create() method
        mIALocationManager = IALocationManager.create(this);

        //Create edges with unique clockwise (lat, lon) points
//        List edges = Arrays.asList(new double[][]{{51.52231720,-0.13089649},{51.52230740,-0.13089247},
//                {51.52231219,-0.13088007},{51.52230218,-0.13087805}});
//        //Create the geofence for 10 seconds in floor number 2
//        IAGeofence geofence = new IAGeofence.Builder()
//                .withEdges(edges)
//                .withFloor(2)
//                .withId("Stairs E GeoFence")
//                .withTransitionType(IAGeofence.GEOFENCE_TRANSITION_ENTER | IAGeofence.GEOFENCE_TRANSITION_EXIT).build();
//
//        IAGeofenceRequest geofenceRequest = new IAGeofenceRequest.Builder()
//                .withGeofence(geofence)
//                .withInitialTrigger(IAGeofenceRequest.INITIAL_TRIGGER_ENTER).build();
//
//        mIALocationManager.addGeofences(geofenceRequest,mIAGeofenceListener);

        //Create a new instance of SpeechRecognizer using its createSpeechRecognizer() method
        mSR = SpeechRecognizer.createSpeechRecognizer(this);
        //Set the Speech Listener as the new speechListener defined in inner class below
        mSR.setRecognitionListener(new speechListener());

        initialiseBluetooth();//Check Bluetooth is enabled and initialize
        initialiseBLE();//Initialize BLE Scan setting
        //startLeScan(true);//Start scanning for bluetooth devices if RSSI values or GATT configuration are required
        playSound(0);//Play intro sound

        new CountDownTimer(5000, 1000){// 5 seconds count down timer to allow for data download from XML
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {//At end of 5 seconds, initialize core configuration parameters
                ConfigEntry myConfigObject = configData.get(0);
                DEFAULT_DISPLACEMENT = Float.parseFloat(myConfigObject.getDefaultdisplacement());
                DEFAULT_INTERVAL = Long.parseLong(myConfigObject.getDefaultinterval());
                DEFAULT_CURRENT_DISTANCE = Double.parseDouble(myConfigObject.getDefaultcurrentdistance());
                DEFAULT_ACCURACY = Double.parseDouble(myConfigObject.getDefaultaccuracy());
                DEFAULT_FLOOR_CERTAINTY = Float.parseFloat(myConfigObject.getDefaultfloorcertainty());
                DEFAULT_SPEECH_RATE = Float.parseFloat(myConfigObject.getDefaultspeechrate());
                DEFAULT_PITCH = Float.parseFloat(myConfigObject.getDefaultpitch());
                DEFAULT_FF_LATITUDE = Double.parseDouble(myConfigObject.getFirstfixlatitude());
                DEFAULT_FF_LONGITUDE = Double.parseDouble(myConfigObject.getFirstfixlongitude());
                DEFAULT_FF_FLOOR = Integer.parseInt(myConfigObject.getFirstfixfloor());
                DEFAULT_FF_ACCURACY = Float.parseFloat(myConfigObject.getFirstfixaccuracy());

                IALocationRequest request = IALocationRequest.create();//Set High accuracy as priority and fastest interval and default displacement
                request.setPriority(IALocationRequest.PRIORITY_HIGH_ACCURACY);//High-accuracy updates requested
                request.setFastestInterval(DEFAULT_INTERVAL);//Explicitly set the fastest interval for location updates in milliseconds
                request.setSmallestDisplacement(DEFAULT_DISPLACEMENT);//Set the minimum displacement between location updates in meters
                mIALocationManager.registerRegionListener(mRegionListener);//Start listening for change in region
                mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                //String initString = initializeTTS();//Initialize TTS parameters and get current floor plan details
                //texttospeech(initString,"OnInitialization","");//Launch "On Initialization" state
            }
        }.start();
    }

    @Override//Check whether relevant permissions have been granted and notify with a Toast
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_CODE_PERMISSIONS: {// If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // permission was granted

                    Toast.makeText(MainActivity.this, "Permissions granted", Toast.LENGTH_SHORT).show();
                    permissionOK = true;
                } else {// permission denied

                    Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public IAGeofenceListener mIAGeofenceListener = new IAGeofenceListener() {
        @Override
        public void onGeofencesTriggered(IAGeofenceEvent iaGeofenceEvent) {
            //Do something with the triggered geofences in geoFenceEvent
            Toast.makeText(MainActivity.this, "In GEOFENCE!!!...", Toast.LENGTH_LONG).show();
            //mTextView.setText("Entered Geofence");
            //mTextView.setTextSize(18);
            //mTextView.setTextColor(0xFFFFFFFF);

        }
    };

    public IALocationListener mIALocationListener = new IALocationListener() {
      //Implement an IALocationListener interface and override its onLocationChanged() callback method
      @Override
      public void onLocationChanged(IALocation iaLocation) {//Main logic for Geofencing and Control of state machine implemented here

          String currentLocationName = "";//Debug data to monitor location and accuracy
          mTextView.setText(String.format(Locale.UK, "Latitude: %.8f,\nLongitude: %.8f,\nAccuracy: %.8f,\nCertainty: %.8f,\nLevel: %d, \nAltitude: %.8f.",
                  iaLocation.getLatitude(), iaLocation.getLongitude(),iaLocation.getAccuracy(),iaLocation.getFloorCertainty(),
                  iaLocation.getFloorLevel(),iaLocation.getAltitude()));
          mTextView.setTextSize(15);

          if (iaLocation.getAccuracy() <= DEFAULT_ACCURACY) { //iaLocation.getFloorLevel() == 2 && iaLocation.getAccuracy()<=5
              String locationString = "No Location";//Resume only if accuracy is adequate, initialize to No location
              for (int i = 0; i < roomwaypointData.size();i++){//Loop through all the floor locations and check how far from those locations the user is
                  RoomWayPointEntry myObject = roomwaypointData.get(i);
                  String currentFloodPlanId = myObject.getFloorplanid().trim();
                  //Toast.makeText(MainActivity.this, "::" + currentFloodPlanId + ":::" + currentId, Toast.LENGTH_LONG).show();
                  if (currentFloodPlanId.equalsIgnoreCase(currentId)){
                    double comparelatitude = Double.parseDouble(myObject.getLatitude());
                    double comparelongitude = Double.parseDouble(myObject.getLongitude());
                    Haversine haversineObject = new Haversine(); //Create the Haversine object
                    currentDistance = 1000*(haversineObject.distance(iaLocation.getLatitude(),iaLocation.getLongitude(),comparelatitude,comparelongitude));

                    if (currentDistance <= DEFAULT_CURRENT_DISTANCE){//If accuracy of a decent level, get location details
                        int currentLevelNumber = Integer.parseInt(myObject.getLevelnumber());
                        int estimatedLevelNumber = iaLocation.getFloorLevel();
                        if (currentLevelNumber == estimatedLevelNumber & iaLocation.getFloorCertainty() >= DEFAULT_FLOOR_CERTAINTY) {
                            String textutternace = myObject.getText();
                            String descriptionutternace = myObject.getDescription();
                            currentLocationName = myObject.getName();//Always monitor current location name
                            locationString = textutternace + "... " + descriptionutternace;
                            break;//Break as soon as a location is found nearby
                        }
                    }
                  }
              }

              if (!locationString.equals("No Location")){//Stop listening to region and location updates of a location has been found
              mIALocationManager.removeLocationUpdates(mIALocationListener);
                  mIALocationManager.unregisterRegionListener(mRegionListener);
              }

              if ((startOfRoute)&(!duringRoute)&(!locationString.equals("No Location"))){
                  texttospeech(locationString,"OnLocationChangedStart",currentLocationName);
              }else if ((!startOfRoute)&(!duringRoute)&(!locationString.equals("No Location"))) {
                  texttospeech(locationString, "OnLocationChanged", currentLocationName);
              }else if ((!startOfRoute)&(duringRoute)&(!firstTextFlag)&(!secondTextFlag)&(!locationString.equals("No Location"))) {
                  launchFirstText(currentLocationName);
                  //Toast.makeText(MainActivity.this, "Location Changing...in route!!", Toast.LENGTH_SHORT).show();
              }else if ((!startOfRoute)&(duringRoute)&(firstTextFlag)&(!secondTextFlag)&(!locationString.equals("No Location"))) {
                  launchSecondText(currentLocationName);
              }else if ((!startOfRoute)&(duringRoute)&(!firstTextFlag)&(secondTextFlag)&(!locationString.equals("No Location"))){
                  launchEndText(currentLocationName);

              }else {
                  playSound(1);//Play tick sound until a location is found
              }
          } else {
              playSound(1);//Play tick sound while waiting for adequate accuracy
          }
      }

      @Override//Check calibration and WIFI service and provide a prompt on the screen for debugging purposes
      public void onStatusChanged(String provider, int status, Bundle bundle) {
          mPlayer = null;
          switch (status) {
              case IALocationManager.STATUS_CALIBRATION_CHANGED:
                  String quality = "unknown";
                  switch (bundle.getInt("quality")) {
                      case IALocationManager.CALIBRATION_POOR:
                          quality = "Poor";
                          calibrationOK = false;
                          break;
                      case IALocationManager.CALIBRATION_GOOD:
                          quality = "Good";
                          calibrationOK = true;
                          break;
                      case IALocationManager.CALIBRATION_EXCELLENT:
                          quality = "Excellent";
                          calibrationOK = true;
                          break;
                  }
                  //logText("Calibration Quality: " + quality + " Status Code: " + status);
                  logText("Calibration: " + quality,25,0xFFFF4046);
                  break;
              case IALocationManager.STATUS_AVAILABLE:
                  //logText("onStatusChanged: Available" + " Status Code: " + status);
                  logText("Status: Available",25,0xFFFF4046);
                  statusOK = true;
                  break;
              case IALocationManager.STATUS_LIMITED:
                  logText("Status: Limited",25,0xFFFF4046);
                  statusOK = true;
                  playSound(1);//Play tick sound
                  break;
              case IALocationManager.STATUS_OUT_OF_SERVICE:
                  logText("Status: Out of service",25,0xFFFF4046);
                  statusOK = false;
                  playSound(1);//Play tick sound
                  break;
              case IALocationManager.STATUS_TEMPORARILY_UNAVAILABLE:
                  logText("Status: Temporarily unavailable",25,0xFFFF4046);
                  playSound(1);//Play tick sound
                  statusOK = false;
          }
      }
  };

    private IARegion.Listener mRegionListener = new IARegion.Listener() {
        @Override//Floor plan ID retreived as soon as region change is detected
        public void onEnterRegion(IARegion iaRegion) {
            if (iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN) {
                currentId = iaRegion.getId();
                Toast.makeText(MainActivity.this, "REGION :" + currentId, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "floorPlan changed to " + id);
                mIALocationManager.removeLocationUpdates(mIALocationListener);
                mIALocationManager.unregisterRegionListener(mRegionListener);
                String initString = initializeTTS(currentId);//Initialize TTS parameters and get current floor plan details
                texttospeech(initString,"OnInitialization","");//Launch "On Initialization" state
                // Type of utterance is selected based on floor plan ID
            }
        }

        @Override
        public void onExitRegion(IARegion iaRegion) {
            // leaving a previously entered region
            if (iaRegion.getType() == IARegion.TYPE_FLOOR_PLAN) {
                String id = iaRegion.getId();
                Log.d(TAG, "floorPlan changed to " + id);
                //Do nothing in particular as soon as you leave the region
            }
        }
    };
    @Override
    protected void onResume() {
        if(mPlayer != null)
        {
            mPlayer.start();
        }

        super.onResume();
        //Toast.makeText(MainActivity.this, "DEBUG::onResume() callback...", Toast.LENGTH_LONG).show();
        //mRequestStartTime = SystemClock.elapsedRealtime();
        startLeScan(true);
    }

    @Override
    protected void onPause() {
        if(mPlayer != null && mPlayer.isPlaying())
        {
            mPlayer.pause();
        }

        if(t1 !=null){
            t1.stop();
            t1.shutdown();
        }

        super.onPause();
        mIALocationManager.removeLocationUpdates(mIALocationListener);
        mIALocationManager.removeGeofenceUpdates(mIAGeofenceListener);
        mIALocationManager.unregisterRegionListener(mRegionListener);
        startLeScan(false);
    }

    @Override
    protected void onDestroy() {
        mIALocationManager.destroy();
        startLeScan(false);
        super.onDestroy();
        if(mPlayer != null) {
            mPlayer.release();//releasing and nullifying MediaPLayer
        }

        if(t1 !=null){
            t1.stop();
            t1.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.debug_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.menu_item_floor_plan:
                Intent i = new Intent(MainActivity.this, FloorPlanActivity.class);
                startActivity(i);
                if(mPlayer != null && mPlayer.isPlaying())
                {
                    mPlayer.pause();
                }
                return true;

            case R.id.menu_item_BLE:
                Intent k = new Intent(MainActivity.this, BluetoothScanner.class);
                startActivity(k);
                if(mPlayer != null && mPlayer.isPlaying())
                {
                    mPlayer.pause();
                }
                return true;

            case R.id.menu_test_activity:
                Intent m = new Intent(MainActivity.this, BLEMonitor.class);
                startActivity(m);
                if(mPlayer != null && mPlayer.isPlaying())
                {
                    mPlayer.pause();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /// SpeechListener class implements RecognitionListener interface
    // A number of callback methods called based on RecognitionListener state
    private class speechListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged");
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        @Override
        public void onError(int error) {
            Log.d(TAG,  "error " +  error);
            mLogging.setText("");
            mLogging.setText("Error Number " + error + " occurred.");
            mLogging.setTextSize(20);
            mLogging.setTextColor(0xFFFF4046);
            mScrollView.smoothScrollBy(0, mLogging.getBottom());
            //long startTime = System.currentTimeMillis();
            texttospeech(repatutterance,"Repeat","");
            //long estimatedTime = System.currentTimeMillis() - startTime;
        }

        @Override
        public void onResults(Bundle results) {
            Log.d(TAG, "onResults " + results);

            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String input = data.get(0).toString();
            String myInput = input.trim();
            mLogging.setText("");
            mLogging.setText(String.format(Locale.UK, "\n %s", "You said : " + myInput));
            mLogging.setTextSize(35);
            mLogging.setTextColor(0xFFFFFFFF);
            mScrollView.smoothScrollBy(0, mLogging.getBottom());
            currentDistance = 100d;

            boolean match = roomwaypointVoiceMatch(myInput);

            Toast.makeText(MainActivity.this, "DEBUG::" + myInput, Toast.LENGTH_SHORT).show();

//            for (int i = 0; i < roomwaypointData.size();i++){//Iterate through both Room and Waypoint data
//                RoomWayPointEntry myObject = roomwaypointData.get(i);
//                String currentName = myObject.getName();
//                currentName = currentName.trim();
//                if (myInput.equalsIgnoreCase(currentName)){
//                    match = true;
//                }
//            }
            if (myInput.equals("yes go on")){//NOT USED YET
                duringRoute = true;
                mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mIALocationListener);
                //Need to request Geofence updates but requestGeofenceUpdates not available!!!!
                //startLeScan(true);
            }else if (match){
                duringRoute = false;
                Toast.makeText(MainActivity.this, "START::"+mytempLocation, Toast.LENGTH_SHORT).show();
                Toast.makeText(MainActivity.this, "END::"+myInput, Toast.LENGTH_SHORT).show();
                chooseRoute(mytempLocation,myInput);//Pass myInput which is end location and mytempLocation which is current location and hence start as parameters
            }

            else{
                texttospeech(repatutterance,"Repeat","");
            }

        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults");

            texttospeech(repatutterance,"Repeat","");
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
            texttospeech(repatutterance,"Repeat","");
        }
    }

    /**
     * Fire an intent to start the voice recognition activity.
     */
    private void    startVoiceRecognitionActivity()
    {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak now...");
        //intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"com.example.salfino.voice_test");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,8000);
        mSR.startListening(intent);
    }

    private class DownloadXML extends AsyncTask<String,Void,String> {//Defined as an Inner class
        private static final String TAG = "DownloadData";

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.d(TAG, "onPostExecute: parameter is " + s);

            if (s != null)
            {
                xmlFile = s;//XML file in string format
            }

            else {
                xmlFile = rawXML();
            }
                ParseIndoorAtlas parseIndoorAtlas = new ParseIndoorAtlas();//Create a new instance of ParseIndoorAtlas

                //Parse for each datatype amd store in respective ArrayList:
                parseIndoorAtlas.parseText(xmlFile);
                textData = parseIndoorAtlas.getTextData();

                parseIndoorAtlas.parseConfiguration(xmlFile);
                configData = parseIndoorAtlas.getConfigurationData();

                parseIndoorAtlas.parseRoomWayPoint(xmlFile);
                roomwaypointData = parseIndoorAtlas.getRoomWayPointData();//Retreive both room and waypoint data

                parseIndoorAtlas.parseRoom(xmlFile); //downloaded XML file
                roomData = parseIndoorAtlas.getRoomData();//Get room data only

                parseIndoorAtlas.parseRoute(xmlFile);
                routeData = parseIndoorAtlas.getRouteData();//Get route data

                parseIndoorAtlas.parseWayPoint(xmlFile);
                waypointData = parseIndoorAtlas.getWayPointData();//Get waypoint data only

                parseIndoorAtlas.parseEvent(xmlFile);
                eventData = parseIndoorAtlas.getEventData();//Get events data if any available

        }

        @Override
        protected String doInBackground(String... params) {
            Log.d(TAG, "doInBackground: starts with " + params[0]);
            int count = params.length;
            String indoorAtlasFeed = "";
            for (int i = 0; i < count; i++) {
                indoorAtlasFeed = downloadXML(params[i]);//Calling downloadXML method with all file Urls in case more than one url is provided - currently only one url is provided
            }
            if (indoorAtlasFeed == null) {
                Log.e(TAG, "doInBackground: Error downloading XML data");
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,"Error downloading XML data. Using default configuration" , Toast.LENGTH_SHORT).show();
                    }
                });

            }
            return indoorAtlasFeed;
        }

        private String rawXML (){

            StringBuilder xmlResult = new StringBuilder();
            try {
                InputStream inputStream = getResources().openRawResource(R.raw.ia_config);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader);

                int charsRead;
                char[] inputBuffer = new char[500];//Read 500 characters at a time
                while(true){
                    charsRead = reader.read(inputBuffer);
                    if(charsRead < 0){//End of stream of data
                        break;
                    }
                    if(charsRead > 0){//Keep count of number of characters read from stream
                        xmlResult.append(String.copyValueOf(inputBuffer,0,charsRead));//Append until there is no more data to read
                    }
                }

                reader.close();//All IO object will be closed
                return xmlResult.toString();

            } catch (IOException e) {
                // Error handling
                Log.e(TAG, "getXML from RAW: Error Detected! " + e.getMessage());
            }
            return null;

        }
        private String downloadXML (String urlPath){
            StringBuilder xmlResult = new StringBuilder();

            try{
                URL url = new URL(urlPath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();//Open HTTP connection
                int response = connection.getResponseCode();//Get HTTP response code
                Log.d(TAG, "downloadXML: The response code was " + response);
                InputStream inputStream = connection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader);

                int charsRead;
                char[] inputBuffer = new char[500];//Read 500 characters at a time
                while(true){
                    charsRead = reader.read(inputBuffer);
                    if(charsRead < 0){//End of stream of data
                        break;
                    }
                    if(charsRead > 0){//Keep count of number of characters read from stream
                        xmlResult.append(String.copyValueOf(inputBuffer,0,charsRead));//Append until there is no more data to read
                    }
                }
                reader.close();//All IO object will be closed

                return xmlResult.toString();

            } catch (MalformedURLException e) {
                Log.e(TAG, "downloadXML: Invalid URL " + e.getMessage());
                //Toast.makeText(MainActivity.this,"downloadXML: Invalid URL. Please fix configuration file and relaunch Naviblind" , Toast.LENGTH_SHORT).show();

            } catch (IOException e){
                Log.e(TAG, "downloadXML: IO Exception reading data: " + e.getMessage());
                //Toast.makeText(MainActivity.this,"downloadXML: IO Exception reading data. Please fix configuration file and relaunch Naviblind" , Toast.LENGTH_SHORT).show();

            } catch (SecurityException e){
                Log.e(TAG, "downloadXML: Security Exception. Needs Permission! " + e.getMessage());
                //Toast.makeText(MainActivity.this,"downloadXML: Security Exception. Needs Permission. Please fix configuration file and relaunch Naviblind" , Toast.LENGTH_SHORT).show();
                //e.printStackTrace();
            }

            return null;
        }

    }

}
