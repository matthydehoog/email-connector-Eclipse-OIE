package com.mirth.connect.connectors.pop3.client;

import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.connectors.pop3.shared.Pop3ReceiverProperties;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthCheckBox;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;

/** Swing settings panel of the "POP3 Reader" source connector. */
public class Pop3Reader extends ConnectorSettingsPanel {

    public Pop3Reader() {
        initComponents();
        initLayout();
    }

    @Override
    public String getConnectorName() {
        return new Pop3ReceiverProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        Pop3ReceiverProperties properties = new Pop3ReceiverProperties();

        properties.setHost(hostField.getText().trim());
        properties.setPort(portField.getText().trim());
        properties.setUseSsl(sslCheckBox.isSelected());
        properties.setUsername(usernameField.getText().trim());
        properties.setPassword(new String(passwordField.getPassword()));
        properties.setDeleteAfterFetch(deleteCheckBox.isSelected());

        return properties;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        Pop3ReceiverProperties props = (Pop3ReceiverProperties) properties;

        hostField.setText(props.getHost());
        portField.setText(props.getPort());
        sslCheckBox.setSelected(props.isUseSsl());
        usernameField.setText(props.getUsername());
        passwordField.setText(props.getPassword());
        deleteCheckBox.setSelected(props.isDeleteAfterFetch());
    }

    @Override
    public ConnectorProperties getDefaults() {
        return new Pop3ReceiverProperties();
    }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        Pop3ReceiverProperties props = (Pop3ReceiverProperties) properties;

        boolean valid = true;

        if (props.getHost().isEmpty()) {
            valid = false;
            if (highlight) {
                hostField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (!isValidPort(props.getPort())) {
            valid = false;
            if (highlight) {
                portField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (props.getUsername().isEmpty()) {
            valid = false;
            if (highlight) {
                usernameField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        hostField.setBackground(null);
        portField.setBackground(null);
        usernameField.setBackground(null);
    }

    private static boolean isValidPort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        hostLabel = new JLabel("POP3 Host:");
        hostField = new MirthTextField();
        hostField.setToolTipText("Host name of the POP3 server, e.g. pop.provider.com");

        portLabel = new JLabel("Port:");
        portField = new MirthTextField();
        portField.setToolTipText("995 for POP3 over SSL, 110 for plain POP3");

        sslLabel = new JLabel("Use SSL:");
        sslCheckBox = new MirthCheckBox("POP3S (encrypted connection)");
        sslCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);

        usernameLabel = new JLabel("Username:");
        usernameField = new MirthTextField();

        passwordLabel = new JLabel("Password:");
        passwordField = new MirthPasswordField();

        deleteLabel = new JLabel("Delete after fetch:");
        deleteCheckBox = new MirthCheckBox("Delete a mail from the server once it was handed to the channel");
        deleteCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        deleteCheckBox.setToolTipText("With this off, every poll dispatches every mail in the mailbox again");
    }

    private void initLayout() {
        // No "fill": with it, MigLayout stretches every row and column over the free space.
        setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3, gap 6 6", "6[]13[]"));

        add(hostLabel, "right");
        add(hostField, "w 200!, wrap");
        add(portLabel, "right");
        add(portField, "w 75!, wrap");
        add(sslLabel, "right");
        add(sslCheckBox, "wrap");
        add(usernameLabel, "right");
        add(usernameField, "w 200!, wrap");
        add(passwordLabel, "right");
        add(passwordField, "w 200!, wrap");
        add(deleteLabel, "right");
        add(deleteCheckBox, "wrap");
    }

    private JLabel hostLabel;
    private MirthTextField hostField;
    private JLabel portLabel;
    private MirthTextField portField;
    private JLabel sslLabel;
    private MirthCheckBox sslCheckBox;
    private JLabel usernameLabel;
    private MirthTextField usernameField;
    private JLabel passwordLabel;
    private MirthPasswordField passwordField;
    private JLabel deleteLabel;
    private MirthCheckBox deleteCheckBox;
}
