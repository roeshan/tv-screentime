Installing this on a TV: follow [AGENTS.md](AGENTS.md).

Changing the code: plain Java, no AndroidX, minSdk 30. Which apps count or need the PIN is in
`app/src/main/java/io/github/roeshan/tvscreentime/Apps.java`. User-facing text lives in
`app/src/main/res/values/strings.xml` (English) and `values-in/strings.xml` (Indonesian); add every new string to
both. Build and install with `scripts/install.sh`.
