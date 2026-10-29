# Agility Action Camera

Action-movie camera work for OSRS agility. When you start a long obstacle — a
deathslide, zipline, rope swing or rooftop leap — the camera cuts to a
cinematic shot, letterbox bars slide in, and when you land it glides back to
your own camera.

## Shots

| Shot | What it does | Favoured for |
|---|---|---|
| Tracking | Side-on dolly alongside you | Anything |
| Chase | Low over-the-shoulder, looking down the line | Long straight runs |
| Flyby | Starts ahead of you, whips round as you pass | Long obstacles |
| Crane | Top-down to low (or reverse) | Drops and climbs |
| Orbit | Slow orbit around you | Short obstacles |
| Hero | Low, tight angle looking up | Variety |
| Establishing | Wide view of the whole obstacle | Ziplines, deathslides |

A director scores every enabled shot on both sides of the path for fit, line
of sight against terrain and the scene edge, and variety, then picks the best.
Long obstacles get a hard cut to a second shot halfway through.

## Rules

- Shots are computed live from the obstacle's path. No camera positions are
  stored ([RuneLite rejects camera presets](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)).
- While the camera is detached, clicks on the game world are dropped, as
  [Jagex's guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
  forbid world interaction from a detached camera. Clicking the world ends the
  shot immediately so your next click goes through.
- The plugin never sends input or server actions.

## Development

Requires a JDK 11+ (17 recommended).

```
./gradlew test   # unit tests for detection, shots and camera math
./gradlew run    # developer client with the plugin loaded
```

Turn on **Debug → Debug overlay** to see detected paths, and
**Debug → Preview hotkey** to play a test orbit without running a lap.
**Debug → Log camera values** logs raw camera state for calibration.

Obstacle tables are copied from the core Agility plugin and need re-syncing
when new courses come out.
