package ru.ne1es.timetable;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class FeatureTest {
 static int n;static void check(boolean ok,String m){n++;if(!ok)throw new AssertionError(m);}
 static ZonedDateTime at(String time){return LocalDateTime.parse("2026-11-03T"+time).atZone(Schedule.ZONE);}
 public static void main(String[] args)throws Exception{
  Schedule.Data d=Schedule.parse(new String(Files.readAllBytes(Paths.get("tests/fixtures/schedule.html")),"UTF-8"),"Пдоп-35",Schedule.INDEX);
  ScheduleLogic.Timeline t=ScheduleLogic.timeline(d,null,2,at("08:40:00"));check(t.current!=null&&t.current.start.equals("08:30"),"Current math");check(t.remainingMinutes==70,"70 minutes left");check(t.next.start.equals("10:00"),"Next math");
  check(t.progress==125,"Progress and remaining time share the same timestamp");
  t=ScheduleLogic.timeline(d,null,2,at("08:49:59.500"));check(t.remainingMinutes==61,"Partial minute rounds up");
  check(RussianText.week(LocalDate.of(2026,11,2)).equals("2–8 ноября"),"Compact Russian week");
  check(RussianText.week(LocalDate.of(2026,10,26)).equals("26 октября – 1 ноября"),"Cross-month week");
  check(RussianText.teacher("Салихов С А").equals("Салихов С. А."),"Spaced teacher initials");
  check(RussianText.teacher("Салихов С А").equals("Салихов С. А."),"Non-breaking spaces in initials");
  check(RussianText.teacher("Салихов С.А.").equals("Салихов С. А."),"Compact teacher initials");
  check(RussianText.teacher("Салихов Сергей Александрович").equals("Салихов Сергей Александрович"),"Full name preserved");
  t=ScheduleLogic.timeline(d,null,2,at("09:50:00"));check(t.current==null,"End is exclusive");check(t.remainingMinutes==10,"Break countdown");
  t=ScheduleLogic.timeline(d,null,2,at("11:30:00"));check(t.current==null&&t.next.start.equals("18:00"),"Evening DOT remains evening");check(t.remainingMinutes==390,"Evening countdown");
  t=ScheduleLogic.timeline(d,null,2,at("18:00:00"));check(t.current.slot==7&&t.remainingMinutes==80,"Exact start inclusive");
  t=ScheduleLogic.timeline(d,null,2,at("20:50:00"));check(t.current==null&&t.next.date.equals("2026-11-04"),"Next published day");
  long now=at("08:00:00").toInstant().toEpochMilli();ScheduleLogic.Reminder r=ScheduleLogic.nextReminder(d,null,2,now,15,Collections.emptySet());check(r.lesson.slot==1&&r.at==at("08:15:00").toInstant().toEpochMilli(),"15-minute reminder");
  Set<String> sent=new HashSet<>();sent.add(r.key);r=ScheduleLogic.nextReminder(d,null,2,now,15,sent);check(r.lesson.slot==2&&r.at==at("09:45:00").toInstant().toEpochMilli(),"No duplicate reminder");
  check(ScheduleLogic.nextReminder(d,null,2,now,0,sent)==null,"Disabled reminders");
  r=ScheduleLogic.nextReminder(d,null,2,at("08:20:00").toInstant().toEpochMilli(),15,Collections.emptySet());check(r.at==at("08:20:00").toInstant().toEpochMilli()+1500,"Enable shortly before class");
  Schedule.Changes canceled=Schedule.parseDocx(ParserTest.doc("Пдоп-35","7,8","нет"),"03.11.26.docx","Пдоп-35",d);
  t=ScheduleLogic.timeline(d,canceled,2,at("18:10:00"));check(t.current==null&&t.next.date.equals("2026-11-04"),"Canceled lesson not current");
  r=ScheduleLogic.nextReminder(d,canceled,2,at("17:00:00").toInstant().toEpochMilli(),15,Collections.emptySet());check(r.lesson.date.equals("2026-11-04"),"No cancellation reminder");
  check(ScheduleLogic.diff(d,null,d,null,2,LocalDate.of(2026,11,3)).isEmpty(),"Same schedule silent");
  Schedule.Data changed=Schedule.decode(Schedule.encode(d));changed.checkedAt+=9000;check(ScheduleLogic.diff(d,null,changed,null,2,LocalDate.of(2026,11,3)).isEmpty(),"Timestamp silent");
  for(Schedule.Lesson l:changed.lessons)if(l.date.equals("2026-11-03")&&l.slot==1){l.room="1-999";l.teacher="Новый преподаватель";}
  List<String> diff=ScheduleLogic.diff(d,null,changed,null,2,LocalDate.of(2026,11,3));check(diff.size()==1&&diff.get(0).contains("1-999")&&diff.get(0).contains("Новый преподаватель"),"Concrete room/teacher diff");
  Schedule.Changes first=Schedule.parseDocx(ParserTest.doc("Пдоп-35","7","лек. Другое - 1 п/г"),"03.11.26.docx","Пдоп-35",d);check(ScheduleLogic.diff(d,null,d,first,2,LocalDate.of(2026,11,3)).isEmpty(),"Other subgroup notification excluded");
  check(ScheduleLogic.diff(d,null,d,canceled,2,LocalDate.of(2026,11,3)).size()==2,"Two cancellation changes");
  check(ScheduleLogic.diff(null,null,d,null,2,LocalDate.of(2026,11,3)).isEmpty(),"First sync has no change spam");
  check(ScheduleLogic.diff(d,null,changed,null,2,LocalDate.of(2026,11,4)).isEmpty(),"Past changes excluded");
  Schedule.Data rolled=Schedule.decode(Schedule.encode(d));rolled.dates.remove("2026-11-03");rolled.lessons.removeIf(l->l.date.equals("2026-11-03"));check(ScheduleLogic.diff(d,null,rolled,null,2,LocalDate.of(2026,11,3)).isEmpty(),"Publication window rolling not cancellation");
  check(ScheduleLogic.timeline(null,null,2,at("10:00:00")).current==null,"Missing cache safe");
  System.out.println("PASS "+n+" timeline/reminder/change checks");
 }
}
