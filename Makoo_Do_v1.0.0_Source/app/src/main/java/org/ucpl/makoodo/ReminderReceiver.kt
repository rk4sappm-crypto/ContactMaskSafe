package org.ucpl.makoodo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val text=intent.getStringExtra("text")?:"Your Makoo Do reminder"
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel="makoo_reminders"
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(channel,"Makoo Do reminders",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=android.app.Notification.Builder(context,if(Build.VERSION.SDK_INT>=26)channel else "")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Makoo Do Reminder").setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(open).build()
        nm.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),n)
    }
}
