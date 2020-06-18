package com.dfrobot.angelo.bleTester;

import android.content.Context;
import android.content.SharedPreferences;

public class SavedSettings {

    public static final String PREF_NAME = "preferenceOfBLE";
    public static final String ENABLE_TRIGGER = "trigger_ON";
    public static final String TRIGGER = "TRIGGER";
    public static final String MESSAGE = "MESSAGE";
    public static final String RESPONSE = "RESPONSE";
    public static final String DEVICE_NAME = "Bluno";
    public static final String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";

    private Context context;
    private static SavedSettings instance;

    private SavedSettings (Context context) {
        this.context = context.getApplicationContext();
    }

    public static SavedSettings getInstance(Context context) {
        if (instance == null) {
            instance = new SavedSettings(context);
        }
        return instance;
    }

    public void setEnableTrigger(boolean passSave) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(ENABLE_TRIGGER , passSave);
        editor.commit();
    }

    public Boolean getEnableTrigger() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getBoolean(ENABLE_TRIGGER , true);
    }

    public void setTrigger(String newPassword) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(TRIGGER, newPassword);
        editor.commit();
    }

    public String getTrigger() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(TRIGGER, "password");
    }

    public void setMessage(String newPassword) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(MESSAGE, newPassword);
        editor.commit();
    }

    public String getMessage() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(MESSAGE, "password");
    }

    public void setResponse(String newPassword) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(RESPONSE, newPassword);
        editor.commit();
    }

    public String getResponse() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(RESPONSE, "password");
    }

    public void setDeviceName(String name) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(DEVICE_NAME, name);
        editor.commit();
    }

    public String getDeviceName() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(DEVICE_NAME, "device name");
    }

    public void setDeviceAddress(String address) {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(DEVICE_ADDRESS, address);
        editor.commit();
    }

    public String getDeviceAddress() {
        SharedPreferences settings = context.getSharedPreferences(PREF_NAME, 0);
        return settings.getString(DEVICE_ADDRESS, "AA:BB:CC:DD:EE:FF");
    }

}
