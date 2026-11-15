package ru.ne1es.timetable;

import java.io.*;

/** Last confirmed snapshot plus a validated backup. Selection never writes to this store. */
public final class ScheduleStore {
 private static final Object LOCK=new Object();
 private final File directory;
 public ScheduleStore(File directory){this.directory=directory;}
 private String name(String group){return "schedule-"+Schedule.norm(group).replaceAll("[^A-ZА-Я0-9]","")+".json";}
 public Schedule.Data load(String group){synchronized(LOCK){Schedule.Data d=read(new File(directory,name(group)),group);return d!=null?d:read(new File(directory,name(group)+".bak"),group);}}
 private Schedule.Data read(File f,String group){try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>6000000)return null;out.write(b,0,n);}Schedule.Data d=Schedule.decode(out.toString("UTF-8"));Schedule.validate(d);if(!Schedule.norm(d.group).equals(Schedule.norm(group)))return null;return d;}catch(Exception invalid){return null;}}
 public void save(Schedule.Data next)throws Exception{synchronized(LOCK){Schedule.Data previous=load(next.group);Schedule.validateReplacement(previous,next);String encoded=Schedule.encode(next);write(new File(directory,name(next.group)+".bak"),Schedule.encode(previous==null?next:previous));write(new File(directory,name(next.group)),encoded);}}
 private void write(File destination,String text)throws IOException{File temp=new File(directory,destination.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes("UTF-8"));out.getFD().sync();}if(!temp.renameTo(destination))throw new IOException("Не удалось сохранить расписание");}
}
