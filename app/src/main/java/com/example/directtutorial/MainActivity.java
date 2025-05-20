package com.example.directtutorial;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    TextView connectionStatus, messageTextView;//Элементы интерфейса
    Button discoverButton;
    ListView listView;
    EditText typeMsg;
    ImageButton sendButton;

    WifiP2pManager manager;//Менеджер для работы с P2P
    WifiP2pManager.Channel channel;

    BroadcastReceiver receiver;//Бродкаст ресивер
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers =new ArrayList<WifiP2pDevice>();//Массив доступных устройств
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;

    Socket socket;//Сокет для соединения

    ServerClass serverClass;//Сервер и клиент класс
    ClientClass clientClass;

    private ChatViewModel chatViewModel;

    boolean isHost;


    private ActivityResultLauncher<Intent> wifiSettingsLauncher;//Открытие настроек вайфай
    private ActivityResultLauncher<String> requestLocationPermissionLauncher;//Открытие настроек местоположения
    private ActivityResultLauncher<String[]> requestNearbyPermissionLauncher;//Открытие настроек устройств поблизости

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        wifiSettingsLauncher = registerForActivityResult(               //Регистрация лаунчера для настроек Wi-Fi
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleWifiSettingsResult()
        );


        requestLocationPermissionLauncher = registerForActivityResult( // Регистрация лаунчера для запроса разрешения местоположения
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        onLocationPermissionGranted();
                    } else {
                        onLocationPermissionDenied();
                    }
                }
        );


        requestNearbyPermissionLauncher = registerForActivityResult(    // Регистрация лаунчера для запроса разрешений
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (result.containsValue(false)) {
                        // Какое-то разрешение было отклонено
                        onNearbyDevicesPermissionDenied();
                    } else {
                        // Все разрешения предоставлены
                        onNearbyDevicesPermissionGranted();
                    }
                }
        );

        // Проверка всех необходимых разрешений
        checkPermissions();

        initialWork();//Инициализация объектов интерфейса
        exqListener();//Установка листенеров
    }

    @Override                       //Перекрытие соединения при уничтожении приложения
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Группа удалена успешно
                }

                @Override
                public void onFailure(int reason) {
                    // Ошибка при удалении группы
                }
            });
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (serverClass != null && serverClass.serverSocket != null) {
            try {
                serverClass.serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////ПРОВЕРКА РАЗРЕШЕНИЙ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    private void handleWifiSettingsResult() {
        Toast.makeText(MainActivity.this, "Настройки Wi-Fi завершены!", Toast.LENGTH_SHORT).show();
    }

    private void checkPermissions() {   //Проверка разрешений
        // Проверяем разрешение на местоположение
        checkLocationPermission();

        // Проверяем разрешения на устройства поблизости
        checkNearbyDevicesPermissions();
    }

    private void checkNearbyDevicesPermissions() {     //Проверка разрешения устройств поблизости
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            boolean nearbyPermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;

            if (nearbyPermissionGranted) {
                onNearbyDevicesPermissionGranted();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                showNearbyPermissionRationale();
            } else {
                requestNearbyPermissionLauncher.launch(new String[]{
                        Manifest.permission.NEARBY_WIFI_DEVICES
                });
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12
            boolean scanPermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean connectPermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            if (scanPermissionGranted && connectPermissionGranted) {
                onNearbyDevicesPermissionGranted();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)
                    || shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                showNearbyPermissionRationale();
            } else {
                requestNearbyPermissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
            }
        } else {
            // Для устройств ниже Android 12
            onNearbyDevicesPermissionGranted();
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            onLocationPermissionGranted();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showLocationPermissionRationale();
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void showNearbyPermissionRationale() {      //Показ диалогового окна для устройств поблизости
        new AlertDialog.Builder(this)
                .setTitle("Доступ к устройствам поблизости")
                .setMessage("Это приложение требует доступ к устройствам поблизости для их обнаружения.")
                .setPositiveButton("Разрешить", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                        requestNearbyPermissionLauncher.launch(new String[]{
                                Manifest.permission.NEARBY_WIFI_DEVICES
                        });
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12
                        requestNearbyPermissionLauncher.launch(new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        });
                    }
                })
                .setNegativeButton("Отмена", (dialog, which) -> onNearbyDevicesPermissionDenied())
                .show();
    }

    private void showLocationPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Доступ к местоположению")
                .setMessage("Это приложение требует доступ к вашему местоположению для корректной работы.")
                .setPositiveButton("Разрешить", (dialog, which) -> {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                })
                .setNegativeButton("Отмена", (dialog, which) -> onLocationPermissionDenied())
                .show();
    }


    //Тосты для отображения результатов
    private void onNearbyDevicesPermissionGranted() {
        Toast.makeText(MainActivity.this, "Доступ к устройствам поблизости предоставлен!", Toast.LENGTH_SHORT).show();
    }

    private void onNearbyDevicesPermissionDenied() {
        Toast.makeText(MainActivity.this, "Приложение не будет работать без доступа к устройствам поблизости.", Toast.LENGTH_SHORT).show();
    }

    private void onLocationPermissionDenied() {
        Toast.makeText(MainActivity.this, "Приложение не будет работать без доступа к местоположению.", Toast.LENGTH_SHORT).show();
    }

    private void onLocationPermissionGranted() {
        Toast.makeText(MainActivity.this, "Доступ к местоположению предоставлен!", Toast.LENGTH_SHORT).show();
    }

    private boolean hasAllPermissions() {           //Есть все разрешения
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////УСТАНОВКА ЛИСТЕНЕРОВ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("MissingPermission")
    private void exqListener() {        //Установка листнеров

        discoverButton.setOnClickListener(view -> {   //Кнопка обнаружения
            if (hasAllPermissions()) {
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Discovery Start Error: " + reason);
                    }
                });
            } else {
                connectionStatus.setText("Недостаточно разрешений для выполнения операции.");
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {  //Установка листенера для выбора устройства
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];    //Выбранное устройство
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress=device.deviceAddress;


                manager.connect(channel, config, new WifiP2pManager.ActionListener() {  //Подключение к выбранному устройству
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Connected: " + device.deviceAddress);
                    }

                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Not connected");
                    }
                });

            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {  //Отправка сообщения
            @Override
            public void onClick(View view) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                String msg = typeMsg.getText().toString();
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
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////ИНИЦИАЛИЗАЦИЯ ОБЪЕКТОВ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    private void initialWork() {
        connectionStatus=findViewById(R.id.connectionStatus);
        messageTextView=findViewById(R.id.messageTextView);

        discoverButton=findViewById(R.id.buttonDiscover);
        listView=findViewById(R.id.listView);
        typeMsg=findViewById(R.id.editTextTypeMsg);
        sendButton=findViewById(R.id.sendButton);

        ///////////////////////////////////////////////////////////////////////////////////////////////////////
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);      //Настройка вайфай директа
        channel =  manager.initialize(this,getMainLooper(),null);
        receiver = new WiFiDirectBroadcastReceiver(manager,channel,this);

        intentFilter=new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);



