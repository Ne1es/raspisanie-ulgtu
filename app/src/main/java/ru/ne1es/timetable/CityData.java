package ru.ne1es.timetable;

import android.content.Context;
import org.json.*;
import org.osmdroid.util.GeoPoint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class CityData {
 static class Place {
  final String name,building,detail;
  Place(String name,String building,String detail){this.name=name;this.building=building;this.detail=detail;}
 }
 static final class Building extends Place {
  final GeoPoint point;
  final List<GeoPoint> entrances=new ArrayList<>();
  final boolean approximate,campusArrival;
  Building(String id,JSONObject json)throws JSONException{
   super(json.getString("name"),id,json.getString("address"));
   point=readPoint(json.getJSONArray("point"));
   approximate=json.optBoolean("approximate");
   campusArrival=json.optBoolean("campusArrival");
   JSONArray doors=json.getJSONArray("entrances");
   for(int i=0;i<doors.length();i++)entrances.add(readPoint(doors.getJSONObject(i).getJSONArray("point")));
  }
 }
 final Map<String,Building> buildings=new LinkedHashMap<>();
 final List<Place> rooms=new ArrayList<>();
 static GeoPoint readPoint(JSONArray point)throws JSONException{return new GeoPoint(point.getDouble(0),point.getDouble(1));}
 CityData(Context context)throws IOException,JSONException{
  JSONObject json;
  try(InputStream stream=context.getAssets().open("maps/native-index.json")){
   json=new JSONObject(new String(bytes(stream,1024*1024), StandardCharsets.UTF_8));
  }
  JSONObject all=json.getJSONObject("buildings");
  TreeSet<String> ids=new TreeSet<>();
  Iterator<String> keys=all.keys();while(keys.hasNext())ids.add(keys.next());
  for(String id:ids)buildings.put(id,new Building(id,all.getJSONObject(id)));
  JSONArray list=json.getJSONArray("rooms");
  for(int i=0;i<list.length();i++){
   JSONArray row=list.getJSONArray(i);
   String number=row.getString(0),label=row.getString(1),building=row.getString(2);
   String detail="Корпус "+building+" · этаж "+row.getString(3);
   if(!key(label).equals(key(number)))detail+=" · "+label;
   rooms.add(new Place(number,building,detail));
  }
 }
 static String key(String text){
  return text.toLowerCase(Locale.ROOT).replace('ё','е').replace('а','a').replace('в','b').replace('е','e').replace('к','k').replace('м','m').replace('н','h').replace('о','o').replace('р','p').replace('с','c').replace('т','t').replace('х','x').replaceAll("[\\s\\-–—]+","");
 }
 List<Place> find(String text){
  String term=key(text);
  List<Place> result=new ArrayList<>();
  for(Building building:buildings.values())if(key(building.name).contains(term)||key(building.detail).contains(term))result.add(building);
  if(term.isEmpty())return result;
  ArrayList<Place> matches=new ArrayList<>();
  for(Place room:rooms)if(key(room.name).contains(term)||key(room.detail).contains(term))matches.add(room);
  matches.sort(Comparator.comparingInt(room->key(room.name).equals(term)?0:1));
  result.addAll(matches.subList(0,Math.min(22,matches.size())));return result;
 }
 GeoPoint entrance(Building building,GeoPoint from){
  Building target=building.campusArrival?buildings.get("6"):building;
  GeoPoint best=target.point;double distance=Double.POSITIVE_INFINITY;
  for(GeoPoint door:target.entrances){double d=from.distanceToAsDouble(door);if(d<distance){best=door;distance=d;}}
  return best;
 }
 static byte[] bytes(InputStream in,int limit)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] part=new byte[8192];int n;
  while((n=in.read(part))!=-1){if(out.size()+n>limit)throw new IOException("Response is too large");out.write(part,0,n);}
  return out.toByteArray();
 }
}
