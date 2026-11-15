package ru.ne1es.timetable;

/** Chooses measured fixes without rounding, averaging or snapping coordinates. */
final class LocationQuality {
 static final long MAX_AGE_MS=12000;
 static final class Fix {
  final double lat,lon;final float accuracy;final long elapsed;final String provider;
  Fix(double lat,double lon,float accuracy,long elapsed,String provider){this.lat=lat;this.lon=lon;this.accuracy=accuracy;this.elapsed=elapsed;this.provider=provider;}
 }
 private Fix pending;
 static boolean valid(Fix f,long now){return f!=null&&Double.isFinite(f.lat)&&Double.isFinite(f.lon)&&Math.abs(f.lat)<=90&&Math.abs(f.lon)<=180&&Float.isFinite(f.accuracy)&&f.accuracy>0&&f.accuracy<=20000&&f.elapsed>0&&now>=f.elapsed&&now-f.elapsed<=MAX_AGE_MS;}
 static double distance(Fix a,Fix b){double r=Math.PI/180,dlat=(b.lat-a.lat)*r,dlon=(b.lon-a.lon)*r,h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(a.lat*r)*Math.cos(b.lat*r)*Math.sin(dlon/2)*Math.sin(dlon/2);return 12742017.6*Math.asin(Math.min(1,Math.sqrt(h)));}
 boolean accept(Fix next,Fix current,long now){
  if(!valid(next,now))return false;
  if(!valid(current,now)){pending=null;return true;}
  if(next.elapsed<current.elapsed||next.elapsed==current.elapsed&&next.accuracy>=current.accuracy)return false;
  double movement=distance(current,next);long gap=next.elapsed-current.elapsed,age=now-current.elapsed;
  // Isolated high-speed jumps need an independent, later confirming sample.
  boolean jump=current.accuracy<=50&&next.accuracy<=50&&movement>300&&gap<2500&&movement/Math.max(.1,gap/1000.0)>150;
  if(jump){boolean confirmed=pending!=null&&next.elapsed-pending.elapsed>=400&&next.elapsed-pending.elapsed<=6000&&distance(pending,next)<=Math.max(25,pending.accuracy+next.accuracy);pending=next;if(!confirmed)return false;}
  else pending=null;
  // A coarse provider must not overwrite a recent precise fix.
  if(age<8000&&next.accuracy>current.accuracy*3+10&&!jump)return false;
  long hold=next.accuracy>current.accuracy*3+10?8000:2500;
  if(age<hold&&next.accuracy>current.accuracy*1.2+1&&movement<=current.accuracy+next.accuracy)return false;
  return true;
 }
}
