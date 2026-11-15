package ru.ne1es.timetable;

import android.animation.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class UiMotion {
 private static final java.util.WeakHashMap<android.app.Activity,Boolean> navigation=new java.util.WeakHashMap<>();
 static boolean beginNavigation(android.app.Activity activity){
  if(activity.isFinishing()||navigation.containsKey(activity))return false;
  navigation.put(activity,true);
  return true;
 }
 static void resumed(android.app.Activity activity){navigation.remove(activity);}
 static void quiet(android.app.Activity activity){activity.overridePendingTransition(0,0);}
 static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}
 static Drawable ripple(int colour,float radius,View v){GradientDrawable shape=new GradientDrawable();shape.setColor(colour);shape.setCornerRadius(radius);GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(radius);return new RippleDrawable(ColorStateList.valueOf(UiTheme.dark(v.getContext())?0x22d2b4f5:0x2266508a),shape,mask);}
 static void press(View v){if(!enabled())return;StateListAnimator a=new StateListAnimator();ObjectAnimator down=ObjectAnimator.ofPropertyValuesHolder(v,PropertyValuesHolder.ofFloat("scaleX",.975f),PropertyValuesHolder.ofFloat("scaleY",.975f));down.setDuration(100);ObjectAnimator up=ObjectAnimator.ofPropertyValuesHolder(v,PropertyValuesHolder.ofFloat("scaleX",1f),PropertyValuesHolder.ofFloat("scaleY",1f));up.setDuration(200);up.setInterpolator(new DecelerateInterpolator());a.addState(new int[]{android.R.attr.state_pressed,android.R.attr.state_enabled},down);a.addState(new int[]{},up);v.setStateListAnimator(a);}
 static void enter(View v,int delay){if(!enabled())return;float dy=8*v.getResources().getDisplayMetrics().density;v.animate().cancel();v.setAlpha(.8f);v.setTranslationY(dy);v.animate().alpha(1).translationY(0).setDuration(240).setStartDelay(Math.min(delay,150)).setInterpolator(new DecelerateInterpolator()).start();}
 static void forward(android.app.Activity activity){activity.overridePendingTransition(enabled()?R.anim.axis_enter:0,enabled()?R.anim.axis_exit:0);}
 static void back(android.app.Activity activity){activity.overridePendingTransition(enabled()?R.anim.axis_back_enter:0,enabled()?R.anim.axis_back_exit:0);}
 static void sheet(View panel){
  if(!enabled())return;
  panel.animate().cancel();panel.setAlpha(.85f);panel.setTranslationY(18*panel.getResources().getDisplayMetrics().density);
  panel.animate().alpha(1).translationY(0).setDuration(235)
   .setInterpolator(new DecelerateInterpolator()).start();
 }
 static void refreshState(android.widget.ImageView button,boolean loading){
  ObjectAnimator spinning=(ObjectAnimator)button.getTag();
  if(loading&&spinning==null){
   button.setImageResource(R.drawable.ms_refresh);button.setContentDescription("Обновляем расписание");
  }
  if(loading&&spinning==null&&enabled()){
   spinning=ObjectAnimator.ofFloat(button,View.ROTATION,0,360);
   spinning.setInterpolator(new android.view.animation.LinearInterpolator());
   spinning.setDuration(850);spinning.setRepeatCount(ValueAnimator.INFINITE);
   button.setTag(spinning);spinning.start();
  }else if(!loading&&spinning!=null){
   spinning.cancel();button.setTag(null);button.setRotation(0);
  }
  if(!loading&&"Обновляем расписание".contentEquals(button.getContentDescription()))button.setContentDescription("Обновить расписание");
  button.setEnabled(!loading);
 }
 static void refreshSuccess(android.widget.ImageView button){
  button.setImageResource(R.drawable.ms_check_circle);
  button.setContentDescription("Расписание обновлено");
  if(enabled()){
   button.setScaleX(.82f);button.setScaleY(.82f);
   button.animate().scaleX(1).scaleY(1).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
  }
  button.postDelayed(()->{
   if(button.getTag()!=null)return;
   button.setImageResource(R.drawable.ms_refresh);button.setContentDescription("Обновить расписание");
  },1000);
 }
 static final class RefreshScroll extends android.widget.ScrollView {
  private final Runnable refresh;
  private float startY,startX;
  private boolean startedAtTop;
  RefreshScroll(android.content.Context context,Runnable refresh){super(context);this.refresh=refresh;}
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
   switch(event.getActionMasked()){
    case android.view.MotionEvent.ACTION_DOWN:
     startY=event.getY();startX=event.getX();startedAtTop=getScrollY()==0;break;
    case android.view.MotionEvent.ACTION_MOVE:
     float pull=event.getY()-startY;
     if(startedAtTop&&getScrollY()==0&&pull>0&&pull>Math.abs(event.getX()-startX)*1.5f&&enabled()&&getChildCount()>0){
      getChildAt(0).setTranslationY(Math.min(pull*.23f,AppDesign.dp(getContext(),32)));
     }
     break;
    case android.view.MotionEvent.ACTION_POINTER_DOWN:
    case android.view.MotionEvent.ACTION_CANCEL:startedAtTop=false;settle();break;
    case android.view.MotionEvent.ACTION_UP:
     boolean refreshNow=startedAtTop&&getScrollY()==0&&event.getY()-startY>96*getResources().getDisplayMetrics().density
      &&event.getY()-startY>Math.abs(event.getX()-startX)*1.5f;
     startedAtTop=false;settle();
     if(refreshNow){
      android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);
      cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
      performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);refresh.run();return true;
     }
     break;
    default:break;
   }
   return super.dispatchTouchEvent(event);
  }
  private void settle(){
   if(getChildCount()==0)return;
   View content=getChildAt(0);
   if(enabled()&&content.getTranslationY()!=0)content.animate().translationY(0).setDuration(225)
    .setInterpolator(new android.view.animation.OvershootInterpolator(.6f)).start();
   else content.setTranslationY(0);
  }
 }
}
