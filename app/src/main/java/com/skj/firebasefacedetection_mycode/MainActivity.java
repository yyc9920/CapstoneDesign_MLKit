package com.skj.firebasefacedetection_mycode;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import com.ibm.cloud.sdk.core.security.Authenticator;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;           //STT 기능 관련 라이브러리를 import
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;                         //STT 기능 관련 라이브러리를 import
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.speech_to_text.v1.websocket.RecognizeCallback;
import com.skj.firebasefacedetection_mycode.FaceDetection.FaceDetectionProcessor;
import com.skj.firebasefacedetection_mycode.FaceDetection.FaceGraphic;
import com.skj.firebasefacedetection_mycode.Interfaces.FrameReturn;
import com.skj.firebasefacedetection_mycode.Process_part.CameraSourcePreview;
import com.skj.firebasefacedetection_mycode.Process_part.FrameMetadata;
import com.skj.firebasefacedetection_mycode.Process_part.GraphicOverlay;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;

public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        CompoundButton.OnCheckedChangeListener,
        FrameReturn {
    private static final String FACE_DETECTION = "Face Detection";
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUESTS = 1;
    private static final int REQUEST_TAKE_ALBUM = 2;

    BluetoothSPP bt;
    Bitmap originalImage = null;

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private Button btn_blue;
    private Button albumButton, captureBtn;
    private boolean safeToTakePicture = true;
    private boolean smileHandsFreeChecked;
    private ToggleButton handsfree;
    private ToggleButton smileHandsFree;
    private SpeechToText speechService;
    private TextView returnedText;
    private MicrophoneInputStream capture;
    private MicrophoneHelper microphoneHelper;
    private String LOG_TAG = "VoiceRecognitionActivity";
    private static final String cheese[] = {"치즈 ",      //해당 단어를 인식하여 사진 캡쳐를 수행
                                            "사진 ",
                                            "스마일 ",
                                            "김치 ",
                                            "캡처 ",
                                            "캡쳐 "};
    private int handsFreeCaptureCnt = 1;
    private boolean listening = false;

    String X_f = null;
    String Y_f = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        microphoneHelper = new MicrophoneHelper(this);
        safeToTakePicture = true;
        speechService = initSpeechToTextService();

        albumButton = (Button) findViewById(R.id.albumButton);
        captureBtn = findViewById(R.id.camera_button);
        handsfree = findViewById(R.id.handsFree);
        returnedText = findViewById(R.id.returnedtext);
        smileHandsFree = findViewById(R.id.smile_toggle);

        //핸즈프리 캡쳐 기능을 사용할 때 토글시켜주는 버튼의 동작을 구현
        handsfree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!listening) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handsfree.setBackground(getDrawable(R.drawable.voicerecongnition_clicked));     //토글버튼이 활성화됬을 때 버튼 모양 설정
                        }
                    });
                    capture = microphoneHelper.getInputStream(true);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                speechService.recognizeUsingWebSocket(getRecognizeOptions(capture), new MicrophoneRecognizeDelegate());
                            } catch (Exception e) {
                                showError(e);
                            }
                        }
                    }).start();

                    listening = true;       //STT 기능을 활성화시키는 코드
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handsfree.setBackground(getDrawable(R.drawable.voicerecongnition));             //토글버튼이 비활성화됬을 때 버튼 모양 설정
                        }
                    });
                    microphoneHelper.closeInputStream();
                    listening = false;      //STT 기능을 비활성화시키는 코드
                }
            }
        });

        smileHandsFree.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {
                    smileHandsFreeChecked = true;
                } else {
                    smileHandsFreeChecked = false;
                }
            }
        });

        albumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getAlbum();
            }
        });

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSource.camera.takePicture(cameraSource.shutterCallback, null, cameraSource.pictureCallback);
            }
        });

        preview = (CameraSourcePreview) findViewById(R.id.firePreview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = (GraphicOverlay) findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        ToggleButton facingSwitch = (ToggleButton) findViewById(R.id.facingswitch);
        facingSwitch.setOnCheckedChangeListener(this);

        /** ==============================================================================
         * Bluetooth Code
         * =============================================================================*/
        bt = new BluetoothSPP(this);

        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);


        if(!bt.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "Bluetooth is not available"
                    , Toast.LENGTH_SHORT).show();
            finish();
        }

        bt.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
            public void onDataReceived(byte[] data, String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });

        bt.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
            public void onDeviceConnected(String name, String address) {
                Toast.makeText(getApplicationContext()
                        , "Connected to " + name + "\n" + address
                        , Toast.LENGTH_SHORT).show();
            }

            public void onDeviceDisconnected() {
                Toast.makeText(getApplicationContext()
                        , "Connection lost", Toast.LENGTH_SHORT).show();
            }

            public void onDeviceConnectionFailed() {
                Toast.makeText(getApplicationContext()
                        , "Unable to connect", Toast.LENGTH_SHORT).show();
            }
        });


        // dialog 속성 설정
        mBuilder.setTitle("Bluetooth 기능 활성화 대화상자")
                .setMessage("Bluetooth를 활성화 하시겠습니까?")
                .setCancelable(false)
                .setPositiveButton("On", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
                            bt.disconnect();
                        } else {
                            Intent intent = new Intent(getApplicationContext(), DeviceList.class);
                            startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
                        }
                    }
                })
                .setNegativeButton("Off", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        AlertDialog dialog = mBuilder.create();
        dialog.show();
        /**====================================================================================
         * Bluetooth Code END
         * ====================================================================================*/


        if (allPermissionsGranted()) {
            // Must confirm this method //
           createCameraSource();
        } else {
            getRuntimePermissions();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case MicrophoneHelper.REQUEST_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void getAlbum(){
        Log.i("getAlbum", "Call");
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.setType(android.provider.MediaStore.Images.Media.CONTENT_TYPE);
        startActivityForResult(intent, REQUEST_TAKE_ALBUM);
    }

    private SpeechToText initSpeechToTextService() {
        Authenticator authenticator = new IamAuthenticator(getString(R.string.speech_text_apikey));     //IBM Cloud STT 서비스의 API key => res/values/strings.xml 참고
        SpeechToText service = new SpeechToText(authenticator);
        service.setServiceUrl(getString(R.string.speech_text_url));            //IBM Cloud STT 서비스의 URL => res/values/strings.xml 참고
        return service;
    }

    private RecognizeOptions getRecognizeOptions(InputStream captureStream) {
        return new RecognizeOptions.Builder()
                .audio(captureStream)
                .contentType(ContentType.OPUS.toString())
                .model("ko-KR_BroadbandModel")      //한국어 모델 사용
                .interimResults(true)
                .inactivityTimeout(2000)
                .build();
    }

    //TODO : 사진 두 번 찍히는 현상 해결할 수 있는 다른 방법 강구해보기.
    private class MicrophoneRecognizeDelegate extends BaseRecognizeCallback implements RecognizeCallback {
        @Override
        public void onTranscription(SpeechRecognitionResults speechResults) {
            System.out.println(speechResults);
            if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                showMicText(text);
                if((handsFreeCaptureCnt % 2) == 1){     //음성인식을 수행하면서 여러 가지 후보를 따지는데, 경우에 따라 같은 단어로 두 개의 후보가 생성되기도 함.
                    handsFreeCapture(text);             //해당 조건검사를 해주지 않으면 사진이 두 번 연속 찍히는 현상 발생. 정상적으로 한 번만 인식되는 경우도 있어 완벽한 조건검사는 아님.
                }
                handsFreeCaptureCnt++;
                if(handsFreeCaptureCnt == 3) handsFreeCaptureCnt=1;
            }
        }

        @Override
        public void onError(Exception e) {
            try {
                // This is critical to avoid hangs
                // (see https://github.com/watson-developer-cloud/android-sdk/issues/59)
                capture.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            showError(e);
            enableMicButton();
        }

        @Override
        public void onDisconnected() {
            enableMicButton();
        }
    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                returnedText.setText(text);
                safeToTakePicture = true;               //카메라 캡쳐를 소프트웨어로 구현하기 위해서는 플래그 검사를 통해 제어해줄 필요가 있음.(구글링한 정보, 없으면 오류남)
            }
        });
    }

    private void handsFreeCapture(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View handsFreeCapture = findViewById(R.id.camera_button);
                for(int i=0; i<6; i++){
                    if (text.equals(cheese[i])){        //인식한 단어와 미리 설정한 캡쳐 수행 단어를 비교하여 같으면 캡쳐 기능 수행.
                        Toast.makeText(MainActivity.this, "Take Photo",
                                Toast.LENGTH_SHORT).show();
                        if (safeToTakePicture) {        //플래그 검사 및 카메라 캡쳐 기능
                            handsFreeCapture.performClick();
                            safeToTakePicture = false;
                        }
                    }
                }
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                // Update the icon background
                handsfree.setBackground(getDrawable(R.drawable.voicerecongnition));
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handsfree.setEnabled(true);
            }
        });
    }

    ///////////////////////// toggle button -> for Camera facing //////////////////////////
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }
    //////////////////////////////////////////////////////////////////////////////////////


    //////////////////////////////// Bluetooth Permission Result /////////////////////////////////
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK)
                bt.connect(data);
        } else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth is enabled!!"
                        , Toast.LENGTH_SHORT).show();

