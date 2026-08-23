# SkyBlock Progression Coach

SkyBlock Progression Coach is a read-only Fabric client mod for Hypixel SkyBlock. It turns the selected profile into a concrete progression audit instead of a generic tip list.

## What it actually does

- Loads the selected profile and supports both the classic UUID-keyed member map and the current member wrapper shape.
- Converts raw `experience_skill_*` fields into real skill levels using the official SkyBlock skill resource.
- Reads purse, bank balance, collections, Slayer XP, Catacombs XP, and the live Bazaar snapshot.
- Counts real accessory-bag items from compressed profile NBT when inventory data is available.
- Builds measurable checkpoints for Combat, Dungeons, Mining, Wealth, and Accessories.
- Shows current value, target value, remaining progress, and a concrete action for each checkpoint.
- Uses live Bazaar weighted prices and liquidity only as price context; it never places orders.
- Clearly labels unavailable fields instead of inventing values.

Open the dashboard with `P` or `/coach`.

Useful commands:

- `/coach refresh` loads a fresh profile and Bazaar snapshot.
- `/coach status` prints the current state and selected goal.
- `/coach goal <combat|dungeons|mining|wealth|accessories>` changes the audit.
- `/coach key-status` confirms whether a local key is configured without printing it.

## Build

This project targets Minecraft `26.1.2`, Fabric Loader `0.19.3`, Fabric API `0.154.0+26.1.2`, and Java 25.

```text
./gradlew build
```

The jar is written to `build/libs/progression-coach-<version>.jar`.

## Configure the API key

Run Minecraft once with the mod installed. It creates:

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

The key is sent in the official `API-Key` request header. It is never put in a URL, stored in source control, or printed in logs. Do not paste a real key into source code, screenshots, issues, or pull requests.

The mod refreshes only when requested and enforces a minimum refresh interval. It does not automate gameplay, click interfaces, buy items, manipulate packets, or send gameplay actions.

## API-key application description

> SkyBlock Progression Coach is a read-only Fabric client mod for Hypixel SkyBlock. It uses a player's selected profile, official skill thresholds, collections, Slayer and Catacombs progress, optional accessory-bag data, and live Bazaar prices to calculate measurable next milestones for a selected goal. It does not automate gameplay, purchase items, click interfaces, manipulate packets, or send gameplay actions. Requests are user-triggered and locally rate-limited. The API key is kept local and is sent only in the official API-Key request header.

For a public release, a small backend proxy is safer than distributing a shared developer key inside a client jar.

## License

MIT. This project is not affiliated with Hypixel, Mojang, or Microsoft.
