package ru.ne1es.timetable;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.app.Dialog;
import android.widget.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class CityActivity extends Activity implements LocationListener {
 private static final int LOCATION=52;
 private final ExecutorService work=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LocationQuality quality=new LocationQuality();
 private final Runnable locationTimeout=()->{
  waiting=false;
  trip("Не удалось получить точную точку старта. Попробуй ещё раз на открытом месте.");
 };
 private boolean tilesReceived,headingIcon;
 private int tileFailures;
 private final Runnable tileTimeout=()->{if(!tilesReceived)tileUnavailable();};
 private MapView map;
 private FrameLayout mapArea;
 private LinearLayout card;
 private ScrollView destinationPanel;
 private Dialog placePicker;
 private TextView searchButton;
 private TextView cityStatus,title,address,trip,routeButton,planButton;
 private CityData data;
 private CityData.Place destination;
 private Marker position;
 private Polyline line;
 private Future<?> request;
 private LocationManager locations;
 private Location fix;
 private boolean following,tracking,waiting,resumed;
 private int serial;

 @Override public void onCreate(Bundle state){
  UiTheme.apply(this);super.onCreate(state);UiTheme.bars(this);
  locations=getSystemService(LocationManager.class);
  LinearLayout body=column();
  body.setPadding(dp(14),dp(8),dp(14),0);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout copy=column();
  copy.addView(AppDesign.text(this,"Карта",27,true));
  TextView caption=AppDesign.text(this,"Корпуса УлГТУ",12,true);
  caption.setTextColor(UiTheme.accent(this));copy.addView(caption);
  heading.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  heading.addView(AppDesign.iconAction(this,R.drawable.ms_my_location,"Моё местоположение",this::locate),new LinearLayout.LayoutParams(dp(48),dp(48)));
  body.addView(heading);
  searchButton=button("Корпус или аудитория",this::openPicker);
  searchButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
  searchButton.setTextColor(UiTheme.muted(this));searchButton.setPaddingRelative(dp(13),0,dp(13),0);
  android.graphics.drawable.Drawable searchIcon=getDrawable(R.drawable.ms_search).mutate();
  searchIcon.setTint(UiTheme.accent(this));searchIcon.setBounds(0,0,dp(21),dp(21));
  searchButton.setCompoundDrawablesRelative(searchIcon,null,null,null);searchButton.setCompoundDrawablePadding(dp(9));
  searchButton.setEnabled(false);
  AppDesign.space(body,8);body.addView(searchButton);
  mapArea=new FrameLayout(this);mapArea.setBackground(AppDesign.panel(this,UiTheme.surface(this),22));mapArea.setClipToOutline(true);
  LinearLayout.LayoutParams area=new LinearLayout.LayoutParams(-1,0,1);area.topMargin=dp(8);body.addView(mapArea,area);
  cityStatus=AppDesign.text(this,"Открываем карту…",14,true);
  cityStatus.setGravity(Gravity.CENTER);cityStatus.setMaxLines(3);mapArea.addView(cityStatus,new FrameLayout.LayoutParams(-1,-1));
  TextView credits=AppDesign.text(this,"© OpenStreetMap",11,false);
  credits.setTextColor(UiTheme.muted(this));credits.setGravity(Gravity.END);credits.setPadding(0,dp(4),dp(5),dp(4));
  credits.setOnClickListener(v->Modal.show(this,"Источники карты",(panel,dialog)->{
   panel.addView(button("OpenStreetMap · Лицензия",()->{dialog.dismiss();open("https://www.openstreetmap.org/copyright");}));
   AppDesign.space(panel,6);
   panel.addView(button("FOSSGIS · Пешие маршруты",()->{dialog.dismiss();open("https://routing.openstreetmap.de/about.html");}));
  }));
  body.addView(credits);
  buildCard(body);
  FrameLayout root=AppDesign.background(this);
  LinearLayout scaffold=AppDesign.scaffold(this,body,1,tab->AppDesign.switchFromChild(this,tab));
  root.addView(scaffold,new FrameLayout.LayoutParams(-1,-1));AppDesign.insets(scaffold);
  body.setFocusableInTouchMode(true);body.requestFocus();
  setContentView(root);scaffold.requestApplyInsets();
  work.execute(()->{
   try{
    CityData loaded=new CityData(this);
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     data=loaded;searchButton.setEnabled(true);
     try{startMap();}catch(RuntimeException error){
      android.util.Log.e("UlstuCity","Map initialization failed",error);
      cityStatus.setText("Не удалось открыть карту корпусов");
     }
    });
   }catch(IOException|JSONException error){
    runOnUiThread(()->cityStatus.setText("Не удалось открыть данные карты"));
   }
  });
 }

 private void startMap(){
  File cache=new File(getCacheDir(),"city-tiles");
  Configuration.getInstance().setOsmdroidBasePath(cache);
  Configuration.getInstance().setOsmdroidTileCache(cache);
  Configuration.getInstance().setUserAgentValue("UlstuTimetable/0.10.4 (ru.ne1es.timetable)");
  map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);
  map.setMultiTouchControls(true);map.setMinZoomLevel(4d);map.setMaxZoomLevel(19d);
  map.setHorizontalMapRepetitionEnabled(false);map.setVerticalMapRepetitionEnabled(false);
  map.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
  if(UiTheme.dark(this))map.getOverlayManager().getTilesOverlay().setColorFilter(
   new ColorMatrixColorFilter(new ColorMatrix(new float[]{-.79f,0,0,0,218,0,-.76f,0,0,218,0,0,-.69f,0,218,0,0,0,1,0})));
  map.getController().setZoom(13d);
  map.getController().setCenter(new GeoPoint(54.337,48.389));
  map.setContentDescription("Городская карта Ульяновска");
  mapArea.removeView(cityStatus);mapArea.addView(map,new FrameLayout.LayoutParams(-1,-1));
  cityStatus.setText("Загружаем карту…");
  cityStatus.setBackground(AppDesign.panel(this,UiTheme.surface(this),14));
  cityStatus.setPadding(dp(11),dp(7),dp(11),dp(7));
  FrameLayout.LayoutParams statusBounds=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  statusBounds.setMargins(dp(10),dp(9),dp(10),0);mapArea.addView(cityStatus,statusBounds);
  map.getTileProvider().getTileRequestCompleteHandlers().add(new Handler(Looper.getMainLooper(),message->{
   if(message.what==org.osmdroid.tileprovider.MapTileProviderBase.MAPTILE_SUCCESS_ID){
    if(!tilesReceived)android.util.Log.i("UlstuCity","Received city map tiles");
    tilesReceived=true;tileFailures=0;cityStatus.setVisibility(View.GONE);cityStatus.setOnClickListener(null);main.removeCallbacks(tileTimeout);
   }else if(++tileFailures>=2&&!tilesReceived)tileUnavailable();
   return true;
  }));
  main.postDelayed(tileTimeout,9000);
  android.util.Log.i("UlstuCity","Native city map ready");
  map.setOnTouchListener((view,event)->{
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN)following=false;
   return false;
  });
  for(CityData.Building item:data.buildings.values()){
   Marker marker=new Marker(map);marker.setPosition(item.point);marker.setTitle(item.name);
   marker.setIcon(pin(item.building+(item.approximate?"≈":""),UiTheme.accent(this),true));
   marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
   marker.setOnMarkerClickListener((m,v)->{select(item);return true;});
   map.getOverlays().add(marker);
  }
  if(resumed)map.onResume();
  applyDestination(getIntent());
  updatePosition();startLocation();
 }
 @Override protected void onNewIntent(Intent intent){
  super.onNewIntent(intent);setIntent(intent);
  if(map!=null)applyDestination(intent);
 }
 private void applyDestination(Intent intent){
  String room=intent.getStringExtra("room");
  if(room==null)return;
  String code=CityData.key(room);
  CityData.Place match=data.rooms.stream().filter(item->CityData.key(item.name).equals(code)).findFirst().orElse(null);
  if(match!=null)select(match);
  else if(!code.isEmpty()&&data.buildings.containsKey(code.substring(0,1)))
   select(new CityData.Place(room,code.substring(0,1),"Положение аудитории на плане не подтверждено"));
 }

 private void tileUnavailable(){
  if(map==null||isDestroyed())return;
  cityStatus.setText("Нет подложки карты · Повторить");
  cityStatus.setVisibility(View.VISIBLE);
  cityStatus.setOnClickListener(view->{
   tileFailures=0;cityStatus.setText("Загружаем карту…");cityStatus.setOnClickListener(null);
   map.getTileProvider().clearTileCache();map.invalidate();main.removeCallbacks(tileTimeout);main.postDelayed(tileTimeout,9000);
  });
 }
 private android.graphics.drawable.Drawable pin(String text,int color,boolean outline){
  int size=dp(43);
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
  android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
  android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  float center=size/2f;paint.setColor(color);canvas.drawCircle(center,center,center-dp(3),paint);
  paint.setColor(Color.WHITE);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
  if(outline)canvas.drawCircle(center,center,center-dp(3),paint);
  paint.setStyle(android.graphics.Paint.Style.FILL);paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTypeface(UiTheme.font(this,true));paint.setTextSize(dp(15));
  canvas.drawText(text,center,center-(paint.ascent()+paint.descent())/2,paint);
  return new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);
 }
 private void buildCard(LinearLayout body){
  card=column();card.setBackground(AppDesign.panel(this,UiTheme.surface(this),24));card.setPadding(dp(13),dp(10),dp(13),dp(12));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout copy=column();
  title=AppDesign.text(this,"",20,true);copy.addView(title);
  address=AppDesign.text(this,"",13,false);address.setTextColor(UiTheme.muted(this));copy.addView(address);
  header.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  header.addView(AppDesign.iconAction(this,R.drawable.ms_close,"Закрыть карточку",this::clearSelection),new LinearLayout.LayoutParams(dp(48),dp(48)));
  card.addView(header);
  trip=AppDesign.text(this,"",14,true);trip.setPadding(dp(10),dp(8),dp(10),dp(8));
  trip.setTextColor(UiTheme.selectedText(this));trip.setBackground(AppDesign.panel(this,UiTheme.selected(this),12));
  card.addView(trip);trip.setVisibility(View.GONE);
  AppDesign.space(card,8);
  LinearLayout row=new LinearLayout(this);
  boolean large=getResources().getConfiguration().fontScale>=1.45f||getResources().getConfiguration().screenWidthDp<340;
  row.setOrientation(large?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
  planButton=button("Открыть план",this::openPlan);routeButton=button("Маршрут пешком",this::route);AppDesign.emphasize(routeButton);
  row.addView(planButton,new LinearLayout.LayoutParams(large?-1:0,-2,large?0:1));
  LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(large?-1:0,-2,large?0:1);
  if(large)action.topMargin=dp(6);else action.leftMargin=dp(6);
  row.addView(routeButton,action);card.addView(row);
  destinationPanel=new ScrollView(this){
   @Override protected void onMeasure(int width,int height){
    int limit=dp(Math.max(95,Math.min(220,getResources().getConfiguration().screenHeightDp/3)));
    if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)limit=Math.min(limit,View.MeasureSpec.getSize(height));
    super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
   }
  };
  destinationPanel.setVerticalScrollBarEnabled(true);destinationPanel.addView(card);body.addView(destinationPanel,new LinearLayout.LayoutParams(-1,-2));
  destinationPanel.setVisibility(View.GONE);
 }

 private void select(CityData.Place item){
  if(map==null)return;
  if(placePicker!=null)placePicker.dismiss();
  cancelRoute();destination=item;
  CityData.Building building=data.buildings.get(item.building);
  title.setText(item.name);
  address.setText(item==building?building.detail+(building.approximate?" · Примерное положение":""):item.detail);
  planButton.setText(item==building?"Открыть план":"Внутри корпуса");
  boolean entering=destinationPanel.getVisibility()!=View.VISIBLE;
  destinationPanel.setVisibility(View.VISIBLE);destinationPanel.scrollTo(0,0);trip.setVisibility(View.GONE);
  if(entering)UiMotion.sheet(card);
  if(UiMotion.enabled())map.getController().animateTo(building.point,16.8,240L);
  else{map.getController().setZoom(16.8);map.getController().setCenter(building.point);}
 }

 private void clearSelection(){destination=null;cancelRoute();destinationPanel.setVisibility(View.GONE);}
 private void openPicker(){
  if(data==null||placePicker!=null)return;
  placePicker=Modal.showPage(this,"Поиск аудитории","",(body,dialog)->{
   EditText field=new EditText(this);
   field.setSingleLine(true);field.setTextSize(16);field.setHint("Корпус, название или номер");
   field.setTextColor(UiTheme.ink(this));field.setHintTextColor(UiTheme.muted(this));field.setPadding(dp(14),dp(8),dp(12),dp(8));
   field.setMinHeight(dp(50));field.setBackground(AppDesign.panel(this,UiTheme.card(this),20));body.addView(field);
   AppDesign.space(body,10);
   TextView caption=AppDesign.text(this,"Корпуса",13,true);caption.setTextColor(UiTheme.muted(this));body.addView(caption);
   AppDesign.space(body,6);
   ScrollView scroll=new ScrollView(this);LinearLayout results=column();scroll.addView(results);
   body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   Runnable update=()->{
    results.removeAllViews();scroll.scrollTo(0,0);
    String query=field.getText().toString().trim();
    caption.setText(query.isEmpty()?"Корпуса":"Результаты поиска");
    List<CityData.Place> matches=data.find(query);
    if(matches.isEmpty())results.addView(AppDesign.text(this,"Ничего не найдено",15,false));
    for(CityData.Place place:matches)results.addView(placeRow(place));
   };
   update.run();
   field.addTextChangedListener(new TextWatcher(){
    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
    public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}
    public void afterTextChanged(Editable s){}
   });
  });
  placePicker.setOnDismissListener(dialog->placePicker=null);
 }

 private LinearLayout placeRow(CityData.Place place){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(10),dp(9));
  row.setBackground(UiMotion.ripple(UiTheme.card(this),dp(17),row));
  row.addView(AppDesign.icon(this,place instanceof CityData.Building?R.drawable.ms_school:R.drawable.ms_location_on,UiTheme.accent(this)),new LinearLayout.LayoutParams(dp(32),dp(30)));
  LinearLayout copy=column();copy.setPadding(dp(9),0,dp(6),0);copy.addView(AppDesign.text(this,place.name,15,true));
  TextView details=AppDesign.text(this,place.detail,12,false);details.setTextColor(UiTheme.muted(this));copy.addView(details);
  row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  row.addView(AppDesign.icon(this,R.drawable.ms_chevron_right,UiTheme.muted(this)),new LinearLayout.LayoutParams(dp(20),dp(20)));
  LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.bottomMargin=dp(6);row.setLayoutParams(bounds);
  row.setFocusable(true);row.setOnClickListener(v->select(place));UiMotion.press(row);
  return row;
 }
 private void openPlan(){
  if(destination==null||!UiMotion.beginNavigation(this))return;
  Intent intent=new Intent(this,MapActivity.class).putExtra("plan",destination.building);
  if(data.rooms.contains(destination))intent.putExtra("room",destination.name).putExtra("indoor_route",true);
  startActivity(intent);UiMotion.forward(this);
 }
 private void open(String url){
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
  catch(android.content.ActivityNotFoundException unavailable){toast("Установи браузер, чтобы открыть источник");}
 }

 private boolean permission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 private void locate(){
  following=true;
  if(!permission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION);return;}
  if(locations==null||(Build.VERSION.SDK_INT>=28&&!locations.isLocationEnabled())){
   if(waiting)trip("Включи геолокацию в настройках телефона");
   else toast("Включи геолокацию в настройках телефона");
   waiting=false;main.removeCallbacks(locationTimeout);return;
  }
  startLocation();updatePosition();
  if(!fresh())toast("Уточняем местоположение…");
 }
 private void startLocation(){
  if(locations==null||!resumed||!permission()||tracking)return;
  for(String provider:new String[]{LocationManager.GPS_PROVIDER,"fused",LocationManager.NETWORK_PROVIDER}){
   try{
    if(!locations.isProviderEnabled(provider))continue;
    locations.requestLocationUpdates(provider,900,2,this,Looper.getMainLooper());tracking=true;
    Location last=locations.getLastKnownLocation(provider);if(last!=null)onLocationChanged(last);
   }catch(SecurityException|IllegalArgumentException unavailable){}
  }
 }
 private LocationQuality.Fix sample(Location value){
  if(value==null)return null;
  return new LocationQuality.Fix(value.getLatitude(),value.getLongitude(),value.hasAccuracy()?value.getAccuracy():Float.NaN,value.getElapsedRealtimeNanos()/1_000_000,value.getProvider());
 }
 private boolean fresh(){return LocationQuality.valid(sample(fix),SystemClock.elapsedRealtime());}
 @Override public void onLocationChanged(Location value){
  if(!quality.accept(sample(value),sample(fix),SystemClock.elapsedRealtime()))return;
  fix=value;updatePosition();
  if(waiting&&fix.getAccuracy()<=100){waiting=false;main.removeCallbacks(locationTimeout);route();}
 }
 private void updatePosition(){
  if(!fresh()||map==null)return;
  GeoPoint point=new GeoPoint(fix.getLatitude(),fix.getLongitude());
  boolean hasHeading=fix.hasBearing();
  if(position==null){
   position=new Marker(map);position.setIcon(pin(hasHeading?"▲":"●",0xff2b78df,false));headingIcon=hasHeading;position.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
   position.setOnMarkerClickListener((marker,view)->true);map.getOverlays().add(position);
  }
  if(hasHeading!=headingIcon){position.setIcon(pin(hasHeading?"▲":"●",0xff2b78df,false));headingIcon=hasHeading;}
  position.setRotation(fix.hasBearing()?-fix.getBearing():0);position.setPosition(point);
  if(following){map.getController().setZoom(17d);map.getController().setCenter(point);following=false;}
  map.invalidate();
 }
 @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){
  super.onRequestPermissionsResult(code,names,grants);
  if(code!=LOCATION)return;
  if(permission())locate();
  else{waiting=false;main.removeCallbacks(locationTimeout);toast("Геолокация недоступна");if(destination!=null)trip("Разреши геолокацию для маршрута");}
 }
 private void route(){
  if(destination==null||map==null)return;
  if(line!=null){cancelRoute();trip.setVisibility(View.GONE);return;}
  if(request!=null)return;
  if(!fresh()||fix.getAccuracy()>100){
   waiting=true;trip("Уточняем точку старта…");
   main.removeCallbacks(locationTimeout);main.postDelayed(locationTimeout,15_000);locate();return;
  }
  CityData.Building building=data.buildings.get(destination.building);
  GeoPoint origin=new GeoPoint(fix.getLatitude(),fix.getLongitude()),end=data.entrance(building,origin);
  boolean campus=building.campusArrival;
  int token=++serial;trip("Строим пеший маршрут…");
  request=work.submit(()->{
   try{
    CityWalk walk=CityWalk.get(origin,end);
    runOnUiThread(()->{if(token==serial&&!isDestroyed())showRoute(walk,campus);});
   }catch(Exception failure){
    runOnUiThread(()->{if(token==serial&&!isDestroyed()){request=null;trip("Пеший маршрут недоступен. Попробуй ещё раз.");}});
   }
  });
 }
 private void showRoute(CityWalk walk,boolean campus){
  request=null;
  line=new Polyline(map);line.setPoints(walk.points);line.getOutlinePaint().setColor(UiTheme.accent(this));line.getOutlinePaint().setStrokeWidth(dp(5));
  map.getOverlays().add(0,line);
  trip(walk.label()+(campus?" · До главного кампуса":""));
  routeButton.setText("Завершить");
  map.invalidate();
  map.zoomToBoundingBox(BoundingBox.fromGeoPoints(walk.points),UiMotion.enabled(),dp(32),18,250L);
 }
 private void cancelRoute(){
  serial++;waiting=false;main.removeCallbacks(locationTimeout);
  if(request!=null){request.cancel(true);request=null;}
  if(line!=null&&map!=null){map.getOverlays().remove(line);line=null;map.invalidate();}
  if(routeButton!=null)routeButton.setText("Маршрут пешком");
 }
 private void trip(String text){trip.setText(text);trip.setVisibility(View.VISIBLE);}
 private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
 private TextView button(String text,Runnable action){return Modal.button(this,text,action);}
 private int dp(int n){return AppDesign.dp(this,n);}
 @Override protected void onResume(){super.onResume();UiMotion.resumed(this);resumed=true;if(map!=null)map.onResume();startLocation();}
 @Override protected void onPause(){resumed=false;if(map!=null)map.onPause();if(tracking){locations.removeUpdates(this);tracking=false;}super.onPause();}
 @Override protected void onDestroy(){cancelRoute();main.removeCallbacksAndMessages(null);work.shutdownNow();if(map!=null)map.onDetach();super.onDestroy();}
 @Override public void onBackPressed(){if(destination!=null){clearSelection();return;}super.onBackPressed();}
 @Override public void finish(){super.finish();UiMotion.back(this);}
}
