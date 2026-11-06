# Fonts (bundled locally, no downloadable fonts, no network)

Bundled per DESIGN.md §3.2, pulled from `google/fonts` (OFL):

- **Inter** — interface (headings, body). `inter_regular.ttf` (variable font, OFL).
- **Literata** — reflective/editorial text. `literata_regular.ttf` (variable font, OFL).
- **IBM Plex Mono** — numbers, durations. `plex_mono_regular.ttf` + `plex_mono_medium.ttf` (OFL).

Artifacts pulled via `curl` from `github.com/google/fonts/raw/main` (`ofl/inter`, `ofl/literata`,
`ofl/ibmplexmono`). All three families are open-source (Inter by rsms, Literata by The Connected Part /
Google Fonts, IBM Plex Mono by IBM) under the SIL Open Font License 1.1.

`Type.kt` uses these via `Font(R.font.*)`; family XMLs in this directory wrap them for
Android's font resources.
