package ru.ne1es.timetable;

import android.content.Context;
import android.webkit.CookieManager;
import org.json.*;
import org.jsoup.Jsoup;
import java.net.*;
import java.io.*;
import java.util.*;
import java.time.*;

class Repository {
 static class LoginRequired extends IOException {
  final String reason;
  LoginRequired(){this("форма входа");}
  LoginRequired(String reason){super("Нужен вход в УлГТУ");this.reason=reason;}
 }
 private static final class HttpFailure extends IOException {
  final int status;
  HttpFailure(int status){super("Источник ответил HTTP "+status);this.status=status;}
 }
 private static final Object SESSION_LOCK=new Object();
 private static URL official(URL url)throws IOException{
  if(!url.getProtocol().equals("https")||!url.getHost().equals("lk.ulstu.ru")||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443))
   throw new IOException("Неожиданный адрес авторизации");
  return url;
 }
 private final Context context;
 private final String changeSource;
 private static final Object FILE_LOCK=new Object();
 private static final Map<String,Long> RETRY_AFTER=new java.util.concurrent.ConcurrentHashMap<>();
 private static final String RATE_LIMIT="Источник ограничил частоту запросов (HTTP 429). Последние данные сохранены.";
 Repository(Context c){
  context=c.getApplicationContext();
  android.content.SharedPreferences prefs=ReminderReceiver.prefs(context);
  changeSource=prefs.getString("changes_source",Schedule.DISK);
  if(!prefs.getBoolean("web_login_migrated",false))
   prefs.edit().remove("auto_login_blocked").putBoolean("web_login_migrated",true).apply();
 }
 byte[] request(String address,boolean cookies,int limit)throws Exception{
  return exchange(address,cookies,limit).body;
 }
 private static final class Page {
  final URL url;final byte[] body;
  Page(URL url,byte[] body){this.url=url;this.body=body;}
 }
 private Page exchange(String address,boolean cookies,int limit)throws Exception{
  URL url=new URL(address);
  for(int redirects=0;redirects<6;redirects++){
   if(!url.getProtocol().equals("https"))throw new IOException("Источник требует незащищённое соединение");
   if(cookies)official(url);
   String host=url.getHost();
   if(!cookies&&RETRY_AFTER.getOrDefault(host,0L)>android.os.SystemClock.elapsedRealtime())throw new IOException(RATE_LIMIT);
   HttpURLConnection c=openConnection(url,cookies);c.setConnectTimeout(18000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",context.getSharedPreferences("timetable",0).getString("web_user_agent","Mozilla/5.0 Android UlstuTimetable/0.7.0"));
   if(cookies){String ck=CookieManager.getInstance().getCookie(url.toString());if(ck!=null)c.setRequestProperty("Cookie",ck);}
   try{
    int status=c.getResponseCode();if(cookies)for(Map.Entry<String,List<String>> e:c.getHeaderFields().entrySet())if(e.getKey()!=null&&e.getKey().equalsIgnoreCase("Set-Cookie"))for(String v:e.getValue())CookieManager.getInstance().setCookie(url.toString(),v);
    if(status>=300&&status<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("Пустое перенаправление");url=new URL(url,loc);continue;}
    if(status==429&&!cookies){
     int seconds=Math.max(60,Math.min(3600,c.getHeaderFieldInt("Retry-After",300)));
     RETRY_AFTER.put(host,android.os.SystemClock.elapsedRealtime()+seconds*1000L);throw new IOException(RATE_LIMIT);
    }
    if((status==401||status==403)&&cookies)throw new LoginRequired("HTTP "+status);
    if(status!=200)throw new HttpFailure(status);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Файл превышает допустимый размер");out.write(buf,0,n);}return new Page(url,out.toByteArray());}
   }catch(java.net.SocketTimeoutException timeout){
    if(cookies)recordRefusal("таймаут",false);
    throw timeout;
   }finally{c.disconnect();}
  }throw new IOException("Слишком много перенаправлений");
 }
 private HttpURLConnection openConnection(URL url,boolean cookies)throws IOException{
  String host=url.getHost();
  if(!cookies&&(host.equals("cloud-api.yandex.net")||host.endsWith(".yandex.ru")||host.endsWith(".yandex.net")||host.endsWith(".yandexcloud.net")||host.equals("yadi.sk"))){
   android.net.ConnectivityManager manager=context.getSystemService(android.net.ConnectivityManager.class);
   try{
    android.net.Network active=manager==null?null:manager.getActiveNetwork();
    android.net.NetworkCapabilities current=active==null?null:manager.getNetworkCapabilities(active);
    if(current!=null&&current.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)){
     for(android.net.Network network:manager.getAllNetworks()){
      android.net.NetworkCapabilities caps=manager.getNetworkCapabilities(network);
      if(caps!=null&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)&&caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))
       return (HttpURLConnection)network.openConnection(url);
     }
    }
   }catch(IOException|SecurityException unavailable){/* Use system routing when direct routing is unavailable. */}
  }
  return (HttpURLConnection)url.openConnection();
 }
 private org.jsoup.nodes.Document document(Page page)throws IOException{
  org.jsoup.nodes.Document parsed=Jsoup.parse(new ByteArrayInputStream(page.body),null,page.url.toString());
  parsed.outputSettings().prettyPrint(false);
  return parsed;
 }
 private String timetable(Page page)throws IOException{
  String html=document(page).outerHtml();
  if(SourceSession.loginPage(html))throw new LoginRequired();
  return html;
 }
 String html(String url)throws Exception{
  synchronized(SESSION_LOCK){
   restoreSession();
   try{return timetable(exchange(url,true,4000000));}
   catch(LoginRequired expired){
    android.content.SharedPreferences prefs=context.getSharedPreferences("timetable",0);
    recordRefusal(expired.reason,true);
    if(prefs.getBoolean("auto_login_blocked",false))throw expired;
    JSONObject credentials;
    try{credentials=StoredLogin.read(context);}catch(Exception unavailable){credentials=null;}
    if(credentials==null){
     prefs.edit().putBoolean("session_required",true).apply();
     throw expired;
    }
    try{
     login(credentials.getString("login"),credentials.getString("password"),true);
     return timetable(exchange(url,true,4000000));
    }catch(WebLogin.Failure rejected){
     recordRefusal(rejected.getMessage(),rejected.interactive);
     if(!rejected.interactive)throw rejected;
     prefs.edit().putBoolean("session_required",true).putBoolean("auto_login_blocked",true).apply();
     throw new LoginRequired(rejected.getMessage());
    }
   }
  }
 }
 void recordRefusal(String reason,boolean sessionFailure){
  android.content.SharedPreferences prefs=context.getSharedPreferences("timetable",0);
  long now=System.currentTimeMillis();
  android.content.SharedPreferences.Editor edit=prefs.edit()
   .putString("last_refusal_reason",reason).putLong("last_refusal_at",now);
  if(sessionFailure&&prefs.getLong("session_expired_at",0)==0)edit.putLong("session_expired_at",now);
  if(sessionFailure)edit.putLong("login_before_refusal_at",prefs.getLong("last_login_at",0));
  edit.apply();
 }
 private static final String[] COOKIE_SCOPES={WebLogin.ORIGIN+"/",Schedule.DIRECTORY};
 private static boolean cookieRestoreAttempted;
 private void restoreSession(){
  if(cookieRestoreAttempted)return;
  cookieRestoreAttempted=true;

  try{restoreSnapshot(StoredLogin.readCookies(context),false);}catch(Exception ignored){}
 }
 private JSONObject snapshot()throws JSONException{
  JSONObject result=new JSONObject();
  CookieManager cookies=CookieManager.getInstance();
  for(String scope:COOKIE_SCOPES){
   String value=cookies.getCookie(scope);if(value!=null&&!value.isEmpty())result.put(scope,value);
  }
  return result;
 }
 private void restoreSnapshot(JSONObject snapshot,boolean replace){
  if(snapshot==null)return;
  CookieManager cookies=CookieManager.getInstance();
  Set<String> root=new HashSet<>();
  for(String scope:COOKIE_SCOPES){
   String present=cookies.getCookie(scope);
   Set<String> names=new HashSet<>();
   if(present!=null&&!replace)for(String value:present.split("; ")){
    int equals=value.indexOf('=');if(equals>0)names.add(value.substring(0,equals));
   }
   for(String cookie:snapshot.optString(scope).split("; ")){
   int equals=cookie.indexOf('=');
   if(equals<1||cookie.indexOf('\n')>=0||cookie.indexOf('\r')>=0)continue;
   if(scope.equals(COOKIE_SCOPES[0]))root.add(cookie);
   else if(root.contains(cookie))continue;
   if(!names.contains(cookie.substring(0,equals)))cookies.setCookie(scope,cookie+"; Secure; HttpOnly; Path="+(scope.equals(COOKIE_SCOPES[0])?"/":"/timetable/"));
   }
  }
 }
 void prepareManual(){synchronized(SESSION_LOCK){restoreSession();}}
 void saveAndLogin(String user,String password)throws Exception{
  synchronized(SESSION_LOCK){
   restoreSession();
   JSONObject previous=snapshot();
   StoredLogin.save(context,user,password);
   try{
    WebLogin.resetCookies();
    login(user,password,false);
   }catch(Exception rejected){
    recordRefusal(responseStatus(rejected),rejected instanceof WebLogin.Failure&&((WebLogin.Failure)rejected).interactive);
    WebLogin.resetCookies();
    restoreSnapshot(previous,true);
    throw rejected;
   }
  }
 }
 void confirmDirectory(boolean automatic)throws Exception{
  Page result=exchange(Schedule.DIRECTORY,true,4000000);
  String html=timetable(result);
  if(!WebLogin.directory(android.net.Uri.parse(result.url.toString()))
    ||(!SourceSession.confirmedTimetable(html)&&Schedule.catalogSections(html,result.url.toString()).isEmpty()))
   throw new WebLogin.Failure("Страница после входа не открылась",true);
  completeLogin(automatic);
 }
 void completeLogin(boolean automatic)throws Exception{
  StoredLogin.saveCookies(context,snapshot());
  authenticated(automatic);
  context.getSystemService(android.app.NotificationManager.class).cancel(5504);
  context.getSharedPreferences("timetable",0).edit().putLong("last_login_at",System.currentTimeMillis()).apply();
  CookieManager.getInstance().flush();
  WebLogin.trace(context,"Расписание подтверждено: HTTP 200");
 }
 private void login(String user,String password,boolean automatic)throws Exception{
  WebLogin.await(context,user,password);
  try{confirmDirectory(automatic);}
  catch(LoginRequired rejected){throw new WebLogin.Failure(rejected.reason.equals("форма входа")?"Сайт вернул форму после отправки":rejected.reason,true);}
 }
 void forget(Runnable complete)throws Exception{
  synchronized(SESSION_LOCK){
   StoredLogin.clear(context);
   cookieRestoreAttempted=true;
   context.getSharedPreferences("timetable",0).edit().remove("has_session").remove("session_verified_at")
    .remove("last_login_at").remove("last_refusal_reason").remove("last_refusal_at").remove("login_before_refusal_at").remove("session_expired_at").remove("automatic_login_at").remove("auto_login_blocked").remove("session_required").remove("auto_login_steps").apply();
   android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
   java.util.concurrent.CountDownLatch cleared=new java.util.concurrent.CountDownLatch(1);
   main.post(()->CookieManager.getInstance().removeAllCookies(removed->{
    try{CookieManager.getInstance().flush();}finally{cleared.countDown();}
   }));
   if(!cleared.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Удаление cookie не подтверждено");
   main.post(complete);
  }
 }
 String changeSource(){return changeSource;}
 List<Schedule.Group> groups()throws Exception{
  try{return acceptCatalog(html(Schedule.DIRECTORY),Schedule.DIRECTORY);}catch(LoginRequired expired){throw expired;}catch(Exception unavailable){return acceptCatalog(html(Schedule.INDEX));}
 }
 List<Schedule.Group> acceptCatalog(String html)throws Exception{return acceptCatalog(html,Schedule.INDEX);}
 List<Schedule.Group> acceptCatalog(String html,String base)throws Exception{
  Map<String,Schedule.Group> found=new TreeMap<>();
  int loaded=0;
  for(Schedule.Group g:Schedule.groups(html,base)){found.put(Schedule.norm(g.name),g);loaded++;}
  int count=0;
  for(String link:Schedule.catalogPages(html,base)){
   if(link.equals(base)||count++>=18)continue;
   try{for(Schedule.Group g:Schedule.groups(html(link),link)){found.put(Schedule.norm(g.name),g);loaded++;}}
   catch(LoginRequired expired){throw expired;}catch(IOException unavailable){/* Preserve the last valid section. */}
  }
  if(loaded==0)throw new IOException("Каталог групп не распознан");
  return mergeGroups(found);
 }
 static final class GroupDiagnostics {
  String directory="Нет ответа";
  final List<PartDiagnostics> parts=new ArrayList<>();
  GroupDiagnostics(){for(int number=1;number<=6;number++)parts.add(new PartDiagnostics(number));}
 }
 static final class PartDiagnostics {
  final int number;
  int linkCount;
  final Set<String> responses=new LinkedHashSet<>();
  final Map<String,Schedule.Group> groups=new LinkedHashMap<>();
  PartDiagnostics(int number){this.number=number;}
 }
 private static String responseStatus(Exception error){
  if(error instanceof LoginRequired){
   String reason=((LoginRequired)error).reason;
   return reason.equals("форма входа")?"Пришла форма входа":reason;
  }
  if(error instanceof WebLogin.Failure)return error.getMessage();
  if(error instanceof HttpFailure)return "HTTP "+((HttpFailure)error).status;
  if(error instanceof java.net.SocketTimeoutException)return "Таймаут";
  return "Нет ответа";
 }
 GroupDiagnostics diagnoseGroups(){
  GroupDiagnostics diagnostic=new GroupDiagnostics();
  Map<Integer,Set<String>> sections;
  try{
   String directory=html(Schedule.DIRECTORY);
   diagnostic.directory="HTTP 200";
   sections=Schedule.catalogSections(directory,Schedule.DIRECTORY);
   if(sections.isEmpty())diagnostic.directory="HTTP 200 · Ссылки на части не найдены";
  }catch(Exception unavailable){
   diagnostic.directory=responseStatus(unavailable);
   return diagnostic;
  }
  Map<String,Schedule.Group> additions=new LinkedHashMap<>();
  for(PartDiagnostics part:diagnostic.parts){
   part.linkCount=sections.getOrDefault(part.number,Collections.emptySet()).size();
   for(String url:sections.getOrDefault(part.number,Collections.emptySet())){
    try{
     String page=html(url);
     part.responses.add("HTTP 200");
     for(Schedule.Group group:Schedule.groups(page,url))part.groups.put(Schedule.norm(group.name),group);
    }catch(Exception unavailable){part.responses.add(responseStatus(unavailable));}
   }
   additions.putAll(part.groups);
  }
  if(!additions.isEmpty()){
   try{mergeGroups(additions);}
   catch(IOException unavailable){diagnostic.directory+=" · кэш не сохранён";}
  }
  return diagnostic;
 }
 private List<Schedule.Group> mergeGroups(Map<String,Schedule.Group> additions)throws IOException{
  synchronized(FILE_LOCK){
   Map<String,Schedule.Group> merged=new TreeMap<>();
   for(Schedule.Group group:cachedGroups())merged.put(Schedule.norm(group.name),group);
   merged.putAll(additions);
   try{
    JSONArray rows=new JSONArray();
    for(Schedule.Group group:merged.values())rows.put(new JSONObject().put("name",group.name).put("url",group.url).put("faculty",group.faculty));
    save("groups.json",rows.toString());
   }catch(Exception unavailable){throw new IOException("Не удалось сохранить список групп");}
   return new ArrayList<>(merged.values());
  }
 }
 List<Schedule.Group> cachedGroups(){
  Map<String,Schedule.Group> merged=new TreeMap<>();
  try{for(Schedule.Group group:parseGroups(assetText("groups.json")))merged.put(Schedule.norm(group.name),group);}
  catch(Exception unavailable){/* Live cache may still be available. */}
  try{for(Schedule.Group group:parseGroups(read("groups.json")))merged.put(Schedule.norm(group.name),group);}
  catch(Exception unavailable){/* The bundled catalog is available offline. */}
  return new ArrayList<>(merged.values());
 }
 private String assetText(String name)throws Exception{try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 private List<Schedule.Group> parseGroups(String s)throws Exception{List<Schedule.Group> g=new ArrayList<>();JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);g.add(new Schedule.Group(j.getString("name"),j.getString("url"),j.optString("faculty",Schedule.facultyFromSource(j.getString("url")))));}return g;}
 Schedule.Data load(Schedule.Group g)throws Exception{
  Exception problem=null;
  if(Schedule.universityPage(g.url)&&!g.url.endsWith("raspisan.html")){
   try{return Schedule.parse(html(g.url),g.name,g.url);}catch(LoginRequired expired){throw expired;}catch(Exception unavailable){problem=unavailable;}
  }
  try{for(Schedule.Group fresh:groups())if(Schedule.norm(fresh.name).equals(Schedule.norm(g.name))&&!fresh.url.equals(g.url))
    return Schedule.parse(html(fresh.url),fresh.name,fresh.url);}
  catch(LoginRequired expired){throw expired;}catch(Exception unavailable){if(problem==null)problem=unavailable;}
  if(problem!=null)throw problem;
  throw new IOException("Группа отсутствует в доступном каталоге УлГТУ");
 }
 private void authenticated(boolean automatic){android.content.SharedPreferences p=context.getSharedPreferences("timetable",0);android.content.SharedPreferences.Editor e=p.edit().putBoolean("has_session",true).putBoolean("session_required",false).putLong("session_verified_at",System.currentTimeMillis());if(automatic)e.putLong("automatic_login_at",System.currentTimeMillis());e.remove("session_expired_at");e.remove("auto_login_blocked");if(p.getBoolean("background_auth_required",false)||p.getString("background_error","").equals("УлГТУ: Нужно войти в личный кабинет УлГТУ"))e.remove("background_error").putBoolean("background_auth_required",false);e.apply();}

 void saveData(Schedule.Data d)throws Exception{new ScheduleStore(context.getFilesDir()).save(d);}
 Schedule.Data cached(String g){return new ScheduleStore(context.getFilesDir()).load(g);}
 String cacheName(String group){return "schedule-"+Schedule.norm(group).replaceAll("[^A-ZА-Я0-9]","")+".json";}
 void saveChanges(String group,Schedule.Changes c)throws Exception{save(changesCache(group),Schedule.encodeChanges(c));}
 Schedule.Changes cachedChanges(String group){return cachedRemoteChanges(group);}
 Schedule.Changes cachedRemoteChanges(String group){try{return Schedule.decodeChanges(read(changesCache(group)));}catch(Exception e){return new Schedule.Changes();}}
 private String changesCache(String group){
  if(Schedule.DISK.equals(changeSource))return "changes-"+cacheName(group);
  return "changes-"+UUID.nameUUIDFromBytes(changeSource.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+cacheName(group);
 }
 static boolean validChangeSource(String text){
  try{URL u=new URL(text);return u.getProtocol().equals("https")&&u.getUserInfo()==null&&!u.getHost().isEmpty()
    &&(isYandex(u)||u.getPath().toLowerCase(Locale.ROOT).endsWith(".docx"));}
  catch(MalformedURLException e){return false;}
 }
 private static boolean isYandex(URL url){
  return Arrays.asList("disk.yandex.ru","disk.yandex.com","yadi.sk").contains(url.getHost().toLowerCase(Locale.ROOT));
 }
 private void applyChanges(Schedule.Changes result,Schedule.Changes part,String source){
  result.lessons.removeIf(l->part.checkedDates.contains(l.date));
  for(Schedule.Lesson l:part.lessons){l.source=source;result.lessons.add(l);}
  result.checkedDates.addAll(part.checkedDates);
 }
 Schedule.Changes changes(Schedule.Data schedule,Schedule.Changes previous)throws Exception{
  try {
   Schedule.Changes result=remoteChanges(schedule,previous);
   if(result.warnings.stream().noneMatch(message->message.contains("HTTP 429")))return result;
   return backupChanges(schedule,result);
  }catch(IOException error){
   if(error.getMessage()==null||!error.getMessage().contains("HTTP 429"))throw error;
   Schedule.Changes result=new Schedule.Changes();
   if(previous!=null){result.lessons.addAll(previous.lessons);result.checkedDates.addAll(previous.checkedDates);result.checkedAt=previous.checkedAt;}
   result.warnings.add(error.getMessage());
   return backupChanges(schedule,result);
  }
 }
 private Schedule.Changes backupChanges(Schedule.Data schedule,Schedule.Changes result){
  String url=context.getSharedPreferences("timetable",0).getString("changes_backup","");
  if(!validChangeSource(url))return result;
  try{
   URL parsed=new URL(url);
   if(isYandex(parsed))return result;
   Schedule.Changes part=Schedule.parseDocx(request(url,false,16000000),parsed.getPath(),schedule.group,schedule);
   if(Collections.disjoint(part.checkedDates,schedule.dates))return result;
   applyChanges(result,part,url);
   result.warnings.add("Резервный DOCX: "+String.join(", ",part.checkedDates));
  }catch(Exception error){result.warnings.add("Резервная ссылка: "+error.getMessage());}
  return result;
 }
 private Schedule.Changes remoteChanges(Schedule.Data schedule,Schedule.Changes previous)throws Exception{
  if(!validChangeSource(changeSource))throw new IOException("Некорректная ссылка на изменения");
  if(!isYandex(new URL(changeSource))){
   String name=new URL(changeSource).getPath();
   Schedule.Changes part=Schedule.parseDocx(request(changeSource,false,16000000),name,schedule.group,schedule);
   Schedule.Changes result=new Schedule.Changes();if(previous!=null){result.lessons.addAll(previous.lessons);result.checkedDates.addAll(previous.checkedDates);}
   applyChanges(result,part,changeSource);result.checkedAt=part.checkedAt;return result;
  }
  String base="https://cloud-api.yandex.net/v1/disk/public/resources";
  String key="public_key="+URLEncoder.encode(changeSource,"UTF-8");
  Map<String,JSONObject> files=new TreeMap<>();
  Schedule.Changes result=new Schedule.Changes();
  if(previous!=null){result.lessons.addAll(previous.lessons);result.checkedDates.addAll(previous.checkedDates);}
  int offset=0,total=1;
  while(offset<total){
   JSONObject page=new JSONObject(new String(request(base+"?"+key+"&limit=100&offset="+offset,false,3000000),"UTF-8"));
   if(offset==0&&page.optString("type").equals("file")){
    page.remove("path");
    selectChangeFile(page,schedule,files,result.warnings);
    break;
   }
   JSONObject listing=page.getJSONObject("_embedded");total=listing.getInt("total");
   if(total>1000)throw new IOException("Слишком большой каталог изменений");
   JSONArray items=listing.getJSONArray("items");
   if(items.length()==0&&offset<total)throw new IOException("Неполный список файлов");
   for(int i=0;i<items.length();i++)selectChangeFile(items.getJSONObject(i),schedule,files,result.warnings);
   offset+=items.length();
  }
  for(JSONObject f:files.values()){
   try{applyChanges(result,yandexChange(base,key,f,schedule),changeSource);}
   catch(Exception e){result.warnings.add(f.optString("name")+": "+e.getMessage());}
  }
  result.checkedAt=result.warnings.isEmpty()?System.currentTimeMillis():previous==null?0:previous.checkedAt;return result;
 }
 private void selectChangeFile(JSONObject file,Schedule.Data schedule,Map<String,JSONObject> files,List<String> warnings){
  String name=file.optString("name");
  if(!file.optString("type").equals("file")){warnings.add("Нужна проверка подпапки: "+name);return;}
  String date;
  try{date=Schedule.changeDate(name);}catch(IOException unknown){warnings.add(unknown.getMessage());return;}
  if(!schedule.dates.contains(date))return;
  if(!name.toLowerCase(Locale.ROOT).endsWith(".docx")){warnings.add("Нужна ручная проверка файла: "+name);return;}
  JSONObject prior=files.get(date);
  if(prior==null||prior.optString("modified").compareTo(file.optString("modified"))<0)files.put(date,file);
 }
 private Schedule.Changes yandexChange(String base,String key,JSONObject file,Schedule.Data schedule)throws Exception{
  String path=file.optString("path");
  String url=base+"/download?"+key+(path.isEmpty()||path.equals("/")?"":"&path="+URLEncoder.encode(path,"UTF-8"));
  JSONObject link=new JSONObject(new String(request(url,false,1000000),"UTF-8"));
  URL download=new URL(link.getString("href"));String host=download.getHost();
  if(!download.getProtocol().equals("https")||!(host.endsWith(".yandex.ru")||host.endsWith(".yandex.net")||host.endsWith(".yandexcloud.net")))throw new IOException("Неожиданный сервер файла");
  return Schedule.parseDocx(request(download.toString(),false,16000000),file.getString("name"),schedule.group,schedule);
 }
 private String read(String name)throws Exception{try(InputStream in=context.openFileInput(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 private void save(String name,String text)throws Exception{synchronized(FILE_LOCK){File temp=new File(context.getFilesDir(),name+".tmp"),dest=new File(context.getFilesDir(),name);try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes("UTF-8"));out.getFD().sync();}if(!temp.renameTo(dest))throw new IOException("Не удалось сохранить расписание");}}
}
