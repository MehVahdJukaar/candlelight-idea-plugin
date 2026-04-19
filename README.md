# Candlelight IDEA Plugin

An IntelliJ IDEA plugin to help with developing Minecraft mods in a cross-platform setup
Additional features available when using the Candlelight gradle plugin


## Disclaimer

This plugin is born out of a fork or Architectury IDEA plugin.
It's distributed under the same license.

## Features
### @PlatformImpl Support
-  navigation between @PlatformImpl methods and their platform implementations

    ![Example of gutter icons](doc/gutter-icon.png)

- Inspection warnings for missing platform implementations with quick fixes
https://doc/missing-platform-inspection.png

- ASM transformation automatically replaces method bodies with delegation calls

### Virtual Overrides
- Detects methods that override platform‑specific members (e.g., NeoForge‑only methods in Block) and treats them as valid overrides in common code.

- Code completion suggests platform‑specific overridable methods
https://doc/virtual-override-completion.png

- Gutter icons navigate from a virtual override to its platform declaration
https://doc/virtual-override-gutter.png

- Implicit usage marks virtual overrides and their parameters as used

- Platform‑specific only – shows suggestions and gutter icons only for methods present in some, but not all, platforms
