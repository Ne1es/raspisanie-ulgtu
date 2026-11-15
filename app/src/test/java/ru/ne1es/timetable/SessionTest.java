package ru.ne1es.timetable;
import java.nio.file.*;
public final class SessionTest {
 static int n;static void check(boolean ok,String m){n++;if(!ok)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  String base="tests/fixtures/";String schedule=new String(Files.readAllBytes(Paths.get(base+"schedule.html")),"UTF-8"),catalog=new String(Files.readAllBytes(Paths.get(base+"index.html")),"UTF-8");
  check(SourceSession.confirmedTimetable(schedule),"Official timetable recognised");check(SourceSession.confirmedTimetable(catalog),"Official group catalog recognised");check(!SourceSession.loginPage(schedule),"Timetable is authenticated content");check(!SourceSession.loginPage(catalog),"Catalog is authenticated content");
  for(String page:new String[]{schedule,catalog})for(String form:new String[]{"<form><input name='login'></form>","<form style='display:none'><input type='password'></form>","<nav><a href='?q=auth'>Войти</a></nav>","<form><input name='login'><input type='password'></form>"})check(!SourceSession.loginPage(page+form),"Schedule/catalog never misclassified by login UI");
  check(SourceSession.loginPage("<h1>Вход в личный кабинет</h1><form><input name='username'><input type='password'></form>"),"Actual password gate recognised");check(SourceSession.loginPage("<form><input TYPE='PASSWORD'></form>"),"Uppercase HTML attribute recognised");check(!SourceSession.loginPage("<h1>Расписание временно недоступно</h1><input name='login'>"),"Unavailable source is not expired session");check(!SourceSession.loginPage("<h1>Ошибка 503</h1>"),"Server failure is not expired session");
  check(!SourceSession.loginPage("<input type='hidden' name='login'><a href='auth'>вход</a>"),"Login metadata alone insufficient");check(!SourceSession.loginPage("<div hidden><input type='password'></div>"),"Hidden password widget not a gate");check(!SourceSession.loginPage("<div aria-hidden='true'><input type='password'></div>"),"Aria-hidden widget not a gate");check(!SourceSession.loginPage("<div style='visibility: hidden'><input type='password'></div>"),"Invisible password not a gate");check(!SourceSession.loginPage("<form><input name='login' value=''></form>"),"A field named login does not prove logout");
  System.out.println("PASS "+n+" session classification checks");
 }
}
