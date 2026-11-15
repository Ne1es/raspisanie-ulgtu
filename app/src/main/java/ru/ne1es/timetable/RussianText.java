package ru.ne1es.timetable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class RussianText {
 private static final Locale RU = Locale.forLanguageTag("ru");
 private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", RU);
 // Use explicit Unicode spaces to keep this expression valid in Android ICU and the JVM.
 private static final java.util.regex.Pattern INITIALS = java.util.regex.Pattern.compile(
   "([А-ЯЁ][а-яё-]+)[\\s\\p{Zs}]+([А-ЯЁ])\\.?[\\s\\p{Zs}]*([А-ЯЁ])\\.?(?![А-Яа-яЁё])");
 static String week(LocalDate start) {
  LocalDate end = start.plusDays(6);
  if (start.getYear()!=end.getYear()) {
   DateTimeFormatter full = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
   return start.format(full)+" – "+end.format(full);
  }
  if (start.getMonth()==end.getMonth()) return start.getDayOfMonth()+"–"+end.format(DAY);
  return start.format(DAY)+" – "+end.format(DAY);
 }
 static String teacher(String name) {
  return INITIALS.matcher(name).replaceAll("$1 $2. $3.");
 }
 static String duration(long minutes){
  long hours=minutes/60,rest=minutes%60;
  return hours==0?rest+" мин":hours+" ч"+(rest==0?"":" "+rest+" мин");
 }
 static String capitalize(String value) {
  return value.isEmpty()?value:value.substring(0,1).toUpperCase(RU)+value.substring(1);
 }
}