//
    }

    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////ОБНОВЛЕНИЕ СПИСКА УСТРОЙСТВ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if (!wifiP2pDeviceList.equals(peers)) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                deviceNameArray = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];

                int index = 0;
                for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                    // Обработка случая, если device.deviceName == null
                    deviceNameArray[index] = (device.deviceName != null) ? device.deviceName : "Unnamed Device";
                    deviceArray[index] = device;
                    index++;
                }

                // Создаем адаптер с исправленным массивом
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        getApplicationContext(),
                        android.R.layout.simple_list_item_1,
                        deviceNameArray
                );
                listView.setAdapter(adapter);

                if (peers.size() == 0) {
                    connectionStatus.setText("No Device Found");
                    return;
                }
            }
        }
    };


    ///////////////////////////////////////////////////////////////////////////////////
    ///////////////////ОБНОВЛЕНИЕ СОСТОЯНИЯ ПОДКЛЮЧЕНИЯ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner)
            {
                connectionStatus.setText("Host");
                isHost = true;
                serverClass=new ServerClass();
                serverClass.start();
            }else if(wifiP2pInfo.groupFormed)
            {
                connectionStatus.setText("Client");
                isHost = false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }

            // Переход к экрану чата после успешного соединения
//            if (wifiP2pInfo.groupFormed) {
//                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
//                intent.putExtra("isHost", isHost); // Передаем статус устройства
//                startActivity(intent);
//            }
        }
    };



    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d("MainActivity", "Получено сообщение через Broadcast");
            if (intent.getAction().equals("SEND_MESSAGE")) {
                String message = intent.getStringExtra("smessage");
                Log.d("MainActivity", "Сообщение: " + message);
            }

            if (intent.getAction().equals("SEND_MESSAGE")) {
                String message = intent.getStringExtra("smessage");
                if (message != null && !message.isEmpty()) {
                    // Отправляем сообщение другому устройству
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        if (isHost && serverClass != null) {
                            serverClass.write(message.getBytes());
                        } else if (!isHost && clientClass != null) {
                            clientClass.write(message.getBytes());
                        }
                    });
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver,intentFilter);
        Log.d("MainActivity", "onResume() - активность снова на экране");

        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver,
                new IntentFilter("SEND_MESSAGE"));

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivity", "onPause() - активность свернута");
        unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    public class ServerClass extends Thread{
        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

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
                serverSocket=new ServerSocket(8888);
                socket=serverSocket.accept();
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
                                chatViewModel.setReceivedMessage(receivedMessage);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
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
                                chatViewModel.setReceivedMessage(receivedMessage);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

    }

}

