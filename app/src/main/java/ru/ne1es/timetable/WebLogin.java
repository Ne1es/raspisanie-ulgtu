package ru.ne1es.timetable;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.json.JSONTokener;

final class WebLogin {
 static final String ORIGIN="https://lk.ulstu.ru";
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 static final class Failure extends IOException {
  final boolean interactive;
  Failure(String reason,boolean interactive){super(reason);this.interactive=interactive;}
 }
 static boolean official(Uri uri){
  return "https".equals(uri.getScheme())&&"lk.ulstu.ru".equals(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);
 }
 static boolean directory(Uri uri){
  return official(uri)&&("/timetable/".equals(uri.getPath())||"/timetable".equals(uri.getPath()));
 }
 static String route(String url){
  Uri uri=Uri.parse(url==null?"":url);
  if(!official(uri))return "Переход за пределы УлГТУ";
  String path=uri.getEncodedPath();
  if(path==null||path.equals("/")||path.equals("/timetable/")||path.equals("/timetable")||path.equals("/timetable/oidc/redirect"))
   return "lk.ulstu.ru"+(path==null?"/":path);
  return "lk.ulstu.ru/[другой путь]";
 }
 static void trace(Context context,String step){
  android.content.SharedPreferences prefs=ReminderReceiver.prefs(context);
  if(!prefs.getBoolean("auto_login_debug",false))return;
  String log=prefs.getString("auto_login_steps","");
  if(log.length()>2400)log=log.substring(log.length()-1800);
  prefs.edit().putString("auto_login_steps",log+(log.isEmpty()?"":"\n")+step).apply();
 }
 static String script(String user,String password,boolean submit){
  return "(()=>{if(location.origin!=="+JSONObject.quote(ORIGIN)+")return 'origin';"
   +"const p=document.querySelector('input[type=password]');if(!p)return 'noform';"
   +"const f=p.form;if(!f)return 'missing';"
   +"const u=[...f.querySelectorAll('input')].find(e=>!e.disabled&&['login','username','user','email'].includes(e.name.toLowerCase()))"
   +"||f.querySelector('input[autocomplete=username],input[type=email],input[type=text]:not([disabled])');"
   +"if(!u||p.disabled)return 'missing';"
   +"const b=f.querySelector('button[type=submit],input[type=submit],button:not([type])');"
   +"const action=new URL(b?.getAttribute('formaction')||f.action,location.href);"
   +"if(action.origin!==location.origin||action.username||action.password||(b?.getAttribute('formmethod')||f.method).toLowerCase()!=='post')return 'origin';"
   +"const set=(e,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);"
   +"e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};"
   +"set(u,"+JSONObject.quote(user)+");set(p,"+JSONObject.quote(password)+");"
   +"if(document.querySelector('[name*=captcha i],[id*=captcha i],iframe[src*=captcha i],input[autocomplete=one-time-code]'))return 'challenge';"
   +(submit?"setTimeout(()=>{const a=new URL(b?.getAttribute('formaction')||f.action,location.href);if(location.origin!=="+JSONObject.quote(ORIGIN)+"||a.origin!==location.origin||a.username||a.password||(b?.getAttribute('formmethod')||f.method).toLowerCase()!=='post')return;if(b)b.click();else if(f.requestSubmit)f.requestSubmit();},25);return 'submitted';":"return 'filled';")+"})()";
 }
 static String result(String value){
  try{return String.valueOf(new JSONTokener(value).nextValue());}
  catch(Exception invalid){return "";}
 }
 static void resetCookies()throws Exception{
  CountDownLatch done=new CountDownLatch(1);
  MAIN.post(()->CookieManager.getInstance().removeAllCookies(cleared->{CookieManager.getInstance().flush();done.countDown();}));
  if(!done.await(8,TimeUnit.SECONDS))throw new Failure("Не удалось начать новый вход",false);
 }
 static void await(Context context,String user,String password)throws Exception{
  CountDownLatch done=new CountDownLatch(1);
  Flow flow=new Flow(context,user,password,done);
  MAIN.post(flow::start);
  try{
   if(!done.await(36,TimeUnit.SECONDS))throw new Failure("Страница после входа не открылась",true);
   if(flow.failure!=null)throw flow.failure;
  }finally{MAIN.post(flow::close);}
 }
 static void secureSettings(WebView web){
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
  settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setJavaScriptCanOpenWindowsAutomatically(false);
  web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
  CookieManager.getInstance().setAcceptCookie(true);
 }
 static WebResourceResponse denied(){
  return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
 }
 private static final class Flow extends WebViewClient {
  final Context context;final String user,password;final CountDownLatch done;
  WebView web;Failure failure;boolean finished,submitted;
  Flow(Context context,String user,String password,CountDownLatch done){this.context=context;this.user=user;this.password=password;this.done=done;}
  void start(){
   try{
    web=new WebView(context);secureSettings(web);web.setWebViewClient(this);
    trace(context,"Открываем /timetable/");web.loadUrl(Schedule.DIRECTORY);
    MAIN.postDelayed(()->finish(new Failure(submitted?"Страница после входа не открылась":"Форма входа не загрузилась",true)),32000);
   }catch(RuntimeException unavailable){finish(new Failure("Форма входа не загрузилась",true));}
  }
  void finish(Failure error){
   if(finished)return;finished=true;failure=error;
   if(error!=null)trace(context,error.getMessage());
   done.countDown();
  }
  void close(){finished=true;if(web!=null){web.stopLoading();web.destroy();web=null;}}
  @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){return !official(request.getUrl());}
  @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){return official(request.getUrl())?null:denied();}
  @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){
   if(!official(Uri.parse(url))){w.stopLoading();finish(new Failure("Сайт запросил переход за пределы УлГТУ",true));}
  }
  @Override public void onPageFinished(WebView w,String url){
   if(finished||!url.equals(w.getUrl())||!official(Uri.parse(url)))return;
   trace(context,"Открылась страница: "+route(url));
   if(submitted){
    if(directory(Uri.parse(url))){CookieManager.getInstance().flush();finish(null);}
    else w.evaluateJavascript("!!document.querySelector('input[type=password]')",hasForm->{
     if("true".equals(hasForm)&&url.equals(w.getUrl()))finish(new Failure("Сайт вернул форму после отправки",true));
    });
    return;
   }
   w.evaluateJavascript(script(user,password,true),value->{
    if(finished||!url.equals(w.getUrl()))return;
    switch(result(value)){
     case "submitted": submitted=true;trace(context,"Форма загружена\nПоля найдены\nФорма отправлена");break;
     case "challenge": trace(context,"Форма загружена\nПоля найдены");finish(new Failure("Сайт требует подтверждения",true));break;
     case "missing": finish(new Failure("Не найдены поля формы",true));break;
     case "origin": finish(new Failure("Адрес формы не соответствует УлГТУ",true));break;
     default: if(directory(Uri.parse(url)))finish(null);else finish(new Failure("Форма входа не загрузилась",true));
    }
   });
  }
  @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError error){
   if(request.isForMainFrame())finish(new Failure(error.getErrorCode()==ERROR_TIMEOUT?"Таймаут":"Нет сети или сайт недоступен",false));
  }
  @Override public void onReceivedHttpError(WebView w,WebResourceRequest request,WebResourceResponse response){
   if(request.isForMainFrame()){int code=response.getStatusCode();finish(new Failure("HTTP "+code,code==401||code==403));}
  }
  @Override public void onReceivedSslError(WebView w,SslErrorHandler handler,android.net.http.SslError error){
   handler.cancel();finish(new Failure("Не удалось проверить сертификат УлГТУ",false));
  }
 }
}
