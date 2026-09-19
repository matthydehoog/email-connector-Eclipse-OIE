// POP3 Reader source connector - settings panel for the web administrator.
// Engine-hosted plugins are loaded as a single blob module, so only bare @oie/*
// imports (resolved through the page's import map) are available. The form
// building blocks come from @oie/web-ui; React comes from the platform object.
import {
  ConnectorForm,
  PollSection,
  YES_NO,
  requireFields,
  defaultSourceProperties,
  defaultPollProperties
} from "@oie/web-ui";

const PROPERTIES_CLASS = "com.mirth.connect.connectors.pop3.shared.Pop3ReceiverProperties";
const CONNECTOR_NAME = "POP3 Reader";

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
        host: "",
        port: "995",
        useSsl: true,
        username: "",
        password: "",
        deleteAfterFetch: false
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
            { section: "POP3 Reader Settings" },
            { key: "host", label: "POP3 Host", type: "text", width: "220px", tooltip: "Host name of the POP3 server, e.g. pop.provider.com" },
            { key: "port", label: "Port", type: "number", width: "90px", tooltip: "995 for POP3 over SSL, 110 for plain POP3" },
            { key: "useSsl", label: "Use SSL", type: "radio", options: YES_NO, tooltip: "POP3S (encrypted connection)" },
            { key: "username", label: "Username", type: "text", width: "220px" },
            { key: "password", label: "Password", type: "password", width: "220px" },
            {
              key: "deleteAfterFetch",
              label: "Delete After Fetch",
              type: "radio",
              options: YES_NO,
              tooltip: "Delete a mail from the server once it was handed to the channel. With No, every poll dispatches every mail in the mailbox again."
            }
          ]
        })
      );
    },

    // Mirrors the Swing panel (Pop3Reader.checkProperties).
    validate(properties) {
      const errors = requireFields(properties, [
        { key: "host", label: "POP3 Host" },
        { key: "port", label: "Port" },
        { key: "username", label: "Username" }
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
