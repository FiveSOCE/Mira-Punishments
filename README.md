# MiraPunishments

MiraPunishments is the moderation punishment and case-history system for the Mira Paper server suite. It provides persistent bans, mutes, warnings, kicks, staff notes, temporary durations and auditable moderation records.

## Download

[**Download MiraPunishments v0.1.1**](https://github.com/FiveSOCE/Mira-Punishments/releases/download/v0.1.1/MiraPunishments-0.1.1.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-Punishments/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.2.0 or newer
- MiraStaff optional integration layer
- MiraReports optional integration layer

## How MiraPunishments Works

Staff actions are stored as persistent punishment cases rather than disposable command results. Bans and mutes can be temporary using durations such as `30m`, `12h`, `7d` or `2w`, or permanent with `perm`. Warnings, kicks and staff notes are also written into the player's moderation history. Revoking a ban or mute preserves the historical record.

Every case has a stable short case ID. `/punish case <id>` can inspect the target, type, issuing staff member, reason, created/expiry time and active/revoked/expired state directly. History output also includes case IDs so staff can jump from a player's timeline into a specific record.

v0.1.1 registers the public punishment service through MiraCore, records issue/revoke/admin actions in the global Core audit trail and emits typed `PunishmentIssuedEvent` and `PunishmentRevokedEvent` lifecycle events. This lets MiraStaff, MiraReports and future moderation tooling react without reading `punishments.yml` directly.

Active bans are enforced when a player joins and active mutes are enforced on Paper chat events. `mirakick` records a KICK case before removing an online player.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/miraban <player> <duration|perm> <reason>` | `mirapunishments.ban` | Issues a temporary/permanent ban case. |
| `/miraunban <player>` | `mirapunishments.ban` | Revokes active MiraPunishments bans while retaining history. |
| `/mute <player> <duration|perm> <reason>` | `mirapunishments.mute` | Issues a temporary/permanent mute case. |
| `/unmute <player>` | `mirapunishments.mute` | Revokes active mutes while retaining history. |
| `/warn <player> <reason>` | `mirapunishments.warn` | Issues a warning case. |
| `/mirakick <player> <reason>` | `mirapunishments.kick` | Records a kick case and kicks an online target. |
| `/history <player> [page]` | `mirapunishments.staff` | Views paginated case history with IDs and state. |
| `/punish case <id>` | `mirapunishments.staff` | Inspects one punishment case directly. |
| `/punish note <player> <note>` | `mirapunishments.staff` | Adds a persistent staff-note case. |
| `/punish history <player> [page]` | `mirapunishments.staff` | Alternate history entry point. |
| `/punish reload` | `mirapunishments.staff` | Reloads punishment records from disk. |

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `mirapunishments.staff` | OP | General punishment administration, case lookup, history and notes. |
| `mirapunishments.warn` | OP | Allows warning players. |
| `mirapunishments.mute` | OP | Allows muting and unmuting players. |
| `mirapunishments.ban` | OP | Allows banning and unbanning players. |
| `mirapunishments.kick` | OP | Allows recorded MiraPunishments kicks. |

## API / Integration

`PunishmentApi` is available through Bukkit ServicesManager and MiraCore. It exposes:

- active ban/mute checks
- active punishment lookup by type
- case lookup by ID
- complete player history
- recent server-wide cases

Typed Bukkit lifecycle events:

- `PunishmentIssuedEvent`
- `PunishmentRevokedEvent`

## Persistence

Punishment records are stored in `plugins/MiraPunishments/punishments.yml`. Expiry uses absolute timestamps, so temporary punishments remain correct across restarts.

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.
