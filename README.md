# MiraPunishments

Moderation punishment history and enforcement for Paper 1.21.11 / Java 21.

## Current release

**v0.1.0**

Direct download:
https://github.com/FiveSOCE/Mira-Punishments/releases/download/v0.1.0/MiraPunishments-0.1.0.jar

All releases:
https://github.com/FiveSOCE/Mira-Punishments/releases

## Features

- Persistent bans and mutes
- Warnings
- Staff notes
- Temporary durations (`30m`, `12h`, `7d`, etc.) and permanent punishments
- Revoke/unban/unmute history
- Paginated punishment history
- Public Bukkit ServicesManager API

## Commands

- `/miraban <player> <duration|perm> <reason>`
- `/miraunban <player>`
- `/mute <player> <duration|perm> <reason>`
- `/unmute <player>`
- `/warn <player> <reason>`
- `/history <player> [page]`
- `/punish note <player> <note>`

## Build

`./gradlew build`

Output: `build/libs/MiraPunishments-0.1.0.jar`
