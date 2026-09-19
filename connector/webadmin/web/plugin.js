// POP3 Reader source connector (POP3 and IMAP) - settings panel for the web administrator.
// Engine-hosted plugins are loaded as a single blob module, so only bare @oie/*
// imports (resolved through the page's import map) are available. The form
// building blocks come from @oie/web-ui; React comes from the platform object.
import {
  ConnectorForm,
  PollSection,
  YES_NO,
  asBool,
  requireFields,
  defaultSourceProperties,
  defaultPollProperties
} from "@oie/web-ui";

const PROPERTIES_CLASS = "com.mirth.connect.connectors.pop3.shared.Pop3ReceiverProperties";
const CONNECTOR_NAME = "POP3 Reader";

const isImap = (p) => p.mailProtocol === "IMAP";

// 995/110 (POP3) and 993/143 (IMAP) follow the protocol and SSL choice; a custom port is left alone.
const DEFAULT_PORTS = { POP3: { true: "995", false: "110" }, IMAP: { true: "993", false: "143" } };
const isDefaultPort = (port) => ["110", "143", "993", "995"].includes(String(port ?? "").trim());
function syncPort(properties) {
  if (isDefaultPort(properties.port)) {
    properties.port = DEFAULT_PORTS[isImap(properties) ? "IMAP" : "POP3"][String(asBool(properties.useSsl))];
  }
}

export function createPop3Reader(platform) {
  return {
    defaults(version) {
      return {
        "@class": PROPERTIES_CLASS,
        "@version": version,
        pluginProperties: null,
        // The engine default of 5 seconds is far too eager for a mailbox.
        pollConnectorProperties: { ...defaultPollProperties(version), pollingFrequency: 60000 },
        sourceConnectorProperties: defaultSourceProperties(version),
        mailProtocol: "POP3",
        host: "",
        port: "995",
        useSsl: true,
        username: "",
        password: "",
        deleteAfterFetch: false,
        folder: "INBOX",
        unreadOnly: true,
        markAsRead: true
      };
    },

    component({ properties, onChange }) {
      // platform.React is set by the shell at boot, so read it at render time.
      const React = platform.React;
      return React.createElement(
        "div",
        null,
        React.createElement(PollSection, { properties, onChange }),
        React.createElement(ConnectorForm, {
          properties,
          onChange,
          fields: [
            { section: "Mail Server Settings" },
            {
              key: "mailProtocol",
              label: "Protocol",
              type: "select",
              width: "110px",
              options: ["POP3", "IMAP"],
              refresh: true,
              onSet: syncPort,
              tooltip: "POP3 downloads what is in the mailbox. IMAP can read one folder and keep track of read mails."
            },
            { key: "host", label: "Host", type: "text", width: "220px", tooltip: "Host name of the mail server, e.g. pop.provider.com or imap.provider.com" },
            { key: "port", label: "Port", type: "number", width: "90px", tooltip: "POP3: 995 (SSL) or 110. IMAP: 993 (SSL) or 143." },
            { key: "useSsl", label: "Use SSL", type: "radio", options: YES_NO, onSet: syncPort, tooltip: "POP3S / IMAPS (encrypted connection)" },
            { key: "username", label: "Username", type: "text", width: "220px" },
            { key: "password", label: "Password", type: "password", width: "220px" },
            { key: "folder", label: "Folder", type: "text", width: "220px", visible: isImap, tooltip: "IMAP folder to read, e.g. INBOX" },
            {
              key: "unreadOnly",
              label: "Only Unread",
              type: "radio",
              options: YES_NO,
              visible: isImap,
              tooltip: "Fetch only mails that are not marked as read. With No, every poll dispatches every mail in the folder again (unless they are deleted)."
            },
            {
              key: "markAsRead",
              label: "Mark as Read",
              type: "radio",
              options: YES_NO,
              visible: (p) => isImap(p) && !asBool(p.deleteAfterFetch),
              tooltip: "Mark a mail as read once it was handed to the channel. Together with Only Unread this makes sure every mail is fetched once."
            },
            {
              key: "deleteAfterFetch",
              label: "Delete After Fetch",
              type: "radio",
              options: YES_NO,
              refresh: true,
              tooltip: "Delete a mail from the server once it was handed to the channel. POP3: with No, every poll dispatches every mail in the mailbox again."
            }
          ]
        })
      );
    },

    // Mirrors the Swing panel (Pop3Reader.checkProperties).
    validate(properties) {
      const errors = requireFields(properties, [
        { key: "host", label: "Host" },
        { key: "port", label: "Port" },
        { key: "username", label: "Username" },
        { key: "folder", label: "Folder", when: isImap }
      ]);
      const port = Number(String(properties.port ?? "").trim());
      if (!errors.some((e) => e.key === "port") && !(Number.isInteger(port) && port >= 1 && port <= 65535)) {
        errors.push({ key: "port", label: "Port (1-65535)" });
      }
      return errors;
    }
  };
}

export function register(platform) {
  platform.registerConnectorPanel(CONNECTOR_NAME, "SOURCE", createPop3Reader(platform));
}
