package ru.ne1es.timetable;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class GroupSections {
 private static final String[] TITLES={"Все","Часть 1","Часть 2","Часть 3","КЭИ","СФ","Часть 6"};
 private static final String[] DESCRIPTIONS={"","Часть 1 · МФ, РТФ, ЭФ, ИФМИ, ИИ-магистр, ЭАбз, Эбз, Рбз",
  "Часть 2 · ФИСТ, ГФ, ВТАСбз, ИДбз","Часть 3 · ИЭФ, ЗВФ ИННО","КЭИ","СФ","Часть 6 · ИАТУ, ССФ"};

 private static final java.util.regex.Pattern RUNS=java.util.regex.Pattern.compile("[0-9]+|[^0-9]+");
 private final Map<String,Integer> courses=new HashMap<>();
 private static final java.text.Collator RUSSIAN=java.text.Collator.getInstance(Locale.forLanguageTag("ru"));
 static String searchKey(String text){
  String value=text.toUpperCase(Locale.ROOT);
  String latin="ABCEHKMOPTXY",russian="АВСЕНКМОРТХУ";
  StringBuilder result=new StringBuilder();
  for(char c:value.toCharArray()){
   int index=latin.indexOf(c);
   if(index>=0)result.append(russian.charAt(index));
   else if(Character.isLetterOrDigit(c))result.append(c);
  }
  return result.toString();
 }
 static boolean matches(String name,String query){
  String key=searchKey(query);
  return searchKey(name).contains(key)||query.toUpperCase(Locale.ROOT).contains("P")&&
    searchKey(name).contains(searchKey(query.toUpperCase(Locale.ROOT).replace('P','П')));
 }
 static int natural(String a,String b){
  java.util.regex.Matcher first=RUNS.matcher(a),second=RUNS.matcher(b);
  while(first.find()){
   if(!second.find())return 1;
   String x=first.group(),y=second.group();
   int comparison=Character.isDigit(x.charAt(0))&&Character.isDigit(y.charAt(0))
    ?new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)):RUSSIAN.compare(x,y);
   if(comparison!=0)return comparison;
  }
  return second.find()?-1:0;
 }
 int course(Schedule.Group group){return courses.getOrDefault(group.url+"|"+Schedule.norm(group.name),0);}
 List<List<Schedule.Group>> load(Context context,List<Schedule.Group> current){
  List<Map<String,Schedule.Group>> byPart=new ArrayList<>();
  for(int i=0;i<TITLES.length;i++)byPart.add(new LinkedHashMap<>());
  try(java.io.InputStream in=context.getAssets().open("group-parts.json")){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
   byte[] buffer=new byte[8192];
   for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
   JSONArray rows=new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
   for(int i=0;i<rows.length();i++){
    JSONObject row=rows.getJSONObject(i);
    Schedule.Group group=new Schedule.Group(row.getString("name"),row.getString("url"),row.getString("faculty"));
    courses.put(group.url+"|"+Schedule.norm(group.name),row.optInt("course",0));
    byPart.get(row.getInt("part")).put(Schedule.norm(group.name),group);
   }
  }catch(java.io.IOException|JSONException unavailable){/* The current catalogue remains usable. */}
  for(Schedule.Group group:current){
   String key=Schedule.norm(group.name);
   byPart.get(0).put(key,group);
   int part=Schedule.catalogPart(group.url,"");
   if(part>0)byPart.get(part).put(key,group);
  }
  Comparator<Schedule.Group> order=Comparator.comparingInt((Schedule.Group group)->course(group)==0?7:course(group)).thenComparing(group->group.name,GroupSections::natural);
  List<List<Schedule.Group>> result=new ArrayList<>();
  for(Map<String,Schedule.Group> section:byPart){
   List<Schedule.Group> rows=new ArrayList<>(section.values());rows.sort(order);result.add(rows);
  }
  return result;
 }
 static String title(int part){return TITLES[part];}
 static String subtitle(Schedule.Group group){
  int part=Schedule.catalogPart(group.url,"");
  String key=Schedule.norm(group.name);
  // The university's newspaper confirms these particular groups, not the whole prefix.
  if(part==2&&key.equals("ПИБД21"))return "ФИСТ · Часть 2";
  if(part==2&&key.equals("СОБД21"))return "ГФ · Часть 2";
  return DESCRIPTIONS[part];
 }
}
