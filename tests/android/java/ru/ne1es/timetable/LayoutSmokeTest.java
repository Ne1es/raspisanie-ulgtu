package ru.ne1es.timetable;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.content.*;
import android.webkit.WebView;
import java.io.*;
import java.util.*;
import java.time.*;
import java.lang.reflect.*;
import org.jsoup.Jsoup;

public final class LayoutSmokeTest extends Instrumentation {
 int checks;Throwable uiFailure;boolean reopenOnly;
 void ui(Runnable action){uiFailure=null;runOnMainSync(()->{try{action.run();}catch(Throwable e){uiFailure=e;}});if(uiFailure!=null)throw new RuntimeException(uiFailure);}
 void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 String asset(String name)throws Exception{try(InputStream in=getContext().getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);return bytes.toString("UTF-8");}}
 byte[] assetBytes(String name)throws Exception{try(InputStream in=getContext().getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);return bytes.toByteArray();}}
 Object get(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
 void set(MainActivity a,String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
 void call(MainActivity a,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
 void ready(MainActivity a,FixtureRepository r){ui(()->{try{set(a,"generation",((Integer)get(a,"generation"))+1);set(a,"loading",false);set(a,"repo",r);LocalDate day=LocalDate.of(2026,11,3);set(a,"selected",day);set(a,"week",day.with(DayOfWeek.MONDAY));call(a,"showDays");call(a,"showLessons");}catch(Exception e){throw new RuntimeException(e);}});}
 void refresh(MainActivity a)throws Exception{ui(()->{try{call(a,"refresh");}catch(Exception e){throw new RuntimeException(e);}});for(int i=0;i<180;i++){final boolean[] pending={true};ui(()->{try{pending[0]=(Boolean)get(a,"loading");}catch(Exception e){throw new RuntimeException(e);}});if(!pending[0])return;Thread.sleep(500);}throw new AssertionError("Refresh timed out");}
 void count(MainActivity a,int n,String message){ui(()->check(((LinearLayout)a.findViewById(R.id.lesson_list)).getChildCount()==n,message));}
 void day(MainActivity a,int index){ui(()->((LinearLayout)a.findViewById(R.id.week_strip)).getChildAt(index).performClick());}
 String screenshot(Activity activity)throws Exception{View root=activity.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280);Bitmap b=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);}
 final class FixtureRepository extends Repository {
  byte[] index,html,doc;boolean failed;int loads;
  FixtureRepository()throws Exception{super(getTargetContext());index=asset("index.html").getBytes("UTF-8");html=Jsoup.parse(asset("schedule.html")).outerHtml().getBytes("UTF-8");doc=assetBytes("changes.docx");}
  @Override byte[] request(String address,boolean cookies,int limit)throws Exception{if(failed)throw new IOException("Test network failure");loads++;return address.equals(Schedule.INDEX)?index:html;}
  @Override Schedule.Changes changes(Schedule.Data d,Schedule.Changes prior)throws Exception{return Schedule.parseDocx(doc,"03.11.26.docx","Пдоп-35",d);}
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);reopenOnly=args!=null&&"true".equals(args.getString("reopen_only"));start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
   if(reopenOnly){Repository saved=new Repository(getTargetContext());Schedule.Data restored=saved.cached("Пдоп-35");check(restored!=null&&restored.lessons.size()==47,"47 entries recovered after complete process stop");MainActivity reopened=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready(reopened,new FixtureRepository());count(reopened,4,"Tuesday visible after complete process restart without reseeding cache");result.putString("stream","PASS "+checks+" complete process restart checks");finish(0,result);return;}

   getTargetContext().getSharedPreferences("timetable",0).edit().putString("group","Пдоп-35").putInt("subgroup",2).putBoolean("has_session",false).commit();
   Schedule.Data data=Schedule.parse(asset("schedule.html"),"Пдоп-35",Schedule.INDEX.replace("raspisan.html","322.html"));Repository repo=new Repository(getTargetContext());repo.saveData(data);
   MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));FixtureRepository fixture=new FixtureRepository();ready(activity,fixture);
   ui(()->{View decor=activity.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(720,1073741824),View.MeasureSpec.makeMeasureSpec(1280,1073741824));decor.layout(0,0,720,1280);LinearLayout strip=activity.findViewById(R.id.week_strip);check(strip.getChildCount()==7,"Seven day tiles");int edge=0;for(int i=0;i<7;i++){View tile=strip.getChildAt(i);check(tile.getWidth()>0,"Visible day "+i);check(tile.getRight()<=strip.getWidth(),"Day inside screen "+i);TextView label=(TextView)((LinearLayout)tile).getChildAt(0);check((label.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL,"Centred day "+i);edge=tile.getRight();}check(edge<=strip.getWidth(),"Sunday fully visible");});
   count(activity,4,"Tuesday initially shows four cards");
   for(int i=0;i<3;i++){day(activity,6);count(activity,1,"Sunday only has empty-state container");day(activity,1);count(activity,4,"Returning from empty Sunday retains Tuesday");day(activity,5);count(activity,4,"Saturday classes retained");day(activity,1);}
   refresh(activity);count(activity,4,"Refresh through actual Repository HTML parsing preserves Tuesday");check(fixture.loads>=2,"Refresh read catalog and schedule");check(repo.cached("Пдоп-35").lessons.size()==47,"All 47 entries persisted after refresh");
   day(activity,6);refresh(activity);day(activity,1);count(activity,4,"Refresh while empty Sunday selected preserves other days");
   fixture.failed=true;refresh(activity);count(activity,4,"Network failure preserves Tuesday");check(repo.cached("Пдоп-35").lessons.size()==47,"Network failure preserves persisted cache");
   fixture.failed=false;fixture.html="<p>Пдоп-35</p><table><tr><td>Время</td><td>08:30–09:50</td></tr><tr><td>03.11.2026</td><td></td></tr></table>".getBytes("UTF-8");refresh(activity);count(activity,4,"All-empty source response cannot erase known lessons");check(repo.cached("Пдоп-35").lessons.size()==47,"Rejected empty result not written to cache");
   ui(()->activity.finish());
   MainActivity reopened=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));FixtureRepository fresh=new FixtureRepository();ready(reopened,fresh);count(reopened,4,"App restart recovers last confirmed Tuesday");refresh(reopened);count(reopened,4,"Refresh after restart succeeds");
   ui(()->{try{result.putString("main_png",screenshot(reopened));}catch(Exception e){throw new RuntimeException(e);}});
   AuthActivity auth=(AuthActivity)startActivitySync(new Intent(getTargetContext(),AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->{try{View decor=auth.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(720,1073741824),View.MeasureSpec.makeMeasureSpec(1280,1073741824));decor.layout(0,0,720,1280);WebView w=auth.findViewById(R.id.auth_web);check(w.getWidth()>500,"Auth browser width");check(w.getHeight()>500,"Auth browser has visible height");check(w.getBottom()>w.getTop(),"Auth browser not collapsed");result.putString("auth_png",screenshot(auth));}catch(Exception e){throw new RuntimeException(e);}});
   result.putString("stream","PASS "+checks+" Android layout/refresh/restart checks");finish(0,result);
  }catch(Throwable e){result.putString("stream","FAIL "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));finish(1,result);}}
}
