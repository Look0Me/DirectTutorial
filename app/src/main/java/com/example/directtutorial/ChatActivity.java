package com.example.directtutorial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ChatActivity extends AppCompatActivity {

    TextView cmpnName, receivedmsg;
    Button stopconnection;
    ImageButton send;
    EditText msgI;

    Socket socket;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private boolean isHost;

    ServerClass serverClass;//Сервер и клиент класс
    ClientClass clientClass;

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

    private void exqListener() {
        send.setOnClickListener(view -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            String msg = msgI.getText().toString();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if(msg!=null && isHost)
                    {
                        serverClass.write(msg.getBytes());
                    }else if(msg !=null && !isHost){
                        clientClass.write(msg.getBytes());
                    }
                }
            });
        });

    }

    private void initialwork() {//initialize objects
        cmpnName = findViewById(R.id.companionName);
        receivedmsg = findViewById(R.id.receivemsg);
        stopconnection = findViewById(R.id.btnstop);
        send = findViewById(R.id.sendButton);
        msgI = findViewById(R.id.msgInput);


        // Получаем информацию из Intent
        isHost = getIntent().getBooleanExtra("isHost", false);

        // Инициализируем Wi-Fi Direct
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        checkConnectionStatus();

        if (isHost) {
            if (serverClass != null) {
                serverClass.closeServer();
                serverClass = null;
                Log.d("ChatActivity", "Deleted old server");
            }

            serverClass = new ServerClass();
            serverClass.start();
        } else {
            manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                    if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                        clientClass = new ClientClass(wifiP2pInfo.groupOwnerAddress);
                        clientClass.start();
                    }
                }
            });
        }
    }

    // Регистрация приемника
    @Override
    protected void onResume() {
        super.onResume();


    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void checkConnectionStatus() {
        manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                if (wifiP2pInfo.groupFormed) {
                    if (wifiP2pInfo.isGroupOwner) {
                        Log.d("ChatActivity", "Я Host");
                        Toast.makeText(ChatActivity.this, "Я Host", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d("ChatActivity", "Я Client");
                        Toast.makeText(ChatActivity.this, "Я Client", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("ChatActivity", "Нет активного соединения");
                }
            }
        });
    }


    public class ServerClass extends Thread{
        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public void write(byte[] bytes) {
            try {
                if (outputStream != null) {
                    outputStream.write(bytes);
                    Log.d("WiFiDirect", "Сообщение отправлено другому устройству: " + new String(bytes));
                }
            } catch (IOException e) {
                Log.e("WiFiDirect", "Ошибка отправки сообщения", e);
            }
        }


        @Override
        public void run() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    Log.e("ServerClass", "Сервер уже запущен! Пропускаем создание нового.");
                    return;
                }

                serverSocket = new ServerSocket(8888); // Проверяем, не занят ли порт
                Log.d("ServerClass", "Сервер запущен, ожидаем соединение...");

                socket = serverSocket.accept(); // Ожидаем подключения клиента
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                Log.d("ServerClass", "Клиент подключен!");

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());

                executor.execute(() -> {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    try {
                        while (socket != null && !socket.isClosed()) {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0) {
                                String receivedMessage = new String(buffer, 0, bytes);
                                Log.d("ServerClass", "Получено сообщение: " + receivedMessage);

                                // Здесь передаём сообщение в UI или ViewModel
                                handler.post(() -> {
                                    receivedmsg.append("Companion: " + receivedMessage + "\n");
                                });
                            }
                        }
                    } catch (IOException e) {
                        Log.e("ServerClass", "Ошибка при приёме сообщения", e);
                    }
                });

            } catch (IOException e) {
                Log.e("ServerClass", "Ошибка при запуске сервера", e);
            }
        }

        // Метод для закрытия сервера
        public void closeServer() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    Log.d("ServerClass", "Сервер закрыт.");
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    Log.d("ServerClass", "Сокет закрыт.");
                }
            } catch (IOException e) {
                Log.e("ServerClass", "Ошибка при закрытии сервера", e);
            }
        }
    }


    public class ClientClass extends Thread{
        String hostadd;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ClientClass(InetAddress hostAddress){
            hostadd=hostAddress.getHostAddress();
            socket=new Socket();

        }

        public void write (byte[] bytes)
        {
            try {
                outputStream.write(bytes);
                Log.d("WiFiDirect", "Сообщение отправлено другому устройству: " + new String(bytes));
            } catch (IOException e) {
                Log.e("WiFiDirect", "Ошибка отправки сообщения", e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostadd,8888),5000);
                inputStream=socket.getInputStream();
                outputStream=socket.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    try {
                        inputStream = socket.getInputStream();
                        outputStream = socket.getOutputStream();

                        while (socket != null) {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0) {
                                String receivedMessage = new String(buffer, 0, bytes);
                                Log.d("MainActivity", "Получено сообщение от другого устройства: " + receivedMessage);

                                // Передаём сообщение в ViewModel, чтобы оно отобразилось в ChatActivity
                                receivedmsg.append("ball");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverClass != null) {
            serverClass.closeServer();
        }
    }
}