package ru.ne1es.timetable;
import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;

final class UiTheme {
 static boolean dark(Context c){String mode=c.getSharedPreferences("timetable",0).getString("theme","system");return mode.equals("dark")||(mode.equals("system")||mode.equals("dynamic"))&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
 static void apply(Activity a){a.setTheme(dark(a)?R.style.AppThemeDark:R.style.AppTheme);}
 static int ink(Context c){return dark(c)?0xfffaf3ff:0xff211831;}
 static int background(Context c){return dark(c)?0xff0d0a13:0xfff7f3fc;}
 static int card(Context c){return dark(c)?0xff211b2b:0xffeee8f7;}
 static int muted(Context c){return dark(c)?0xffcbc1d6:0xff5a4b69;}
 private static final int[] LIGHT={0xff6c36ad,0xff215d9e,0xff0b6b50,0xffa7296d};
 private static final int[] DARK={0xffd3a4ff,0xffa0cbff,0xff8be2bb,0xffffa6d4};
 static int colorChoice(Context c){return Math.max(0,Math.min(3,c.getSharedPreferences("timetable",0).getInt("accent_choice",0)));}
 static int swatch(Context c,int choice){return (dark(c)?DARK:LIGHT)[choice];}
 static int accent(Context c){
  if(android.os.Build.VERSION.SDK_INT>=31&&c.getSharedPreferences("timetable",0).getString("theme","").equals("dynamic"))
   return c.getColor(dark(c)?android.R.color.system_accent1_200:android.R.color.system_accent1_800);
  return swatch(c,colorChoice(c));
 }
 static int surface(Context c){return dark(c)?0xff191520:0xfffcf9ff;}
 static int selected(Context c){return dark(c)?mix(accent(c),background(c),.35f):mix(accent(c),background(c),.16f);}
 static int selectedText(Context c){return dark(c)?0xfffbf4ff:mix(accent(c),0xff20132a,.70f);}
 static int progress(Context c){return dark(c)?mix(accent(c),0xff55219b,.75f):accent(c);}
 static int mix(int foreground,int background,float weight){
  return Color.rgb(Math.round(Color.red(foreground)*weight+Color.red(background)*(1-weight)),
   Math.round(Color.green(foreground)*weight+Color.green(background)*(1-weight)),
   Math.round(Color.blue(foreground)*weight+Color.blue(background)*(1-weight)));
 }
 static int outline(Context c){return dark(c)?0xff453554:0xffe0d4ee;}
 private static android.graphics.Typeface regular,strong;
 static synchronized android.graphics.Typeface font(Context c,boolean bold){
  if(regular==null){
   android.graphics.Typeface face=c.getResources().getFont(R.font.golos_text);
   regular=android.os.Build.VERSION.SDK_INT>=28?android.graphics.Typeface.create(face,400,false):face;
   strong=android.os.Build.VERSION.SDK_INT>=28?android.graphics.Typeface.create(face,620,false):android.graphics.Typeface.create(face,android.graphics.Typeface.BOLD);
  }
  return bold?strong:regular;
 }
 static void bars(Activity a){a.getWindow().setStatusBarColor(background(a));a.getWindow().setNavigationBarColor(background(a));a.getWindow().getDecorView().setSystemUiVisibility(dark(a)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
}
