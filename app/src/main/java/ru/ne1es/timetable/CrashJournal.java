package ru.ne1es.timetable;

import android.app.Application;
import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

public final class CrashJournal extends Application {
 private static String stamp(){
  return ZonedDateTime.now(Schedule.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm:ss",java.util.Locale.forLanguageTag("ru-RU")));
 }
 private static AtomicFile journal(Context context){
  return new AtomicFile(new File(context.getNoBackupFilesDir(),"last-crash.txt"));
 }
 @Override public void onCreate(){
  super.onCreate();
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{
    // Deliberately omit messages, causes, thread names, URLs and application data.
    StringBuilder safe=new StringBuilder().append(stamp())
     .append("\n").append(error.getClass().getName()).append("\n");
    int count=0;
    for(StackTraceElement frame:error.getStackTrace()){
     if(count++==32)break;
     safe.append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber()).append('\n');
    }
    write(this,safe.toString());
   }catch(IOException ignored){}
   finally{if(previous!=null)previous.uncaughtException(thread,error);}
  });
 }
 private static synchronized void write(Context context,String entry)throws IOException{
  AtomicFile file=journal(context);
  FileOutputStream stream=file.startWrite();
  try{
   stream.write(entry.getBytes(StandardCharsets.UTF_8));
   file.finishWrite(stream);
  }catch(IOException error){
   file.failWrite(stream);throw error;
  }
 }
 static synchronized void testEntry(Context context)throws IOException{
  String current=read(context);
  if(current.equals("Необработанных ошибок пока нет"))current="";
  if(current.length()>12000)current=current.substring(0,12000);
  write(context,stamp()+" · Тестовая запись журнала\n"+current);
 }
 static String read(Context context){
  try{return new String(journal(context).readFully(),StandardCharsets.UTF_8);}
  catch(Exception unavailable){return "Необработанных ошибок пока нет";}
 }
}
