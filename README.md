# Dzienniczek

Prywatny klient e-dzienników **Librus Synergia** i **eduVulcan** na Androida (Kotlin Multiplatform, Compose).
Fork projektu [DzienniczekSzpontniczek](https://github.com/szponciciel04/DzienniczekSzpontniczek) (MIT).

## Funkcje

- konta Librus i eduVulcan jednocześnie, wielu uczniów na jednym koncie,
- oceny, plan lekcji, sprawdziany, zadania domowe, frekwencja, uwagi, ogłoszenia,
- wiadomości – Librus przez darmową wersję webową Synergii, eduVulcan przez `wiadomosci.eduvulcan.pl`,
- opcjonalne powiadomienia o nowych wiadomościach z pełną treścią (sprawdzanie w tle co ok. 30 minut, do włączenia w Ustawieniach),
- automatyczne odnawianie tokenów Librusa.

## Build

Build uruchamia się ręcznie w GitHub Actions: **Actions → Build → Run workflow** (dowolny branch).
Gotowy plik `dzienniczek.apk` (wersja release) jest w sekcji **Artifacts** danego uruchomienia.

APK jest podpisywany stałym kluczem z sekretów repozytorium (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`) – dzięki temu kolejne wersje instalują się jako aktualizacja.

Lokalnie: `./gradlew :composeApp:assembleDebug`

## Uwaga

Aplikacja nie jest powiązana z firmami Librus ani VULCAN i korzysta z ich nieoficjalnych interfejsów.
Używaj na własną odpowiedzialność, wyłącznie z własnymi danymi logowania.
