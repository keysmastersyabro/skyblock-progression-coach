# SkyBlock Progression Coach

SkyBlock Progression Coach is a small, read-only Fabric client mod for Hypixel SkyBlock. It turns a player's profile and live Bazaar snapshot into a short, goal-focused upgrade plan.

The first version supports:

- `/coach` and the `P` key to open the dashboard.
- Goal presets for Combat, Dungeons, Mining, Wealth, and Accessories.
- Profile refreshes from the Hypixel SkyBlock API.
- A live Bazaar product count and market-aware recommendation context.
- Recommendations that explain what to do next and why.
- No auto-buying, clicking, packet manipulation, or gameplay automation.

## Build

This project targets Minecraft `26.1.2`, Fabric Loader `0.19.3`, Fabric API `0.154.0+26.1.2`, and Java 25.

```text
./gradlew build
```

The jar is written to `build/libs/progression-coach-<version>.jar`.

## Configure the API key

Run Minecraft once with the mod installed. It will create:

```text
config/progressioncoach/config.json
```

Put your own Hypixel API key in that file:

```json
{
  "version": 1,
  "apiBaseUrl": "https://api.hypixel.net",
  "apiKey": "YOUR_KEY_HERE",
  "goal": "COMBAT",
  "refreshSeconds": 120
}
```

The key is read locally and is never committed to this repository or printed in logs. Do not paste a real key into source code, screenshots, issues, or pull requests. For a public release, a small backend proxy is safer than distributing a shared developer key inside a client jar.

Use `/coach refresh` after configuring the key. The mod deliberately refreshes only on user request; it does not poll aggressively.

## API-key application description

> SkyBlock Progression Coach is a read-only Fabric client mod that uses a player's Hypixel profile and live Bazaar data to recommend efficient next steps toward a selected progression goal. It does not automate gameplay, purchase items, click interfaces, or manipulate packets. Requests are user-triggered and rate-limited by a configurable refresh interval. Profile data is processed locally and the API key is never included in source control.

## License

MIT. This project is not affiliated with Hypixel, Mojang, or Microsoft.

