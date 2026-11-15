package ru.ne1es.timetable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.*;
import java.io.*;
import java.util.zip.*;


public final class Schedule {
 public static final String INDEX="https://lk.ulstu.ru/timetable/shared/schedule/%D0%A7%D0%B0%D1%81%D1%82%D1%8C%204%20%E2%80%93%20%D0%9A%D0%AD%D0%98/raspisan.html";
 public static final String DISK="https://disk.yandex.ru/d/t8yWnkXZ8B0ung";
 public static final java.time.ZoneId ZONE=java.time.ZoneId.of("Europe/Ulyanovsk");
 public static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.uuuu");
 static final Pattern DATE_PATTERN=Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b");
 static final Pattern TIME=Pattern.compile("(\\d{1,2}:\\d{2})\\s*[–—-]\\s*(\\d{1,2}:\\d{2})");
 static final Pattern SUB=Pattern.compile("(?iu)([12])\\s*(?:[-–]?[аяй]+)?\\s*(?:п\\s*[/\\.]\\s*гр?\\.?|подгрупп[аы]?)");
 static final Pattern TYPE=Pattern.compile("(?iu)^(лек[.а-я]*|пр[.а-я]*|лаб[.а-я]*|конс[.а-я]*|сем[.а-я]*|экз[.а-я]*|зач[.а-я]*)(?:\\s|$)");
 public static final class Group { public final String name,url,faculty;
  public Group(String name,String url){this(name,url,facultyFromSource(url));}
  public Group(String name,String url,String faculty){this.name=name;this.url=url;this.faculty=faculty;}
 }
 static int catalogPart(String source,String title){
  String path="";
  try{path=java.net.URI.create(source).getPath();}catch(RuntimeException invalid){}
  for(String value:new String[]{path,title}){
   if(value==null)continue;
   Matcher part=Pattern.compile("(?iu)часть[\\s_-]*([1-6])(?!\\d)").matcher(value);
   if(part.find())return Integer.parseInt(part.group(1));
  }
  return 0;
 }
 private static String groupLink(Element link){
  try{
   String resolved=link.absUrl("href").replace(" ","%20");
   java.net.URI uri=java.net.URI.create(resolved).normalize();
   if(uri.getRawQuery()!=null||uri.getRawFragment()!=null)return "";
   return uri.toASCIIString();
  }catch(IllegalArgumentException invalid){return "";}
 }
 public static Map<Integer,Set<String>> catalogSections(String html,String base){
  Map<Integer,Set<String>> sections=new TreeMap<>();
  for(Element link:Jsoup.parse(html,base).select("a[href]")){
   String url=groupLink(link);
   if(!universityPage(url)||!java.net.URI.create(url).getPath().endsWith("/raspisan.html"))continue;
   int part=catalogPart(url,link.text());
   if(part>0)sections.computeIfAbsent(part,ignored->new LinkedHashSet<>()).add(url);
  }
  return sections;
 }
 static String facultyFromSource(String source){
  int part=catalogPart(source,"");
  if(part==4)return "КЭИ";
  if(part==5)return "СФ";
  return "";
 }
 public static final class Lesson {
  public String date,start,end,title,teacher="",room="",type="",source="",old="",change=""; public int subgroup,slot;
  public Lesson copy(){Lesson n=new Lesson();n.date=date;n.start=start;n.end=end;n.title=title;n.teacher=teacher;n.room=room;n.type=type;n.source=source;n.old=old;n.change=change;n.subgroup=subgroup;n.slot=slot;return n;}
  public String key(){return date+"/"+slot+"/"+subgroup;}
 }
 public static final class Data {
  public String group,url; public long checkedAt; public int parserVersion,sourceCells; public List<Lesson> lessons=new ArrayList<>(); public Set<String> dates=new TreeSet<>(); public Map<Integer,String[]> times=new TreeMap<>();
 }
 public static final class Changes {
  public List<Lesson> lessons=new ArrayList<>(); public Set<String> checkedDates=new TreeSet<>(); public List<String> warnings=new ArrayList<>(); public long checkedAt;
 }
 public static String clean(String x){return x.replace('\u00a0',' ').trim().replaceAll("\\s+"," ");}
 public static String norm(String x){String s=x.toUpperCase(Locale.ROOT).replaceAll("[\\s–—-]","");return s.replaceFirst("^ПДО(?=\\d)","ПДОП");}
 public static boolean sameGroup(String cell,String name){for(String s:cell.split("[,;\\n]"))if(norm(SUB.matcher(s).replaceAll("").replaceAll("[()]+","")).equals(norm(name)))return true;return false;}
 public static final String DIRECTORY="https://lk.ulstu.ru/timetable/";
 public static boolean universityPage(String address){
  try {
   java.net.URI uri=java.net.URI.create(address).normalize();
   return "https".equals(uri.getScheme()) && "lk.ulstu.ru".equals(uri.getHost())
     && uri.getUserInfo()==null && (uri.getPort()==-1||uri.getPort()==443)
     && uri.getPath().startsWith("/timetable/shared/schedule/") && uri.getPath().endsWith(".html");
  } catch(RuntimeException invalid){return false;}
 }
 public static List<Group> groups(String html){return groups(html,INDEX);}
 public static List<Group> groups(String html,String base){
  Map<String,Group> groups=new TreeMap<>();
  for(Element link:Jsoup.parse(html,base).select("a[href]")){
   String url=groupLink(link);
   if(!universityPage(url)||!java.net.URI.create(url).getPath().matches(".*/[0-9]+\\.html"))continue;
   for(String name:clean(link.text()).split("[,;]")){
    name=clean(name);
    if(name.matches("(?iu)[а-яёa-z][а-яёa-z0-9 .–—-]{1,48}\\d[а-яёa-z0-9-]*"))
     {
      String faculty=facultyFromSource(url);
      Element table=link.closest("table");
      if(faculty.isEmpty()&&table!=null){
       Element heading=table.selectFirst("caption");
       if(heading!=null)faculty=explicitFaculty(heading.text());
      }
      groups.putIfAbsent(norm(name),new Group(name,url,faculty));
     }
   }
  }
  return new ArrayList<>(groups.values());
 }
 static String explicitFaculty(String text){
  String value=clean(text).replaceFirst("(?iu)^факультет\\s+","");
  for(String name:new String[]{"МФ","РТФ","ЭФ","ИФМИ","ИИ-магистр","ЭАбз","Эбз","Рбз","ФИСТ","ГФ","ВТАСбз","ИДбз","ИЭФ","ЗВФ ИННО","КЭИ","СФ","ИАТУ","ССФ"})
   if(name.equalsIgnoreCase(value))return name;
  return "";
 }
 public static Set<String> catalogPages(String html,String base){
  Set<String> pages=new LinkedHashSet<>();
  for(Element link:Jsoup.parse(html,base).select("a[href]")){
   String url=groupLink(link);
   if(universityPage(url)&&java.net.URI.create(url).getPath().endsWith("/raspisan.html"))pages.add(url);
  }
  return pages;
 }
 static List<List<Element>> grid(Element table){List<List<Element>> out=new ArrayList<>();for(Element row:table.select("tr")){int r=out.size();out.add(new ArrayList<>()); // rows below can already be occupied by rowspan
 }int r=0;for(Element row:table.select("tr")){int c=0;for(Element cell:row.children()){if(!cell.tagName().equals("td")&&!cell.tagName().equals("th"))continue;while(c<out.get(r).size()&&out.get(r).get(c)!=null)c++;int cs=span(cell,"colspan"),rs=span(cell,"rowspan");for(int y=r;y<Math.min(r+rs,out.size());y++)for(int x=c;x<c+cs;x++){while(out.get(y).size()<=x)out.get(y).add(null);out.get(y).set(x,cell);}c+=cs;}r++;}return out;}
 static int span(Element e,String key){try{return Math.max(1,Math.min(32,Integer.parseInt(e.attr(key))));}catch(Exception x){return 1;}}
 public static Data parse(String html,String group,String url){Data d=new Data();d.group=group;d.url=url;org.jsoup.nodes.Document doc=Jsoup.parse(html,url);
  if(!norm(doc.text()).contains(norm(group)))throw new IllegalArgumentException("Страница другой группы");
  for(Element table:doc.select("table")){List<List<Element>> rows=grid(table);Map<Integer,String[]> times=new HashMap<>();for(List<Element> row:rows){for(int i=1;i<row.size();i++){Element c=row.get(i);if(c==null)continue;Matcher m=TIME.matcher(c.text());if(m.find())times.put(i,new String[]{pad(m.group(1)),pad(m.group(2))});}}
   if(times.isEmpty())continue;d.times.putAll(times);
   for(List<Element> row:rows){if(row.isEmpty()||row.get(0)==null)continue;Matcher dm=DATE_PATTERN.matcher(row.get(0).text());if(!dm.find())continue;String date=LocalDate.parse(dm.group(1),DATE).toString();d.dates.add(date);
    for(int c=1;c<row.size();c++){Element cell=row.get(c);if(cell==null||c>1&&cell==row.get(c-1)||!times.containsKey(c)||clean(cell.text()).isEmpty())continue;int last=c;while(last+1<row.size()&&row.get(last+1)==cell)last++;String[] t=times.get(c),te=times.get(last);if(te==null)throw new IllegalArgumentException("Неизвестное время пары");
     d.sourceCells++;
     List<String> lines=cellLines(cell);if(lines.isEmpty())throw new IllegalArgumentException("Не удалось прочитать непустую ячейку");
     int first=0;for(int i=1;i<=lines.size();i++)if(i==lines.size()||typeHeader(lines.get(i))){
      List<String> block=lines.subList(first,i);Lesson n=new Lesson();n.date=date;n.slot=c;n.start=t[0];n.end=te[1];n.source=url;
      int k=0;String inlineTitle="";if(typeHeader(block.get(0))){String header=block.get(0);Matcher marker=INLINE_TYPE.matcher(header);if(marker.find()){n.type=marker.group(1);inlineTitle=clean(header.substring(marker.end()));}else n.type=header;k++;}
      if(!inlineTitle.isEmpty())n.title=inlineTitle;else{if(k>=block.size())throw new IllegalArgumentException("В ячейке отсутствует дисциплина");n.title=block.get(k++);}
      List<String> tail=new ArrayList<>();for(;k<block.size();k++){Matcher sm=SUB.matcher(block.get(k));if(sm.find())n.subgroup=Integer.parseInt(sm.group(1));else tail.add(block.get(k));}
      if(n.title.isEmpty()||tail.size()<2)throw new IllegalArgumentException("Неполная запись занятия: "+n.title);
      n.room=tail.remove(tail.size()-1);n.teacher=String.join(" ",tail);d.lessons.add(n);first=i;
     }

    }
   }
  }
  if(d.dates.isEmpty())throw new IllegalArgumentException("Не найдены даты расписания");if(d.sourceCells>0&&d.lessons.isEmpty())throw new IllegalArgumentException("Занятия на странице не распознаны");d.parserVersion=2;d.checkedAt=System.currentTimeMillis();return d;
 }
 // Read rendered line breaks, ignoring source indentation and serialization newlines.
 static final Pattern TYPE_ONLY=Pattern.compile("(?iu)^(?:лек\\.?|лекция|лекции|пр\\.?|практ\\.?|практика|практическое(?:\\s+занятие(?:\\s*\\d+)?)?|лаб\\.?|лабораторная(?:\\s+работа)?|конс\\.?|консультация|сем\\.?|семинар|экз\\.?|экзамен|зач\\.?|зач[её]т)$");
 static final Pattern INLINE_TYPE=Pattern.compile("(?iu)^(лек|пр|лаб|конс|сем|экз|зач)\\.\\s+");
 static boolean typeHeader(String s){return TYPE_ONLY.matcher(s).matches()||INLINE_TYPE.matcher(s).find();}
 static List<String> cellLines(Element cell){List<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();readLines(cell,line,out);flushLine(line,out);return out;}
 static void flushLine(StringBuilder line,List<String> out){String text=clean(line.toString());if(!text.isEmpty())out.add(text);line.setLength(0);}
 static void readLines(Node node,StringBuilder line,List<String> out){
  if(node instanceof TextNode){String text=((TextNode)node).getWholeText();if(text.trim().isEmpty()){if(line.length()>0&&line.charAt(line.length()-1)!=' ')line.append(' ');}else line.append(text.replaceAll("[\\r\\n\\t]+"," "));return;}
  if(node instanceof Element){Element e=(Element)node;if(e.tagName().equals("br")){flushLine(line,out);return;}boolean block=e.tagName().equals("p")||e.tagName().equals("div")||e.tagName().equals("li");if(block)flushLine(line,out);for(Node child:e.childNodes())readLines(child,line,out);if(block)flushLine(line,out);}
 }
 public static void validate(Data d){
  if(d==null||d.group==null||d.group.isEmpty()||d.dates.isEmpty()||d.times.isEmpty())throw new IllegalArgumentException("Неполный снимок расписания");
  if(d.lessons.isEmpty()&&(d.parserVersion<2||d.sourceCells>0))throw new IllegalArgumentException("Пустой результат чтения не подтверждён источником");
  for(Lesson l:d.lessons)if(!d.dates.contains(l.date)||!d.times.containsKey(l.slot)||l.title==null||l.title.isEmpty()||l.subgroup<0||l.subgroup>2||l.start==null||l.end==null||!LocalTime.parse(l.end).isAfter(LocalTime.parse(l.start)))throw new IllegalArgumentException("Некорректное занятие в снимке");
 }
 public static void validateReplacement(Data previous,Data next){validate(next);if(previous==null)return;if(!norm(previous.group).equals(norm(next.group)))throw new IllegalArgumentException("Группа снимка не совпадает");if(!previous.lessons.isEmpty()&&next.lessons.isEmpty())for(Lesson l:previous.lessons)if(next.dates.contains(l.date))throw new IllegalArgumentException("Источник вернул пустое расписание. Сохранены последние загруженные пары.");}
 static String pad(String t){return t.length()==4?"0"+t:t;}
 public static List<Lesson> day(Data d,Changes changes,String date,int subgroup){List<Lesson> out=new ArrayList<>();if(d==null)return out;for(Lesson l:d.lessons)if(l.date.equals(date)&&(subgroup==0||l.subgroup==0||l.subgroup==subgroup))out.add(l.copy());if(changes!=null)for(Lesson ch:changes.lessons)if(ch.date.equals(date)&&(subgroup==0||ch.subgroup==0||ch.subgroup==subgroup)){
   Lesson replacement=ch.copy();List<Lesson> replaced=new ArrayList<>();for(Lesson n:out)if(n.slot==ch.slot&&(ch.subgroup==0||n.subgroup==0||n.subgroup==ch.subgroup))replaced.add(n);
   if(!replaced.isEmpty()){Lesson before=replaced.get(0);replacement.old=before.title+(before.room.isEmpty()?"":" · "+before.room);
    if(replacement.type.isEmpty())replacement.type=before.type;
    if(!replacement.change.equals("Отменено")){
     boolean sameTitle=norm(before.title).equals(norm(replacement.title));
     replacement.change=sameTitle?"Изменено":"Замена";
     if(sameTitle&&replacement.room.isEmpty())replacement.room=before.room;
     if(sameTitle&&replacement.teacher.isEmpty())replacement.teacher=before.teacher;
    }if(replacement.change.equals("Отменено")){replacement.title=before.title;replacement.type=before.type;replacement.teacher=before.teacher;replacement.room=before.room;}out.removeAll(replaced);if(subgroup==0&&ch.subgroup!=0&&before.subgroup==0){Lesson other=before.copy();other.subgroup=ch.subgroup==1?2:1;out.add(other);}}out.add(replacement);
  }out.sort(Comparator.comparing((Lesson n)->n.start).thenComparingInt(n->n.subgroup));return out;}
 static String changeDate(String filename)throws IOException{
  Matcher local=Pattern.compile("(?<![0-9])([0-9]{1,2})\\.([0-9]{1,2})\\.([0-9]{4}|[0-9]{2})(?![0-9])").matcher(filename);
  Matcher iso=Pattern.compile("(?<![0-9])([0-9]{4}-[0-9]{2}-[0-9]{2})(?![0-9])").matcher(filename);
  try{
   if(local.find()){
    int year=Integer.parseInt(local.group(3));if(local.group(3).length()==2)year+=2000;
    return LocalDate.of(year,Integer.parseInt(local.group(2)),Integer.parseInt(local.group(1))).toString();
   }
   if(iso.find())return LocalDate.parse(iso.group(1)).toString();
  }catch(java.time.DateTimeException invalid){throw new IOException("Неверная дата файла: "+filename,invalid);}
  throw new IOException("Не определена дата файла: "+filename);
 }
 public static Changes parseDocx(byte[] bytes,String filename,String group,Data schedule)throws Exception{
  String date=changeDate(filename);
  byte[] xml=null;try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=in.getNextEntry())!=null)if(e.getName().equals("word/document.xml")){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(b.size()>5000000)throw new IOException("Слишком большой документ");b.write(buffer,0,n);}xml=b.toByteArray();break;}}if(xml==null)throw new IOException("Файл не является DOCX");
  org.jsoup.nodes.Document doc=Jsoup.parse(new String(xml,"UTF-8"),"",org.jsoup.parser.Parser.xmlParser());List<Element> rows=doc.getElementsByTag("w:tr");Changes changes=new Changes();boolean header=false;int groupColumn=0;String inherited="";
  for(Element row:rows){if(row.children().stream().filter(cell->cell.tagName().equals("w:tc")).count()<3)continue;List<String> cols=new ArrayList<>();for(Element el:row.children())if(el.tagName().equals("w:tc")){StringBuilder text=new StringBuilder();for(Element para:el.getElementsByTag("w:p")){for(Element t:para.getElementsByTag("w:t"))text.append(t.text());text.append(' ');}int span=1;Element sp=el.getElementsByTag("w:gridSpan").first();if(sp!=null)span=Integer.parseInt(sp.attr("w:val"));for(int j=0;j<Math.min(span,12);j++)cols.add(clean(text.toString()));}
   if(cols.size()<5)continue;int heading=-1;for(int i=0;i<cols.size();i++)if(cols.get(i).equalsIgnoreCase("Группа")){heading=i;break;}if(heading>=0&&cols.toString().toLowerCase(Locale.ROOT).contains("дисциплина")){header=true;groupColumn=heading;inherited="";continue;}if(!header)continue;
   if(groupColumn>0)cols=new ArrayList<>(cols.subList(Math.min(groupColumn,cols.size()),cols.size()));if(cols.size()<5)continue;
   String gr=cols.get(0);if(gr.isEmpty()&&!cols.get(1).isEmpty())gr=inherited;else if(!gr.isEmpty())inherited=gr;if(!sameGroup(gr,group))continue;
   int roomCol=cols.size()-1,teacherCol=roomCol-1;String title=cols.get(2),teacher=cols.get(teacherCol),room=cols.get(roomCol);
   if(!teacher.isEmpty()){String bare=teacher.replaceAll("\\p{Punct}","").replaceAll("\\s+","");for(int i=1;i<title.length();i++){String tail=title.substring(i).replaceAll("\\p{Punct}","").replaceAll("\\s+","");if(tail.equalsIgnoreCase(bare)){title=clean(title.substring(0,i));break;}}}Matcher sm=SUB.matcher(gr+" "+title);int sg=sm.find()?Integer.parseInt(sm.group(1)):0;
   for(int slot:slots(cols.get(1))){if(!schedule.times.containsKey(slot))throw new IOException("В заменах неизвестный номер пары");Lesson n=new Lesson();n.date=date;n.slot=slot;n.start=schedule.times.get(slot)[0];n.end=schedule.times.get(slot)[1];n.subgroup=sg;n.source=DISK;n.teacher=teacher;n.room=room;String lowered=clean(SUB.matcher(title).replaceAll("").replaceAll("^[\\s–—-]+|[\\s–—-]+$","")).toLowerCase(Locale.ROOT);
    n.change=lowered.matches("^(нет|отмена|отменено|не будет)[.!]?$")?"Отменено":"Замена";
    n.title=clean(SUB.matcher(title).replaceAll("")).replaceAll("^[\\s–—-]+|[\\s–—-]+$", "");Matcher typ=TYPE.matcher(n.title);if(typ.find()){n.type=typ.group(1);n.title=clean(n.title.substring(typ.end()));}if(n.title.isEmpty())throw new IOException("Пустая дисциплина в заменах");changes.lessons.add(n);}
  }
  if(!header)throw new IOException("Не распознана таблица замен");
  Map<String,Lesson> unique=new LinkedHashMap<>();for(Lesson l:changes.lessons)unique.put(l.key(),l);changes.lessons=new ArrayList<>(unique.values());changes.lessons.sort(Comparator.comparingInt(l->l.subgroup));
  changes.checkedDates.add(date);changes.checkedAt=System.currentTimeMillis();return changes;
 }
 static List<Integer> slots(String s)throws IOException{List<Integer> out=new ArrayList<>();for(String p:s.replace('–','-').replace('—','-').split("[,;]")){String q=p.trim();if(q.matches("[1-8]"))out.add(Integer.parseInt(q));else if(q.matches("[1-8]\\s*-\\s*[1-8]")){String[] a=q.split("-");int from=Integer.parseInt(a[0].trim()),to=Integer.parseInt(a[1].trim());if(to<from)throw new IOException("Неверный диапазон пар");for(int i=from;i<=to;i++)out.add(i);}else throw new IOException("Не распознан номер пары: "+s);}return out;}
 public static JSONObject lessonJson(Lesson n)throws JSONException{return new JSONObject().put("date",n.date).put("slot",n.slot).put("start",n.start).put("end",n.end).put("title",n.title).put("teacher",n.teacher).put("room",n.room).put("type",n.type).put("subgroup",n.subgroup).put("source",n.source).put("change",n.change).put("old",n.old);}
 public static Lesson lessonFrom(JSONObject j){Lesson n=new Lesson();n.date=j.optString("date");n.slot=j.optInt("slot");n.start=j.optString("start");n.end=j.optString("end");n.title=j.optString("title");n.teacher=j.optString("teacher");n.room=j.optString("room");n.type=j.optString("type");n.subgroup=j.optInt("subgroup");n.source=j.optString("source");n.change=j.optString("change");n.old=j.optString("old");return n;}
 public static String encode(Data d)throws JSONException{JSONObject j=new JSONObject().put("group",d.group).put("url",d.url).put("checkedAt",d.checkedAt).put("parserVersion",d.parserVersion).put("sourceCells",d.sourceCells);j.put("dates",new JSONArray(d.dates));JSONArray ls=new JSONArray();for(Lesson n:d.lessons)ls.put(lessonJson(n));j.put("lessons",ls);JSONObject ts=new JSONObject();for(Map.Entry<Integer,String[]> e:d.times.entrySet())ts.put(e.getKey().toString(),new JSONArray(Arrays.asList(e.getValue())));return j.put("times",ts).toString();}
 public static Data decode(String json)throws JSONException{JSONObject j=new JSONObject(json);Data d=new Data();d.group=j.getString("group");d.url=j.getString("url");d.checkedAt=j.getLong("checkedAt");d.parserVersion=j.optInt("parserVersion");d.sourceCells=j.optInt("sourceCells");JSONArray dates=j.getJSONArray("dates"),ls=j.getJSONArray("lessons");for(int i=0;i<dates.length();i++)d.dates.add(dates.getString(i));for(int i=0;i<ls.length();i++)d.lessons.add(lessonFrom(ls.getJSONObject(i)));JSONObject t=j.getJSONObject("times");for(Iterator<String> k=t.keys();k.hasNext();){String key=k.next();JSONArray a=t.getJSONArray(key);d.times.put(Integer.parseInt(key),new String[]{a.getString(0),a.getString(1)});}return d;}
 public static String encodeChanges(Changes c)throws JSONException{JSONArray ls=new JSONArray();for(Lesson n:c.lessons)ls.put(lessonJson(n));return new JSONObject().put("lessons",ls).put("dates",new JSONArray(c.checkedDates)).put("checkedAt",c.checkedAt).put("warnings",new JSONArray(c.warnings)).toString();}
 public static Changes decodeChanges(String s)throws JSONException{JSONObject j=new JSONObject(s);Changes c=new Changes();JSONArray a=j.getJSONArray("lessons"),d=j.getJSONArray("dates"),w=j.optJSONArray("warnings");for(int i=0;i<a.length();i++)c.lessons.add(lessonFrom(a.getJSONObject(i)));for(int i=0;i<d.length();i++)c.checkedDates.add(d.getString(i));if(w!=null)for(int i=0;i<w.length();i++)c.warnings.add(w.getString(i));c.checkedAt=j.getLong("checkedAt");return c;}
}
