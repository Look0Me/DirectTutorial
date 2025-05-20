package com.example.directtutorial;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ChatViewModel extends AndroidViewModel {
    private final MutableLiveData<String> receivedMessage = new MutableLiveData<>();
    private final MutableLiveData<String> sendMessage = new MutableLiveData<>();

    public ChatViewModel(Application application) {
        super(application);
    }

    public void setReceivedMessage(String message) {
        receivedMessage.postValue(message);
    }

    public LiveData<String> getReceivedMessage() {
        return receivedMessage;
    }

    public void setSendMessage(String message) {
        Log.d("ChatViewModel", "setSendMessage() вызвано: " + message);
        sendMessage.postValue(message);
    }

    public LiveData<String> getSendMessage() {
        return sendMessage;
    }
}
