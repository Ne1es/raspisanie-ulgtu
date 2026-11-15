package ru.ne1es.timetable;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
public class ParserTest {
 static int checks=0;
 static void check(boolean value,String m){checks++;if(!value)throw new AssertionError(m);}
 static byte[] doc(String group,String slot,String discipline)throws Exception{String xml="<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:tbl>";for(String[] cells:new String[][]{{"Группа","№ пары","Дисциплина","Преподаватель","Кабинет"},{group,slot,discipline,"Тестов И.И.","1-100"}}){xml+="<w:tr>";for(String cell:cells)xml+="<w:tc><w:p><w:r><w:t>"+cell+"</w:t></w:r></w:p></w:tc>";xml+="</w:tr>";}xml+="</w:tbl></w:body></w:document>";ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("word/document.xml"));z.write(xml.getBytes("UTF-8"));z.closeEntry();}return out.toByteArray();}
 public static void main(String[] args)throws Exception{
  String base=args.length>0?args[0]:"tests/fixtures/";
  String html=new String(Files.readAllBytes(Paths.get(base,"schedule.html")),"UTF-8");
  Schedule.Data data=Schedule.parse(html,"Пдоп-35",Schedule.INDEX.replace("raspisan.html","322.html"));
  System.out.println("Parsed "+data.lessons.size()+" lessons, "+data.dates.size()+" dates");
  List<Schedule.Lesson> today=Schedule.day(data,null,"2026-11-03",2);check(today.size()==4,"Today must have 4 lessons, got "+today.size());check(today.get(2).start.equals("18:00"),"Empty columns must not shift seventh lesson");check(today.get(3).start.equals("19:30"),"Eighth lesson must be 19:30");check(today.get(2).end.equals("19:20"),"Seventh lesson end");
  List<Schedule.Lesson> thu=Schedule.day(data,null,"2026-11-05",2);check(thu.size()==5,"Thursday count");check(thu.get(4).start.equals("19:30"),"Thursday DOT eighth lesson");check(thu.get(2).title.contains("баз данных"),"Second subgroup database lesson");check(thu.stream().noneMatch(l->l.subgroup==1),"First subgroup excluded");
  List<Schedule.Lesson> wed=Schedule.day(data,null,"2026-11-04",2);check(wed.size()==2,"Wednesday count");check(wed.get(0).title.startsWith("Иностранный"),"Second subgroup English");check(wed.get(0).subgroup==2,"English subgroup recognised");
  check(Schedule.day(data,null,"2026-11-07",2).get(3).start.equals("19:30"),"Saturday DOT eighth lesson");check(data.dates.contains("2026-11-08"),"Published empty Sunday");check(!data.dates.contains("2026-11-09"),"Unpublished future distinct from empty day");
  String index=new String(Files.readAllBytes(Paths.get(base,"index.html")),"UTF-8");List<Schedule.Group> groups=Schedule.groups(index);check(groups.size()>100,"Group catalog");check(groups.stream().anyMatch(g->g.name.equals("Пдоп-35")&&g.url.endsWith("322.html")),"Group links loaded from index");
  check(Schedule.decode(Schedule.encode(data)).lessons.size()==data.lessons.size(),"Cache round trip");
  byte[] actual=Files.readAllBytes(Paths.get(base,"changes-03.11.26.docx"));Schedule.Changes official=Schedule.parseDocx(actual,"Изменения на 03.11.26г Вт.docx","Пдоп-35",data);check(official.lessons.isEmpty(),"Official 3 November file has no P35 replacements");check(official.checkedDates.contains("2026-11-03"),"Checked replacement date recorded");
  Schedule.Changes debts=Schedule.parseDocx(actual,"03.11.26.docx","БДдоп-32",data);check(debts.lessons.size()==3,"Merged debt announcement does not abort real replacements");check(debts.lessons.stream().filter(l->l.change.equals("Отменено")).count()==2,"Two cancellations survive");
  Schedule.Changes other=Schedule.parseDocx(actual,"03.11.26.docx","РЭСдоп-22",data);check(other.lessons.size()==1,"Merged DOCX cells");check(other.lessons.get(0).teacher.equals("Богатова Е.А."),"Teacher from merged cell");check(other.lessons.get(0).room.contains("200"),"Room from DOCX");
  check(!other.lessons.get(0).title.contains("Богатова"),"Teacher repeated in source title is removed");
  check(Schedule.sameGroup("Пдоп-35 (2 п/г)","Пдоп-35"),"Group cell may contain subgroup");
  String catalogue="<a href='/timetable/shared/schedule/Part1/raspisan.html'>Институт</a><a href='https://other.example/timetable/shared/schedule/Part2/raspisan.html'>Другой сайт</a>";
  check(Schedule.catalogPages(catalogue,Schedule.DIRECTORY).size()==1,"Only university catalogues followed");
  check(Schedule.groups("<a href='42.html'>ПИбд-21</a>","https://lk.ulstu.ru/timetable/shared/schedule/Part1/raspisan.html").size()==1,"Group resolves relative to non-college section");
  Schedule.Changes cancel=Schedule.parseDocx(doc("Пдоп-35","7,8","нет"),"03.11.26.docx","Пдоп-35",data);check(cancel.lessons.size()==2,"Multi-slot cancellation");check(Schedule.day(data,cancel,"2026-11-03",2).get(3).change.equals("Отменено"),"Cancellation rendered");check(Schedule.day(data,cancel,"2026-11-03",2).get(3).title.startsWith("Разработка"),"Cancellation retains original title");
  Schedule.Changes firstOnly=Schedule.parseDocx(doc("Пдоп-35","7","лек. Тест - 1 п/г"),"03.11.26.docx","Пдоп-35",data);check(Schedule.day(data,firstOnly,"2026-11-03",2).get(2).title.startsWith("Разработка"),"Other subgroup replacement ignored");
  Schedule.Changes subgroupCancel=Schedule.parseDocx(doc("Пдоп-35, Пдоп-36","2,5","1 п/гр нет"),"03.11.26.docx","Пдоп-35",data);
  check(subgroupCancel.lessons.size()==2&&subgroupCancel.lessons.stream().allMatch(l->l.subgroup==1&&l.change.equals("Отменено")),"Merged group list and first subgroup cancellation");
  check(Schedule.slots("5,6").equals(java.util.Arrays.asList(5,6)),"Comma pair numbers");
  check(Schedule.slots("2-4").equals(java.util.Arrays.asList(2,3,4)),"Inclusive pair range");
  check(RussianText.duration(514).equals("8 ч 34 мин")&&RussianText.duration(25).equals("25 мин"),"Shared duration formatter");
  Schedule.Changes range=Schedule.parseDocx(doc("Пдоп-35","1-2","лек. Тест - 2 п/г"),"03.11.26.docx","Пдоп-35",data);check(range.lessons.size()==2,"Range slots parsed");check(range.lessons.get(0).subgroup==2,"Replacement subgroup");check(Schedule.decodeChanges(Schedule.encodeChanges(range)).lessons.size()==2,"Changes cache round trip");
  check(!Schedule.sameGroup("Пдоп-351","Пдоп-35"),"Exact group matching");check(Schedule.sameGroup("Пдоп-34, Пдоп-35","Пдоп-35"),"Combined groups");
  boolean refused=false;try{Schedule.parseDocx(doc("Пдоп-35","9","нет"),"03.11.26.docx","Пдоп-35",data);}catch(Exception e){refused=true;}check(refused,"Unknown slots must fail safely");
  String merged="<p>Пдоп-35</p><table><tr><td>Время</td><td>08:30–09:50</td><td>10:00–11:20</td></tr><tr><td>03.11.2026</td><td colspan='2'>лек.<br>Тест<br>Иванов И И<br>1-001</td></tr></table>";Schedule.Data mergedData=Schedule.parse(merged,"Пдоп-35",Schedule.INDEX);check(mergedData.lessons.size()==1,"Colspan avoids duplicate");check(mergedData.lessons.get(0).end.equals("11:20"),"Colspan end time");
  boolean badDoc=false;try{Schedule.parseDocx("bad".getBytes("UTF-8"),"03.11.26.docx","Пдоп-35",data);}catch(Exception e){badDoc=true;}check(badDoc,"Broken document must not be treated as no changes");
  Schedule.Changes alias=Schedule.parseDocx(doc("ПДО 35","1","нет"),"03.11.26.docx","Пдоп-35",data);check(alias.lessons.size()==1,"Alternative group spelling");
  List<Schedule.Lesson> both=Schedule.day(data,firstOnly,"2026-11-03",0);check(both.stream().anyMatch(l->l.slot==7&&l.subgroup==2&&l.title.startsWith("Разработка")),"Common original retained for unaffected subgroup in all view");
  Schedule.Data sunday=Schedule.parse(merged.replace("03.11.2026","08.11.2026"),"Пдоп-35",Schedule.INDEX);check(Schedule.day(sunday,null,"2026-11-08",2).size()==1,"Published Sunday classes must be shown");
  System.out.println("PASS "+checks+" checks");
 }
}
