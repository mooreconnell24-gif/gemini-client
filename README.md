# Gemini Client

Gemini Client is a multiplayer-safe Fabric mod built to be cleaner, more useful, and less limited to single-player.

## Features
- Fabric client bootstrap and config system
- Keybind support for quick actions
- Server/client networking for multiplayer-safe behavior
- Basic in-game command support
- Clean project structure for future expansion

## Multiplayer-safe design
This version is not hardcoded to single-player. It uses Fabric networking so the mod can still participate when playing on a server, and it includes a shared command system that works on both client and dedicated server contexts.

## Quick start
1. Install Fabric for Minecraft 1.21.1.
2. Run the Gradle setup for this project.
3. Launch the game with the generated mod jar.
4. Use the configured keybind and command `/gemini status`.

## Default keybind
- `G`: toggles the Gemini Client state

## Commands
- `/gemini status` shows current mod status
- `/gemini toggle` toggles the mod state on the server side if the mod is active there

## Project layout
- `src/main/java/com/mooreconnell24/geminiclient` - core mod code
- `src/main/resources` - mod metadata and resources

## Notes
This repo was empty at first, so I created a proper Fabric foundation you can build on. The next step is to add your actual gameplay feature set (HUD, utilities, automation, render tweaks, etc.).
