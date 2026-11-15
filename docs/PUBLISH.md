# Публикация обновлений

Исходники находятся в ветке `main`. Подписанный APK 0.10.4 хранится в каталоге `apk/`. Закрытый ключ подписи хранится у владельца проекта.

Для следующего обновления закоммить изменения, отправь ветку `main` и подготовь подписанный APK. Если нужен отдельный GitHub Release для версии 0.10.4, выполни в корне проекта:

```bash
gh release create v0.10.4 apk/Timetable-UlSTU-0.10.4.apk \
  --repo Ne1es/raspisanie-ulgtu \
  --target main \
  --title "Расписание УлГТУ 0.10.4" \
  --notes-file docs/RELEASE-v0.10.4.md
```

Через сайт GitHub открой **Releases**, выбери **Draft a new release**, создай тег `v0.10.4` на ветке `main`, вставь changelog и прикрепи APK.
