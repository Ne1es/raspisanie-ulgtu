package ru.ne1es.timetable;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Walking navigation, bundled plans and foreground-only, measured location. */
public final class MapActivity extends Activity implements LocationListener {
 private static final String ORIGIN="https://appassets.androidplatform.net";
 private boolean handlingBack;
 private WebView web;private LocationManager locations;private Location fix;
 private FrameLayout mapContent;
 private LinearLayout mapStatus;
 private TextView statusLabel,retry;
 private ProgressBar progress;
 private final Runnable loadingDeadline=()->showMapError("Карта не ответила. Попробуй открыть её снова.");
 private SensorManager sensors;
 private Sensor barometer;
 private float pressure=Float.NaN,reference=Float.NaN;
 private boolean calibrating;
 private int calibrationSamples;
 private final SensorEventListener pressureListener=new SensorEventListener(){
  @Override public void onSensorChanged(SensorEvent event){
   if(!resumed||!Float.isFinite(event.values[0])||event.values[0]<=0)return;
   pressure=Float.isNaN(pressure)?event.values[0]:pressure*.85f+event.values[0]*.15f;
   if(calibrating&&++calibrationSamples>=5){reference=pressure;calibrating=false;}
   if(!Float.isNaN(reference)&&web!=null){
    float meters=SensorManager.getAltitude(reference,pressure);
    web.evaluateJavascript("window.onHeight&&window.onHeight("+meters+")",null);
   }
  }
  @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
 };
 private final LocationQuality quality=new LocationQuality();
 private final List<CancellationSignal> freshRequests=new ArrayList<>();
 private boolean ready,resumed,tracking,asked,staleSent,gnssRegistered;
 private int satellitesSeen,satellitesUsed;private long lastFreshRequest;
 private String statusState="waiting",statusText="Получаем местоположение…";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final GnssStatus.Callback gnss=new GnssStatus.Callback(){
  @Override public void onSatelliteStatusChanged(GnssStatus status){satellitesSeen=status.getSatelliteCount();satellitesUsed=0;for(int i=0;i<satellitesSeen;i++)if(status.usedInFix(i))satellitesUsed++;}
 };
 private final Runnable freshness=new Runnable(){public void run(){
  if(!resumed)return;
  if(fix!=null&&!freshFix()&&!staleSent){staleSent=true;if(!Arrays.asList("disabled","denied","permission").contains(statusState))message("stale","Точка устарела · уточняем положение");}
  if(ready&&(!freshFix()||fix.getAccuracy()>25)&&SystemClock.elapsedRealtime()-lastFreshRequest>15000)requestFreshFix();
  handler.postDelayed(this,1000);
 }};
 @Override public void onCreate(Bundle state){
  UiTheme.apply(this);super.onCreate(state);UiTheme.bars(this);locations=getSystemService(LocationManager.class);sensors=getSystemService(SensorManager.class);if(sensors!=null)barometer=sensors.getDefaultSensor(Sensor.TYPE_PRESSURE);
  mapContent=new FrameLayout(this);
  FrameLayout frame=AppDesign.background(this);
  LinearLayout scaffold=AppDesign.scaffold(this,mapContent,1,tab->AppDesign.switchFromChild(this,tab));
  frame.addView(scaffold,new FrameLayout.LayoutParams(-1,-1));AppDesign.insets(scaffold);
  setContentView(frame);scaffold.requestApplyInsets();
  mapStatus=new LinearLayout(this);mapStatus.setOrientation(LinearLayout.VERTICAL);
  mapStatus.setGravity(Gravity.CENTER);
  int padding=AppDesign.dp(this,24);
  mapStatus.setPadding(padding,padding,padding,padding);
  mapStatus.setBackgroundColor(UiTheme.background(this));
  progress=new ProgressBar(this);
  mapStatus.addView(progress,new LinearLayout.LayoutParams(AppDesign.dp(this,38),AppDesign.dp(this,38)));
  statusLabel=AppDesign.text(this,"Открываем карту…",17,true);
  statusLabel.setGravity(Gravity.CENTER);statusLabel.setPadding(0,padding,0,padding);
  mapStatus.addView(statusLabel,new LinearLayout.LayoutParams(-1,-2));
  retry=Modal.button(this,"Повторить",this::openMap);
  mapStatus.addView(retry,new LinearLayout.LayoutParams(-1,-2));
  mapContent.addView(mapStatus,new FrameLayout.LayoutParams(-1,-1));
  retry.setVisibility(View.GONE);
  // Draw the native shell before initializing the WebView and its renderer.
  mapContent.post(()->{if(!isFinishing()&&!isDestroyed())openMap();});
 }
 private void openMap(){
  closeWeb();
  statusLabel.setText("Открываем карту…");retry.setVisibility(View.GONE);
  progress.setVisibility(View.VISIBLE);mapStatus.setVisibility(View.VISIBLE);
  try{
   WebView browser=new WebView(this);
   web=browser;browser.setId(R.id.map_web);browser.setBackgroundColor(UiTheme.background(this));
   mapContent.addView(browser,0,new FrameLayout.LayoutParams(-1,-1));
   configure(browser);
   handler.postDelayed(loadingDeadline,15000);
  }catch(RuntimeException unavailable){
   closeWeb();showMapError("Android System WebView не запускается.");
   android.util.Log.w("UlstuMap","WebView initialization failed: "+unavailable.getClass().getSimpleName());
  }
 }
 private void configure(WebView browser){
  WebSettings s=browser.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setDomStorageEnabled(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(s.getUserAgentString()+" UlstuTimetable/0.10.4 (ru.ne1es.timetable)");
  browser.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onConsoleMessage(ConsoleMessage message){
    if(message.messageLevel()==ConsoleMessage.MessageLevel.ERROR)
     android.util.Log.w("UlstuMap","Map console error at line "+message.lineNumber());
    return true;
   }
  });
  browser.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
    Uri uri=request.getUrl();if(!"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost()))return null;
    String p=uri.getPath();try{
     if(p==null||!p.startsWith("/maps/")||p.contains("..")||p.indexOf('\\')>=0)throw new FileNotFoundException();
     String mime=p.endsWith(".html")?"text/html":p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":p.endsWith(".svg")?"image/svg+xml":p.endsWith(".png")?"image/png":p.endsWith(".json")?"application/json":p.endsWith(".ttf")?"font/ttf":"text/plain";
     return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-cache"),getAssets().open(p.substring(1)));
    }catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
    Uri u=r.getUrl();if("ulstuapp".equals(u.getScheme())){if(v==web&&r.isForMainFrame()&&v.getUrl()!=null&&v.getUrl().startsWith(ORIGIN+"/maps/"))command(u);return true;}
    if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost()))return false;
    if("https".equals(u.getScheme())&&Arrays.asList("www.openstreetmap.org","www.fossgis.de","routing.openstreetmap.de","ulstu.ru","lk.ulstu.ru","ctc.ulstu.ru").contains(u.getHost()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
    return true;
   }
   @Override public void onPageFinished(WebView view,String url){
    if(view!=web||ready||!url.startsWith(ORIGIN+"/maps/index.html"))return;
    view.evaluateJavascript("window.mapInterfaceReady === true",result->{
     if(view==web&&"true".equals(result))pageReady();
    });
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
    if(view==web&&request.isForMainFrame())showMapError("Не удалось открыть карту. Попробуй ещё раз.");
   }
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
    if(view==web){
     android.util.Log.w("UlstuMap",detail.didCrash()?"Map renderer crashed":"Map renderer was stopped");
     destroyWeb(view);showMapError("Android остановил отображение карты.");
    }
    return true;
   }
  });
  browser.getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale*100));
  String plan=getIntent().getStringExtra("plan");String room=getIntent().getStringExtra("room");browser.loadUrl(ORIGIN+"/maps/index.html?app=android&v=0101&motion="+(UiMotion.enabled()?"1":"0")+"&theme="+(UiTheme.dark(this)?"dark":"light")+"&accent="+String.format(java.util.Locale.ROOT,"%06x",UiTheme.accent(this)&0xffffff)+(plan==null?"":"&plan="+Uri.encode(plan))+(getIntent().getBooleanExtra("indoor_route",false)?"&indoor_route=1":"")+(room==null?"":"#"+Uri.encode(room)));
 }
 @Override public void onBackPressed(){
  if(web==null||!ready){finish();return;}
  if(handlingBack||isFinishing())return;
  handlingBack=true;
  web.evaluateJavascript("window.navigateBack&&window.navigateBack()",handled->{
   handlingBack=false;
   if(!isFinishing()&&!"true".equals(handled))finish();
  });
 }
 @Override public void finish(){super.finish();UiMotion.back(this);}
 private void showMapError(String message){
  handler.removeCallbacks(loadingDeadline);
  statusLabel.setText(message);progress.setVisibility(View.GONE);retry.setVisibility(View.VISIBLE);
  mapStatus.setVisibility(View.VISIBLE);
 }
 private void destroyWeb(WebView view){
  if(view==web)web=null;
  ready=false;
  if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
  view.destroy();
 }
 private void closeWeb(){
  handler.removeCallbacks(loadingDeadline);
  if(web!=null){web.stopLoading();destroyWeb(web);}
 }
 private void pageReady(){
  if(ready||web==null)return;
  ready=true;handler.removeCallbacks(loadingDeadline);mapStatus.setVisibility(View.GONE);
  android.util.Log.i("UlstuMap","Map interface ready");message(statusState,statusText);if(freshFix())publishFix();if(resumed)startGps();}
 private void command(Uri u){
  String action=u.getHost();if("ready".equals(action))pageReady();else if("back".equals(action))finish();
  else if("cityroom".equals(action)){
   String room=u.getQueryParameter("room");
   if(room!=null&&room.length()<=80&&room.matches("[1-6][-–—].+")&&UiMotion.beginNavigation(this)){
    Intent city=new Intent(this,CityActivity.class).putExtra("room",room)
     .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
    startActivity(city);UiMotion.quiet(this);
   }
  }
  else if("sources".equals(action))startActivity(new Intent(this,SettingsActivity.class).putExtra("about",true));
  else if("timing".equals(action)){String value=u.getQueryParameter("events");if(value!=null&&value.length()<1800)ReminderReceiver.prefs(this).edit().putString("map_network_timeline",value).apply();}
  else if("tick".equals(action))web.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
  else if("location".equals(action)){if(!permitted()){asked=false;requestLocationPermission();}else{startGps();requestFreshFix();}}
  else if("barometer".equals(action))calibratePressure();
  else if("precise".equals(action))requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},901);
  else if("locationSettings".equals(action))startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
  else if("appSettings".equals(action))startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
 }
 private void calibratePressure(){
  if(!resumed||web==null)return;
  if(barometer==null){web.evaluateJavascript("window.onHeight&&window.onHeight(null)",null);return;}
  sensors.unregisterListener(pressureListener);
  calibrating=true;calibrationSamples=0;pressure=Float.NaN;reference=Float.NaN;
  if(!sensors.registerListener(pressureListener,barometer,SensorManager.SENSOR_DELAY_UI))
   web.evaluateJavascript("window.onHeight&&window.onHeight(null)",null);
 }
 private void stopPressure(){
  if(sensors!=null)sensors.unregisterListener(pressureListener);
  calibrating=false;reference=Float.NaN;pressure=Float.NaN;
  if(web!=null)web.evaluateJavascript("window.resetHeight&&window.resetHeight()",null);
 }
 private boolean fine(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 private boolean permitted(){return fine()||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 private void requestLocationPermission(){if(asked){message("denied","Разреши геолокацию для автоматической карты");return;}asked=true;message("permission","Разреши точное местоположение");requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},901);}
 private LocationQuality.Fix candidate(Location f){return f==null?null:new LocationQuality.Fix(f.getLatitude(),f.getLongitude(),f.hasAccuracy()?f.getAccuracy():Float.NaN,f.getElapsedRealtimeNanos()/1000000,f.getProvider());}
 private boolean freshFix(){return LocationQuality.valid(candidate(fix),SystemClock.elapsedRealtime());}
 private List<String> providers(){List<String> result=new ArrayList<>();for(String p:new String[]{"gps","fused","network"})try{if(locations.getAllProviders().contains(p)&&locations.isProviderEnabled(p))result.add(p);}catch(Exception ignored){}return result;}
 private void startGps(){
  if(!ready||!resumed||tracking)return;if(!permitted())return;
  stopTracking();List<String> providers=providers();if(providers.isEmpty()){message("disabled","Геолокация телефона выключена");return;}
  message("waiting","Уточняем местоположение…");int registered=0;
  for(String provider:providers)try{
   if(Build.VERSION.SDK_INT>=31){LocationRequest request=new LocationRequest.Builder(500).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setMinUpdateIntervalMillis(500).setMinUpdateDistanceMeters(0).setMaxUpdateDelayMillis(0).build();locations.requestLocationUpdates(provider,request,getMainExecutor(),this);}
   else locations.requestLocationUpdates(provider,500,0,this,Looper.getMainLooper());
   registered++;Location last=locations.getLastKnownLocation(provider);if(last!=null)onLocationChanged(last);
  }catch(SecurityException|IllegalArgumentException ignored){}
  tracking=registered>0;if(!tracking){message("permission","Разреши точное местоположение в настройках Android");return;}
  if(fine())try{gnssRegistered=locations.registerGnssStatusCallback(gnss,handler);}catch(SecurityException ignored){}
  requestFreshFix();
 }
 private void requestFreshFix(){
  if(!resumed||!ready||!permitted())return;long now=SystemClock.elapsedRealtime();if(now-lastFreshRequest<1500)return;lastFreshRequest=now;
  for(CancellationSignal signal:freshRequests)signal.cancel();freshRequests.clear();
  if(Build.VERSION.SDK_INT<30)return;
  for(String provider:providers())try{
   CancellationSignal signal=new CancellationSignal();freshRequests.add(signal);
   if(Build.VERSION.SDK_INT>=31){LocationRequest request=new LocationRequest.Builder(0).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setDurationMillis(12000).build();locations.getCurrentLocation(provider,request,signal,getMainExecutor(),this::onLocationChanged);}
   else locations.getCurrentLocation(provider,signal,getMainExecutor(),this::onLocationChanged);
  }catch(SecurityException|IllegalArgumentException ignored){}
 }
 private void stopTracking(){tracking=false;if(locations!=null){try{locations.removeUpdates(this);}catch(SecurityException ignored){}if(gnssRegistered)locations.unregisterGnssStatusCallback(gnss);}gnssRegistered=false;for(CancellationSignal signal:freshRequests)signal.cancel();freshRequests.clear();}
 private void send(JSONObject data){if(ready&&web!=null)web.evaluateJavascript("window.onGPS&&window.onGPS("+data.toString()+")",null);}
 private void message(String state,String text){statusState=state;statusText=text;try{send(new JSONObject().put("state",state).put("message",text));}catch(Exception ignored){}}
 private void publishFix(){
  if(!freshFix())return;try{
   JSONObject data=new JSONObject().put("state","fix").put("latitude",fix.getLatitude()).put("longitude",fix.getLongitude()).put("accuracy",fix.getAccuracy()).put("provider",fix.getProvider()).put("stamp",fix.getElapsedRealtimeNanos()/1000000).put("ageMillis",(SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos())/1000000).put("precise",fine()).put("satellitesUsed",satellitesUsed).put("satellitesSeen",satellitesSeen);
   if(fix.hasBearing())data.put("bearing",fix.getBearing());send(data);
  }catch(Exception ignored){}
 }
 @Override public void onLocationChanged(Location value){
  if(!resumed||value==null)return;long now=SystemClock.elapsedRealtime();
  if(!quality.accept(candidate(value),candidate(fix),now))return;
  fix=new Location(value);staleSent=false;publishFix();
 }
 @Override public void onProviderDisabled(String provider){
  if(fix!=null&&provider.equals(fix.getProvider())){
   fix=null;staleSent=true;message("stale","Источник положения отключён. Ждём новую точку.");
  }
  tracking=false;if(resumed)startGps();
 }
 @Override public void onProviderEnabled(String p){tracking=false;if(resumed)startGps();}
 @Override public void onStatusChanged(String p,int s,Bundle e){}
 @Override public void onRequestPermissionsResult(int r,String[] names,int[] grants){super.onRequestPermissionsResult(r,names,grants);if(r==901){if(permitted()){tracking=false;startGps();}else message("denied","Разреши геолокацию в настройках Android");}}
 @Override protected void onResume(){super.onResume();UiMotion.resumed(this);resumed=true;handler.removeCallbacks(freshness);handler.post(freshness);if(web!=null)web.onResume();if(ready)startGps();}
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(freshness);stopTracking();stopPressure();if(web!=null)web.onPause();super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopTracking();closeWeb();fix=null;super.onDestroy();}
}
