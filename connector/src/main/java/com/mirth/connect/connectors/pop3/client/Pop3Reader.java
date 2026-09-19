package com.mirth.connect.connectors.pop3.client;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthCheckBox;
import com.mirth.connect.client.ui.components.MirthComboBox;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.connectors.pop3.shared.Pop3ReceiverProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;

/** Swing settings panel of the "POP3 Reader" source connector (POP3 and IMAP). */
public class Pop3Reader extends ConnectorSettingsPanel {

    public Pop3Reader() {
        initComponents();
        initLayout();
        updateProtocolFields();
    }

    @Override
    public String getConnectorName() {
        return new Pop3ReceiverProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        Pop3ReceiverProperties properties = new Pop3ReceiverProperties();

        properties.setMailProtocol(isImapSelected() ? Pop3ReceiverProperties.MAIL_IMAP : Pop3ReceiverProperties.MAIL_POP3);
        properties.setHost(hostField.getText().trim());
        properties.setPort(portField.getText().trim());
        properties.setUseSsl(sslCheckBox.isSelected());
        properties.setUsername(usernameField.getText().trim());
        properties.setPassword(new String(passwordField.getPassword()));
        properties.setFolder(folderField.getText().trim());
        properties.setUnreadOnly(unreadCheckBox.isSelected());
        properties.setMarkAsRead(markReadCheckBox.isSelected());
        properties.setDeleteAfterFetch(deleteCheckBox.isSelected());

        return properties;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        Pop3ReceiverProperties props = (Pop3ReceiverProperties) properties;

        // The listeners must not rewrite the port while the saved values are being loaded.
        loading = true;
        try {
            protocolComboBox.setSelectedItem(props.getMailProtocol());
            hostField.setText(props.getHost());
            portField.setText(props.getPort());
            sslCheckBox.setSelected(props.isUseSsl());
            usernameField.setText(props.getUsername());
            passwordField.setText(props.getPassword());
            folderField.setText(props.getFolder());
            unreadCheckBox.setSelected(props.isUnreadOnly());
            markReadCheckBox.setSelected(props.isMarkAsRead());
            deleteCheckBox.setSelected(props.isDeleteAfterFetch());
        } finally {
            loading = false;
        }
        updateProtocolFields();
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

        if (props.isImap() && folderField.getText().trim().isEmpty()) {
            valid = false;
            if (highlight) {
                folderField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        return valid;
    }

    @Override
    public void resetInvalidProperties() {
        hostField.setBackground(null);
        portField.setBackground(null);
        usernameField.setBackground(null);
        folderField.setBackground(null);
    }

    private static boolean isValidPort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isImapSelected() {
        return Pop3ReceiverProperties.MAIL_IMAP.equals(protocolComboBox.getSelectedItem());
    }

    /** Shows the IMAP-only fields for IMAP. Marking as read is pointless when the mail is deleted anyway. */
    private void updateProtocolFields() {
        boolean imap = isImapSelected();
        folderLabel.setVisible(imap);
        folderField.setVisible(imap);
        unreadLabel.setVisible(imap);
        unreadCheckBox.setVisible(imap);
        markReadLabel.setVisible(imap);
        markReadCheckBox.setVisible(imap);
        markReadCheckBox.setEnabled(!deleteCheckBox.isSelected());
        revalidate();
        repaint();
    }

    /** A default port (110/143/993/995) follows the protocol and SSL choice; a custom port is left alone. */
    private void protocolOrSslChanged() {
        if (!loading && Pop3ReceiverProperties.isDefaultPort(portField.getText().trim())) {
            String mailProtocol = isImapSelected() ? Pop3ReceiverProperties.MAIL_IMAP : Pop3ReceiverProperties.MAIL_POP3;
            portField.setText(Pop3ReceiverProperties.defaultPort(mailProtocol, sslCheckBox.isSelected()));
        }
        updateProtocolFields();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        protocolLabel = new JLabel("Protocol:");
        protocolComboBox = new MirthComboBox<String>();
        protocolComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { Pop3ReceiverProperties.MAIL_POP3, Pop3ReceiverProperties.MAIL_IMAP }));
        protocolComboBox.setToolTipText("POP3 downloads what is in the mailbox. IMAP can read one folder and keep track of read mails.");
        protocolComboBox.addActionListener(evt -> protocolOrSslChanged());

        hostLabel = new JLabel("Host:");
        hostField = new MirthTextField();
        hostField.setToolTipText("Host name of the mail server, e.g. pop.provider.com or imap.provider.com");

        portLabel = new JLabel("Port:");
        portField = new MirthTextField();
        portField.setToolTipText("POP3: 995 (SSL) or 110. IMAP: 993 (SSL) or 143.");

        sslLabel = new JLabel("Use SSL:");
        sslCheckBox = new MirthCheckBox("Encrypted connection (POP3S / IMAPS)");
        sslCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        sslCheckBox.addActionListener(evt -> protocolOrSslChanged());

        usernameLabel = new JLabel("Username:");
        usernameField = new MirthTextField();

        passwordLabel = new JLabel("Password:");
        passwordField = new MirthPasswordField();

        folderLabel = new JLabel("Folder:");
        folderField = new MirthTextField();
        folderField.setToolTipText("IMAP folder to read, e.g. INBOX");

        unreadLabel = new JLabel("Only unread:");
        unreadCheckBox = new MirthCheckBox("Fetch only mails that are not marked as read");
        unreadCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        unreadCheckBox.setToolTipText("With this off, every poll dispatches every mail in the folder again (unless they are deleted)");

        markReadLabel = new JLabel("Mark as read:");
        markReadCheckBox = new MirthCheckBox("Mark a mail as read once it was handed to the channel");
        markReadCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        markReadCheckBox.setToolTipText("Together with Only unread this makes sure every mail is fetched once. Not needed when the mail is deleted.");

        deleteLabel = new JLabel("Delete after fetch:");
        deleteCheckBox = new MirthCheckBox("Delete a mail from the server once it was handed to the channel");
        deleteCheckBox.setBackground(UIConstants.BACKGROUND_COLOR);
        deleteCheckBox.setToolTipText("POP3: with this off, every poll dispatches every mail in the mailbox again");
        deleteCheckBox.addActionListener(evt -> updateProtocolFields());
    }

    private void initLayout() {
        // No "fill": with it, MigLayout stretches every row and column over the free space.
        setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3, gap 6 6", "6[]13[]"));

        add(protocolLabel, "right");
        add(protocolComboBox, "w 75!, wrap");
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
        add(folderLabel, "right");
        add(folderField, "w 200!, wrap");
        add(unreadLabel, "right");
        add(unreadCheckBox, "wrap");
        add(markReadLabel, "right");
        add(markReadCheckBox, "wrap");
        add(deleteLabel, "right");
        add(deleteCheckBox, "wrap");
    }

    private boolean loading;

    private JLabel protocolLabel;
    private MirthComboBox<String> protocolComboBox;
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
    private JLabel folderLabel;
    private MirthTextField folderField;
    private JLabel unreadLabel;
    private MirthCheckBox unreadCheckBox;
    private JLabel markReadLabel;
    private MirthCheckBox markReadCheckBox;
    private JLabel deleteLabel;
    private MirthCheckBox deleteCheckBox;
}
