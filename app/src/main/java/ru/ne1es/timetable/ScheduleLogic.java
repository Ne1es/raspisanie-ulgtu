package ru.ne1es.timetable;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure calculations shared by the screen, background sync and reminders. */
public final class ScheduleLogic {
 public static final class Timeline {
  public Schedule.Lesson current,next; public long remainingMinutes; public int progress;
 }
 public static ZonedDateTime start(Schedule.Lesson l){return LocalDate.parse(l.date).atTime(LocalTime.parse(l.start)).atZone(Schedule.ZONE);}
 public static ZonedDateTime end(Schedule.Lesson l){return LocalDate.parse(l.date).atTime(LocalTime.parse(l.end)).atZone(Schedule.ZONE);}
 public static List<Schedule.Lesson> effective(Schedule.Data d,Schedule.Changes changes,int subgroup){
  List<Schedule.Lesson> out=new ArrayList<>();if(d==null)return out;
  for(String date:d.dates)out.addAll(Schedule.day(d,changes,date,subgroup));
  out.sort(Comparator.comparing(ScheduleLogic::start).thenComparingInt(l->l.slot));return out;
 }
 public static Timeline timeline(Schedule.Data d,Schedule.Changes changes,int subgroup,ZonedDateTime now){
  return timeline(effective(d,changes,subgroup),now);
 }
 static Timeline timeline(List<Schedule.Lesson> lessons,ZonedDateTime now){
  Timeline t=new Timeline();for(Schedule.Lesson l:lessons){
   if("Отменено".equals(l.change))continue;
   if(!start(l).isAfter(now)&&end(l).isAfter(now)&&t.current==null)t.current=l;
   if(start(l).isAfter(now)){t.next=l;break;}
  }
  if(t.current!=null){
   long total=Duration.between(start(t.current),end(t.current)).toMillis();
   long left=Duration.between(now,end(t.current)).toMillis();
   t.remainingMinutes=Math.max(1,(left+59999)/60000);
   t.progress=(int)((total-left)*1000/Math.max(1,total));
  }else if(t.next!=null)t.remainingMinutes=Math.max(1,(Duration.between(now,start(t.next)).toMillis()+59999)/60000);
  return t;
 }
 public static String reminderKey(Schedule.Lesson l){return l.date+"/"+l.slot+"/"+l.subgroup+"/"+l.start;}
 public static final class Reminder {public Schedule.Lesson lesson;public long at;public String key;}
 public static Reminder nextReminder(Schedule.Data d,Schedule.Changes c,int subgroup,long now,int lead,Set<String> delivered){
  if(lead<=0)return null;for(Schedule.Lesson l:effective(d,c,subgroup)){
   if("Отменено".equals(l.change)||start(l).toInstant().toEpochMilli()<=now||delivered.contains(reminderKey(l)))continue;
   Reminder r=new Reminder();r.lesson=l;r.key=reminderKey(l);r.at=Math.max(now+1500,start(l).minusMinutes(lead).toInstant().toEpochMilli());return r;
  }return null;
 }
 private static String signature(List<Schedule.Lesson> list){List<String> a=new ArrayList<>();for(Schedule.Lesson l:list)a.add(l.title+"|"+l.start+"|"+l.end+"|"+l.teacher+"|"+l.room+"|"+l.type+"|"+l.subgroup+"|"+("Отменено".equals(l.change)?"cancel":""));Collections.sort(a);return String.join("\n",a);}
 private static String caption(List<Schedule.Lesson> list){List<String>a=new ArrayList<>();for(Schedule.Lesson l:list)a.add("Отменено".equals(l.change)?"отмена «"+l.title+"»":l.start+" «"+l.title+"» · "+l.room+(l.teacher.isEmpty()?"":" · "+l.teacher));return a.isEmpty()?"нет занятия":String.join("; ",a);}
 public static List<String> diff(Schedule.Data old,Schedule.Changes before,Schedule.Data fresh,Schedule.Changes after,int subgroup,LocalDate today){
  List<String> result=new ArrayList<>();if(old==null)return result;
  for(String date:fresh.dates){if(LocalDate.parse(date).isBefore(today))continue;
   List<Schedule.Lesson> a=Schedule.day(old,before,date,subgroup),b=Schedule.day(fresh,after,date,subgroup);
   if(!old.dates.contains(date)){if(!b.isEmpty())result.add(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM"))+": опубликовано занятий — "+b.size());continue;}
   Set<Integer> slots=new TreeSet<>();for(Schedule.Lesson l:a)slots.add(l.slot);for(Schedule.Lesson l:b)slots.add(l.slot);
   for(int slot:slots){List<Schedule.Lesson>x=new ArrayList<>(),y=new ArrayList<>();for(Schedule.Lesson l:a)if(l.slot==slot)x.add(l);for(Schedule.Lesson l:b)if(l.slot==slot)y.add(l);
    if(!signature(x).equals(signature(y)))result.add(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd.MM"))+", "+slot+" пара: "+caption(x)+" → "+caption(y));}
  }return result;
 }
}
