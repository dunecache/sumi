# App mapping list — "most used" Android packages (workspace draft)

Curated candidate list for the bundled default classification (`:domain`
`DefaultPackageCategories`). **Not Play-Store verified.** Package IDs come from general
knowledge of the Android ecosystem; wiring a wrong ID just means the app stays
Uncategorized (safe) — so this list is a *proposal*, not ground truth.

Confidence tiers:
- **H** (high) — package ID is well-known and stable; wired into the code or proposed for it.
- **M** (medium) — plausible, not verified; do **not** wire in until checked once against a real install.

"Unknown → Uncategorized" stays the default; nothing here silently overrides a user
assignment, and browsers/launchers/keyboards remain deliberately unmapped.

## Social
H: com.instagram.android, com.facebook.katana,
   com.facebook.lite, com.twitter.android, com.snapchat.android, com.reddit.frontpage,
   com.pinterest, com.tumblr, com.linkedin.android, com.discord,
   com.ss.android.ugc.trill / com.zhiliaoapp.musically (TikTok, region variants)
M: com.instagram.lite, com.instagram.androidlite (Instagram Lite — verify which id ships)

## Communication
H: com.whatsapp, com.whatsapp.w4b, org.telegram.messenger, org.thoughtcrime.securesms (Signal),
   com.google.android.gm (Gmail), com.google.android.apps.messaging (Google Messages),
   com.samsung.android.messaging, com.viber.voip, com.tencent.mm (WeChat),
   jp.naver.line.android (LINE — note: the existing `linecorp` substring rule misses it),
   com.microsoft.office.outlook, com.skype.raider, com.google.android.apps.googlechat,
   com.google.android.apps.meetings (Meet)
M: com.google.android.apps.googlevoice, com.tencent.wework (WeCom — work chat, arguably Productivity),
   com.tencent.mobileqq (QQ), com.alibaba.android.rimet (DingTalk)

## Entertainment
H: com.google.android.youtube, com.google.android.apps.youtube.music,
   com.netflix.mediaclient, com.spotify.music, tv.twitch.android.app, com.hulu.plus,
   com.disney.disneyplus, com.soundcloud.android, com.deezer.android, com.tidal.android,
   com.amazon.avod.thirdpartyclient (Prime Video), com.hbo.hbonow
M: com.youtube.tv (YouTube TV), com.google.android.apps.podcasts, com.crunchyroll.crunchyroll (check id),
   com.amazon.ivona? (no), com.mxtech.ffmpegin (MX Player — verify), com.plexapps.plextroll? (no such id)

## Productivity
H: com.slack, com.google.android.apps.docs, com.google.android.sheets, com.google.android.slides,
   com.google.android.keep, com.google.android.calendar, com.microsoft.teams,
   us.zoom.videomeetings, com.microsoft.office.word, com.microsoft.office.excel,
   com.microsoft.office.powerpoint, com.microsoft.office.onenote,
   com.asana, com.trello, com.evernote, com.google.android.apps.tasks
M: notion.id (Notion), com.todoist, com.ticktick, com.gitlab.android, com.airtable

## Education
H: com.duolingo, org.khanacademy.android, org.coursera.android, com.udemy.android,
   org.edx.android, com.quizlet.android, com.ichi2.anki (AnkiDroid),
   org.brilliant.android, com.memrise.android
M: com.oerproj? (no), org.ankiweb.dapp? (AnkiDroid apps moved over time — verify),
   com.gmail.da? no

## Reading
H: com.amazon.kindle, com.google.android.apps.playbooks, com.audible.application,
   com.goodreads, com.blinkist, com.readitlater.android (Pocket), com.medium.android,
   com.overdrive.mobile.android.libby
M: com.moon+? no

## Games (weak evidence on purpose)
H: com.mojang.minecraftpe, com.roblox.client, com.king.candycrushsaga,
   com.supercell.clashofclans, com.supercell.clashroyale, com.supercell.brawlstars,
   com.nianticlabs.pokemongo, com.mobile.legends, com.garena.game.codm,
   com.tencent.ig, com.epicgames.fortnite, com.miHoYo.GenshinImpact,
   com.imangi.templerun2
M: com.garena.game.free, com.riotgames.team? (TFT/LoL not Android-native),
   com.ea.gp.fifa, com.gameloft.android.gplay.fifa, com.mobile? no

## Utilities
H: com.google.android.apps.nbu.files (Files), com.google.android.calculator
M: com.sec.android.app.sbrowser (Samsung Internet — browser; intentionally unmapped),
   com.miui.notes? (notes — Productivity if anything; verify), com.baidu.*? no

## Reddit clients (asked and answered)

How many Android Reddit clients are there? Historically ~8–10; in practice few remain after
the 2023 API changes. Coverage in this mapping:

| Client | Package id | Status |
|---|---|---|
| Reddit (official) | `com.reddit.frontpage` | ✅ substring `reddit` |
| Reddit is Fun | `com.andrewshu.android.reddit` | ✅ substring |
| Boost for Reddit | `it.d4w.android.proreddit` | ✅ substring |
| Relay for Reddit | `com.laurencedawson.redditbrowser` / `...redditbrowserpro` | ✅ substring |
| Sync for Reddit | `me.ccrama.redditsync` (id uncertain) | ✅ if id correct |
| Alien Blue (defunct) | `com.alienblue.app` | ✅ exact rule added |
| Slide for Reddit, Joey for Reddit | ids not verified | unverified — do not add without checking an install |

Lesson encoded in the map: a package id containing `reddit` already lands in Social, so
new wrappers need no rules unless they pick an unrelated id — and unverified ids must
stay out (fail-safe → Uncategorized).

## Deliberately NOT mapped
- Browsers (Chrome/Firefox/Samsung Internet/Edge) — biggest-drift category by design decision pending;
  see the "should browsers be uncategorized?" note below.
- OEM system apps/store (device furniture excluded from usage anyway — excluded at collection).
- Camera, launchers, keyboards, widgets.
- Anything that couldn't be identified with reasonable confidence.

## Open questions for the reviewer
1. Communication → Social merge still pending (your earlier question). If merged, the
   two lists above collapse into one Social bucket at the seeds level.
2. Browsers: map to a category (e.g. Utilities) or leave Uncategorized?
3. YouTube → Entertainment (current) vs a video-specific category (would be terracotta,
   which we reserved for warnings — so Entertainment stands).
4. Regional ecosystems (Tencent WeChat, Alipay, Douyin, KakaoTalk, LINE ok-listed above)
   need region-specific games/education apps — this list is mainstream-US-weighted.

## Signals of likely false positives to watch
- `docs` substring matches Google Drive as well (intended).
- `keep` / `teams` substrings match unrelated apps containing those tokens.
- `sms` substring matches any package containing "sms" (acceptable).
- `pokemon` substring matches Pokémon GO *and* Pokémon games.
- `coursera` / `khan` substrings are benign.