//                bt.setupService();
//                bt.startService(BluetoothState.DEVICE_OTHER);
//                setup();
            } else {
                // Do something if user doesn't choose any device (Pressed back)
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    //////////////////////////////////////////////////////////////////////////////////////////////


    //////////////////////////////// Camera Active Method ////////////////////////////////
    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void createCameraSource() {
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        try {
            FaceDetectionProcessor processor = new FaceDetectionProcessor(getResources());
            processor.frameHandler = this;
            cameraSource.setMachineLearningFrameProcessor(processor);
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: " + FACE_DETECTION, e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
        bt.stopService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!bt.isBluetoothEnabled()) {
            bt.enable();
        } else {
            if(!bt.isServiceAvailable()) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_OTHER);
                setup();
            }
        }
    }


    public void setup() {
        btn_blue = (Button)findViewById(R.id.btn_blue);
        btn_blue.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                bt.send( X_f + "///" + Y_f ,true);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////


    /////////////////////////////Camera Permission Method//////////////////////////////////////////
    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }
    /////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////// OnFrame Method ////////////////////////////////////////////
    /** FrameReturn interface => GETTING FACE INFORMATION */
    //////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onFrame(Bitmap image, FirebaseVisionFace face, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay) {
        originalImage = image;
        FaceGraphic faceGraphic = new FaceGraphic(graphicOverlay);
        float onFrame_X = faceGraphic.translateX(face.getBoundingBox().exactCenterX());
        float onFrame_Y = faceGraphic.translateY(face.getBoundingBox().exactCenterY());
            
       // Just for Debugging.
       // Log.d(this.getClass().getName(), "X_좌표: " + X_f + "//" + "Y_좌표: " + Y_f);
        X_f = Float.toString(onFrame_X);
        Y_f = Float.toString(onFrame_Y);

        if(Float.parseFloat(String.format("%.2f", face.getSmilingProbability())) > 0.8) {
            final View handsFreeCapture = findViewById(R.id.camera_button);
            if (safeToTakePicture && smileHandsFreeChecked) {        //플래그 검사 및 카메라 캡쳐 기능
                handsFreeCapture.performClick();
                safeToTakePicture = false;
            }
        } else {
            safeToTakePicture = true;
        }

        /** 20.04.20 Continuous Bluetooth send Code -> Success! */
        if(bt.isServiceAvailable()) {
            bt.send(X_f + "///" + Y_f,true);
        }else{
            bt.setupService();
            bt.startService(BluetoothState.DEVICE_OTHER);
            bt.send(X_f + "///" + Y_f,true);
        }
    }
}

