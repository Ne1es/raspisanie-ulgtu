package ru.ne1es.timetable;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.osmdroid.util.GeoPoint;

final class CityWalk {
 final List<GeoPoint> points;
 final double meters,seconds;
 private CityWalk(List<GeoPoint> points,double meters,double seconds){this.points=points;this.meters=meters;this.seconds=seconds;}
 String label(){
  String distance=meters<1000?Math.round(meters)+" м":String.format(Locale.forLanguageTag("ru"),"%.1f км",meters/1000);
  return Math.max(1,(long)Math.ceil(seconds/60))+" мин · "+distance;
 }
 static CityWalk get(GeoPoint start,GeoPoint end)throws IOException,JSONException{
  String coordinates=start.getLongitude()+","+start.getLatitude()+";"+end.getLongitude()+","+end.getLatitude();
  HttpURLConnection connection=(HttpURLConnection)new URL("https://routing.openstreetmap.de/routed-foot/route/v1/foot/"+coordinates+"?overview=simplified&geometries=geojson").openConnection();
  connection.setConnectTimeout(7000);connection.setReadTimeout(9000);
  connection.setRequestProperty("User-Agent","UlstuTimetable/0.10.4 (ru.ne1es.timetable)");
  try(InputStream stream=connection.getInputStream()){
   return parse(new JSONObject(new String(CityData.bytes(stream,1024*1024),StandardCharsets.UTF_8)));
  }finally{connection.disconnect();}
 }
 static CityWalk parse(JSONObject response)throws IOException,JSONException{
  if(!"Ok".equals(response.optString("code")))throw new IOException("No walking route");
  JSONObject route=response.getJSONArray("routes").getJSONObject(0);
  JSONObject geometry=route.getJSONObject("geometry");
  if(!"LineString".equals(geometry.optString("type")))throw new IOException("Invalid route geometry");
  JSONArray coordinates=geometry.getJSONArray("coordinates");
  if(coordinates.length()<2)throw new IOException("No route points");
  List<GeoPoint> points=new ArrayList<>();
  for(int i=0;i<coordinates.length();i++){
   JSONArray point=coordinates.getJSONArray(i);double lon=point.getDouble(0),lat=point.getDouble(1);
   if(!Double.isFinite(lon)||!Double.isFinite(lat)||Math.abs(lon)>180||Math.abs(lat)>90)throw new IOException("Invalid coordinate");
   points.add(new GeoPoint(lat,lon));
  }
  double meters=route.getDouble("distance"),seconds=route.getDouble("duration");
  if(!Double.isFinite(meters)||!Double.isFinite(seconds)||meters<0||seconds<0)throw new IOException("Invalid route length");
  return new CityWalk(points,meters,seconds);
 }
}
