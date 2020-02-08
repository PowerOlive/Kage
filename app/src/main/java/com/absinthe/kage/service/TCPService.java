package com.absinthe.kage.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.absinthe.kage.R;
import com.absinthe.kage.device.DeviceManager;
import com.absinthe.kage.server.ConnectionServer;
import com.absinthe.kage.ui.main.MainActivity;
import com.absinthe.kage.utils.NotificationUtils;

public class TCPService extends Service {
    private DeviceManager mDeviceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, getNotificationInstance());

        mDeviceManager = DeviceManager.Singleton.INSTANCE.getInstance();
        mDeviceManager.init();
        mDeviceManager.startMonitorDevice(2000);

        new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectionServer connectionServer = new ConnectionServer();
                connectionServer.start();
            }
        });
    }

    @Override
    public void onDestroy() {
        mDeviceManager.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, TCPService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.createTCPChannel(context);
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, TCPService.class);
        context.stopService(intent);
    }

    private Notification getNotificationInstance() {
        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, NotificationUtils.TCP_CHANNEL_ID)
                .setContentTitle(getText(R.string.notification_channel_tcp))
                .setContentText(getText(R.string.kage_service_notification_content))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
