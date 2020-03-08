package com.example.mad_cw.ui.chat;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mad_cw.BaseActivity;
import com.example.mad_cw.R;
import com.example.mad_cw.ui.adverts.AdvertsModel;
import com.example.mad_cw.ui.chat.adapters.ChatRoomList_Adapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoom_Activity extends BaseActivity implements View.OnClickListener {

    // _____________________
    // class variables:

    // Displaying Selected Images:
    private RecyclerView recyclerView;
    private ChatRoomList_Adapter chatListAdapter;
    private ArrayList<ChatModel> chat_model_list = new ArrayList<>();

    private Map<String, Object> newChatMap = new HashMap<>();
    private Map<String, Object> textHashMap = new HashMap<>();
    private ImageButton sendMsgBtn;
    private EditText user_txt_msg;
    private TextView chat_ad_title, chat_ad_price;

    private String chat_uid;

    private AdvertsModel advert;

    // Access to Firebase Authentication from the Activity
    private FirebaseAuth mAuth;

    // Access a Cloud Firestore instance from the Activity
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    // _____________________
    // class activity cycles:

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load up the Chat Layout:
        setContentView(R.layout.chat_room_layout);

        // Setup the Recycler View, set Gallery as Layout, and assign it:
        recyclerView = findViewById(R.id.chat_recycler_view);
        GridLayoutManager mLayoutManager = new GridLayoutManager(this, 1); // (Context context, int spanCount)
        recyclerView.setLayoutManager(mLayoutManager);

        // Locate Target Fields:
        user_txt_msg = findViewById(R.id.user_text_msg);
        sendMsgBtn = findViewById(R.id.send_msg_btn);
        chat_ad_title = findViewById(R.id.ad_title_txt);
        chat_ad_price = findViewById(R.id.ad_price_txt);

        // Set onCLickListeners:
        sendMsgBtn.setOnClickListener(this);

        // Dealing with passed data accordingly:
        advert = getIntent().getParcelableExtra("advert_info");

        if (advert != null){
            chat_ad_title.setText(advert.getAd_title());
            chat_ad_price.setText(getString(R.string.set_ad_price, advert.getAd_price()));
        }

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        verifyChatExistence();

    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null){

            // Dealing with Instantiating the Recycler View & the View Holder:
            chatListAdapter = new ChatRoomList_Adapter(chat_model_list, currentUser.getUid());
            recyclerView.setAdapter(chatListAdapter);

            // Toast.makeText(this, "Success, user verified", Toast.LENGTH_LONG).show();
        }
    }

    // _____________________
    // data-intent handling methods:

    private void verifyChatExistence() {

        FirebaseUser currentUser = mAuth.getCurrentUser();
        final String userUid = currentUser.getUid();
        final String advertOwnerUid = advert.getAd_owner();

        // Check if Chat Already Exists between both users, act accordingly:
        db.collection("chat").whereArrayContains("users", userUid).get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        List<String> usersMainList = new ArrayList<String>(Arrays.asList(userUid, advertOwnerUid));
                        for (QueryDocumentSnapshot doc : task.getResult()){
                            List<String> users_list = (List<String>) doc.getData().get("users");
                            usersMainList.retainAll(users_list);
                            if (usersMainList.size() == 2 && advert.getDocumentId().equals(doc.getData().get("ad_uid"))){
                                chat_uid = doc.getId();
                                getMessages(chat_uid);
                                System.out.println("Chat Found" + doc.getId());
                            }
                        }
                    }
                });
    }

    private void getMessages(String chat_uid) {

        db.collection("chat").document(chat_uid).collection("messages")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Toast.makeText(getApplicationContext(), "Error Retriving Data", Toast.LENGTH_LONG).show();
                        }
                        // On Document Change retrieves only the updated data:
                        for (DocumentChange doc : queryDocumentSnapshots.getDocumentChanges()) {
                            if (doc.getType() == DocumentChange.Type.ADDED) {
                                ChatModel chatModel = doc.getDocument().toObject(ChatModel.class);
                                chat_model_list.add(chatModel);
                                chatListAdapter.notifyDataSetChanged();
                                recyclerView.smoothScrollToPosition(chatListAdapter.getItemCount());
                            }
                        }
                    }
                });
    }

    private void startChat() {

        FirebaseUser currentUser = mAuth.getCurrentUser();
        final String userUid = currentUser.getUid();
        final String advertOwnerUid = advert.getAd_owner();

        List<String> usersMainList = new ArrayList<>(Arrays.asList(userUid, advertOwnerUid));

        newChatMap.put("users", usersMainList);
        newChatMap.put("ad_uid", advert.getDocumentId());

        db.collection("chat").add(newChatMap)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        chat_uid = documentReference.getId();
                        getMessages(chat_uid);
                        sendMessage(user_txt_msg.getText().toString());
                    }
                });
    }

    // _____________________
    // user action methods:

    private void sendMessage(String txt_msg) {

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String user_uid = currentUser.getUid();

        textHashMap.put("msg_text", txt_msg);
        textHashMap.put("user_uid", user_uid);
        textHashMap.put("timestamp", FieldValue.serverTimestamp());

        db.collection("chat").document(chat_uid).collection("messages")
                .add(textHashMap)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {

                    }
                });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){

            case R.id.send_msg_btn:

                // Instantiate Chat, upon sending a message;
                if (chat_uid == null){
                    startChat();
                } else {
                    sendMessage(user_txt_msg.getText().toString());
                }

                break;
        }

    }

    // ______________________
    // UI/UX methods:

}
