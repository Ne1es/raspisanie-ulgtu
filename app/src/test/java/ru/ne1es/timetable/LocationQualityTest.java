package ru.ne1es.timetable;
public final class LocationQualityTest {
 static int n;static void check(boolean ok,String what){n++;if(!ok)throw new AssertionError(what);}
 static LocationQuality.Fix f(double lat,double lon,float a,long stamp){return new LocationQuality.Fix(lat,lon,a,stamp,"gps");}
 public static void main(String[] args){
  long now=100000;LocationQuality q=new LocationQuality();LocationQuality.Fix good=f(54.3304123456,48.3960123456,8,now-100);
  check(q.accept(good,null,now),"Initial measured fix accepted");check(LocationQuality.valid(good,now),"Fresh exact coordinates valid");
  for(double lat:new double[]{Double.NaN,Double.POSITIVE_INFINITY,91,-91})check(!q.accept(f(lat,48,5,now),good,now),"Invalid latitude rejected");
  for(double lon:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,181,-181})check(!q.accept(f(54,lon,5,now),good,now),"Invalid longitude rejected");
  for(float a:new float[]{0,-3,Float.NaN,Float.POSITIVE_INFINITY,25000})check(!q.accept(f(54,48,a,now),good,now),"Unknown or invalid accuracy rejected");
  check(!q.accept(f(54,48,4,now-12001),good,now),"Old fixes rejected");check(!q.accept(f(54,48,4,now+1),good,now),"Future timestamp rejected");check(!q.accept(f(54,48,4,0),good,now),"Missing elapsed timestamp rejected");
  check(!q.accept(f(good.lat,good.lon,5,now-101),good,now),"Out of order does not rewind position");check(!q.accept(f(good.lat,good.lon,8,good.elapsed),good,now),"Identical cached fix ignored");check(q.accept(f(good.lat,good.lon,5,good.elapsed),good,now),"More precise measurement with equal timestamp accepted");
  check(!q.accept(f(good.lat+.00001,good.lon,500,now),good,now),"Coarse provider cannot overwrite precise GPS");check(!q.accept(f(good.lat+.025,good.lon,900,now),good,now),"Distant coarse network fix cannot teleport precise GPS");check(!q.accept(f(good.lat+.00001,good.lon,31,now),f(good.lat,good.lon,22,now-100),now),"Recent 22m fix preferred over noisy 31m sample");check(q.accept(f(good.lat+.00001,good.lon,6,now),good,now),"Improved accuracy delivered immediately");
  check(q.accept(f(good.lat+.0001,good.lon,8,now),good,now),"Normal walking movement delivered");check(q.accept(f(good.lat+.00001,good.lon,500,now+9000),good,now+9000),"Fresh coarse fix available after precise provider is lost");check(q.accept(f(good.lat,good.lon,30,now+13000),good,now+13000),"Fresh position replaces expired position");
  LocationQuality jumps=new LocationQuality();LocationQuality.Fix jump=f(54.351661,48.389391,5,now+200);check(!jumps.accept(jump,good,now+200),"Single teleport rejected");check(!jumps.accept(jump,good,now+250),"Duplicate teleport does not count as confirmation");check(jumps.accept(f(jump.lat+.00001,jump.lon,5,now+800),good,now+800),"Consistent later sample restores position without permanent lock");
  LocationQuality isolated=new LocationQuality();check(!isolated.accept(jump,good,now+200),"Isolated jump held");check(isolated.accept(f(good.lat+.00001,good.lon,5,now+900),good,now+900),"Return to real path clears isolated outlier");
  check(LocationQuality.distance(good,good)==0,"Zero distance");double metres=LocationQuality.distance(good,f(good.lat+.001,good.lon,5,now));check(metres>110&&metres<112,"Metric distance at campus latitude");
  System.out.println("PASS "+n+" GPS quality checks");
 }
}
