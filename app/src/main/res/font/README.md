# Fonts (bundled locally, no downloadable fonts, no network)

Planned families per DESIGN.md §3.2:

- **Inter** — interface (headings, body). Role: `SumiFontFamilies.Interface`.
- **Literata** — reflective/editorial text. Role: `SumiFontFamilies.Reflection`.
- **IBM Plex Mono** — numbers, durations. Role: `SumiFontFamilies.Numeric`.

Current `Type.kt` maps these roles to `Default` / `Serif` / `Monospace` fallbacks so M0 builds
without large binaries. To bundle, drop TTFs as `inter_regular.ttf`, `inter_medium.ttf`,
`inter_semibold.ttf`, `literata_regular.ttf`, `plex_mono_regular.ttf`, `plex_mono_medium.ttf`
into this `res/font/` directory and update `SumiFontFamilies` to
`FontFamily(Font(R.font.inter_regular))` etc.

All three families are open-source (Inter by rsms, Literata by The Connected Part / Google Fonts,
IBM Plex Mono by IBM). Include license headers/attribution when bundling.
