package ru.ne1es.timetable;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

final class AppNotifications {
 static void channels(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("changes","Изменения расписания",NotificationManager.IMPORTANCE_DEFAULT));n.createNotificationChannel(new NotificationChannel("lessons","Напоминания о парах",NotificationManager.IMPORTANCE_HIGH));n.createNotificationChannel(new NotificationChannel("sources","Доступ к источникам",NotificationManager.IMPORTANCE_DEFAULT));}
 static boolean permitted(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
 static void show(Context c,String channel,int id,String title,String message,String date){if("changes".equals(channel)&&!ReminderReceiver.prefs(c).getBoolean("change_notices",true))return;channels(c);if(!permitted(c))return;Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(date!=null)i.putExtra("date",date);PendingIntent p=PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(p).setAutoCancel(true).setColor(0xff8561b1).build();c.getSystemService(NotificationManager.class).notify(id,n);}
 static synchronized void authRequired(Context c){
  if(!permitted(c))return;
  SharedPreferences prefs=ReminderReceiver.prefs(c);
  long now=System.currentTimeMillis(),last=prefs.getLong("login_notice_at",0);
  if(last>0&&now-last<86400000L)return;
  channels(c);
  Intent screen=new Intent(c,AuthActivity.class);
  PendingIntent pending=PendingIntent.getActivity(c,5504,screen,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification notification=new Notification.Builder(c,"sources").setSmallIcon(R.drawable.ic_notification)
   .setContentTitle("Нужен вход в УлГТУ").setContentText("Открой личный кабинет, чтобы возобновить обновление")
   .setContentIntent(pending).setAutoCancel(true).build();
  c.getSystemService(NotificationManager.class).notify(5504,notification);
  prefs.edit().putLong("login_notice_at",now).apply();
 }
 static void issue(Context c,String problem){if(!permitted(c))return;android.content.SharedPreferences p=ReminderReceiver.prefs(c);long now=System.currentTimeMillis();if(!problem.equals(p.getString("last_notified_issue",""))||now-p.getLong("last_issue_at",0)>43200000){show(c,"sources",5503,"Не удалось проверить расписание",problem,null);p.edit().putString("last_notified_issue",problem).putLong("last_issue_at",now).apply();}}
}
