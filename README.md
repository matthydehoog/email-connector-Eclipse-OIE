# POP3 / IMAP Reader for Eclipse OIE

A **source connector type** for [Eclipse Open Integration Engine](https://openintegrationengine.org/) (tested on **4.6.0**) that polls a **POP3 or IMAP** mailbox and hands every mail to the channel as a small plain-text XML document. Choose "POP3 Reader" as the Source of a channel in the Swing client or the web administrator and pick the protocol (POP3 or IMAP) in its settings.

> Community extension. It is not part of, or endorsed by, the Eclipse OIE project.

The extension lives in [`connector/`](connector).

## What you get

- "POP3 Reader" in the Source connector type list of the **Swing client** and the **web administrator**.
- Settings per channel: protocol (POP3 or IMAP), host, port, SSL, username, password, *Delete after fetch* and, for IMAP, the folder plus *Only unread* and *Mark as read*, plus the engine's standard polling settings (interval, schedule, source queue, response, ...). The default polling interval is **60 seconds**.
- Every mail is dispatched into the channel as one raw message:

```xml
<email>
  <subject>Lab result 12345</subject>
  <from>lab@example.org</from>
  <to>intake@example.org</to>
  <sentDate>Thu Sep 17 08:12:00 CEST 2026</sentDate>
  <body><![CDATA[ ...plain text... ]]></body>
</email>
```

The body is plain text only: in a `multipart/alternative` mail the `text/plain` part wins, HTML is converted to text only when there is no plain-text part, attachments and other non-text parts are skipped, illegal XML characters are removed. Set the channel's **inbound data type** to XML (or Raw) to parse it in a transformer.

## Install

1. Download `pop3-reader-connector-<version>.zip` from the [Releases](../../releases) page (or build it, see below).
2. Settings -> Extensions -> **Install Extension**, choose the zip, restart the engine.
3. Restart the Swing client. In the web administrator do a hard refresh (Ctrl+F5).
4. Create a channel and choose **POP3 Reader** as the source connector type.

When upgrading, install the new zip over the old one and restart. If the installer refuses, uninstall the old version first and restart. **Channels created with a pre-release build of the connector (before 2.0.0) must be recreated** (the class name of the properties changed, see below).

## Settings

| Setting | Meaning | Default |
|---|---|---|
| Protocol | `POP3` or `IMAP` | `POP3` |
| Host | mail server, e.g. `pop.provider.com` or `imap.provider.com` | *(empty)* |
| Port | POP3: 995 (SSL) or 110. IMAP: 993 (SSL) or 143. A default port follows the protocol and SSL choice; a custom port is left alone | `995` |
| Use SSL | POP3S / IMAPS (encrypted connection) | on |
| Username / Password | mailbox credentials (stored in the channel, like any connector password) | *(empty)* |
| Folder | *IMAP only.* Folder to read | `INBOX` |
| Only unread | *IMAP only.* Fetch only mails that are not marked as read | on |
| Mark as read | *IMAP only.* Mark a mail as read once it was handed to the channel. Not shown when *Delete after fetch* is on | on |
| Delete after fetch | delete a mail from the server once it was handed to the channel | **off** |
| Polling | the engine's standard polling settings | every 60 s |

## Important behavior

- **POP3 has no memory of processed mail.** With *Delete after fetch* off, every poll dispatches every mail in the mailbox again. Test with a small test mailbox and switch it on once the channel works.
- **IMAP remembers what it has read.** With the defaults (*Only unread* and *Mark as read* on) every mail is fetched exactly once and stays in the mailbox as a read mail. A mail is only marked as read after the channel accepted it; a mail that could not be dispatched stays unread and is tried again on the next poll. Reading a mail does not mark it as read by itself.
- If you turn *Only unread* off and neither mark nor delete, every poll dispatches every mail in the folder again.
- **"Handled" means dispatched.** A mail is marked or deleted after it was accepted by the channel, not after the channel finished processing it. If dispatching fails the mail stays on the server and is tried again on the next poll.
- One mailbox (and for IMAP one folder) per channel. Channels saved with an earlier version keep working as POP3.
- IMAP uses a plain connection or IMAPS. STARTTLS and OAuth2 logins are not supported.

## Logging

The default `log4j2.properties` uses `rootLogger = ERROR`, so normal messages are hidden. Errors of a poll are always shown in the dashboard's error log and `mirth.log`. To see more, add to `<OIE_HOME>/conf/log4j2.properties` and restart:

```
logger.pop3c.name = com.mirth.connect.connectors.pop3
logger.pop3c.level = INFO
```

## Build

Requirements: a JDK 11+ (the runtime bundled with the engine, `<OIE_HOME>/jre`, includes `javac` and works as `JAVA_HOME`) and Maven 3.9+.

1. Install the engine jars into your local Maven repository under the coordinates `connector/pom.xml` expects:

   ```bash
   mvn install:install-file -Dfile="<OIE_HOME>/server-lib/mirth-server.jar"        -DgroupId=com.mirth.connect -DartifactId=server-api    -Dversion=4.6.0 -Dpackaging=jar
   mvn install:install-file -Dfile="<OIE_HOME>/server-lib/donkey/donkey-server.jar" -DgroupId=com.mirth.connect -DartifactId=donkey-server -Dversion=4.6.0 -Dpackaging=jar
   mvn install:install-file -Dfile="<OIE_HOME>/server-lib/donkey/donkey-model.jar"  -DgroupId=com.mirth.connect -DartifactId=donkey-model  -Dversion=4.6.0 -Dpackaging=jar
   mvn install:install-file -Dfile="<OIE_HOME>/client-lib/mirth-client.jar"         -DgroupId=com.mirth.connect -DartifactId=client        -Dversion=4.6.0 -Dpackaging=jar
   mvn install:install-file -Dfile="<OIE_HOME>/client-lib/mirth-client-core.jar"    -DgroupId=com.mirth.connect -DartifactId=client-core   -Dversion=4.6.0 -Dpackaging=jar
   ```

2. Build:

   ```bash
   cd connector
   mvn clean package
   ```

   This produces `connector/target/pop3-reader-connector-<version>.zip`:

   ```
   pop3-reader/
   ├── source.xml
   ├── pop3reader-shared.jar      (settings class, used by server and clients)
   ├── pop3reader-server.jar      (receiver + POP3/IMAP poller)
   ├── pop3reader-client.jar      (Swing settings panel)
   ├── webadmin/                  (web administrator panel: plugin.json + web/plugin.js)
   └── lib/                       (jakarta.mail, angus-mail and their activation libraries)
   ```

## Design notes (for developers)

- **Package name.** The settings class must live under `com.mirth.connect.connectors.*`. The engine's XStream security allow-list only accepts that family of packages, and a class outside it makes the Swing client fail to read any channel that uses the connector (and freeze while reporting it). That is why this connector does not use a `com.matthy...` package.
- **Folder name = `path`.** The `path` attribute in `source.xml` (`pop3-reader`) must equal the extension folder name; the client download servlet and the web-admin loader both build URLs from it.
- **Web administrator.** `webadmin/web/plugin.js` registers the panel with `platform.registerConnectorPanel("POP3 Reader", "SOURCE", ...)`. Engine-hosted web plugins are loaded as a single module, so it imports only the bare `@oie/web-ui` specifier and reads `platform.React` at render time.
- Mail parts are read through streams instead of `getContent()`, to avoid the mailcap clash with the engine's older `jakarta.mail 1.6.7` / `javax.activation`.

## License

Apache License 2.0, see [LICENSE](LICENSE).
