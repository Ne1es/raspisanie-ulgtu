package ru.ne1es.timetable;
import android.content.*;
public final class RestoreReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){PendingResult p=goAsync();new Thread(()->{try{SyncJob.schedule(c);ReminderReceiver.schedule(c);}finally{p.finish();}},"restore-schedule").start();}
}
