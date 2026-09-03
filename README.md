# MiraPunishments

MiraPunishments is the moderation punishment and history system for the Mira Paper server suite. It provides persistent bans, mutes, warnings, staff notes, temporary durations and a searchable punishment record for staff.

## Download

[**Download MiraPunishments v0.1.0**](https://github.com/FiveSOCE/Mira-Punishments/releases/download/v0.1.0/MiraPunishments-0.1.0.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21

## How MiraPunishments Works

Staff actions are stored as persistent punishment records rather than being treated as disposable command results. Bans and mutes can be temporary using durations such as `30m`, `12h` or `7d`, or permanent with `perm`. Warnings and staff notes are also written into the player's moderation history. Revoking a ban or mute preserves the historical record so staff can still see what happened previously.

The plugin enforces active bans and mutes and exposes punishment information through a public Bukkit ServicesManager API for other Mira moderation systems.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/miraban <player> <duration|perm> <reason>` | `mirapunishments.ban` | Bans a player temporarily or permanently and records the punishment. |
| `/miraunban <player>` | `mirapunishments.ban` | Revokes an active MiraPunishments ban while preserving history. |
| `/mute <player> <duration|perm> <reason>` | `mirapunishments.mute` | Mutes a player temporarily or permanently. |
| `/unmute <player>` | `mirapunishments.mute` | Removes an active mute. |
| `/warn <player> <reason>` | `mirapunishments.warn` | Adds a warning to a player's moderation history. |
| `/history <player> [page]` | `mirapunishments.staff` | Views paginated punishment history for a player. |
| `/punish note <player> <note>` | `mirapunishments.staff` | Adds a staff note to a player's history. |
| `/punish ...` | `mirapunishments.staff` | Accesses the general punishment administration command surface. |

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `mirapunishments.staff` | OP | Allows general punishment administration, history and staff notes. |
| `mirapunishments.warn` | OP | Allows warning players. |
| `mirapunishments.mute` | OP | Allows muting and unmuting players. |
| `mirapunishments.ban` | OP | Allows banning and unbanning players. |
