# Agent instructions

Before building, testing, packaging, or changing the local development environment, read and follow the project-specific rules in [`.local/rules`](.local/rules/).

At minimum, read [`.local/rules/linux-build.md`](.local/rules/linux-build.md) before producing a Linux artifact on this computer.

Read [`.local/rules/update-compatibility.md`](.local/rules/update-compatibility.md) before adding persistence, configuration, plugins, skins, localization, caches, or other mutable application data.

Read [`.local/rules/release-notes.md`](.local/rules/release-notes.md) before creating or publishing a release.

Architectural decisions and non-obvious implementation choices must be documented in the code with Javadoc next to the class or method that implements them. The Javadoc must explain why the chosen approach exists, which rejected failure mode or constraint it addresses, and any compatibility or behavioral invariants that future changes must preserve. Do not merely restate what the code does. Whenever a documented class or method is changed, review and update its Javadoc in the same change so the explanation never describes an obsolete implementation. This applies especially to algorithms, persistence and compatibility rules, resource lifecycles, synchronization, playback transfer, package discovery, and other behavior whose rationale cannot be reconstructed safely from the code alone.
