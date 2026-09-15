# LiquidBounce Extras

Modules that [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) does not ship. Drop the jar from the
[releases](https://github.com/CCBlueX/LiquidBounce-Addon-Extras/releases) into `mods/` next to the client;
the modules show up in an `Extras` category.

![ClickGUI](docs/clickgui.png)

Every module is written against the stable add-on API, half in Kotlin and half in Java. Start with the
[template](https://github.com/CCBlueX/LiquidBounce-Addon-Template), come here to see how something is done.

## Modules

### QuickRespawn (Java)

Respawns a few ticks after you die and says so in chat.

![QuickRespawn](docs/quickrespawn.png)

### Speedometer (Java)

Shows your speed on the screen in blocks per tick or per second, plain or in a box.

![Speedometer](docs/speedometer.png)

## Building

```sh
./gradlew build
```

## License

This project is subject to the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html). This
does only apply for source code located directly in this clean repository. During the development and compilation
process, additional source code may be used to which we have obtained no rights. Such code is not covered by the GPL
license.

The mob spawn rule in `ModuleLightOverlay` is derived from
[Meteor Client](https://github.com/MeteorDevelopment/meteor-client), Copyright Meteor Development, also GPL-3.0.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice
nor legally binding.

*Actions that you are allowed to do:*

- Use
- Share
- Modify

*If you do decide to use ANY code from the source:*

- **You must disclose the source code of your modified work and the source code you took from this project. This means
  you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated)
  application.**
- **Your modified application must also be licensed under the GPL**
