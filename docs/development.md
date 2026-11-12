# Development guide

This guide is for people who build, change or release the Email Reader. For using the connector in a channel, see the [README](../README.md).

## Contents

- [Repository layout](#repository-layout)
- [Building](#building)
- [How the connector is put together](#how-the-connector-is-put-together)
- [A poll, step by step](#a-poll-step-by-step)
- [POP3 and IMAP](#pop3-and-imap)
- [From mail to message](#from-mail-to-message)
- [The mail libraries](#the-mail-libraries)
- [Properties and saved channels](#properties-and-saved-channels)
- [The settings panels](#the-settings-panels)
- [Logging and errors](#logging-and-errors)
- [Testing](#testing)
- [Trying it in OIE](#trying-it-in-oie)
- [GitHub workflows](#github-workflows)
- [Releasing](#releasing)
- [Conventions](#conventions)

## Repository layout

| Path | What it is |
| --- | --- |
| `connector/pom.xml` | The Maven project. Build from `connector/`. |
| `connector/src/main/java/.../email/shared/` | `EmailReceiverProperties`: the settings, used by the server and both administrators. `emailreader-shared.jar`. |
| `connector/src/main/java/.../email/server/` | `EmailReceiver` (the source connector) and `EmailPoller` (POP3/IMAP). `emailreader-server.jar`. |
| `connector/src/main/java/.../email/client/` | `EmailReader`, the Swing settings panel. `emailreader-client.jar`. |
| `connector/src/main/resources/source.xml` | The connector descriptor the engine reads. |
| `connector/src/test/` | JUnit 5 tests. |
| `connector/webadmin/` | The settings panel for the OIE web administrator (`plugin.json`, `web/plugin.js`). |
| `connector/assembly.xml` | How the extension zip is laid out. |
| `oie.json` | The manifest for the OIE Community Store catalog. |
| `.github/` | The catalog and Jira workflows and their scripts. |

`...` is `com/mirth/connect/connectors`.

## Building

### Prerequisites

- **JDK 11 or later.** The engine's own runtime (`<OIE_HOME>/jre`) includes `javac` and works as `JAVA_HOME`.
- **Maven 3.9** or later.
- **The OIE engine jars** in your local Maven repository, version `oie.version` from `connector/pom.xml` (now 4.6.0). They are not in Maven Central; install them from an OIE installation:

```bash
OIE_HOME="/opt/openintegrationengine"   # or "C:/Program Files/OpenIntegrationEngine"
install() {
  mvn install:install-file -Dfile="$OIE_HOME/$2" -DgroupId=com.mirth.connect -DartifactId="$1" -Dversion=4.6.0 -Dpackaging=jar
}
install server-api    server-lib/mirth-server.jar
install donkey-server server-lib/donkey/donkey-server.jar
install donkey-model  server-lib/donkey/donkey-model.jar
install client        client-lib/mirth-client.jar
install client-core   client-lib/mirth-client-core.jar
```

### Build

```bash
cd connector
mvn clean package
```

The result is `connector/target/email-reader-connector-<version>.zip`. `package` runs the tests first; add `-DskipTests` to skip them, or `-o` to build offline.

### What is in the zip

`maven-jar-plugin` makes three jars out of one source tree, by package; `assembly.xml` puts them in the zip together with the mail libraries:

```
email-reader/
├── source.xml
├── emailreader-shared.jar      email/shared/**
├── emailreader-server.jar      email/server/**
├── emailreader-client.jar      email/client/**
├── webadmin/                   the web administrator panel
└── lib/                        jakarta.mail-api, jakarta.activation-api, angus-mail, angus-activation
```

The folder name `email-reader` must equal the `path` attribute in `source.xml`: the engine's client download servlet and the web administrator build URLs from it.

## How the connector is put together

`source.xml` (a `connectorMetaData`) tells the engine about the connector:

| Element | Value | Purpose |
| --- | --- | --- |
| `name` | `Email Reader` | The name in the connector type list. Must equal `EmailReceiverProperties.NAME`. |
| `sharedClassName` | `...email.shared.EmailReceiverProperties` | The settings class. |
| `serverClassName` | `...email.server.EmailReceiver` | The source connector. |
| `clientClassName` | `...email.client.EmailReader` | The Swing settings panel. |
| `library` | the three jars and the four `lib/` jars | What the engine puts on the classpath, per side. |
| `type` | `SOURCE` | A source connector only. |
| `mirthVersion` | `4.6.0,4.7.0` | The engine versions it may load on. |

The classes:

| Class | Role |
| --- | --- |
| `EmailReceiverProperties` | The settings. Extends `ConnectorProperties` and implements the poll and source connector property interfaces, so the engine adds its standard polling and source settings. |
| `EmailReceiver` | A `PollConnector`. On each poll it creates an `EmailPoller`, dispatches every mail into the channel, and reports status and errors. |
| `EmailPoller` | Connects, reads, converts and marks or deletes. It has **no dependency on the engine**, so it can be tested on its own. |
| `EmailReader` | The Swing `ConnectorSettingsPanel`. |

**The package and the names are the connector's identity.** The settings class must be in the `com.mirth.connect.connectors.*` family: the engine's XStream allow-list only accepts that family, and a class outside it makes the Swing client fail, and freeze, on any channel that uses the connector. A channel stores the connector name and the class names, so renaming any of them breaks existing channels. That is what happened when "POP3 Reader" became "Email Reader" in 3.0.0; users had to recreate their channels.

## A poll, step by step

The engine calls `EmailReceiver.poll()` on the schedule of the polling settings (60 seconds by default):

1. Status **Reading** in the dashboard.
2. Parse the port; an invalid port is an error event, and the poll ends.
3. `new EmailPoller(properties, port).poll(this::dispatchMail)`:
   1. Build the JavaMail `Properties` for `pop3`, `pop3s`, `imap` or `imaps`, with 15 second connect and read timeouts.
   2. Connect, open the folder (POP3: `INBOX`; IMAP: the configured folder), read-write only when it must delete or mark.
   3. Get the messages: all of them, or for IMAP with *Only unread* the ones without the `SEEN` flag.
   4. For each message: build the XML and the source map, call the handler, and **only when the handler returned true** set `DELETED` or `SEEN`.
   5. Close the folder (expunging when *Delete after fetch* is on) and the store.
4. `dispatchMail` dispatches a `RawMessage` with the XML and the source map. It returns `true` when the engine accepted the message, `false` when the connector is stopping or the engine refused it (`ChannelException`). `finishDispatch` is always called.
5. Any failure of the poll as a whole (connection, login, folder) is an error event in the dashboard and `mirth.log`.
6. Status **Idle**.

Key behaviour:

- **"Handled" means dispatched**, not processed: a mail is marked or deleted once the channel accepted it, which with a source queue is before the destinations ran.
- **One bad mail does not stop the poll.** An exception for one message is logged and that mail stays on the server; the loop continues.
- **No connection is kept** between polls.

## POP3 and IMAP

| | POP3 | IMAP |
| --- | --- | --- |
| Folder | Always `INBOX` | *Folder* (default `INBOX`) |
| Which mails | All in the mailbox | All, or only unread (`FlagTerm` on `SEEN`) |
| After a successful dispatch | Delete, if *Delete after fetch* | Delete, or mark as read (`SEEN`) |
| Memory of processed mail | None: without delete, every poll sends everything again | The read flag |

Details that matter:

- IMAP sets `mail.imap(s).peek=true`, so **reading** a mail does not mark it as read. Only a mail that was dispatched gets `SEEN`; a mail whose dispatch failed stays unread and is tried again.
- *Mark as read* is ignored when *Delete after fetch* is on (`setSeen = imap && markAsRead && !deleteAfterFetch`).
- Only plain connections and SSL/TLS (`pop3s`, `imaps`) are supported; STARTTLS and OAuth2 are not.

## From mail to message

`EmailPoller.toXml` builds the message:

```xml
<email>
  <subject>…</subject>
  <from>…</from>
  <to>…</to>
  <sentDate>Thu Sep 17 08:12:00 CEST 2026</sentDate>
  <body><![CDATA[…]]></body>
</email>
```

- **Addresses** are the bare addresses (`InternetAddress.getAddress()`), comma separated.
- **`sentDate`** in the XML is `Date.toString()`. This format is kept for existing transformers; the source map has ISO 8601.
- **The body** is plain text (`extractText`):
  - `multipart/alternative`: the `text/plain` part wins; another part (HTML, converted) only when there is no plain text.
  - Other `multipart/*`: the text of all non-attachment parts, joined with a line break.
  - `text/html`: converted with `htmlToText`, a small converter without libraries (drops `script`, `style`, `head` and comments, turns `br`, block ends and list items into line breaks and dashes, decodes common entities).
  - Attachments (`Content-Disposition: attachment`) and non-text parts are skipped.
  - The part's charset is used; UTF-8 when it is missing or unknown.
- **Cleaning** (`cleanText`): line endings to `\n`, non-breaking spaces to spaces, characters illegal in XML 1.0 removed, trailing spaces and more than one blank line removed, trimmed.
- **CDATA safety:** `]]>` in the body is split over two CDATA sections.
- **Subject, from and to** are escaped for XML and cleaned of illegal characters.

**The source map** (`sourceMap`, package-private for the tests) has `subject`, `from`, `to`, `messageId`, `sentDate` (ISO 8601 in the server's time zone), `mailbox` (`username@host`) and, for IMAP, `folder`. A missing value is an empty string, so channel scripts need no null checks. When you add a variable, add it to the README's table as well.

## The mail libraries

The engine ships an **old** `jakarta.mail` 1.6.7 / `javax.activation` on its shared classpath. The connector brings its own `jakarta.mail-api` 2.x with Angus Mail, in `lib/`, listed as `SERVER` libraries in `source.xml`.

Two things follow:

- **Parts are read as streams.** `getContent()` would look up content handlers through the activation framework's mailcap, and the engine's older registrations clash with the bundled `jakarta.activation`. So multiparts are parsed with `new MimeMultipart(new MimePartDataSource(part))`, and text with `part.getInputStream()` and the part's charset. Keep it that way when changing the body extraction.
- **The jar names are in `source.xml`.** `assembly.xml` copies the runtime dependencies into `lib/` with their versions in the file name (`angus-mail-2.0.3.jar`), and `source.xml` lists those exact names. **When you change a mail library version in `pom.xml`, update the `library` paths in `source.xml`**, or the engine will not find the classes. Check the zip's `lib/` against `source.xml` after the build.

## Properties and saved channels

Channels are stored with XStream, which **does not run constructors**. A field added in a later version is `null` (objects) or `false`/`0` (primitives) in a channel saved before. `EmailReceiverProperties` is written for that:

- `getMailProtocol()` returns POP3 unless the field says IMAP, so channels saved before IMAP existed (2.0.0) stay POP3.
- `getFolder()` returns `INBOX` when empty.
- `unreadOnly` and `markAsRead` are primitives that are `false` in such old channels. That is harmless, because those channels are POP3, where both are ignored.

The constructor sets the defaults for **new** channels, including a polling interval of 60 seconds instead of the engine's 5.

### Adding a setting

1. Add the field, its default in the constructor and a null-safe getter to `EmailReceiverProperties`. Choose the getter's fallback so that existing channels behave as before. A new setting that should default to *on* must be a `Boolean` whose getter returns `true` for `null`.
2. Add it to the Swing panel (`EmailReader`: field, `getProperties`, `setProperties`, and `checkProperties` if it is required).
3. Add it to the web panel (`webadmin/web/plugin.js`: `defaults`, a field in the form, and `validate` if it is required).
4. Use it in `EmailPoller` or `EmailReceiver`.
5. Document it in the README's settings table.
6. Test that a channel saved with the previous version still deploys and keeps its behaviour.

## The settings panels

Both panels show the same settings and follow the same rules; there is no shared code, so keep them in sync by hand.

| Rule | Swing (`EmailReader`) | Web (`plugin.js`) |
| --- | --- | --- |
| Folder, Only unread, Mark as read shown for IMAP only | `updateProtocolFields()` | `visible: isImap` |
| Mark as read with *Delete after fetch* on | disabled | hidden |
| A default port (110, 143, 993, 995) follows protocol and SSL; a custom port stays | `protocolOrSslChanged()` | `syncPort()` |
| Host, a port from 1 to 65535 and username required | `checkProperties()` | `validate()` |
| Folder required for IMAP | not checked (an empty folder means `INBOX`) | `validate()` |
| Polling every 60 seconds for a new channel | the properties' constructor | `defaults()` |

**The web panel** is registered with `platform.registerConnectorPanel("Email Reader", "SOURCE", …)`. Engine-hosted web plugins are loaded as a single module, so `plugin.js` may only import the bare `@oie/web-ui` specifier, and it reads React from `platform.React` at render time, not at load time. `defaults()` must produce the same JSON shape as the Java properties (`@class`, `@version`, `pollConnectorProperties`, `sourceConnectorProperties`, and the fields).

## Logging and errors

- Errors of a poll go to the dashboard's error log (an `ErrorEvent` of type `SOURCE_CONNECTOR`) and to `mirth.log`.
- `EmailPoller` logs at INFO how many mails a poll found, and at ERROR a mail that could not be processed. The default `log4j2.properties` hides INFO; the README shows how to switch it on for `com.mirth.connect.connectors.email`.
- Log messages say POP3 or IMAP, the mailbox and the folder, never the password.

## Testing

```bash
cd connector
mvn test
```

`mvn package` runs the same tests. All are JUnit 5; none needs a mail server of your own.

| Test | Covers |
| --- | --- |
| `EmailPollerTextTest` | `htmlToText` (markup, scripts, lists, table cells, entities), `cleanText` (line endings, non-breaking spaces, blank lines) and `removeInvalidXmlChars`. |
| `EmailPollerXmlTest` | The XML a mail becomes: plain and HTML mail, multipart/alternative (plain wins, HTML as fallback), multipart/mixed (text parts joined, attachments skipped), charsets including an unknown one, and a `]]>` in the body. Parses mails from text with `MimeMessage`. |
| `EmailPollerSourceMapTest` | The source map: all variables for IMAP, the time zone of `sentDate`, and empty values for missing headers with POP3. |
| `EmailPollerGreenMailTest` | `EmailPoller.poll` against an in-process [GreenMail](https://greenmail-mail-test.github.io/greenmail/) POP3 and IMAP server: fetching, *Only unread*, *Mark as read*, *Delete after fetch*, and a failed dispatch (the handler returns `false` or throws), which leaves the mail on the server. |

SSL connections and real providers are not covered; try those by hand (below).

## Trying it in OIE

1. `mvn clean package` in `connector/`.
2. Install `connector/target/email-reader-connector-<version>.zip` on a **test** server (*Settings > Extensions > Install Extension*) and restart the service. Restart the Swing Administrator; in the web administrator, refresh with Ctrl+F5.
3. Use a **test mailbox**, never a real one: with POP3 and *Delete after fetch* off, every poll sends every mail again; with it on, mails are deleted.
4. Send test mails: plain text, HTML only, `multipart/alternative`, one with an attachment, one with umlauts in another charset.
5. Check the XML and the source map (*Mappings → Source Map*) in the message browser, the read flags or deletions in the mailbox, and the dashboard's error log for a wrong password or host.

## GitHub workflows

| Workflow | Does |
| --- | --- |
| `build.yml` | Builds and tests every push and pull request against OIE 4.6.0 (Java 17) and 4.7.0 (Java 17 and 21), with the engine jars from the engine's GitHub release (`.github/scripts/install-oie-jars.sh`, cached). Checks that the four version numbers agree (`check-versions.sh`) and keeps the zip as an artifact. A pushed `v*` tag also creates the GitHub release with the zip and its SHA-256 (`release.sh`) and then runs `catalog-pr.yml`. |
| `catalog-pr.yml` | On a published release, files a pull request to the [OIE Community Store catalog](https://github.com/gibson9583/oie-community-catalog) from the fork `<owner>/oie-community-catalog`, using `oie.json` at the tag. Needs the secret `CATALOG_TOKEN` (a classic PAT with `public_repo`); skipped without it. |
| `jira-issue.yml` | Creates a Jira issue for every new GitHub issue and comments its key. Needs the variables `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_PROJECT_KEY` and the secret `JIRA_API_TOKEN`. |

`main` is protected against force pushes and deletion (ruleset *Protect main*).

## Releasing

1. **Branch and pull request.** Reference the issue (`Closes #n`).
2. **Version.** Set the new version in all four places; `build.yml` fails when they differ (`bash .github/scripts/check-versions.sh` checks it locally):
   - `connector/pom.xml` (`<version>`)
   - `connector/src/main/resources/source.xml` (`<pluginVersion>`)
   - `oie.json` (`"version"`)
   - `connector/webadmin/plugin.json` (`"version"`)
3. **Engine versions.** `<mirthVersion>` in `source.xml` lists every OIE version the extension may load on (now `4.6.0,4.7.0`). OIE only loads an extension whose list contains its **exact** version, so a new OIE release needs a new connector release.
4. **Build and test** with `mvn clean package`, and try the zip in OIE.
5. **Merge** the pull request.
6. **Release** on GitHub with the zip and its checksum:

   ```bash
   cd connector/target
   sha256sum email-reader-connector-3.1.2.zip > email-reader-connector-3.1.2.zip.sha256
   gh release create v3.1.2 email-reader-connector-3.1.2.zip email-reader-connector-3.1.2.zip.sha256 \
     --target main --title "3.1.2: …" --notes-file notes.md
   ```

   Or push the tag (`git tag v3.1.2 && git push origin v3.1.2`): `build.yml` then builds, creates the release with the zip, its checksum and generated notes, and files the catalog pull request. Edit the notes afterwards. A release you create by hand first is left alone, apart from attaching the zip if it has none.

   Release notes say what changed for the user, how to upgrade (install over the old version within 3.x and restart; channel settings are kept), and the SHA-256.
7. **Catalog.** Publishing the release runs `catalog-pr.yml`. Check its pull request, and close earlier ones of this connector that are still open.
8. **Jira.** Closing a GitHub issue does not close its Jira issue: set it to *Done* yourself.
9. **Documentation.** Update the README when settings, the XML or the source map change.

## Conventions

- Every source file starts with the MPL 2.0 header.
- Java 11 language level. Runtime dependencies go into `lib/` and into `source.xml`; anything the engine already ships is `provided`.
- Never rename the connector, its classes or its package: saved channels depend on them.
- A mail leaves the server (deleted or marked) only after a successful dispatch.
- Transformation logic belongs in the channel, not in the connector: the XML stays small and simple.
- Error and log messages name the protocol, mailbox and folder, never the password.
- Comments explain *why*; keep them short.
