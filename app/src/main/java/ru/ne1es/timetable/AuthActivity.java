package ru.ne1es.timetable;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** Full-screen official sign-in. Never reads login fields or passwords. */
public final class AuthActivity extends Activity {
 private WebView web;private TextView status,done;private ProgressBar progress;private boolean completing,pageFailed,legacyCatalog;private Schedule.Group targetGroup;private boolean fallback,attempted;private String initialStatus;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String s,int sp,int color){TextView t=AppDesign.text(this,s,sp,false);t.setTextColor(color);return t;}
 private GradientDrawable bg(int c,int radius){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(radius));return g;}
 @Override public void onCreate(Bundle state){UiTheme.apply(this);super.onCreate(state);setResult(RESULT_CANCELED);UiTheme.bars(this);fallback=getIntent().getBooleanExtra("auto_fallback",false);initialStatus=getIntent().getStringExtra("initial_status");
  LinearLayout root=new LinearLayout(this);root.setId(R.id.auth_root);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(UiTheme.background(this));
  AppDesign.insets(root);
  LinearLayout header=new LinearLayout(this);
  header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),dp(10),dp(12),dp(9));
  header.addView(AppDesign.iconAction(this,R.drawable.ms_arrow_back,"Закрыть вход",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(9),0,0,0);
  copy.addView(AppDesign.text(this,"Вход в личный кабинет",22,true));
  TextView domain=AppDesign.text(this,"lk.ulstu.ru",12,true);domain.setTextColor(UiTheme.accent(this));copy.addView(domain);
  header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
  status=text(initialStatus==null?"Загружаем официальный сайт…":initialStatus,13,UiTheme.muted(this));status.setId(R.id.auth_status);status.setPadding(dp(20),0,dp(20),dp(12));root.addView(status);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(false);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
  web=new WebView(this);web.setId(R.id.auth_web);web.setBackgroundColor(Color.WHITE);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
  getSharedPreferences("timetable",0).edit().putString("web_user_agent",settings.getUserAgentString()).apply();CookieManager.getInstance().setAcceptCookie(true);
  web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView w,int value){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}});
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){pageFailed=false;progress.setProgress(0);progress.setVisibility(View.VISIBLE);status.setText(targetGroup==null?"Загружаем официальный сайт…":"Загружаем расписание группы…");}
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !WebLogin.official(r.getUrl());}
   @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return WebLogin.official(r.getUrl())?null:WebLogin.denied();}
   @Override public void onPageFinished(WebView w,String url){
    if(completing||pageFailed)return;
    progress.setVisibility(View.INVISIBLE);
    status.setText(initialStatus!=null?initialStatus:"Заверши вход на официальном сайте.");
    if(fallback&&!attempted&&!WebLogin.directory(Uri.parse(url))&&WebLogin.official(Uri.parse(url))){
     attempted=true;
     io.execute(()->{try{
      JSONObject credentials=StoredLogin.read(AuthActivity.this);
      if(credentials==null)return;
      String script=WebLogin.script(credentials.getString("login"),credentials.getString("password"),true);
      runOnUiThread(()->{if(web!=null&&url.equals(web.getUrl()))web.evaluateJavascript(script,value->{
       String outcome=WebLogin.result(value);
       WebLogin.trace(AuthActivity.this,"Официальный экран: "+WebLogin.route(url));
       if(outcome.equals("submitted"))WebLogin.trace(AuthActivity.this,"Поля найдены, форма отправлена");
       if(outcome.equals("challenge"))status.setText("Заверши подтверждение на официальном сайте.");
      });});
     }catch(Exception unavailable){runOnUiThread(()->status.setText("Не удалось заполнить данные входа."));}});
    }else verifyPage();
   }
   @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){if(e.getErrorCode()==ERROR_TIMEOUT)new Repository(AuthActivity.this).recordRefusal("таймаут",false);pageFailed=true;progress.setVisibility(View.INVISIBLE);status.setText("Страница не загрузилась: "+"проверь сеть и нажми «Повторить».");}}
   @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()){int code=response.getStatusCode();if(code==401||code==403)new Repository(AuthActivity.this).recordRefusal("HTTP "+code,true);pageFailed=true;status.setText("Сайт ответил HTTP "+code+". Можно повторить загрузку.");progress.setVisibility(View.INVISIBLE);}}
   @Override public void onReceivedSslError(WebView w,SslErrorHandler handler,SslError error){handler.cancel();pageFailed=true;status.setText("Не удалось проверить сертификат сайта. Вход остановлен.");progress.setVisibility(View.INVISIBLE);}
  });
  // Activity has a bounded screen height: weight=1 reliably gives the browser visible space.
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(16),dp(12),dp(16),dp(12));TextView retry=text("Повторить",14,UiTheme.accent(this));retry.setId(R.id.auth_retry);retry.setGravity(Gravity.CENTER);retry.setMinHeight(dp(48));retry.setPadding(dp(12),0,dp(12),0);retry.setOnClickListener(v->{status.setText("Загружаем официальный сайт…");legacyCatalog=false;completing=false;attempted=false;web.loadUrl(initialUrl());});footer.addView(retry,new LinearLayout.LayoutParams(0,-2,1));done=text("Проверить вход",14,UiTheme.accent(this));done.setId(R.id.auth_done);done.setGravity(Gravity.CENTER);done.setMinHeight(dp(48));AppDesign.emphasize(done);done.setOnClickListener(v->verifyPage());footer.addView(done,new LinearLayout.LayoutParams(0,-2,1));root.addView(footer);UiMotion.press(retry);UiMotion.press(done);FrameLayout backdrop=AppDesign.background(this);root.setBackgroundColor(Color.TRANSPARENT);backdrop.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(backdrop);root.requestApplyInsets();
  String name=state==null?getIntent().getStringExtra("target_name"):state.getString("target_name");String url=state==null?getIntent().getStringExtra("target_url"):state.getString("target_url");if(name!=null&&Schedule.universityPage(url))targetGroup=new Schedule.Group(name,url);if(state==null||web.restoreState(state)==null){
   io.execute(()->{try{if(fallback)WebLogin.resetCookies();else new Repository(this).prepareManual();}catch(Exception unavailable){}runOnUiThread(()->{if(web!=null)web.loadUrl(initialUrl());});});
  }else web.reload();
 }
 private String initialUrl(){return targetGroup==null?Schedule.DIRECTORY:targetGroup.url;}
 private boolean officialScheduleAddress(String url){
  if(Schedule.universityPage(url))return true;
  if(url==null)return false;
  Uri u=Uri.parse(url);
  return "https".equals(u.getScheme())&&"lk.ulstu.ru".equals(u.getHost())&&u.getUserInfo()==null
   &&("/timetable/".equals(u.getPath())||"/timetable".equals(u.getPath()));
 }
 private void verifyPage(){
  if(completing||web==null||pageFailed)return;
  String url=web.getUrl();if(!officialScheduleAddress(url)){status.setText("Заверши вход на официальном сайте.");return;}
  if(targetGroup!=null&&java.util.Objects.equals(Uri.parse(url).getPath(),Uri.parse(targetGroup.url).getPath())){captureSchedule();return;}
  web.evaluateJavascript("document.documentElement.outerHTML",value->{
   try{
    Object html=new JSONTokener(value).nextValue();
    if(!(html instanceof String)||SourceSession.loginPage((String)html)){status.setText("Заверши вход на официальном сайте.");return;}
    String page=(String)html;if(targetGroup!=null){web.loadUrl(targetGroup.url);return;}completing=true;CookieManager.getInstance().flush();
    io.execute(()->{try{
     Repository repository=new Repository(this);if(fallback)repository.confirmDirectory(false);else repository.completeLogin(false);repository.acceptCatalog(page,url);runOnUiThread(()->complete(false));
    }catch(Exception e){runOnUiThread(()->{if(isFinishing()||web==null)return;completing=false;if(!legacyCatalog){legacyCatalog=true;web.loadUrl(Schedule.INDEX);}else status.setText("Каталог групп пока недоступен. Можно повторить загрузку.");});}});
   }catch(Exception e){status.setText("Не удалось проверить страницу. Повтори загрузку.");}
  });
 }
 private void captureSchedule(){Uri u=Uri.parse(web.getUrl()==null?"":web.getUrl());if(!"https".equals(u.getScheme())||!"lk.ulstu.ru".equals(u.getHost())||!java.util.Objects.equals(u.getPath(),Uri.parse(targetGroup.url).getPath())){status.setText("Ожидаем страницу расписания. При необходимости заверши вход.");return;}web.evaluateJavascript("(function(){return document.querySelector('table') ? document.documentElement.outerHTML : null;})()",value->{try{Object text=new JSONTokener(value).nextValue();if(!(text instanceof String)||SourceSession.loginPage((String)text)){status.setText("Заверши вход на официальном сайте.");return;}completing=true;String page=(String)text;Schedule.Group selected=targetGroup;io.execute(()->{try{Schedule.Data data=Schedule.parse(page,selected.name,selected.url);Repository repository=new Repository(this);repository.saveData(data);repository.completeLogin(false);runOnUiThread(()->complete(true));}catch(Exception e){runOnUiThread(()->{completing=false;status.setText("Не удалось прочитать расписание.");});}});}catch(Exception e){status.setText("Не удалось прочитать страницу расписания. Повтори загрузку.");}});}
 private void complete(boolean scheduleReady){if(isFinishing())return;CookieManager.getInstance().flush();setResult(RESULT_OK,new Intent().putExtra("schedule_ready",scheduleReady));finish();}
 @Override protected void onSaveInstanceState(Bundle out){if(web!=null)web.saveState(out);if(targetGroup!=null){out.putString("target_name",targetGroup.name);out.putString("target_url",targetGroup.url);}super.onSaveInstanceState(out);}
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){io.shutdownNow();if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
