package ru.ne1es.timetable;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SettingsActivity extends Activity {
 private android.content.SharedPreferences prefs;private LinearLayout content;private String visibleAccount;
 private final java.util.Map<View,String> settingLabels=new java.util.LinkedHashMap<>();
 private void section(String title){
  TextView heading=text(title,12,true);heading.setTextColor(UiTheme.accent(this));
  heading.setPadding(dp(7),dp(10),0,dp(5));content.addView(heading);
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){UiTheme.apply(this);super.onCreate(b);prefs=ReminderReceiver.prefs(this);render();if(getIntent().getBooleanExtra("about",false))showAbout();}
 @Override public void finish(){super.finish();UiMotion.back(this);}
 @Override public void onResume(){super.onResume();UiMotion.resumed(this);if(prefs!=null){String account=accountLabel();if(!account.equals(visibleAccount))render();}}
 TextView text(String s,int size,boolean strong){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(UiTheme.ink(this));t.setTypeface(UiTheme.font(this,strong));t.setPadding(0,dp(8),0,dp(8));return t;}
 void action(String name,Runnable callback){action(name,"",callback);}
 void action(String name,String value,Runnable callback){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(54));
  row.setPadding(dp(10),dp(9),dp(10),dp(9));
  row.setBackground(UiMotion.ripple(UiTheme.surface(this),dp(18),row));
  int glyph=name.startsWith("Группа")?R.drawable.ms_school:name.startsWith("Аккаунт")?R.drawable.ms_person:
   name.startsWith("Уведомления")?R.drawable.ms_schedule:name.equals("Тема")?R.drawable.ms_settings:R.drawable.ms_more_vert;
  ImageView icon=AppDesign.icon(this,glyph,UiTheme.accent(this));
  icon.setPadding(dp(8),dp(8),dp(8),dp(8));icon.setBackground(AppDesign.panel(this,UiTheme.selected(this),13));
  row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
  LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(9),0,0,0);
  TextView title=text(name,15,true);title.setPadding(0,0,0,0);copy.addView(title);
  if(!value.isEmpty()){TextView description=text(value,12,false);description.setTextColor(UiTheme.muted(this));description.setPadding(0,0,0,0);copy.addView(description);}
  row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  row.addView(AppDesign.icon(this,R.drawable.ms_chevron_right,UiTheme.muted(this)),new LinearLayout.LayoutParams(dp(24),dp(26)));
  row.setOnClickListener(v->callback.run());row.setFocusable(true);UiMotion.press(row);
  LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.bottomMargin=dp(6);content.addView(row,bounds);settingLabels.put(row,(name+" "+value).toLowerCase(Locale.ROOT));
 }
 void render(){
  UiTheme.bars(this);settingLabels.clear();
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(15),dp(16),dp(12));
  scroll.addView(content);
  TextView heading=AppDesign.text(this,"Настройки",29,true);content.addView(heading);
  TextView subtitle=AppDesign.text(this,"УлГТУ",13,true);subtitle.setTextColor(UiTheme.accent(this));content.addView(subtitle);
  AppDesign.space(content,12);
  EditText search=input("Поиск настроек",android.text.InputType.TYPE_CLASS_TEXT);content.addView(search);
  AppDesign.space(content,12);
  String group=prefs.getString("group","");
  action("Группа и подгруппа",group.isEmpty()?"Не выбрана":group+" · "+prefs.getInt("subgroup",2),()->mainAction("group"));
  section("Аккаунт");
  visibleAccount=accountLabel();
  action("Аккаунт УлГТУ",visibleAccount,this::showAccount);
  section("Расписание");
  action("Параметры расписания","Источники и обновление",this::showAdvanced);
  section("Карта и навигация");
  action("Карта корпусов","Поиск и маршруты",()->AppDesign.switchFromChild(this,1));
  section("Уведомления");
  action("Уведомления",this::showNotifications);
  section("Оформление");
  String theme=prefs.getString("theme","system");
  action("Тема",theme.equals("system")?"Как в системе":theme.equals("dark")?"Тёмная":theme.equals("dynamic")?"Цвета обоев":"Светлая",()->chooseTheme(theme));
  section("О приложении");
  action("О приложении",this::showAbout);
  LinearLayout settingsRows=content;
  TextView empty=text("Настройки не найдены",14,false);empty.setVisibility(View.GONE);settingsRows.addView(empty);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence text,int start,int count,int after){}
   public void onTextChanged(CharSequence text,int start,int before,int count){
    String query=text.toString().trim().toLowerCase(Locale.ROOT);int matches=0;
    for(int i=0;i<settingsRows.getChildCount();i++){
     View item=settingsRows.getChildAt(i);
     String title=settingLabels.get(item);
     if(title==null){if(item instanceof TextView&&i>2&&item!=empty)item.setVisibility(query.isEmpty()?View.VISIBLE:View.GONE);continue;}
     boolean show=title.contains(query);if(show)matches++;
     item.setVisibility(show?View.VISIBLE:View.GONE);
    }
    empty.setVisibility(matches==0?View.VISIBLE:View.GONE);
   }
   public void afterTextChanged(android.text.Editable text){}
  });
  FrameLayout backdrop=AppDesign.background(this);
  LinearLayout scaffold=AppDesign.scaffold(this,scroll,2,tab->AppDesign.switchFromChild(this,tab));
  backdrop.addView(scaffold,new FrameLayout.LayoutParams(-1,-1));
  AppDesign.insets(scaffold);scaffold.setFocusableInTouchMode(true);setContentView(backdrop);scaffold.requestFocus();scaffold.requestApplyInsets();

 }
 private String accountLabel(){return signedIn()?"Вы вошли":"Войти";}
 private boolean signedIn(){return prefs.getBoolean("has_session",false)&&!prefs.getBoolean("session_required",false);}
 private void showAccount(){
  Modal.show(this,"Аккаунт УлГТУ",(panel,dialog)->{
   panel.addView(text(signedIn()?"Вы вошли":"Нужен вход",15,false));
   panel.addView(Modal.button(this,"Официальный вход",()->{dialog.dismiss();mainAction("login");}));
   panel.addView(Modal.button(this,"Автоматический вход",()->{dialog.dismiss();configureLogin();}));
   panel.addView(Modal.button(this,"Выйти и забыть данные",()->{dialog.dismiss();forgetLogin();}));
  });
 }
 private void chooseTheme(String theme){
  String[] values=Build.VERSION.SDK_INT>=31?new String[]{"light","dark","system","dynamic"}:new String[]{"light","dark","system"};
  String[] labels=Build.VERSION.SDK_INT>=31?new String[]{"Светлая","Тёмная","Как в системе","Цвета обоев"}:new String[]{"Светлая","Тёмная","Как в системе"};
  Modal.showPage(this,"Оформление","Тема приложения",(page,dialog)->{
   ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
   LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);scroll.addView(panel);
   page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   RadioGroup modes=new RadioGroup(this);String[] next={theme};
   for(int i=0;i<values.length;i++){
    RadioButton button=new RadioButton(this);button.setId(View.generateViewId());button.setText(labels[i]);
    button.setTextColor(UiTheme.ink(this));button.setMinHeight(dp(52));button.setPadding(dp(10),0,0,0);
    button.setBackground(AppDesign.panel(this,theme.equals(values[i])?UiTheme.selected(this):UiTheme.card(this),17));
    LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(-1,-2);bounds.bottomMargin=dp(5);modes.addView(button,bounds);
    if(theme.equals(values[i]))modes.check(button.getId());
   }
   modes.setOnCheckedChangeListener((group,id)->{
    for(int i=0;i<group.getChildCount();i++){
     View row=group.getChildAt(i);boolean selected=row.getId()==id;
     if(selected)next[0]=values[i];
     row.setBackground(AppDesign.panel(this,selected?UiTheme.selected(this):UiTheme.card(this),17));
    }
   });panel.addView(modes);
   panel.addView(text("Основной цвет",14,true));
   LinearLayout colors=new LinearLayout(this);int[] chosen={UiTheme.colorChoice(this)};
   String[] names={"Фиолетовый","Синий","Зелёный","Розовый"};
   for(int i=0;i<names.length;i++){
    final int choice=i;
    FrameLayout touch=new FrameLayout(this);touch.setContentDescription(names[i]);touch.setFocusable(true);
    View sample=new View(this);sample.setBackground(AppDesign.panel(this,UiTheme.swatch(this,i),30));
    colors.addView(touch,new LinearLayout.LayoutParams(0,dp(48),1));
    touch.addView(sample,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
    touch.setSelected(i==chosen[0]);touch.setAlpha(touch.isSelected()?1f:.65f);
    touch.setOnClickListener(v->{
     chosen[0]=choice;
     if(Build.VERSION.SDK_INT>=31&&next[0].equals("dynamic"))modes.check(modes.getChildAt(2).getId());
     for(int j=0;j<colors.getChildCount();j++){View item=colors.getChildAt(j);item.setSelected(j==choice);item.setAlpha(j==choice?1f:.65f);}
    });UiMotion.press(touch);
   }
   panel.addView(colors);
   Modal.actions(this,page,null,null,"Отмена",dialog::dismiss,"Применить",()->{
    dialog.dismiss();prefs.edit().putString("theme",next[0]).putInt("accent_choice",chosen[0]).apply();recreate();
   });
  });
 }
 private void showNotifications(){
  Modal.show(this,"Уведомления",(panel,dialog)->{
   notificationSwitch(panel,"Напоминать о парах","reminders");
   int lead=prefs.getInt("reminder_minutes",15);
   panel.addView(Modal.button(this,"За "+lead+" мин до начала",()->{
    dialog.dismiss();int[] values={5,10,15,30,60};int selected=2;
    for(int i=0;i<values.length;i++)if(values[i]==lead)selected=i;
    Modal.choice(this,"Напоминание",new String[]{"За 5 минут","За 10 минут","За 15 минут","За 30 минут","За час"},selected,i->{
     prefs.edit().putInt("reminder_minutes",values[i]).apply();ReminderReceiver.schedule(this);showNotifications();
    });
   }));
   notificationSwitch(panel,"Уведомлять о заменах","change_notices");
   if(!AppNotifications.permitted(this))panel.addView(Modal.button(this,"Разрешить уведомления",this::notifications));
   if(Build.VERSION.SDK_INT>=31&&!ReminderReceiver.exact(this))panel.addView(Modal.button(this,"Разрешить точные напоминания",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))));
  });
 }
 private void notificationSwitch(LinearLayout panel,String title,String key){
  Switch control=new Switch(this);control.setText(title);control.setTextColor(UiTheme.ink(this));control.setMinHeight(dp(56));
  control.setPadding(dp(10),0,dp(10),0);control.setBackground(AppDesign.panel(this,UiTheme.card(this),17));
  android.content.res.ColorStateList thumb=new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{UiTheme.accent(this),UiTheme.muted(this)});
  control.setThumbTintList(thumb);UiMotion.press(control);control.setChecked(prefs.getBoolean(key,true));
  control.setOnCheckedChangeListener((button,on)->{
   prefs.edit().putBoolean(key,on).apply();if(on)notifications();ReminderReceiver.schedule(this);
  });panel.addView(control);AppDesign.space(panel,6);
 }
 private void showAdvanced(){
  Modal.show(this,"Дополнительно",(panel,dialog)->{
   panel.addView(Modal.button(this,"Ссылка на замены",()->{dialog.dismiss();editSource();}));
   panel.addView(Modal.button(this,"Запасная прямая ссылка",()->{dialog.dismiss();editBackup();}));
   panel.addView(Modal.button(this,"Фон и экономия заряда",()->{dialog.dismiss();batteryHelp();}));
  });
 }
 private void showAbout(){
  Modal.show(this,"О приложении",(panel,dialog)->{
   int[] taps={0};
   panel.addView(Modal.button(this,"Расписание УлГТУ · 0.10.4",()->{
    if(++taps[0]==7){dialog.dismiss();diagnostics();}
   }));
   panel.addView(text("Расписание и планы · УлГТУ\nЗамены · Яндекс Диск\nКарта · OpenStreetMap\nПешие маршруты · FOSSGIS",15,false));
   panel.addView(Modal.button(this,"Политика конфиденциальности",()->{
    dialog.dismiss();Modal.show(this,"Конфиденциальность",(body,window)->{
     String policy;
     try(java.io.InputStream in=getAssets().open("privacy.txt");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
      byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;)bytes.write(buffer,0,n);
      policy=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
     }catch(java.io.IOException unavailable){policy="Текст недоступен";}
     body.addView(text(policy,14,false));
    });
   }));
  });
 }
 private EditText input(String hint,int type){
  EditText field=new EditText(this);field.setHint(hint);field.setSingleLine(true);field.setInputType(type);
  field.setBackground(AppDesign.panel(this,UiTheme.card(this),16));field.setPadding(dp(12),dp(10),dp(12),dp(10));field.setMinHeight(dp(52));field.setTextColor(UiTheme.ink(this));field.setHintTextColor(UiTheme.muted(this));field.setSaveEnabled(false);
  field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);return field;
 }
 private void configureLogin(){
  Modal.show(this,"Автоматический вход",(panel,dialog)->{
   EditText login=input("Логин УлГТУ",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
   EditText password=input("Пароль",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
   panel.addView(login);AppDesign.space(panel,8);panel.addView(password);
   CheckBox show=new CheckBox(this);show.setText("Показать пароль");show.setTextColor(UiTheme.ink(this));
   show.setOnCheckedChangeListener((view,checked)->{password.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());password.setSelection(password.length());});
   panel.addView(show);
   Modal.actions(this,panel,null,null,"Отмена",dialog::dismiss,"Сохранить",()->{
    String user=login.getText().toString().trim(),secret=password.getText().toString();
    if(user.isEmpty()||secret.isEmpty()){password.setError("Введи логин и пароль");return;}
    login.getText().clear();password.getText().clear();dialog.dismiss();
    Modal.show(this,"Проверка входа",(status,progress)->{
     status.addView(text("Открываем официальный сайт…",14,false));
     new Thread(()->{
      String result;boolean visible=false;
      try{new Repository(this).saveAndLogin(user,secret);result="Вход выполнен";}
      catch(WebLogin.Failure failure){result=failure.getMessage();visible=failure.interactive;}
      catch(java.net.SocketTimeoutException failure){result="Таймаут";}
      catch(java.io.IOException failure){result="Нет сети или сайт недоступен";}
      catch(Exception failure){result="Не удалось сохранить данные входа";}
      String message=result;boolean fallback=visible;
      runOnUiThread(()->{
       if(isFinishing())return;
       progress.dismiss();render();
       if(fallback)startActivityForResult(new Intent(this,AuthActivity.class).putExtra("auto_fallback",true).putExtra("initial_status",message),606);
       else Modal.show(this,"Проверка входа",(body,window)->{body.addView(text(message,15,false));Modal.actions(this,body,null,null,null,null,"Закрыть",window::dismiss);});
      });
     },"ulstu-login").start();
    });
   });
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==606&&result==RESULT_OK)Toast.makeText(this,"Вход выполнен",Toast.LENGTH_SHORT).show();
 }
 private String stamp(long value){
  return value==0?"Нет данных":Instant.ofEpochMilli(value).atZone(Schedule.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm:ss",Locale.forLanguageTag("ru-RU")));
 }
 private void diagnostics(){
  Modal.show(this,"Диагностика",(panel,dialog)->{
   panel.addView(text("Последний вход · "+stamp(prefs.getLong("last_login_at",0)),14,false));
   panel.addView(text("Последний перевход · "+stamp(prefs.getLong("automatic_login_at",0)),14,false));
   panel.addView(text("Последний отказ · "+stamp(prefs.getLong("last_refusal_at",0))+" · "+prefs.getString("last_refusal_reason","Нет данных"),14,false));
   panel.addView(text("Планы · Навигатор УлГТУ. Карта · OpenStreetMap. Маршруты · FOSSGIS.",13,false));
   for(String title:new String[]{"Диагностика групп","Режим отладки автовхода","Сетевой таймлайн карты","Журнал ошибок"}){
    panel.addView(Modal.button(this,title,()->{dialog.dismiss();switch(title){
     case "Диагностика групп":showGroupDiagnostics();break;
     case "Режим отладки автовхода":showLoginDebug();break;
     case "Сетевой таймлайн карты":
      Modal.show(this,title,(body,window)->body.addView(text(prefs.getString("map_network_timeline","Открой карту, чтобы записать таймлайн.")+"\nОбход VPN для карты не выполняется.",14,false)));break;
     default:showJournal();
    }}));
   }
   Modal.actions(this,panel,null,null,null,null,"Закрыть",dialog::dismiss);
  });
 }
 private void showLoginDebug(){
  Modal.show(this,"Режим отладки автовхода",(panel,dialog)->{
   CheckBox toggle=new CheckBox(this);toggle.setText("Записывать шаги");toggle.setTextColor(UiTheme.ink(this));
   toggle.setChecked(prefs.getBoolean("auto_login_debug",false));toggle.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("auto_login_debug",on).apply());
   panel.addView(toggle);TextView steps=text(prefs.getString("auto_login_steps","Записей пока нет"),13,false);panel.addView(steps);
   Modal.actions(this,panel,null,null,"Очистить",()->{prefs.edit().remove("auto_login_steps").apply();steps.setText("Записей пока нет");},"Закрыть",dialog::dismiss);
  });
 }
 private void forgetLogin(){
  new Thread(()->{
   try{new Repository(this).forget(()->{getSystemService(NotificationManager.class).cancel(5504);mainAction("reload");});}
   catch(Exception unavailable){runOnUiThread(()->Toast.makeText(this,"Не удалось удалить данные входа",Toast.LENGTH_LONG).show());}
  },"ulstu-logout").start();
 }
 private void showGroupDiagnostics(){
  Modal.show(this,"Диагностика групп",(panel,dialog)->{
   LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);panel.addView(rows);
   rows.addView(text("Запрашиваем каталог в текущем сеансе…",14,false));
   Modal.actions(this,panel,null,null,null,null,"Закрыть",dialog::dismiss);
   new Thread(()->{
    Repository.GroupDiagnostics result=new Repository(this).diagnoseGroups();
    runOnUiThread(()->{
     if(!dialog.isShowing()||isFinishing())return;
     rows.removeAllViews();
     rows.addView(text("Каталог · "+result.directory,15,true));
     for(Repository.PartDiagnostics part:result.parts){
      rows.addView(text("Часть "+part.number,16,true));
      String status=part.linkCount==0?(result.directory.equals("Пришла форма входа")?"Пришла форма входа":result.directory.startsWith("HTTP 200")?"Ссылки на части не найдены":"Нет данных каталога"):
       (part.responses.contains("HTTP 200")?"Ссылки найдены, групп: "+part.groups.size():"Ссылки найдены")+" · "+String.join(", ",part.responses);
      rows.addView(text(status,14,false));
      Set<String> faculties=new TreeSet<>();
      for(Schedule.Group group:part.groups.values())faculties.add(group.faculty.isEmpty()?"Без факультета":group.faculty);
      if(!faculties.isEmpty())rows.addView(text("Факультеты · "+String.join(", ",faculties),14,false));
      int shown=0;
      for(Schedule.Group group:part.groups.values()){
       if(shown++==3)break;
       rows.addView(text(group.name+" · "+(group.faculty.isEmpty()?"Без факультета":group.faculty),14,false));
      }
     }
    });
   },"ulstu-group-diagnostics").start();
  });
 }
 private void showJournal(){
  Modal.show(this,"Журнал ошибок",(panel,dialog)->{
   TextView entries=text(CrashJournal.read(this),14,false);panel.addView(entries);
   Modal.actions(this,panel,null,null,"Копировать",()->getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Журнал ошибок",entries.getText())),"Закрыть",dialog::dismiss);
  });
 }
 void editSource(){editLink(false);}
 void editBackup(){editLink(true);}
 private void editLink(boolean backup){
  Modal.show(this,backup?"Запасной DOCX":"Ссылка на замены",(panel,dialog)->{
   EditText input=input("HTTPS-ссылка",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
   input.setText(prefs.getString(backup?"changes_backup":"changes_source",backup?"":Schedule.DISK));
   input.setSelection(input.length());panel.addView(input);
   Modal.actions(this,panel,backup?null:"По умолчанию",()->input.setText(Schedule.DISK),"Отмена",dialog::dismiss,"Сохранить",()->{
    String value=input.getText().toString().trim();String path=Uri.parse(value).getPath();
    if((!backup||!value.isEmpty())&&(!Repository.validChangeSource(value)||(backup&&(path==null||!path.toLowerCase(Locale.ROOT).endsWith(".docx"))))){
     input.setError("Проверь ссылку");return;
    }
    dialog.dismiss();
    if(backup){prefs.edit().putString("changes_backup",value).apply();mainAction("reload");}
    else saveSource(value);
   });
  });
 }
 void saveSource(String url){prefs.edit().putString("changes_source",url).apply();mainAction("reload");}
 void batteryHelp(){
  Modal.show(this,"Фоновые проверки",(panel,dialog)->{
   panel.addView(text("Android может отложить проверку при экономии заряда.\n\nXiaomi: в настройках батареи приложения выбери «Без ограничений» и разреши автозапуск, если этот пункт доступен.\n\nHuawei: в разделе «Батарея» открой «Запуск приложений» и разреши работу в фоне.",14,false));
   Modal.actions(this,panel,null,null,"Закрыть",dialog::dismiss,"Настройки приложения",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
  });
 }
 void mainAction(String action){setResult(RESULT_OK,new Intent().putExtra("action",action));finish();}
 void notifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},800);else if(!AppNotifications.permitted(this))startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
 @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);ReminderReceiver.schedule(this);render();}
}
