package com.example.directtutorial;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChatActivity extends AppCompatActivity {

    TextView cmpnName, receivedmsg;
    Button stopconnection;
    ImageButton send;
    EditText msg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initialwork();
        exqListener();
    }

    private void exqListener() {//put listeners here
    }

    private void initialwork() {//initialize objects
        cmpnName = findViewById(R.id.companionName);
        receivedmsg = findViewById(R.id.receivemsg);
        stopconnection = findViewById(R.id.btnstop);
        send = findViewById(R.id.sendButton);
        msg = findViewById(R.id.msgInput);
    }
}