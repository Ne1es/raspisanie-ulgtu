package ru.ne1es.timetable;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

final class Modal {
 static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
 static TextView label(Activity activity,String value,int size,boolean strong){
  TextView view=new TextView(activity);
  view.setText(value);view.setTextSize(size);view.setTextColor(UiTheme.ink(activity));
  view.setTypeface(UiTheme.font(activity,strong));
  view.setPadding(0,dp(activity,5),0,dp(activity,5));
  return view;
 }
 static Dialog show(Activity activity,String title,BiConsumer<LinearLayout,Dialog> content){
  Dialog dialog=new Dialog(activity);
  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  LinearLayout panel=new LinearLayout(activity);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(dp(activity,20),dp(activity,16),dp(activity,20),dp(activity,20));
  GradientDrawable surface=new GradientDrawable();
  surface.setColor(UiTheme.surface(activity));surface.setCornerRadius(dp(activity,28));
  panel.setBackground(surface);
  View handle=new View(activity);
  GradientDrawable bar=new GradientDrawable();bar.setColor(UiTheme.outline(activity));bar.setCornerRadius(dp(activity,3));handle.setBackground(bar);
  LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(activity,34),dp(activity,4));
  grip.gravity=Gravity.CENTER_HORIZONTAL;grip.bottomMargin=dp(activity,8);panel.addView(handle,grip);
  TextView heading=label(activity,title,22,true);
  panel.addView(heading);
  content.accept(panel,dialog);
  ScrollView scroll=new ScrollView(activity){
   @Override protected void onMeasure(int width,int height){
    int ceiling=(int)(activity.getResources().getDisplayMetrics().heightPixels*.85);
    int bound=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED?ceiling:Math.min(View.MeasureSpec.getSize(height),ceiling);
    super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(bound,View.MeasureSpec.AT_MOST));
   }
  };scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(panel);
  dialog.setContentView(scroll);
  Window window=dialog.getWindow();
  if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(0);}
  dialog.show();
  if(window!=null)window.setLayout(-1,-2);
  UiMotion.sheet(panel);
  return dialog;
 }
 static Dialog showPage(Activity activity,String title,String subtitle,BiConsumer<LinearLayout,Dialog> content){
  Dialog dialog=new Dialog(activity);
  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
  android.widget.FrameLayout background=AppDesign.background(activity);
  LinearLayout panel=new LinearLayout(activity);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(dp(activity,16),dp(activity,12),dp(activity,16),dp(activity,12));
  LinearLayout toolbar=new LinearLayout(activity);toolbar.setGravity(Gravity.CENTER_VERTICAL);
  android.widget.ImageView back=AppDesign.iconAction(activity,R.drawable.ms_arrow_back,"Назад",dialog::dismiss);
  toolbar.addView(back,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));
  LinearLayout copy=new LinearLayout(activity);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(activity,8),0,0,0);
  copy.addView(label(activity,title,23,true));
  if(!subtitle.isEmpty()){
   TextView caption=label(activity,subtitle,13,false);caption.setTextColor(UiTheme.muted(activity));copy.addView(caption);
  }
  toolbar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  panel.addView(toolbar);AppDesign.space(panel,8);
  content.accept(panel,dialog);
  background.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,-1));
  AppDesign.insets(background);
  dialog.setContentView(background);
  Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawableResource(android.R.color.transparent);
   window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
   window.setStatusBarColor(UiTheme.background(activity));
   window.setNavigationBarColor(UiTheme.background(activity));
  }
  dialog.show();
  if(window!=null)window.setLayout(-1,-1);
  background.setFocusableInTouchMode(true);background.requestFocus();background.requestApplyInsets();
  UiMotion.enter(toolbar,0);
  return dialog;
 }
 static TextView button(Activity activity,String title,Runnable callback){
  TextView view=label(activity,title,13,true);
  view.setGravity(Gravity.CENTER);view.setTextColor(UiTheme.accent(activity));
  view.setMinHeight(dp(activity,48));view.setPadding(dp(activity,4),dp(activity,7),dp(activity,4),dp(activity,7));
  view.setBackground(UiMotion.ripple(UiTheme.card(activity),dp(activity,16),view));
  view.setOnClickListener(v->callback.run());view.setFocusable(true);UiMotion.press(view);
  return view;
 }
 static void row(Activity activity,LinearLayout panel,TextView... views){
  LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
  row.setPadding(0,dp(activity,12),0,0);
  boolean large=activity.getResources().getConfiguration().fontScale>=1.4f;
  for(TextView view:views){
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(large?0:-2,-2,large?1:0);
   params.leftMargin=dp(activity,4);
   row.addView(view,params);
  }
  panel.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }
 static void actions(Activity activity,LinearLayout panel,String a,Runnable first,String b,Runnable second,String c,Runnable third){
  java.util.List<TextView> buttons=new java.util.ArrayList<>();
  if(a!=null)buttons.add(button(activity,a,first));
  if(b!=null)buttons.add(button(activity,b,second));
  if(c!=null)buttons.add(button(activity,c,third));
  row(activity,panel,buttons.toArray(new TextView[0]));
 }
 static void choice(Activity activity,String title,String[] labels,int selected,IntConsumer choose){
  show(activity,title,(panel,dialog)->{
   for(int i=0;i<labels.length;i++){
    final int item=i;
    android.widget.RadioButton option=new android.widget.RadioButton(activity);
    option.setText(labels[i]);option.setTextColor(UiTheme.ink(activity));option.setChecked(i==selected);
    option.setBackground(AppDesign.panel(activity,i==selected?UiTheme.selected(activity):UiTheme.card(activity),16));
    option.setPadding(dp(activity,8),0,dp(activity,8),0);UiMotion.press(option);
    option.setMinHeight(dp(activity,52));option.setOnClickListener(v->{dialog.dismiss();choose.accept(item);});
    panel.addView(option);AppDesign.space(panel,6);
   }
   actions(activity,panel,null,null,null,null,"Отмена",dialog::dismiss);
  });
 }
}
