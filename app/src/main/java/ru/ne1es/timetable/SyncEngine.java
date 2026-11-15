package ru.ne1es.timetable;
import android.content.*;
import java.time.*;
import java.util.*;

/** Called from a system job, independent of Activity and ChatGPT. */
final class SyncEngine {
 static final Object LOCK=new Object();
 static final class Result {Schedule.Data data;Schedule.Changes changes;List<String> differences=new ArrayList<>();String issue="";}
 static Result run(Context c,Repository repo,java.util.function.BooleanSupplier active)throws Exception{synchronized(LOCK){
  if(!active.getAsBoolean())throw new InterruptedException();
  android.content.SharedPreferences prefs=ReminderReceiver.prefs(c);String group=prefs.getString("group","");if(group.isEmpty())throw new IllegalStateException("Сначала выбери группу");int subgroup=prefs.getInt("subgroup",2);
  prefs.edit().putLong("background_attempt",System.currentTimeMillis()).apply();Schedule.Data before=repo.cached(group);Schedule.Changes old=repo.cachedChanges(group);Schedule.Group entry=null;for(Schedule.Group g:repo.cachedGroups())if(Schedule.norm(g.name).equals(Schedule.norm(group)))entry=g;if(entry==null)entry=new Schedule.Group(group,Schedule.INDEX);
  Result r=new Result();Exception portalError=null;
  try{r.data=repo.load(entry);Schedule.validateReplacement(before,r.data);}
  catch(Exception unavailable){if(before==null)throw unavailable;r.data=before;portalError=unavailable;}
  r.changes=old;
  try{r.changes=repo.changes(r.data,repo.cachedRemoteChanges(group));if(!r.changes.warnings.isEmpty())r.issue="Источник замен: "+String.join("; ",r.changes.warnings);}catch(Exception e){r.issue="Источник замен: "+human(e);}
  if(Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new InterruptedException();
  if(!Schedule.norm(prefs.getString("group","")).equals(Schedule.norm(group))||prefs.getInt("subgroup",2)!=subgroup||!prefs.getString("changes_source",Schedule.DISK).equals(repo.changeSource()))return r;
  if(portalError==null)repo.saveData(r.data);if(r.changes!=old)repo.saveChanges(group,r.changes);r.changes=repo.cachedChanges(group);r.differences=ScheduleLogic.diff(before,old,r.data,r.changes,subgroup,LocalDate.now(Schedule.ZONE));prefs.edit().putLong("background_checked",System.currentTimeMillis()).putString("background_error",r.issue).putBoolean("background_auth_required",false).apply();
  if(!r.differences.isEmpty()){String body=String.join("\n",r.differences.subList(0,Math.min(5,r.differences.size())));if(r.differences.size()>5)body+="\nИ ещё изменений: "+(r.differences.size()-5);AppNotifications.show(c,"changes",5502,"Расписание · "+group+" · изменений: "+r.differences.size(),body,null);}
  if(portalError!=null)throw portalError;
  if(!r.issue.isEmpty())AppNotifications.issue(c,r.issue);else prefs.edit().remove("last_notified_issue").apply();ReminderReceiver.schedule(c);return r;
 }}
 static String human(Exception e){return e.getMessage()==null?"Нет соединения с источником":e.getMessage();}
}
