package ru.ne1es.timetable;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppDesign {
 static int dp(Context context,float dp){return Math.round(dp*context.getResources().getDisplayMetrics().density);}
 static GradientDrawable panel(Context context,int color,int radius){
  GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);
  drawable.setCornerRadius(dp(context,radius));drawable.setStroke(dp(context,1),UiTheme.outline(context));
  return drawable;
 }
 static RippleDrawable primary(View view){
  Context context=view.getContext();
  int accent=UiTheme.accent(context);
  int left=UiTheme.mix(accent,0xff341252,UiTheme.dark(context)?.42f:.8f);
  int right=UiTheme.mix(accent,0xff3b1454,UiTheme.dark(context)?.48f:.94f);
  GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{left,right});
  gradient.setCornerRadius(dp(context,24));
  return new RippleDrawable(ColorStateList.valueOf(0x42ffffff),gradient,null);
 }
 static void emphasize(TextView view){view.setBackground(primary(view));view.setTextColor(0xffffffff);}
 static TextView text(Context context,String value,int size,boolean strong){
  TextView view=new TextView(context);view.setText(value);view.setTextSize(size);
  view.setTypeface(UiTheme.font(context,strong));view.setTextColor(UiTheme.ink(context));return view;
 }
 static ImageView icon(Context context,int asset,int tint){
  ImageView view=new ImageView(context);view.setImageResource(asset);view.setColorFilter(tint);
  view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);return view;
 }
 static ImageView iconAction(Context context,int asset,String title,Runnable action){
  ImageView button=icon(context,asset,UiTheme.accent(context));
  int padding=dp(context,12);
  button.setPadding(padding,padding,padding,padding);
  button.setMinimumWidth(dp(context,48));button.setMinimumHeight(dp(context,48));
  button.setContentDescription(title);button.setFocusable(true);
  button.setBackground(UiMotion.ripple(UiTheme.card(context),dp(context,16),button));
  button.setOnClickListener(v->action.run());UiMotion.press(button);
  return button;
 }
 static void space(LinearLayout parent,int amount){parent.addView(new View(parent.getContext()),new LinearLayout.LayoutParams(1,dp(parent.getContext(),amount)));}
 static FrameLayout background(Context context){
  return new FrameLayout(context){
   private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
   {setWillNotDraw(false);}
   @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
    if(w>0)paint.setShader(new RadialGradient(w*.64f,0,w*1.22f,
     UiTheme.dark(context)?new int[]{0xff321052,0xff1a102b,UiTheme.background(context)}:new int[]{0xffe5d4fb,0xfff0e8fa,UiTheme.background(context)},
     new float[]{0,.43f,1},Shader.TileMode.CLAMP));
   }
   @Override protected void onDraw(Canvas canvas){
    canvas.drawColor(UiTheme.background(context));canvas.drawRect(0,0,getWidth(),getHeight(),paint);
   }
  };
 }
 static void insets(View view){
  view.setOnApplyWindowInsetsListener((v,insets)->{
   if(android.os.Build.VERSION.SDK_INT>=30){
    int types=WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout();
    if(android.os.Build.VERSION.SDK_INT>=35)types|=WindowInsets.Type.ime();
    android.graphics.Insets bars=insets.getInsets(types);
    v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
   }else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
   return insets;
  });
 }
 static LinearLayout scaffold(Activity activity,View body,int active,java.util.function.IntConsumer select){
  LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
  column.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  FrameLayout nav=new FrameLayout(activity);
  nav.setPadding(dp(activity,5),dp(activity,5),dp(activity,5),dp(activity,5));
  nav.setBackground(panel(activity,UiTheme.surface(activity),32));
  nav.setElevation(dp(activity,4));
  LinearLayout items=new LinearLayout(activity);nav.addView(items,new FrameLayout.LayoutParams(-1,-2));
  String[] titles={"Расписание","Карта","Настройки"};
  int[] assets={R.drawable.ms_calendar_month,R.drawable.ms_location_on,R.drawable.ms_settings};
  for(int i=0;i<3;i++){
   final int tab=i;
   LinearLayout item=new LinearLayout(activity);item.setGravity(Gravity.CENTER);item.setOrientation(LinearLayout.VERTICAL);item.setMinimumHeight(dp(activity,54));
   item.setPadding(dp(activity,2),dp(activity,4),dp(activity,2),dp(activity,4));
   boolean compact=activity.getResources().getConfiguration().fontScale>=1.35f;
   int tint=i==active?UiTheme.selectedText(activity):UiTheme.muted(activity);
   ImageView symbol=icon(activity,assets[i],tint);
   item.addView(symbol,new LinearLayout.LayoutParams(dp(activity,21),dp(activity,21)));
   if(!compact){
    TextView label=text(activity,titles[i],10,i==active);label.setTextColor(tint);label.setMaxLines(1);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(activity,2);item.addView(label,lp);
   }
   item.setBackground(UiMotion.ripple(i==active?UiTheme.selected(activity):0,dp(activity,27),item));
   item.setContentDescription(titles[i]);item.setSelected(i==active);item.setFocusable(true);
   item.setOnClickListener(v->{
    if(active==tab)return;
    item.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    select.accept(tab);
   });
   UiMotion.press(item);items.addView(item,new LinearLayout.LayoutParams(0,-2,1));
  }
  LinearLayout.LayoutParams navBounds=new LinearLayout.LayoutParams(-1,-2);
  navBounds.setMargins(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,10));column.addView(nav,navBounds);
  return column;
 }
 static void switchFromChild(Activity activity,int tab){
  if(!UiMotion.beginNavigation(activity))return;
  activity.startActivity(new Intent(activity,MainActivity.class)
   .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
   .putExtra("destination_tab",tab));
  UiMotion.quiet(activity);
 }
}
