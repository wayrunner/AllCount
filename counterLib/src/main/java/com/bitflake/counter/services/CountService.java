package com.bitflake.counter.services;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.bitflake.counter.Constances;
import com.bitflake.counter.SensorCounter;
import com.bitflake.counter.StateWindow;

public class CountService extends SensorService implements SensorCounter.CountListener, CountConstants {
    private SensorCounter counter = new SensorCounter();
    private Bundle statusBundle = new Bundle();
    private float[] particleCounts;
    private float[] stateScores;
    protected int countOffset;
    protected Bundle currentStates;

    @Override
    public void onCreate() {
        super.onCreate();
        setAnalyser(counter);
        counter.setCountListener(this);
        statusBundle.putFloat(DATA_COUNT_PROGRESS, 0);
        statusBundle.putInt(DATA_COUNT, 0);
        statusBundle.putBoolean(DATA_IS_COUNTING, false);
        boolean isRunningOnWatch = false;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
//            isRunningOnWatch = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
//        }
//        target = isRunningOnWatch ? Constances.INTENT_TARGET_WEAR : Constances.INTENT_TARGET_MOBILE;
        registerReceiver(new IntentFilter(Constances.INTENT_COUNT_CONTROL));
    }

    @Override
    public void onReceive(Intent intent) {
        Bundle data = intent.getExtras();
        if (data == null)
            return;
        String cmd = data.getString(DATA_COMMAND);
        if (cmd != null)
            switch (cmd) {
                case CMD_START_COUNTING:
                    startCounting(data);
                    break;
                case CMD_STOP_COUNTING:
                    stopCounting();
                    stopSelf();
                    break;
                case CMD_REQUEST_UPDATE:
                    broadcastStatusAndStates();
                    break;
                default:
                    break;
            }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            onReceive(intent);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    protected void stopCounting() {
        stopListening();
        statusBundle.putBoolean(DATA_IS_COUNTING, false);
        broadcastStatus(EVENT_STOP_COUNTING);
        stopForeground(true);
        Log.d("my", this.toString() + " stopCounting()");
    }

    protected void startCounting(Bundle data) {
        if (isListening())
            return;
        statusBundle.putBoolean(DATA_IS_COUNTING, true);
        if (data.containsKey(DATA_STATES)) {
            currentStates = data.getBundle(DATA_STATES);
            counter.setStates(StateWindow.fromBundles(currentStates));
        }
        counter.reset();
        countOffset = data.getInt(DATA_COUNT_OFFSET);
        statusBundle.putInt(DATA_COUNT, countOffset);
        broadcastStatus(EVENT_START_COUNTING);
        startListening();
        Log.d("my", this.toString() + " startCounting()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCounting();
        unregisterReceiver();
    }

    @Override
    public void onCount(int count) {
        count += countOffset;
        statusBundle.putInt(DATA_COUNT, count);
        statusBundle.putFloat(DATA_COUNT_PROGRESS, 0.5f);
        broadcastStatus(EVENT_COUNT);
        Log.d("my", this.toString() + " onCount(" + count + ")");
    }

    @Override
    public void onCountProgress(float progress) {
        Intent i = new Intent(Constances.INTENT_COUNT_PROGRESS);
        i.putExtra(DATA_COUNT_PROGRESS, progress);
        particleCounts = counter.getParticleCounts(particleCounts);
        i.putExtra(DATA_PARTICLE_COUNT, particleCounts);
        stateScores = counter.getStateScores(stateScores);
        i.putExtra(DATA_STATE_SCORES, stateScores);
        sendBroadcast(i);
    }

    public void broadcastStatus(String eventType) {
        Intent i = new Intent(Constances.INTENT_COUNT_STATUS);
        i.putExtras(statusBundle);
        i.putExtra(DATA_EVENT_TYPE, eventType);
        sendBroadcast(i);
    }

    public void broadcastStatusAndStates() {
        Intent i = new Intent(Constances.INTENT_COUNT_STATUS);
        i.putExtras(statusBundle);
        i.putExtra(DATA_EVENT_TYPE, EVENT_STATUS);
        i.putExtra(DATA_STATES, currentStates);
        sendBroadcast(i);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!isListening())
            stopSelf();
        return super.onUnbind(intent);
    }
}
