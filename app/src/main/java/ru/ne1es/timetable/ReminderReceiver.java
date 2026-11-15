package ru.ne1es.timetable;
import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;

public final class ReminderReceiver extends android.content.BroadcastReceiver {
 static final int REQUEST=3501;
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("timetable",0);}
 static Set<String> delivered(Context c,String group){return new HashSet<>(prefs(c).getStringSet("reminded-"+Schedule.norm(group),Collections.emptySet()));}
 static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
 static void schedule(Context c){
  AlarmManager manager=c.getSystemService(AlarmManager.class);PendingIntent pending=PendingIntent.getBroadcast(c,REQUEST,new Intent(c,ReminderReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.cancel(pending);
  if(!prefs(c).getBoolean("reminders",true)||!AppNotifications.permitted(c))return;String group=prefs(c).getString("group","");if(group.isEmpty())return;Repository repo=new Repository(c);
  ScheduleLogic.Reminder next=ScheduleLogic.nextReminder(repo.cached(group),repo.cachedChanges(group),prefs(c).getInt("subgroup",2),System.currentTimeMillis(),prefs(c).getInt("reminder_minutes",15),delivered(c,group));
  if(next==null){prefs(c).edit().remove("reminder_at").apply();return;}
  prefs(c).edit().putLong("reminder_at",next.at).apply();
  try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,pending);else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,pending);}catch(SecurityException e){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,pending);}
 }
 @Override public void onReceive(Context c,Intent intent){
  if(!prefs(c).getBoolean("reminders",true)||!AppNotifications.permitted(c))return;String group=prefs(c).getString("group","");Repository repo=new Repository(c);long now=System.currentTimeMillis();Set<String> seen=delivered(c,group);
  ScheduleLogic.Reminder next=ScheduleLogic.nextReminder(repo.cached(group),repo.cachedChanges(group),prefs(c).getInt("subgroup",2),now,prefs(c).getInt("reminder_minutes",15),seen);
  if(next!=null&&next.at<=now+3000){Schedule.Lesson l=next.lesson;seen.add(next.key);seen.removeIf(k->k.compareTo(java.time.LocalDate.now(Schedule.ZONE).minusDays(3).toString())<0);prefs(c).edit().putStringSet("reminded-"+Schedule.norm(group),seen).commit();AppNotifications.show(c,"lessons",5501,"Скоро пара · "+l.start,l.title+"\n"+l.room+(l.teacher.isEmpty()?"":" · "+l.teacher),l.date);}
  schedule(c);
 }
}
