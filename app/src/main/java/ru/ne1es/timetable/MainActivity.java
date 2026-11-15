package ru.ne1es.timetable;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.webkit.*;
import android.net.Uri;
import android.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.*;

public class MainActivity extends Activity {
 private int INK=0xff281b3d,PURPLE=0xff604583,FAINT=0xfff7f2fc,CARD=0xffeee6f8,MUTED=0xff756887;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private Repository repo;private android.content.SharedPreferences prefs;private Schedule.Group group;private int subgroup=2;
 private Schedule.Data data;private Schedule.Changes changes;private List<Schedule.Group> groups=new ArrayList<>();
 private LocalDate selected=LocalDate.now(Schedule.ZONE),week=selected.with(java.time.DayOfWeek.MONDAY);
 private LinearLayout screen,days,lessons,liveCard;private TextView groupLabel,status,dayTitle,weekLabel,live,liveBadge,liveMeta,liveToggle;private LinearLayout liveDetails;private TextView howToWalk,dailyCount;private ProgressBar dailyProgress;private String nextRoom="";private String detailKey="";private ProgressBar liveProgress;private boolean isDark,liveExpanded;private String animatedDay="";
 private final Runnable clock=new Runnable(){public void run(){updateLive();main.postDelayed(this,1000);}};private FrameLayout root;
 private boolean loading,loginNeeded,offline,initializing=true;
 private boolean foreground;
 private Schedule.Data timelineData;
 private Schedule.Changes timelineChanges;
 private int timelineSubgroup=-1;
 private List<Schedule.Lesson> effectiveLessons=Collections.emptyList();
 private ImageView refreshIcon;private boolean manualRefresh;private String error="",changesError="";private volatile int generation;private boolean authOpen;
 private int insetTop=24,insetBottom=16;private long lastAttempt;
 @Override public void onCreate(Bundle state){UiTheme.apply(this);super.onCreate(state);isDark=UiTheme.dark(this);INK=UiTheme.ink(this);FAINT=UiTheme.background(this);CARD=UiTheme.card(this);MUTED=UiTheme.muted(this);PURPLE=UiTheme.accent(this);repo=new Repository(this);prefs=getSharedPreferences("timetable",0);subgroup=prefs.getInt("subgroup",2);if(state!=null){try{liveExpanded=state.getBoolean("live_expanded",false);selected=LocalDate.parse(state.getString("selected"));week=selected.with(java.time.DayOfWeek.MONDAY);}catch(Exception ignored){}}
  getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getDecorView().setSystemUiVisibility((isDark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  if(state==null&&getIntent().hasExtra("date")){try{selected=LocalDate.parse(getIntent().getStringExtra("date"));week=selected.with(java.time.DayOfWeek.MONDAY);}catch(Exception ignored){}}
  render();routeTab(getIntent());
  io.execute(()->{
   List<Schedule.Group> catalogue=repo.cachedGroups();
   String saved=prefs.getString("group","");
   Schedule.Group chosen=catalogue.stream().filter(g->Schedule.norm(g.name).equals(Schedule.norm(saved))).findFirst().orElse(null);
   Schedule.Data savedData=chosen==null?null:repo.cached(chosen.name);
   Schedule.Changes savedChanges=chosen==null?null:repo.cachedChanges(chosen.name);
   main.post(()->{
    if(isDestroyed()||isFinishing())return;
    groups=catalogue;group=chosen;data=savedData;changes=savedChanges;initializing=false;
    updateGroupLabel();
    if(foreground)resumeSchedule();
   });
   AppNotifications.channels(getApplicationContext());
   SyncJob.schedule(getApplicationContext());
   ReminderReceiver.schedule(getApplicationContext());
  });
 }
 private void updateGroupLabel(){
  groupLabel.setText(group==null?"Выбрать группу":group.name+" · "+(subgroup==0?"Все подгруппы":subgroup+"-я подгруппа")+"  ⌄");
 }
 private void resumeSchedule(){
  if(initializing)return;
  if(group==null){
   if(prefs.getBoolean("onboarded",false)){if(lastAttempt==0){lastAttempt=1;chooseGroup();}}
   else if(lastAttempt==0){lastAttempt=1;onboarding();}
   return;
  }
  requestNotifications();
  if(!authOpen&&!loading&&System.currentTimeMillis()-lastAttempt>300000)refresh();
  else showLessons();
 }
 @Override protected void onSaveInstanceState(Bundle b){b.putString("selected",selected.toString());b.putBoolean("live_expanded",liveExpanded);super.onSaveInstanceState(b);}
 @Override protected void onResume(){
  super.onResume();UiMotion.resumed(this);foreground=true;
  if(UiTheme.dark(this)!=isDark||UiTheme.accent(this)!=PURPLE){recreate();return;}
  main.removeCallbacks(clock);main.post(clock);resumeSchedule();
  if(group!=null){
   final Schedule.Group requested=group;
   io.execute(()->{
    Schedule.Data saved=repo.cached(requested.name);
    Schedule.Changes updates=repo.cachedChanges(requested.name);
    main.post(()->{
     if(!isDestroyed()&&group==requested&&saved!=null&&(data==null||saved.checkedAt>data.checkedAt)){
      data=saved;changes=updates;showLessons();
     }
    });
   });
  }
 }
 @Override protected void onPause(){foreground=false;main.removeCallbacks(clock);super.onPause();}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);routeTab(i);if(i.hasExtra("date")){try{selected=LocalDate.parse(i.getStringExtra("date"));week=selected.with(java.time.DayOfWeek.MONDAY);showDays();showLessons();}catch(Exception ignored){}}}
 private void routeTab(Intent intent){
  int destination=intent.getIntExtra("destination_tab",0);
  intent.removeExtra("destination_tab");
  if(destination!=0)main.post(()->{if(!isFinishing())selectTab(destination,true);});
 }
 private void selectTab(int tab){selectTab(tab,false);}
 private void selectTab(int tab,boolean fromChild){
  if(tab==1)openMap(null,fromChild);else if(tab==2)openSettings(fromChild);
 }
 private void userRefresh(){if(!loading&&group!=null)manualRefresh=true;refresh();}
 private boolean connected(){
  android.net.ConnectivityManager manager=getSystemService(android.net.ConnectivityManager.class);
  return manager==null||manager.getActiveNetwork()!=null;
 }
 private void requestNotifications(){if(group!=null&&android.os.Build.VERSION.SDK_INT>=33&&!prefs.getBoolean("notification_asked",false)){prefs.edit().putBoolean("notification_asked",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},800);}}
 @Override public void onRequestPermissionsResult(int r,String[] n,int[] g){super.onRequestPermissionsResult(r,n,g);ReminderReceiver.schedule(this);}
 private String roomName(Schedule.Lesson n){return n.room.toUpperCase(Locale.ROOT).contains("ДОТ")?"Дистанционно":n.room.isEmpty()?"Аудитория не указана":n.room;}
 private String lessonKey(Schedule.Lesson n){return n==null?"":n.date+"/"+n.slot+"/"+n.start+"/"+n.end+"/"+n.title+"/"+n.room+"/"+n.teacher+"/"+n.type+"/"+n.subgroup;}
 private void updateLive(){
  if(live==null||!live.isAttachedToWindow())return;
  ZonedDateTime now=ZonedDateTime.now(Schedule.ZONE);
  if(timelineData!=data||timelineChanges!=changes||timelineSubgroup!=subgroup){
   timelineData=data;timelineChanges=changes;timelineSubgroup=subgroup;
   effectiveLessons=ScheduleLogic.effective(data,changes,subgroup);
  }
  ScheduleLogic.Timeline t=ScheduleLogic.timeline(effectiveLessons,now);
  Schedule.Lesson n=t.current!=null?t.current:t.next;
  liveCard.setVisibility(n!=null?View.VISIBLE:View.GONE);
  String badge=t.current!=null?"Сейчас · ещё "+RussianText.duration(t.remainingMinutes):t.next!=null?"Следующая · "+(t.next.date.equals(now.toLocalDate().toString())?"через "+RussianText.duration(t.remainingMinutes):shortDate(LocalDate.parse(t.next.date))+" в "+t.next.start):"Ближайшие занятия";
  setText(liveBadge,badge);
  setText(live,n==null?(data==null?"Расписание ещё не загружено":"На опубликованные даты ближайших пар нет"):n.title);
  live.setMaxLines(liveExpanded?Integer.MAX_VALUE:2);live.setEllipsize(liveExpanded?null:android.text.TextUtils.TruncateAt.END);
  setText(liveMeta,n==null?"Проверь источники расписания":n.start+"–"+n.end+" · "+roomName(n));
  nextRoom=t.next==null?"":t.next.room.trim();
  howToWalk.setContentDescription(t.next==null?"Как пройти":"Как пройти на следующую пару: "+t.next.title+", "+nextRoom);
  howToWalk.setVisibility(nextRoom.matches("[1-6]\\s*[-–—]\\s*.+")?View.VISIBLE:View.GONE);
  liveDetails.setVisibility(liveExpanded?View.VISIBLE:View.GONE);setText(liveToggle,liveExpanded?"Свернуть ⌃":"Подробнее ⌄");
  String key=lessonKey(n)+"|"+lessonKey(t.current==null?null:t.next);
  if(liveExpanded&&!key.equals(detailKey)){
   detailKey=key;liveDetails.removeAllViews();
   if(n!=null)addLessonDetails(liveDetails,n);
   if(t.current!=null&&t.next!=null){LinearLayout next=vertical();next.setPadding(dp(14),dp(12),dp(14),dp(12));next.setBackground(shape(UiTheme.surface(this),18));next.addView(label("Далее",12,MUTED,true));next.addView(label(t.next.title,16,INK,true));addLessonDetails(next,t.next);liveDetails.addView(next);}
  }
  liveProgress.setVisibility(t.current!=null?View.VISIBLE:View.GONE);if(liveProgress.getProgress()!=t.progress)liveProgress.setProgress(t.progress,UiMotion.enabled());
  updateDayProgress(now);
  if(lessons!=null)for(int i=0;i<lessons.getChildCount();i++){
   View row=lessons.getChildAt(i);if(!(row.getTag() instanceof Schedule.Lesson))continue;
   Schedule.Lesson item=(Schedule.Lesson)row.getTag();boolean active=t.current!=null&&lessonKey(item).equals(lessonKey(t.current));
   if(row.isActivated()!=active){row.setActivated(active);styleLesson(row,item,active);}
  }
 }
 private static void setText(TextView view,String value){
  if(!value.contentEquals(view.getText()))view.setText(value);
 }
 private void updateDayProgress(ZonedDateTime now){
  if(dailyCount==null||dailyProgress==null||lessons==null)return;
  int finished=0,total=0;
  for(int i=0;i<lessons.getChildCount();i++){
   Object item=lessons.getChildAt(i).getTag();
   if(!(item instanceof Schedule.Lesson))continue;
   Schedule.Lesson lesson=(Schedule.Lesson)item;
   if("Отменено".equals(lesson.change))continue;
   total++;
   if(!ScheduleLogic.end(lesson).isAfter(now))finished++;
  }
  View panel=(View)dailyCount.getParent();panel.setVisibility(total==0?View.GONE:View.VISIBLE);
  if(total>0){
   String text="Завершено: "+finished+" из "+total;
   if(!text.contentEquals(dailyCount.getText()))dailyCount.setText(text);
   int progress=finished*100/total;
   if(dailyProgress.getProgress()!=progress)dailyProgress.setProgress(progress,UiMotion.enabled());
  }
 }
 private int lessonBackground(Schedule.Lesson n,boolean active){return active?(isDark?0xff4c3863:0xffe2d1f3):!n.change.isEmpty()?(isDark?0xff492938:0xffffe5eb):CARD;}
 private void metaRow(LinearLayout into,String icon,String value){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(4));
  Icon glyph=new Icon(icon);glyph.setFocusable(false);glyph.setClickable(false);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);glyph.setColorFilter(MUTED);
  row.addView(glyph,new LinearLayout.LayoutParams(dp(22),dp(24)));
  TextView text=label(value,14,INK,false);text.setPadding(dp(10),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));into.addView(row);
 }
 private void addLessonDetails(LinearLayout into,Schedule.Lesson n){
  metaRow(into,"time",n.start+"–"+n.end+" · "+n.slot+"-я пара");
  metaRow(into,"room",roomName(n));
  if(!n.teacher.isEmpty())metaRow(into,"person",RussianText.teacher(n.teacher));
  metaRow(into,"school",typeName(n.type)+" · "+(n.subgroup==0?"Общая пара":n.subgroup+"-я подгруппа"));
 }
 private void openMap(String room){openMap(room,false);}
 private void openMap(String room,boolean fromChild){
  if(!UiMotion.beginNavigation(this))return;
  startTab(new Intent(this,CityActivity.class).putExtra("room",room),fromChild,0);
 }
 private void jumpToday(){selected=LocalDate.now(Schedule.ZONE);week=selected.with(DayOfWeek.MONDAY);showDays();showLessons();}
 private void openSettings(boolean fromChild){
  if(!UiMotion.beginNavigation(this))return;
  startTab(new Intent(this,SettingsActivity.class),fromChild,1002);
 }
 private void startTab(Intent intent,boolean fromChild,int request){
  if(fromChild)intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
  if(request==0)startActivity(intent);else startActivityForResult(intent,request);
  if(fromChild)UiMotion.quiet(this);else UiMotion.forward(this);
 }
 private void toggleLive(){final ScrollView page=findViewById(R.id.main_scroll);final int scrollY=page.getScrollY();page.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){page.getViewTreeObserver().removeOnPreDrawListener(this);page.scrollTo(0,scrollY);return true;}});if(UiMotion.enabled()){
  android.transition.TransitionSet transition=new android.transition.TransitionSet();
  transition.addTransition(new android.transition.ChangeBounds());
  transition.addTransition(new android.transition.Fade(android.transition.Fade.IN).addTarget(liveDetails));
  transition.setDuration(230);transition.setInterpolator(new android.view.animation.DecelerateInterpolator());
  android.transition.TransitionManager.beginDelayedTransition(screen,transition);
 }liveExpanded=!liveExpanded;updateLive();}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setFontFeatureSettings("kern");t.setTypeface(UiTheme.font(this,bold));return t;}
 private android.graphics.drawable.Drawable shape(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private android.graphics.drawable.Drawable touch(int color,float radius){return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22604583),shape(color,radius),shape(Color.WHITE,radius));}
 private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private TextView action(String title,Runnable run){TextView b=label(title,15,UiTheme.accent(this),true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(8),dp(16),dp(8));b.setBackground(touch(CARD,26));b.setOnClickListener(v->run.run());UiMotion.press(b);return b;}
 private void render(){if(refreshIcon!=null)UiMotion.refreshState(refreshIcon,false);root=AppDesign.background(this);root.setId(R.id.main_root);ScrollView page=new UiMotion.RefreshScroll(this,this::userRefresh);page.setId(R.id.main_scroll);page.setFillViewport(true);page.setClipToPadding(false);screen=vertical();screen.setPadding(dp(12),dp(8),dp(12),dp(8));page.addView(screen);
  LinearLayout scaffold=AppDesign.scaffold(this,page,0,this::selectTab);
  root.addView(scaffold,new FrameLayout.LayoutParams(-1,-1));AppDesign.insets(scaffold);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout heading=vertical();TextView title=label("Расписание",27,INK,true);
  boolean large=getResources().getConfiguration().fontScale>=1.4f;
  heading.addView(title,new LinearLayout.LayoutParams(-1,-2));
  heading.addView(label("УлГТУ",13,PURPLE,true));header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Icon headerCalendar=new Icon("calendar");headerCalendar.setContentDescription("Выбрать дату");headerCalendar.setBackground(touch(CARD,16));headerCalendar.setOnClickListener(v->datePicker());
  header.addView(headerCalendar,new LinearLayout.LayoutParams(dp(48),dp(48)));UiMotion.press(headerCalendar);
  refreshIcon=new Icon("refresh");refreshIcon.setContentDescription("Обновить расписание");refreshIcon.setBackground(touch(UiTheme.surface(this),16));refreshIcon.setOnClickListener(v->userRefresh());
  LinearLayout.LayoutParams refreshBounds=new LinearLayout.LayoutParams(dp(48),dp(48));refreshBounds.leftMargin=dp(6);header.addView(refreshIcon,refreshBounds);UiMotion.press(refreshIcon);screen.addView(header);
  groupLabel=label(group==null?"Выбрать группу":group.name+" · "+(subgroup==0?"Все подгруппы":subgroup+"-я подгруппа")+"  ⌄",13,INK,true);
  groupLabel.setMinHeight(dp(34));groupLabel.setGravity(Gravity.CENTER_VERTICAL);groupLabel.setPadding(dp(12),0,dp(12),0);groupLabel.setBackground(touch(CARD,24));groupLabel.setOnClickListener(v->chooseGroup());UiMotion.press(groupLabel);
  LinearLayout dayTools=new LinearLayout(this);dayTools.setGravity(Gravity.CENTER_VERTICAL);dayTools.setOrientation(large?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
  dayTools.addView(groupLabel,new LinearLayout.LayoutParams(large?-1:0,-2,large?0:1));
  weekLabel=label("",11,MUTED,true);weekLabel.setGravity(Gravity.CENTER);weekLabel.setMinHeight(dp(44));
  weekLabel.setPadding(dp(8),0,dp(8),0);weekLabel.setBackground(touch(CARD,15));weekLabel.setOnClickListener(v->datePicker());
  weekLabel.setContentDescription("Выбрать дату и неделю");
  LinearLayout.LayoutParams weekBounds=new LinearLayout.LayoutParams(large?-1:-2,-2);if(large)weekBounds.topMargin=dp(5);else weekBounds.leftMargin=dp(6);dayTools.addView(weekLabel,weekBounds);
  LinearLayout.LayoutParams toolBounds=new LinearLayout.LayoutParams(-1,-2);toolBounds.topMargin=dp(5);screen.addView(dayTools,toolBounds);
  int stripHeight=dp(large?78:56);
  FrameLayout weekStrip=new FrameLayout(this);weekStrip.setId(R.id.week_strip);
  dayIndicator=new View(this);dayIndicator.setBackground(AppDesign.primary(dayIndicator));
  weekStrip.addView(dayIndicator,new FrameLayout.LayoutParams(0,stripHeight));
  days=new LinearLayout(this);days.setOrientation(LinearLayout.HORIZONTAL);days.setGravity(Gravity.CENTER_VERTICAL);
  weekStrip.addView(days,new FrameLayout.LayoutParams(-1,stripHeight));
  LinearLayout.LayoutParams stripBounds=new LinearLayout.LayoutParams(-1,stripHeight);stripBounds.topMargin=dp(8);screen.addView(weekStrip,stripBounds);
  LinearLayout dayProgress=vertical();dayProgress.setPadding(dp(12),dp(7),dp(12),dp(7));dayProgress.setBackground(touch(CARD,17));
  dailyCount=label("",12,INK,true);dayProgress.addView(dailyCount);
  dailyProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);dailyProgress.setMax(100);
  dailyProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(UiTheme.progress(this)));
  LinearLayout.LayoutParams barBounds=new LinearLayout.LayoutParams(-1,dp(4));barBounds.topMargin=dp(6);dayProgress.addView(dailyProgress,barBounds);
  LinearLayout.LayoutParams progressBounds=new LinearLayout.LayoutParams(-1,-2);progressBounds.topMargin=dp(8);screen.addView(dayProgress,progressBounds);
  dayProgress.setOnClickListener(v->sourceState());UiMotion.press(dayProgress);
  liveCard=vertical();liveCard.setId(R.id.live_card);liveCard.setPadding(dp(13),dp(10),dp(13),dp(10));liveCard.setBackground(touch(CARD,24));liveCard.setElevation(0);UiMotion.press(liveCard);liveCard.setOnClickListener(v->toggleLive());LinearLayout liveTop=new LinearLayout(this);liveTop.setGravity(Gravity.CENTER_VERTICAL);liveBadge=label("",12,UiTheme.accent(this),true);liveTop.addView(liveBadge,new LinearLayout.LayoutParams(0,-2,1));liveToggle=label("",11,MUTED,false);liveTop.addView(liveToggle);liveCard.addView(liveTop);
  live=label("",16,INK,true);live.setId(R.id.live_lesson);live.setPadding(0,dp(10),0,dp(7));live.setLineSpacing(dp(2),1);liveCard.addView(live);liveMeta=label("",13,MUTED,false);liveCard.addView(liveMeta);howToWalk=action("Как пройти",()->openMap(nextRoom));AppDesign.emphasize(howToWalk);liveCard.addView(howToWalk);liveProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);liveProgress.setMax(1000);liveProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(UiTheme.accent(this)));liveProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(isDark?0xff49375f:0xffdcd0ec));LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(-1,dp(3));pr.topMargin=dp(13);liveCard.addView(liveProgress,pr);detailKey="";liveDetails=vertical();liveDetails.setId(R.id.live_details);liveDetails.setPadding(0,dp(17),0,dp(2));liveCard.addView(liveDetails);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);
  TextView today=action("Сегодня",this::jumpToday);
  LinearLayout dayRow=new LinearLayout(this);dayRow.setGravity(Gravity.CENTER_VERTICAL);dayTitle=label("",16,INK,true);dayTitle.setId(R.id.day_heading);dayRow.addView(dayTitle,new LinearLayout.LayoutParams(0,-2,1));today.setTextSize(12);today.setPadding(dp(8),0,dp(8),0);if(!large)dayRow.addView(today,new LinearLayout.LayoutParams(-2,-2));Icon more=new Icon("more");more.setId(R.id.more_menu);more.setContentDescription("Источники, статус и обновление");more.setOnClickListener(v->menu(more));dayRow.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout.LayoutParams dr=new LinearLayout.LayoutParams(-1,-2);dr.topMargin=dp(7);screen.addView(dayRow,dr);if(large)screen.addView(today,new LinearLayout.LayoutParams(-1,-2));status=label("",12,MUTED,false);status.setId(R.id.source_status);status.setPadding(0,0,0,dp(14));status.setOnClickListener(v->sourceState());screen.addView(status);
  lessons=vertical();lessons.setId(R.id.lesson_list);lessons.setPadding(0,0,0,dp(20));screen.addView(lessons);screen.addView(liveCard,lp);setContentView(root);scaffold.requestApplyInsets();showDays();showLessons();
 }
 private String shortDate(LocalDate d){return d.format(DateTimeFormatter.ofPattern("d MMM",new Locale("ru"))).replace(".","");}
 private int shownDay=-1;
 private View dayIndicator;
 private void moveDayIndicator(View tile,boolean animate){
  dayIndicator.animate().cancel();
  FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)dayIndicator.getLayoutParams();
  if(bounds.width!=tile.getWidth()){bounds.width=tile.getWidth();dayIndicator.setLayoutParams(bounds);}
  if(animate&&UiMotion.enabled())dayIndicator.animate().translationX(tile.getLeft()).setDuration(225)
   .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
  else dayIndicator.setTranslationX(tile.getLeft());
 }
 private void showDays(){
  int previousDay=shownDay;shownDay=selected.getDayOfWeek().getValue()-1;
  days.removeAllViews();weekLabel.setText(RussianText.week(week));
  String[] names={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
  for(int i=0;i<7;i++){
   LocalDate d=week.plusDays(i);boolean active=d.equals(selected);LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setPadding(0,dp(8),0,dp(8));tile.setBackground(touch(active?Color.TRANSPARENT:0x1066508a,19));
   TextView name=label(names[i],12,active?Color.WHITE:INK,active);name.setGravity(Gravity.CENTER);name.setIncludeFontPadding(false);name.setSingleLine(true);
   TextView date=label(String.valueOf(d.getDayOfMonth()),17,active?Color.WHITE:INK,active);date.setGravity(Gravity.CENTER);date.setIncludeFontPadding(false);date.setSingleLine(true);date.setPadding(0,0,0,dp(3));
   tile.addView(date,new LinearLayout.LayoutParams(-1,-2));tile.addView(name,new LinearLayout.LayoutParams(-1,-2));UiMotion.press(tile);tile.setContentDescription(names[i]+" "+d.toString());tile.setOnClickListener(v->{selected=d;showDays();showLessons();});
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(i<6)p.rightMargin=dp(4);days.addView(tile,p);
   if(active)tile.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
    if(v.getParent()==days)moveDayIndicator(v,previousDay>=0&&previousDay!=shownDay);
   });
  }
 }
 private void showLessons(){if(lessons==null)return;updateRefreshIcon();boolean animateDay=!selected.toString().equals(animatedDay);animatedDay=selected.toString();String day=selected.format(DateTimeFormatter.ofPattern("EEE, d MMMM",new Locale("ru")));dayTitle.setText((selected.equals(LocalDate.now(Schedule.ZONE))?"Сегодня, "+day:RussianText.capitalize(day)));
  if(loading)status.setText("Обновляем расписание…");else if(loginNeeded)status.setText("Нужен вход в УлГТУ");else if(!error.isEmpty())status.setText(data==null?"Не удалось загрузить расписание":"Не удалось обновить · сохранены последние пары");else if(!changesError.isEmpty())status.setText("Расписание загружено · замены не проверены");else if(changes!=null&&changes.checkedDates.contains(selected.toString())){
   boolean match=changes.lessons.stream().anyMatch(l->l.date.equals(selected.toString())&&(l.subgroup==0||subgroup==0||l.subgroup==subgroup));
   status.setText(match?"Замены проверены · "+stamp(changes.checkedAt):"Для твоей группы замен нет");
  }else if(data!=null)status.setText("По расписанию УлГТУ · замен на этот день пока нет в папке");else status.setText("Подключи личный кабинет УлГТУ");
  List<Schedule.Lesson> list=Schedule.day(data,changes,selected.toString(),subgroup);
  boolean reuse=!list.isEmpty()&&list.size()==lessons.getChildCount();
  for(int i=0;reuse&&i<list.size();i++)reuse=sameCard(lessons.getChildAt(i).getTag(),list.get(i));
  if(reuse){updateLive();return;}
  lessons.removeAllViews();
  for(Schedule.Lesson lesson:list){
   View row=card(lesson);lessons.addView(row);
   if(animateDay)UiMotion.enter(row,Math.min(lessons.getChildCount()-1,4)*12);
  }
  updateLive();
  if(list.isEmpty())renderEmpty();

 }
 private boolean sameCard(Object item,Schedule.Lesson lesson){
  if(!(item instanceof Schedule.Lesson))return false;
  Schedule.Lesson old=(Schedule.Lesson)item;
  return lessonKey(old).equals(lessonKey(lesson))&&Objects.equals(old.change,lesson.change)
   &&Objects.equals(old.old,lesson.old)&&Objects.equals(old.source,lesson.source);
 }
 private void renderEmpty(){
  LinearLayout empty=vertical();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(30),dp(12),dp(22));
  boolean firstLoad=data==null&&(loading||initializing), failed=data==null&&!error.isEmpty()&&!loginNeeded;
  boolean noLessons=data!=null&&data.dates.contains(selected.toString());
  if(firstLoad){
   ProgressBar spinner=new ProgressBar(this);
   spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(UiTheme.progress(this)));
   empty.addView(spinner,new LinearLayout.LayoutParams(dp(56),dp(56)));
  }else{
   int asset=failed?(offline?R.drawable.ms_wifi_off:R.drawable.ms_warning):R.drawable.empty_art;
   ImageView symbol=AppDesign.icon(this,asset,failed&&!offline?(isDark?0xffffa9bb:0xff9c3852):PURPLE);
   if(failed){
    symbol.setPadding(dp(16),dp(16),dp(16),dp(16));symbol.setBackground(AppDesign.panel(this,UiTheme.selected(this),23));
   }
   empty.addView(symbol,new LinearLayout.LayoutParams(dp(failed?76:164),dp(failed?76:120)));
  }
  String title=firstLoad?"Загрузка расписания…":loginNeeded&&data==null?"Вход в УлГТУ":failed?(offline?"Нет подключения к интернету":"Не удалось обновить расписание"):noLessons?"Пар в этот день нет":data==null?"Расписание ещё не загружено":"Расписание ещё не опубликовано";
  TextView heading=label(title,20,INK,true);heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(16),0,dp(8));empty.addView(heading);
  String message=firstLoad?"Получаем актуальные занятия и замены.":failed?"Можно повторить попытку.":loginNeeded&&data==null?"Подключи личный кабинет, чтобы получить расписание.":"";
  if(!message.isEmpty()){TextView copy=label(message,13,MUTED,false);copy.setGravity(Gravity.CENTER);empty.addView(copy);AppDesign.space(empty,13);}
  if(!firstLoad&&!noLessons){
   TextView button=action(loginNeeded?"Войти":failed?"Повторить":"Обновить",loginNeeded?this::login:this::userRefresh);
   AppDesign.emphasize(button);empty.addView(button,new LinearLayout.LayoutParams(-1,-2));
  }
  lessons.addView(empty);
 }
 private View card(Schedule.Lesson n){
  boolean changed=!n.change.isEmpty(),cancel=n.change.equals("Отменено");
  LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
  card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setTag(n);UiMotion.press(card);
  styleLesson(card,n,false);
  LinearLayout.LayoutParams cardBounds=new LinearLayout.LayoutParams(-1,-2);cardBounds.bottomMargin=dp(8);card.setLayoutParams(cardBounds);
  View marker=new View(this);marker.setBackground(shape(UiTheme.progress(this),3));
  LinearLayout.LayoutParams markerBounds=new LinearLayout.LayoutParams(dp(4),dp(48));markerBounds.rightMargin=dp(10);card.addView(marker,markerBounds);
  LinearLayout info=vertical();card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
  TextView title=label(n.title,16,INK,true);if(cancel)title.setPaintFlags(title.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);info.addView(title);
  if(changed){
   TextView badge=label(n.change,11,UiTheme.selectedText(this),true);
   badge.setPadding(dp(7),dp(2),dp(7),dp(2));badge.setBackground(shape(UiTheme.selected(this),12));info.addView(badge,new LinearLayout.LayoutParams(-2,-2));
  }
  if(!n.old.isEmpty()&&!cancel)info.addView(label("Вместо «"+n.old+"»",11,MUTED,false));
  boolean wide=getResources().getConfiguration().fontScale<1.3f&&getResources().getConfiguration().screenWidthDp>=340;
  LinearLayout metadata=new LinearLayout(this);metadata.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  LinearLayout left=vertical(),right=vertical();
  if(wide){metadata.addView(left,new LinearLayout.LayoutParams(0,-2,1));metadata.addView(right,new LinearLayout.LayoutParams(0,-2,1));}
  else{metadata.addView(left);metadata.addView(right);}
  compactMeta(left,"time",n.start+"–"+n.end);
  if(!n.teacher.isEmpty())compactMeta(left,"person",RussianText.teacher(n.teacher));
  compactMeta(right,"room",roomName(n));compactMeta(right,"school",typeName(n.type)+(n.subgroup>0?" · "+n.subgroup+"-я подгруппа":""));
  info.addView(metadata);card.setOnClickListener(v->detail(n));return card;
 }
 private void compactMeta(LinearLayout parent,String kind,String value){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(2),dp(4),0);
  Icon icon=new Icon(kind);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);icon.setColorFilter(MUTED);
  row.addView(icon,new LinearLayout.LayoutParams(dp(15),dp(16)));
  TextView text=label(value,11,MUTED,false);text.setPadding(dp(4),0,0,0);
  row.addView(text,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }
 private void styleLesson(View row,Schedule.Lesson lesson,boolean active){
  GradientDrawable fill=AppDesign.panel(this,lessonBackground(lesson,active),17);
  if(active||!lesson.change.isEmpty())fill.setStroke(dp(1),PURPLE);
  row.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x36a464ef),fill,null));
 }
 private void updateRefreshIcon(){
  if(refreshIcon==null)return;
  UiMotion.refreshState(refreshIcon,loading);
  if(!loading&&manualRefresh){
   manualRefresh=false;
   if(error.isEmpty()&&changesError.isEmpty())UiMotion.refreshSuccess(refreshIcon);
  }
 }
 private String typeName(String s){String n=s.toLowerCase(Locale.ROOT);if(n.startsWith("лек"))return "Лекция";if(n.startsWith("пр"))return "Практика";if(n.startsWith("лаб"))return "Лабораторная";return s.isEmpty()?"Занятие":s;}
 private void sheet(String title,java.util.function.BiConsumer<LinearLayout,Dialog> populate){
  Modal.show(this,title,populate);
 }
 private void buttons(LinearLayout body,TextView... actions){Modal.row(this,body,actions);}
 private void detail(Schedule.Lesson n){Modal.showPage(this,"Пара","",(page,dialog)->{
  ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
  LinearLayout body=vertical();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView title=label(n.title,21,INK,true);title.setPadding(dp(15),dp(14),dp(15),dp(14));
  title.setBackground(AppDesign.panel(this,UiTheme.selected(this),20));body.addView(title);AppDesign.space(body,9);
  detailLine(body,"time","Время",n.start+"–"+n.end+" · "+n.slot+"-я пара");
  if(!n.teacher.isEmpty())detailLine(body,"person","Преподаватель",RussianText.teacher(n.teacher));
  detailLine(body,"room","Аудитория",roomName(n));
  java.util.regex.Matcher room=java.util.regex.Pattern.compile("^([1-6])\\s*[-–—]\\s*.+").matcher(n.room);
  if(room.matches())detailLine(body,"map","Корпус",room.group(1));
  detailLine(body,"school","Тип занятия",typeName(n.type)+" · "+(n.subgroup==0?"Общая пара":n.subgroup+"-я подгруппа"));
  if(!n.change.isEmpty())detailLine(body,"refresh","Изменение",n.change+(n.old.isEmpty()?"":" · Было: "+n.old));
  TextView source=action("Источник",()->open(n.source));
  if(n.room.matches("[1-6]\\s*[-–—]\\s*.+")){
   TextView route=action("Построить маршрут",()->{dialog.dismiss();openMap(n.room);});AppDesign.emphasize(route);
   buttons(body,source);AppDesign.space(page,8);page.addView(route,new LinearLayout.LayoutParams(-1,-2));
  }else buttons(body,source,action("Закрыть",dialog::dismiss));
 });}
 private void detailLine(LinearLayout body,String kind,String caption,String value){
  LinearLayout panel=vertical();panel.setPadding(dp(10),dp(6),dp(10),dp(6));panel.setBackground(AppDesign.panel(this,CARD,16));
  TextView label=label(caption,11,MUTED,false);label.setPadding(dp(32),0,0,0);panel.addView(label);
  metaRow(panel,kind,value);AppDesign.space(body,7);body.addView(panel);
 }
 private void menu(View anchor){sheet("Расписание",(body,dialog)->Modal.actions(this,body,null,null,"Источники и статус",()->{dialog.dismiss();sourceState();},"Обновить",()->{dialog.dismiss();refresh();}));}
 private void datePicker(){sheet("Выбрать дату",(body,dialog)->{
  DatePicker picker=new DatePicker(this,null,android.R.attr.datePickerStyle);
  picker.init(selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth(),null);
  Modal.row(this,body,action("− Неделя",()->{
   LocalDate d=LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth()).minusWeeks(1);
   picker.updateDate(d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
  }),action("+ Неделя",()->{
   LocalDate d=LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth()).plusWeeks(1);
   picker.updateDate(d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
  }));
  body.addView(picker);
  Modal.actions(this,body,null,null,"Отмена",dialog::dismiss,"Выбрать",()->{
   selected=LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth());
   week=selected.with(java.time.DayOfWeek.MONDAY);dialog.dismiss();showDays();showLessons();
  });
 });}
 private String stamp(long checked){return checked==0?"ещё не проверено":Instant.ofEpochMilli(checked).atZone(Schedule.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm:ss",Locale.forLanguageTag("ru")));}
 private void sourceState(){sheet("Источники",(body,dialog)->{
  metaRow(body,"calendar","Расписание · "+stamp(data==null?0:data.checkedAt));
  metaRow(body,"time","Замены · данные от "+stamp(changes==null?0:changes.checkedAt));
  metaRow(body,"time","Вход · "+stamp(prefs.getLong("last_login_at",0)));
  if(changes!=null&&changes.warnings.isEmpty()&&!changes.checkedDates.isEmpty()&&changes.lessons.stream().noneMatch(l->l.subgroup==0||subgroup==0||l.subgroup==subgroup))
   body.addView(label("Для твоей группы замен нет",14,INK,true));
  Modal.actions(this,body,null,null,"Открыть замены",()->open(repo.changeSource()),"Обновить",()->{dialog.dismiss();refresh();});
 });}
 private void open(String url){if(url==null||!url.startsWith("https://"))return;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Не найден браузер",Toast.LENGTH_SHORT).show();}}
 private void onboarding(){
  FrameLayout background=AppDesign.background(this);
  LinearLayout column=vertical();column.setGravity(Gravity.CENTER);column.setPadding(dp(24),dp(16),dp(24),dp(24));
  background.addView(column,new FrameLayout.LayoutParams(-1,-1));AppDesign.insets(background);
  LinearLayout hero=vertical();hero.setGravity(Gravity.CENTER);
  ImageView illustration=AppDesign.icon(this,R.drawable.welcome_art,PURPLE);
  ImageView emblem=AppDesign.icon(this,R.drawable.ms_calendar_month,Color.WHITE);
  emblem.setPadding(dp(13),dp(13),dp(13),dp(13));emblem.setBackground(AppDesign.primary(emblem));
  hero.addView(emblem,new LinearLayout.LayoutParams(dp(60),dp(60)));
  hero.addView(illustration,new LinearLayout.LayoutParams(-1,dp(138)));
  TextView heading=label("Расписание",33,INK,true);heading.setGravity(Gravity.CENTER);hero.addView(heading);
  TextView university=label("УлГТУ",27,PURPLE,true);university.setGravity(Gravity.CENTER);hero.addView(university);
  TextView caption=label("Расписание, замены и навигация по корпусам",15,MUTED,false);
  caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(12),0,dp(12));hero.addView(caption);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(hero);
  column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView button=action("Начать",()->{render();chooseGroup();});AppDesign.emphasize(button);
  column.addView(button,new LinearLayout.LayoutParams(-1,-2));setContentView(background);background.requestApplyInsets();UiMotion.enter(hero,0);
 }
 private LinkedHashSet<String> storedGroups(String key){
  return new LinkedHashSet<>(Arrays.asList(prefs.getString(key,"").split("\\|")));
 }
 private void rememberGroup(String name){
  LinkedHashSet<String> items=storedGroups("recent_groups");items.remove("");items.remove(name);
  List<String> ordered=new ArrayList<>(items);ordered.add(0,name);
  prefs.edit().putString("recent_groups",String.join("|",ordered.subList(0,Math.min(8,ordered.size())))).apply();
 }
 private void chooseGroup(){
  if(initializing)return;
  GroupSections catalogue=new GroupSections();List<List<Schedule.Group>> sections=catalogue.load(this,groups);
  Modal.showPage(this,"Выбор группы","Введите название группы",(body,dialog)->{
   HorizontalScrollView strip=new HorizontalScrollView(this);
   strip.setHorizontalScrollBarEnabled(false);strip.setOverScrollMode(View.OVER_SCROLL_NEVER);
   LinearLayout chips=new LinearLayout(this);strip.addView(chips);
   EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Найти группу");search.setTextSize(15);
   search.setPadding(dp(13),dp(8),dp(13),dp(8));search.setMinHeight(dp(48));search.setBackground(AppDesign.panel(this,CARD,23));
   android.graphics.drawable.Drawable searchGlyph=getDrawable(R.drawable.ms_search).mutate();
   searchGlyph.setTint(MUTED);searchGlyph.setBounds(0,0,dp(19),dp(19));
   search.setCompoundDrawablesRelative(searchGlyph,null,null,null);search.setCompoundDrawablePadding(dp(8));
   LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2);
   searchParams.topMargin=dp(6);searchParams.bottomMargin=dp(9);body.addView(search,searchParams);
   body.addView(strip);
   int[] selectedCourse={0};
   String[] labels={"Все курсы","1 курс","2 курс","3 курс","4 курс","5 курс","6 курс"};
   TextView courses=action(labels[0],()->{});body.addView(courses);
   ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(dp(5));list.setNestedScrollingEnabled(true);
   body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
   List<Object> filtered=new ArrayList<>();
   Schedule.Group[] selectedGroup={group};
   LinkedHashSet<String> favourites=storedGroups("favourite_groups");
   ArrayAdapter<Object> adapter=new ArrayAdapter<Object>(this,android.R.layout.simple_list_item_1,filtered){
    @Override public boolean areAllItemsEnabled(){return false;}
    @Override public boolean isEnabled(int position){return getItem(position) instanceof Schedule.Group;}
    @Override public View getView(int position,View reusable,android.view.ViewGroup parent){
     LinearLayout row=reusable instanceof LinearLayout?(LinearLayout)reusable:vertical();
     if(row.getChildCount()==0){
      row.setPadding(dp(10),dp(7),dp(10),dp(7));row.setMinimumHeight(dp(56));
      LinearLayout summary=new LinearLayout(MainActivity.this);summary.setGravity(Gravity.CENTER_VERTICAL);
      ImageView icon=AppDesign.icon(MainActivity.this,R.drawable.ms_school,PURPLE);
      icon.setBackground(shape(UiTheme.selected(MainActivity.this),14));icon.setPadding(dp(8),dp(8),dp(8),dp(8));
      summary.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
      LinearLayout copy=vertical();copy.setPadding(dp(10),0,0,0);
      copy.addView(label("",16,INK,true));copy.addView(label("",12,MUTED,false));
      summary.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(summary);
     }
     LinearLayout summary=(LinearLayout)row.getChildAt(0),copy=(LinearLayout)summary.getChildAt(1);
     Object item=getItem(position);
     if(item instanceof String){
      row.setBackgroundColor(Color.TRANSPARENT);
      ((TextView)copy.getChildAt(0)).setText((String)item);
      copy.getChildAt(1).setVisibility(View.GONE);summary.getChildAt(0).setVisibility(View.GONE);return row;
     }
     Schedule.Group entry=(Schedule.Group)item;String subtitle=GroupSections.subtitle(entry);
     boolean chosen=selectedGroup[0]!=null&&entry.url.equals(selectedGroup[0].url)&&entry.name.equals(selectedGroup[0].name);
     row.setBackground(AppDesign.panel(MainActivity.this,chosen?UiTheme.selected(MainActivity.this):CARD,14));
     summary.getChildAt(0).setVisibility(View.VISIBLE);
     ((TextView)copy.getChildAt(0)).setText(entry.name+(favourites.contains(entry.name)?"  ★":"")+(chosen?"  ✓":""));
     TextView caption=(TextView)copy.getChildAt(1);caption.setText(subtitle);
     caption.setVisibility(subtitle.isEmpty()?View.GONE:View.VISIBLE);
     return row;
    }
   };
   list.setAdapter(adapter);
   int[] part={0};
   Runnable filter=()->{
    String query=search.getText().toString();int course=selectedCourse[0];
    Set<String> recent=storedGroups("recent_groups");
    filtered.clear();
    boolean pinned=group!=null&&GroupSections.matches(group.name,query)&&(course==0||catalogue.course(group)==course);
    if(pinned){filtered.add("Текущая группа");filtered.add(group);}
    int lastCourse=-1;
    for(Schedule.Group entry:sections.get(Math.max(0,part[0]))){
     if(pinned&&Schedule.norm(entry.name).equals(Schedule.norm(group.name)))continue;
     if(part[0]<0&&!recent.contains(entry.name)&&!favourites.contains(entry.name))continue;
     int level=catalogue.course(entry);
     if((course>0&&level!=course)||!GroupSections.matches(entry.name,query))continue;
     if(level!=lastCourse){filtered.add(level==0?"Курс не указан":level+" курс");lastCourse=level;}
     filtered.add(entry);
    }
    if(filtered.isEmpty())filtered.add("Группы не найдены");
    adapter.notifyDataSetChanged();list.setSelection(0);
   };
   courses.setOnClickListener(view->Modal.choice(this,"Курс",labels,selectedCourse[0],value->{
    selectedCourse[0]=value;courses.setText(labels[value]);filter.run();
   }));
   Runnable styleChips=()->{
    for(int i=0;i<chips.getChildCount();i++){
     TextView chip=(TextView)chips.getChildAt(i);boolean active=(i==0?0:i-1==0?-1:i-1)==part[0];
     chip.setSelected(active);
     if(active)AppDesign.emphasize(chip);
     else{chip.setBackground(touch(CARD,22));chip.setTextColor(INK);}
    }
   };
   for(int i=0;i<=sections.size();i++){
    final int section=i==0?0:i==1?-1:i-1;
    String title=section<0?"Избранные/Недавние":GroupSections.title(section)+" · "+sections.get(section).size();
    TextView chip=action(title,()->{part[0]=section;styleChips.run();filter.run();});
    chip.setSingleLine(true);chip.setTextSize(13);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(6);chips.addView(chip,lp);
   }
   styleChips.run();filter.run();
   search.addTextChangedListener(new TextWatcher(){
    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
    public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}
    public void afterTextChanged(Editable e){}
   });
   TextView next=action("Продолжить",()->{if(selectedGroup[0]!=null){dialog.dismiss();chooseSubgroup(selectedGroup[0]);}});
   next.setEnabled(selectedGroup[0]!=null);next.setAlpha(next.isEnabled()?1f:.55f);AppDesign.emphasize(next);
   list.setOnItemClickListener((view,row,position,id)->{
    if(!(filtered.get(position) instanceof Schedule.Group))return;
    selectedGroup[0]=(Schedule.Group)filtered.get(position);next.setEnabled(true);next.setAlpha(1f);
    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);adapter.notifyDataSetChanged();
   });
   list.setOnItemLongClickListener((view,row,position,id)->{
    if(!(filtered.get(position) instanceof Schedule.Group))return false;
    String name=((Schedule.Group)filtered.get(position)).name;
    boolean added=!favourites.remove(name);if(added)favourites.add(name);favourites.remove("");
    Toast.makeText(this,added?"Группа в избранном":"Группа удалена из избранного",Toast.LENGTH_SHORT).show();
    prefs.edit().putString("favourite_groups",String.join("|",favourites)).apply();
    filter.run();return true;
   });
   AppDesign.space(body,8);body.addView(next,new LinearLayout.LayoutParams(-1,-2));
   Modal.actions(this,body,null,null,null,null,"Обновить список",()->{dialog.dismiss();refreshGroupChooser();});
  });
 }

 private void chooseSubgroup(Schedule.Group next){
  Modal.showPage(this,"Выбор подгруппы",next.name,(body,dialog)->{
   ScrollView choicesScroll=new ScrollView(this);choicesScroll.setVerticalScrollBarEnabled(false);
   body.addView(choicesScroll,new LinearLayout.LayoutParams(-1,0,1));
   RadioGroup choices=new RadioGroup(this);
   String[] labels={"Все подгруппы","Общие и первая подгруппа","Общие и вторая подгруппа"};
   for(int index=0;index<labels.length;index++){
    RadioButton button=new RadioButton(this);
    button.setId(100+index);button.setText(labels[index]);button.setTextColor(INK);
    button.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE));
    button.setMinHeight(dp(56));button.setPadding(dp(10),dp(6),dp(10),dp(6));
    RadioGroup.LayoutParams option=new RadioGroup.LayoutParams(-1,-2);option.bottomMargin=dp(8);choices.addView(button,option);
   }
   choices.setOnCheckedChangeListener((r,id)->{for(int i=0;i<r.getChildCount();i++){View choice=r.getChildAt(i);choice.setBackground(AppDesign.panel(this,choice.getId()==id?UiTheme.selected(this):CARD,15));}});
   choices.check(100+subgroup);choicesScroll.addView(choices);
   TextView save=action("Продолжить",()->{
    dialog.dismiss();rememberGroup(next.name);subgroup=choices.getCheckedRadioButtonId()-100;group=next;
    prefs.edit().putBoolean("onboarded",true).putString("group",next.name).putInt("subgroup",subgroup).apply();
    generation++;loading=false;data=null;changes=null;error="";changesError="";
    final int request=generation;
    render();SyncJob.schedule(this);ReminderReceiver.schedule(this);requestNotifications();
    io.execute(()->{
     Schedule.Data cached=repo.cached(next.name);Schedule.Changes updates=repo.cachedChanges(next.name);
     main.post(()->{
      if(request!=generation||isDestroyed()||isFinishing())return;
      data=cached;changes=updates;showLessons();refresh();
     });
    });
   });
   AppDesign.emphasize(save);body.addView(save,new LinearLayout.LayoutParams(-1,-2));
  });
 }
 private void refreshGroupChooser(){
  io.execute(()->{try{
   List<Schedule.Group> fresh=repo.groups();
   main.post(()->{if(!isFinishing()){groups=fresh;chooseGroup();}});
  }catch(Repository.LoginRequired expired){main.post(()->openAuth(true));}
  catch(Exception unavailable){main.post(()->{if(!isFinishing()){Toast.makeText(this,"Список групп сейчас недоступен",Toast.LENGTH_SHORT).show();chooseGroup();}});}});
 }
 private void loginForCatalog(){openAuth(true);}
 private void refresh(){if(initializing)return;if(group==null){chooseGroup();return;}if(authOpen||loading)return;lastAttempt=System.currentTimeMillis();loading=true;error="";offline=false;loginNeeded=false;showLessons();final int token=++generation;final Schedule.Group selectedGroup=group;io.execute(()->{try{Schedule.Data fresh=repo.load(selectedGroup);if(token!=generation)return;repo.saveData(fresh);main.post(()->{if(token==generation){data=fresh;loginNeeded=false;showLessons();}});Schedule.Changes prior=repo.cachedRemoteChanges(selectedGroup.name),updated;String issue="";try{updated=repo.changes(fresh,prior);if(token!=generation)return;repo.saveChanges(selectedGroup.name,updated);if(!updated.warnings.isEmpty())issue=String.join("\n",updated.warnings);}catch(Exception e){updated=prior;issue="Не удалось проверить источник замен: "+human(e);}final Schedule.Changes ready=updated;final String warning=issue;main.post(()->{if(token!=generation)return;changes=ready;changesError=warning;loading=false;showLessons();ReminderReceiver.schedule(this);});}catch(Exception e){main.post(()->{if(token!=generation)return;loading=false;loginNeeded=e instanceof Repository.LoginRequired;if(loginNeeded)AppNotifications.authRequired(this);error=human(e);offline=!connected();showLessons();if(data!=null)refreshChangesOnly();});}});}
 private String human(Exception e){String m=e.getMessage();return m==null?"Нет соединения с источником":m;}
 private void login(){openAuth(group==null);}
 private void openAuth(boolean catalog){if(authOpen)return;generation++;loading=false;authOpen=true;Intent intent=new Intent(this,AuthActivity.class);if(!catalog&&group!=null)intent.putExtra("target_name",group.name).putExtra("target_url",group.url);startActivityForResult(intent,1001);}
 @Override protected void onActivityResult(int request,int result,Intent value){super.onActivityResult(request,result,value);if(request==1002&&result==RESULT_OK&&value!=null){String action=value.getStringExtra("action");if("group".equals(action))chooseGroup();else if("login".equals(action))login();else if("login_settings".equals(action))openAuth(true);else if("reload".equals(action))recreate();else if("today".equals(action)){selected=LocalDate.now(Schedule.ZONE);week=selected.with(java.time.DayOfWeek.MONDAY);showDays();showLessons();}return;}if(request==1001){authOpen=false;if(result==RESULT_OK){loginNeeded=false;error="";groups=repo.cachedGroups();if(group==null){chooseGroup();}else if(value!=null&&value.getBooleanExtra("schedule_ready",false)){data=repo.cached(group.name);lastAttempt=System.currentTimeMillis();showLessons();refreshChangesOnly();}else refresh();}else showLessons();}}
 private void refreshChangesOnly(){if(group==null||data==null)return;loading=true;showLessons();final int token=++generation;final String name=group.name;final Schedule.Data source=data;io.execute(()->{try{Schedule.Changes updated=repo.changes(source,repo.cachedRemoteChanges(name));if(token!=generation)return;repo.saveChanges(name,updated);main.post(()->{if(token!=generation)return;changes=updated;changesError=String.join("\n",updated.warnings);loading=false;showLessons();ReminderReceiver.schedule(this);});}catch(Exception e){main.post(()->{if(token!=generation)return;changesError="Не удалось проверить источник замен: "+human(e);loading=false;showLessons();});}});}
 private void loadCatalog(){status.setText("Загружаем список групп…");io.execute(()->{try{List<Schedule.Group> list=repo.groups();main.post(()->{groups=list;loginNeeded=false;if(group==null)chooseGroup();else{for(Schedule.Group g:list)if(Schedule.norm(g.name).equals(Schedule.norm(group.name))){group=g;break;}refresh();}});}catch(Exception e){main.post(()->{loginNeeded=e instanceof Repository.LoginRequired;if(loginNeeded)AppNotifications.authRequired(this);error=human(e);offline=!connected();showLessons();if(data!=null)refreshChangesOnly();Toast.makeText(this,error,Toast.LENGTH_LONG).show();});}});}
 @Override protected void onDestroy(){main.removeCallbacks(clock);if(refreshIcon!=null)UiMotion.refreshState(refreshIcon,false);generation++;io.shutdownNow();super.onDestroy();}
 private final class Icon extends ImageView {
  Icon(String kind){
   super(MainActivity.this);
   int resource;
   switch(kind){
    case "refresh":resource=R.drawable.ms_refresh;break;
    case "calendar":resource=R.drawable.ms_calendar_month;break;
    case "settings":resource=R.drawable.ms_settings;break;
    case "map":resource=R.drawable.ms_map;break;
    case "more":resource=R.drawable.ms_more_vert;break;
    case "left":resource=R.drawable.ms_chevron_left;break;
    case "right":resource=R.drawable.ms_chevron_right;break;
    case "time":resource=R.drawable.ms_schedule;break;
    case "room":resource=R.drawable.ms_location_on;break;
    case "person":resource=R.drawable.ms_person;break;
    default:resource=R.drawable.ms_school;
   }
   setImageResource(resource);setColorFilter(INK);setScaleType(ScaleType.CENTER_INSIDE);UiMotion.press(this);
  }
 }
}
