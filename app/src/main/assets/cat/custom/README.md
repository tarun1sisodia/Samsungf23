# Bring your own cat (custom layered artwork)

The app ships with a code-drawn black tuxedo cat (night sky with moon and stars). To use
YOUR own layered artwork instead, drop PNG files into this folder
(`app/src/main/assets/cat/custom/`) and rebuild.

## File names (recognized roles)

| File | Role | Animates? |
|---|---|---|
| `tail.png` **(required)** | the tail | rotates around its pivot when flicking |
| `body.png` **(required)** | body + head (one layer) | squash & stretch on wake |
| `eyes_open.png` **(required)** | open eyes | variant-swapped for blink/wake |
| `eyes_closed.png` **(required)** | closed/happy eyes | shown mid-blink |
| `eyes_wide.png` (optional) | wide-awake eyes | shown at the start of the wake animation |
| `ear_left.png` / `ear_right.png` (optional) | ears | slight perk rotation on wake |
| `bg.png` (optional) | replaces the background | parallax only |
| `fg.png` (optional) | foreground decoration | parallax only |

If any of the four required files is missing, the app falls back to the
built-in cat entirely — a half-filled folder never crashes.

## Artwork rules

- **Square PNGs, all the same size** (1024×1024 recommended, max 2048).
- **Cat cels** (tail/body/eyes/ears) are stacked 1:1 — draw each part in the
  same position it occupies in the full picture (transparent animation cels).
  Each square maps to the **middle band of the scene**: a square area that
  starts 30% down the screen. Center your cat in the square, roughly
  55–65% of the canvas height, facing forward.
- `bg.png` / `fg.png` are scaled uniformly to cover the whole screen (sides
  may crop on the tall F23 display).
- Keep ~10% empty margin around the canvas edges so parallax never clips.
- The tail rotates around a pivot you declare in `custom.json`.

## Optional tuning: `custom.json`

```json
{
  "depth":     { "bg": 0.15, "body": 0.72, "ears": 0.75,
                 "eyes": 0.82, "tail": 0.86, "fg": 1.0 },
  "tailPivot": [500, 850],
  "topOffset": 300
}
```

- `depth` — 0 = far (barely moves with tilt), 1 = near (moves most).
- `tailPivot` — where the tail rotation is anchored, in the same square
  coordinates as your cels (0–1000 per side).
- `topOffset` — vertical position of the cel band in the scene (default 300;
  raise it to move the cat band down).

After adding files, rebuild (push to `main` — CI publishes a new APK) and
reinstall.
