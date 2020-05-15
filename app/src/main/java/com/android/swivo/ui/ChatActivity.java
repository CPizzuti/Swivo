package com.android.swivo.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.swivo.R;
import com.android.swivo.data.SharedPreferenceHelper;
import com.android.swivo.data.StaticConfig;
import com.android.swivo.model.Consersation;
import com.android.swivo.model.Message;
import com.android.swivo.util.AudioCall;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;


public class ChatActivity extends AppCompatActivity implements View.OnClickListener {
    private RecyclerView recyclerChat;
    public static final int VIEW_TYPE_USER_MESSAGE = 0;
    public static final int VIEW_TYPE_FRIEND_MESSAGE = 1;
    private ListMessageAdapter adapter;
    private String roomId;
    private ArrayList<CharSequence> idFriend;
    private Consersation consersation;
    private ImageButton btnSend;

    private EditText editWriteMessage;
    private LinearLayoutManager linearLayoutManager;
    public static HashMap<String, Bitmap> bitmapAvataFriend;
    public Bitmap bitmapAvataUser;
    TextToSpeech tts;
    // Audio
    private TextView recordTimeText;
    private ImageButton audioSendButton;
    private Timer timer;
    private long startTime = 0L;
    long timeInMilliseconds = 0L;
    long timeSwapBuff = 0L;
    long updatedTime = 0L;
    private View recordPanel;
    private MediaRecorder mRecorder;
    private String mFileAudioName;
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference storageRef;
    private MediaPlayer mediaPlayer;
    private AudioRecord audioRecord;
    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
    private AudioCall audioCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Intent intentData = getIntent();
        idFriend = intentData.getCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID);
        roomId = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID);
        String nameFriend = intentData.getStringExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND);
        recordPanel = findViewById(R.id.record_panel);


        consersation = new Consersation();
        btnSend = (ImageButton) findViewById(R.id.btnSend);
        btnSend.setOnClickListener(this);

        tts=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.ITALIAN);
                }
            }
        });



        recordTimeText = (TextView) findViewById(R.id.recording_time_text);
        audioSendButton = (ImageButton) findViewById(R.id.btnAudioSend);



        audioSendButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    requestAudioPermissions(); // The startRecord is in requestAudioPermission
                    audioSendButton.getParent()
                            .requestDisallowInterceptTouchEvent(true);
                    recordPanel.setVisibility(View.VISIBLE);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                        || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    stopRecording();
                    recordPanel.setVisibility(View.INVISIBLE);
                    // stopRecording(true);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {

                }
                view.onTouchEvent(motionEvent);
                return true;
            }
        });


        String base64AvataUser = SharedPreferenceHelper.getInstance(this).getUserInfo().avata;
        if (!base64AvataUser.equals(StaticConfig.STR_DEFAULT_BASE64)) {
            byte[] decodedString = Base64.decode(base64AvataUser, Base64.DEFAULT);
            bitmapAvataUser = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } else {
            bitmapAvataUser = null;
        }

        editWriteMessage = (EditText) findViewById(R.id.editWriteMessage);
        if (idFriend != null && nameFriend != null) {
            getSupportActionBar().setTitle(nameFriend);
            linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
            recyclerChat = (RecyclerView) findViewById(R.id.recyclerChat);
            recyclerChat.setLayoutManager(linearLayoutManager);
            adapter = new ListMessageAdapter(this, consersation, bitmapAvataFriend, bitmapAvataUser);
            FirebaseDatabase.getInstance().getReference().child("message/" + roomId).addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    if (dataSnapshot.getValue() != null) {
                        HashMap mapMessage = (HashMap) dataSnapshot.getValue();
                        Message newMessage = new Message();
                        newMessage.idSender = (String) mapMessage.get("idSender");
                        newMessage.idReceiver = (String) mapMessage.get("idReceiver");
                        newMessage.text = (String) mapMessage.get("text");
                        newMessage.timestamp = (long) mapMessage.get("timestamp");
                        newMessage.messageType = (String) mapMessage.get("messageType");
                        newMessage.audioFileName = (String) mapMessage.get("audioFileName");
                        if (!(newMessage.idSender.equals(StaticConfig.UID))){
                            if (newMessage.messageType.equals("AUDIO")){
                            playAudio(newMessage.audioFileName);
                            }
                        }
                        if (!(newMessage.text.equals("AUDIO"))){
                        consersation.getListMessageData().add(newMessage);
                        adapter.notifyDataSetChanged();
                        linearLayoutManager.scrollToPosition(consersation.getListMessageData().size() - 1);
                        }
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
            recyclerChat.setAdapter(adapter);
        }
    }




    private void startrecord() {
        // TODO Auto-generated method stub
        startListening();
        startTime = SystemClock.uptimeMillis();
        timer = new Timer();
        MyTimerTask myTimerTask = new MyTimerTask();
        timer.schedule(myTimerTask, 1000, 1000);
        vibrate();
    }


    private boolean isPlayed(String fileName){
        Context context = getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(StaticConfig.AUDIO_FILE_PLAYED, Context.MODE_PRIVATE);
        return  sharedPref.getBoolean(fileName,false);
    }

    private void setPlayed(String fileName){
        Context context = getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(StaticConfig.AUDIO_FILE_PLAYED, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(fileName, true);
        editor.commit();
    }

    private void playAudio(final String fileName){
        storageRef = storage.getReferenceFromUrl("gs://swivo-956ec.appspot.com"+"/"+fileName+".3gpp");
        File localDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
        try {
            final File outputFile = File.createTempFile("audio_", ".3gpp", localDir);
            if (!isPlayed(fileName)){
            storageRef.getFile(outputFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    // Local temp file has been created
                    System.out.println("FILE OK ");

                    if (mediaPlayer != null ){
                        mediaPlayer.reset();
                    } else {
                        mediaPlayer = new MediaPlayer();
                    }

                    try {
                        mediaPlayer.setDataSource( outputFile.getPath());
                        mediaPlayer.prepare();
                        mediaPlayer.start();

                        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                try {

                                   setPlayed(fileName);


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });



                        // out.deleteOnExit();

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }



                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle any errors
                }
            });

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    private void stoprecord() {
        // TODO Auto-generated method stub
        if (timer != null) {
            timer.cancel();
        }
        if (recordTimeText.getText().toString().equals("00:00")) {
            return;
        }
        recordTimeText.setText("00:00");
        vibrate();
    }

    private void sendAudio() {
        editWriteMessage.setText("");
        final Message newMessage = new Message();
        newMessage.messageType = "AUDIO";
        newMessage.audioFileName= mFileAudioName;
        newMessage.text = "AUDIO";
        newMessage.idSender = StaticConfig.UID;
        newMessage.idReceiver = roomId;
        newMessage.timestamp = System.currentTimeMillis();


        storageRef = storage.getReferenceFromUrl("gs://swivo-956ec.appspot.com"+"/"+newMessage.audioFileName+".3gpp");
        storageRef.putFile(Uri.fromFile(new File(getApplicationContext().getFilesDir()+"/"+newMessage.audioFileName)))
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        // ...
                    }
                });
    }
    private void vibrate() {
        // TODO Auto-generated method stub
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();//mRecoreder has been declared globally
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        mFileAudioName = roomId + "_"+ ts;
        mRecorder.setOutputFile(getApplicationContext().getFilesDir()+"/"+ mFileAudioName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
           // Log.e(LOG_TAG, "prepare() failed");//initiallize LOG_TAG as a string anything u like to get an indication
        }

        mRecorder.start();


}



    private void stopRecording() {
        if (mRecorder != null) {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        stoprecord();
        sendAudio();

        }
    }


    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();
                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            startrecord();
        }
    }

    //Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                   // startrecord();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }


    private void startListening(){

        // RECORD FOR STREAMING IN REALTIME
        int freq = 16000;
        Thread Rthread = null;
        final byte[] buffer = new byte[freq];
        final int bufferSize = AudioRecord.getMinBufferSize(freq,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,  AudioFormat.SAMPLE_RATE_UNSPECIFIED ,
                AudioFormat.CHANNEL_IN_MONO,
                MediaRecorder.AudioEncoder.AMR_NB, bufferSize);
        audioRecord.startRecording();
        Log.i("info", "Audio Recording started");
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        mFileAudioName = roomId + "_"+ ts;
        storageRef = storage.getReferenceFromUrl("gs://swivo-956ec.appspot.com"+"/"+mFileAudioName);

        Rthread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        int numberOfBytes = audioRecord.read(buffer, 0, bufferSize);
                        // Some error checking
                        if (numberOfBytes == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e("error", "The AudioRecord object was not properly initialized");

                        } else if (numberOfBytes == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e("error", "The parameters do not resolve to valid data and indexes.");

                        } else if (numberOfBytes > buffer.length) {
                            Log.e("error", "Read more bytes than is buffer length:" + numberOfBytes + ": " + buffer.length);

                        } else if (numberOfBytes == 0) {
                            Log.e("error", "Read zero bytes");

                        }

                        storageRef.putBytes(buffer);

                        Log.i("Info", buffer.toString());


                    } catch (Throwable t) {
                        Log.e("Error", "Read write failed");
                        t.printStackTrace();
                    }
                }
            }
        });
        Rthread.start();

    }


    private void startListening_2(){

        // RECORD FOR STREAMING IN REALTIME
        int freq = 16000;
        Thread Rthread = null;
        final byte[] buffer = new byte[freq];
        final int bufferSize = AudioRecord.getMinBufferSize(freq,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,  AudioFormat.SAMPLE_RATE_UNSPECIFIED ,
                AudioFormat.CHANNEL_IN_MONO,
                MediaRecorder.AudioEncoder.AMR_NB, bufferSize);
        audioRecord.startRecording();
        Log.i("info", "Audio Recording started");
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        mFileAudioName = roomId + "_"+ ts;
        storageRef = storage.getReferenceFromUrl("gs://swivo-956ec.appspot.com"+"/"+mFileAudioName);



    }

    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            final String hms = String.format(
                    "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(updatedTime)
                            - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                            .toHours(updatedTime)),
                    TimeUnit.MILLISECONDS.toSeconds(updatedTime)
                            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                            .toMinutes(updatedTime)));
            long lastsec = TimeUnit.MILLISECONDS.toSeconds(updatedTime)
                    - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                    .toMinutes(updatedTime));
            System.out.println(lastsec + " hms " + hms);
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        if (recordTimeText != null)
                            recordTimeText.setText(hms);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }

                }
            });
        }
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            Intent result = new Intent();
            result.putExtra("idFriend", idFriend.get(0));
            setResult(RESULT_OK, result);
            this.finish();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        Intent result = new Intent();
        result.putExtra("idFriend", idFriend.get(0));
        setResult(RESULT_OK, result);
        this.finish();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btnSend) {
            String content = editWriteMessage.getText().toString().trim();
            if (content.length() > 0) {
                editWriteMessage.setText("");
                Message newMessage = new Message();
                newMessage.messageType = "TESTO";
                newMessage.text = content;
                newMessage.idSender = StaticConfig.UID;
                newMessage.idReceiver = roomId;
                newMessage.timestamp = System.currentTimeMillis();
                FirebaseDatabase.getInstance().getReference().child("message/" + roomId).push().setValue(newMessage);
            }
        }
    }
}

class ListMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private Consersation consersation;
    private HashMap<String, Bitmap> bitmapAvata;
    private HashMap<String, DatabaseReference> bitmapAvataDB;
    private Bitmap bitmapAvataUser;


    public ListMessageAdapter(Context context, Consersation consersation, HashMap<String, Bitmap> bitmapAvata, Bitmap bitmapAvataUser) {
        this.context = context;
        this.consersation = consersation;
        this.bitmapAvata = bitmapAvata;
        this.bitmapAvataUser = bitmapAvataUser;
        bitmapAvataDB = new HashMap<>();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == ChatActivity.VIEW_TYPE_FRIEND_MESSAGE) {
            View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_friend, parent, false);
            return new ItemMessageFriendHolder(view);
        } else if (viewType == ChatActivity.VIEW_TYPE_USER_MESSAGE) {
            View view = LayoutInflater.from(context).inflate(R.layout.rc_item_message_user, parent, false);
            return new ItemMessageUserHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemMessageFriendHolder) {
            ((ItemMessageFriendHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
            Bitmap currentAvata = bitmapAvata.get(consersation.getListMessageData().get(position).idSender);
            if (currentAvata != null) {
                ((ItemMessageFriendHolder) holder).avata.setImageBitmap(currentAvata);
            } else {
                final String id = consersation.getListMessageData().get(position).idSender;
                if(bitmapAvataDB.get(id) == null){
                    bitmapAvataDB.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/avata"));
                    bitmapAvataDB.get(id).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.getValue() != null) {
                                String avataStr = (String) dataSnapshot.getValue();
                                if(!avataStr.equals(StaticConfig.STR_DEFAULT_BASE64)) {
                                    byte[] decodedString = Base64.decode(avataStr, Base64.DEFAULT);
                                    ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
                                }else{
                                    ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
                                }
                                notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
            }
        } else if (holder instanceof ItemMessageUserHolder) {
            ((ItemMessageUserHolder) holder).txtContent.setText(consersation.getListMessageData().get(position).text);
            if (bitmapAvataUser != null) {
                ((ItemMessageUserHolder) holder).avata.setImageBitmap(bitmapAvataUser);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return consersation.getListMessageData().get(position).idSender.equals(StaticConfig.UID) ? ChatActivity.VIEW_TYPE_USER_MESSAGE : ChatActivity.VIEW_TYPE_FRIEND_MESSAGE;
    }

    @Override
    public int getItemCount() {
        return consersation.getListMessageData().size();
    }
}

class ItemMessageUserHolder extends RecyclerView.ViewHolder {
    public TextView txtContent;
    public CircleImageView avata;

    public ItemMessageUserHolder(View itemView) {
        super(itemView);
        txtContent = (TextView) itemView.findViewById(R.id.textContentUser);
        avata = (CircleImageView) itemView.findViewById(R.id.imageView2);
    }
}

class ItemMessageFriendHolder extends RecyclerView.ViewHolder {
    public TextView txtContent;
    public CircleImageView avata;

    public ItemMessageFriendHolder(View itemView) {
        super(itemView);
        txtContent = (TextView) itemView.findViewById(R.id.textContentFriend);
        avata = (CircleImageView) itemView.findViewById(R.id.imageView3);
    }
}
