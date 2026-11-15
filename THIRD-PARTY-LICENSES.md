# сторонние компоненты и сервисы · 0.6.0

- leaflet 1.9.4, bsd-2-clause: локальные leaflet.js и leaflet.css. полный текст лицензии: assets/maps/vendor/leaflet-LICENSE.txt. https://leafletjs.com/ и https://github.com/Leaflet/Leaflet/tree/v1.9.4
- openstreetmap, данные odbl: https://www.openstreetmap.org/copyright. контуры корпусов сохранены со ссылками на исходные объекты. стандартные тайлы запрашиваются по https с идентификацией приложения и обычным кэшем webview. предварительная массовая загрузка не реализована. https://operations.osmfoundation.org/policies/tiles/
- fossgis / osrm: используется только публичный профиль пеших маршрутов. запросы ограничены, частые gps-обновления не отменяют текущий запрос. при ошибке сохраняется последний подтверждённый путь к той же цели. атрибуция и ссылка исправления карты видны на карте. https://routing.openstreetmap.de/about.html
- jsoup 1.18.3: mit, https://jsoup.org/license.
- планы этажей: официальный навигатор улгту https://lk.ulstu.ru/map/. используются в личном учебном приложении. геометрия исходная, оформление собственное.

в 0.6.0 нет спутниковых слоёв, запросов eox, яндекс карт и выбора других способов передвижения. прежние сведения и проверки остаются в истории соответствующих версий.

публичные сервисы могут быть временно недоступны. приложение сообщает об ошибке и не подменяет рассчитанный путь выдуманной прямой линией.

## Дополнения 0.7.0

- Golos Text распространяется по SIL Open Font License 1.1. Полный текст лицензии находится в assets/GolosText-OFL.txt. https://github.com/google/fonts/tree/main/ofl/golostext
- Material Symbols Rounded распространяется по Apache License 2.0. Полный текст лицензии находится в assets/MaterialSymbols-LICENSE.txt. https://github.com/google/material-design-icons
- AndroidX WorkManager распространяется по Apache License 2.0. https://developer.android.com/jetpack/androidx/releases/work
- Дополнительные планы корпусов 3–5 используют геометрию официального навигатора УлГТУ. https://lk.ulstu.ru/map/

## Городская карта

- [Стандартные растровые тайлы OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/) используются для карты города с атрибуцией. Приложение не загружает районы пакетно и не отключает HTTP-кэш.

- Городской экран Android использует [osmdroid 6.1.20](https://github.com/osmdroid/osmdroid/tree/osmdroid-parent-6.1.20). Apache License 2.0, копия в `assets/maps/osmdroid-LICENSE.txt`.
