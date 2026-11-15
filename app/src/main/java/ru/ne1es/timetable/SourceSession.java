package ru.ne1es.timetable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.Locale;

/** Classifies the content, never a login link, a query substring or a hidden field. */
final class SourceSession {
 static boolean timetable(Document page){
  for(Element table:page.select("table")){
   String text=table.text();
   if(text.matches("(?s).*\\b\\d{1,2}:\\d{2}\\s*[–—-]\\s*\\d{1,2}:\\d{2}.*")&&text.matches("(?s).*\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}.*"))return true;
   for(Element a:table.select("a[href]"))if(a.attr("href").matches("(?:.*/)?\\d+\\.html(?:[?#].*)?"))return true;
  }return false;
 }
 static boolean hidden(Element e){for(Element p=e;p!=null;p=p.parent()){String style=p.attr("style").replace(" ","").toLowerCase(Locale.ROOT);if(p.hasAttr("hidden")||p.attr("aria-hidden").equalsIgnoreCase("true")||style.contains("display:none")||style.contains("visibility:hidden"))return true;}return false;}
 static boolean loginPage(String html){Document page=Jsoup.parse(html);if(timetable(page))return false;
  for(Element input:page.select("input"))if(input.attr("type").equalsIgnoreCase("password")&&!hidden(input))return true;
  String title=page.title().toLowerCase(Locale.ROOT),body=page.body()==null?"":page.body().text().toLowerCase(Locale.ROOT);
  return (title.contains("авторизац")||title.contains("вход"))&&(body.contains("пароль")||body.contains("password"))&&!page.select("form").isEmpty();
 }
 static boolean confirmedTimetable(String html){return timetable(Jsoup.parse(html));}
}
