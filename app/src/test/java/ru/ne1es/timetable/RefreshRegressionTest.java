package ru.ne1es.timetable;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.json.JSONObject;

/** Reproduces update loss using the real downloaded timetable, then verifies recovery. */
public final class RefreshRegressionTest {
 static int checks;
 interface Action {void run()throws Exception;}
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void refused(Action action,String message)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,message);}
 static Schedule.Data parse(String html){return Schedule.parse(html,"Пдоп-35",Schedule.INDEX.replace("raspisan.html","322.html"));}
 static void same(Schedule.Data expected,Schedule.Data actual,String message)throws Exception{
  check(expected.dates.equals(actual.dates),message+": dates");check(expected.lessons.size()==actual.lessons.size(),message+": count");
  for(int i=0;i<expected.lessons.size();i++)check(Schedule.lessonJson(expected.lessons.get(i)).toString().equals(Schedule.lessonJson(actual.lessons.get(i)).toString()),message+": lesson "+i);
 }
 public static void main(String[] args)throws Exception{
  String fixture=args.length>0?args[0]:"tests/fixtures/schedule.html";
  String raw=new String(Files.readAllBytes(Paths.get(fixture)),"UTF-8");Schedule.Data baseline=parse(raw);
  check(baseline.lessons.size()==47,"Official fixture has 47 entries");check(baseline.lessons.stream().allMatch(l->!l.type.isEmpty()),"Lesson types preserved");
  String formatted=raw;
  for(int i=0;i<5;i++){formatted=Jsoup.parse(formatted).outerHtml();same(baseline,parse(formatted),"Repeated HTTP serialization "+i);}
  Document compact=Jsoup.parse(raw);compact.outputSettings().prettyPrint(false);same(baseline,parse(compact.outerHtml()),"Compact DOM");
  Document streamed=Jsoup.parse(new ByteArrayInputStream(raw.getBytes("UTF-8")),null,Schedule.INDEX);same(baseline,parse(streamed.outerHtml()),"InputStream HTTP path");
  same(baseline,parse(raw.replaceAll("(?i)<br\\s*/?>","<br>\n  \t\n")),"Whitespace after visible line breaks");
  String original=Schedule.encode(baseline);
  for(int i=0;i<20;i++){
   check(Schedule.day(baseline,null,"2026-11-08",2).isEmpty(),"Empty Sunday");
   List<Schedule.Lesson> tuesday=Schedule.day(baseline,null,"2026-11-03",2);check(tuesday.size()==4,"Tuesday after empty Sunday");tuesday.clear();
   check(Schedule.day(baseline,null,"2026-11-07",2).size()==4,"Saturday after Sunday");
  }
  check(original.equals(Schedule.encode(baseline)),"Selecting days never changes complete snapshot");
  List<Schedule.Lesson> copy=Schedule.day(baseline,null,"2026-11-03",2);copy.get(0).title="changed view";check(original.equals(Schedule.encode(baseline)),"Returned lessons are copies");
  Schedule.Changes cancellation=Schedule.parseDocx(ParserTest.doc("Пдоп-35","7,8","нет"),"03.11.26.docx","Пдоп-35",baseline);
  Schedule.day(baseline,cancellation,"2026-11-03",2);check(original.equals(Schedule.encode(baseline)),"Applying replacements never changes baseline");

  Path dir=Files.createTempDirectory("timetable-cache-test-");ScheduleStore store=new ScheduleStore(dir.toFile());store.save(baseline);
  check(new ScheduleStore(dir.toFile()).load("ПДО 35").lessons.size()==47,"Reopen persisted snapshot using group alias");
  Path primary=dir.resolve("schedule-ПДОП35.json"),backup=dir.resolve("schedule-ПДОП35.json.bak");check(Files.exists(backup),"Backup created on first successful load");
  byte[] saved=Files.readAllBytes(primary);
  Schedule.Data poisoned=Schedule.decode(original);poisoned.lessons.clear();
  refused(()->store.save(poisoned),"Malformed empty refresh rejected");check(Arrays.equals(saved,Files.readAllBytes(primary)),"Failed refresh cannot overwrite snapshot");
  poisoned.sourceCells=0;
  refused(()->store.save(poisoned),"Sudden all-empty overlapping timetable rejected");check(store.load("Пдоп-35").lessons.size()==47,"Last good lessons retained");
  String header="<p>Пдоп-35</p><table><tr><td>Время</td><td>08:30–09:50</td></tr>";
  String blank=header+"<tr><td>03.11.2026</td><td></td></tr></table>";
  refused(()->store.save(parse(blank)),"All-empty overlapping source does not erase known classes");
  refused(()->store.save(parse(header+"<tr><td>03.11.2026</td><td>лек.<br>Поломанная запись</td></tr></table>")),"Incomplete nonempty source cell rejected");
  refused(()->store.save(parse("<form><input name='login'></form>")),"Login HTML cannot replace schedule");
  check(new ScheduleStore(dir.toFile()).load("Пдоп-35").lessons.size()==47,"Failed refresh survives app restart");
  Files.write(primary,"{interrupted".getBytes("UTF-8"));same(baseline,store.load("Пдоп-35"),"Recover interrupted primary from backup");
  store.save(parse(formatted));same(baseline,store.load("Пдоп-35"),"Refresh after corrupt primary");

  Path legacyDir=Files.createTempDirectory("timetable-legacy-test-");Path legacyFile=legacyDir.resolve("schedule-ПДОП35.json");JSONObject legacy=new JSONObject(original);legacy.remove("parserVersion");legacy.remove("sourceCells");Files.write(legacyFile,legacy.toString().getBytes("UTF-8"));
  ScheduleStore legacyStore=new ScheduleStore(legacyDir.toFile());check(legacyStore.load("Пдоп-35").lessons.size()==47,"Existing 0.2.1 nonempty cache migrates");
  legacy.put("lessons",new org.json.JSONArray());Files.write(legacyFile,legacy.toString().getBytes("UTF-8"));check(legacyStore.load("Пдоп-35")==null,"Existing poisoned 0.2.1 cache is not a confirmed day off");
  legacyStore.save(parse(formatted));check(legacyStore.load("Пдоп-35").lessons.size()==47,"Poisoned old installation recovers on refresh");
  Schedule.Data future=parse(blank.replace("03.11.2026","10.11.2026"));store.save(future);check(store.load("Пдоп-35").dates.contains("2026-11-10"),"Published genuinely empty new dates accepted");
  check(store.load("Другая-12")==null,"Cache separated by group");
  System.out.println("PASS "+checks+" refresh/cache regression checks");
 }
}
