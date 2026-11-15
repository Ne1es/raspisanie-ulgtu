package ru.ne1es.timetable;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public final class SyncJob extends Worker {
 private static final String PERIODIC="timetable-periodic", NOW="timetable-now";

 public SyncJob(Context context,WorkerParameters params){super(context,params);}

 static void schedule(Context context){
  WorkManager manager=WorkManager.getInstance(context);
  SharedPreferences prefs=ReminderReceiver.prefs(context);
  if(!prefs.getBoolean("work_manager_migrated",false)){
   JobScheduler legacy=context.getSystemService(JobScheduler.class);
   legacy.cancel(3535);legacy.cancel(3536);
   prefs.edit().putBoolean("work_manager_migrated",true).apply();
  }
  if(prefs.getString("group","").isEmpty()){
   manager.cancelUniqueWork(PERIODIC);
   return;
  }
  int interval=60;
  PeriodicWorkRequest work=new PeriodicWorkRequest.Builder(SyncJob.class,interval,TimeUnit.MINUTES,15,TimeUnit.MINUTES)
   .setConstraints(constraints(true)).build();
  manager.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,work);
 }

 static void now(Context context){
  OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(SyncJob.class).setConstraints(constraints(false)).addTag(NOW).build();
  WorkManager.getInstance(context).enqueueUniqueWork(NOW,ExistingWorkPolicy.KEEP,work);
 }

 private static Constraints constraints(boolean periodic){
  return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(periodic).build();
 }

 @Override public Result doWork(){
  Context context=getApplicationContext();
  SharedPreferences prefs=ReminderReceiver.prefs(context);
  boolean manual=getTags().contains(NOW);
  if(prefs.getString("group","").isEmpty())return Result.success();
  try{
   SyncEngine.run(context,new Repository(context),()->!isStopped());
   return Result.success();
  }catch(InterruptedException stopped){
   Thread.currentThread().interrupt();
   return Result.failure();
  }catch(Exception error){
   if(isStopped()||Thread.currentThread().isInterrupted())return Result.failure();
   String problem="УлГТУ: "+SyncEngine.human(error);
   prefs.edit().putString("background_error",problem).putBoolean("background_auth_required",error instanceof Repository.LoginRequired)
    .putLong("background_attempt",System.currentTimeMillis()).apply();
   if(error instanceof Repository.LoginRequired)AppNotifications.authRequired(context);
   else AppNotifications.issue(context,problem);
   return manual&&getRunAttemptCount()<2&&error instanceof java.net.SocketTimeoutException?Result.retry():Result.failure();
  }
 }
}
