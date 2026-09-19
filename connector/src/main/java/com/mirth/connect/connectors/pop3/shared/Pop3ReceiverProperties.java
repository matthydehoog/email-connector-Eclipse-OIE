package com.mirth.connect.connectors.pop3.shared;

import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.SourceConnectorProperties;
import com.mirth.connect.donkey.model.channel.SourceConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;

/**
 * Settings of the "POP3 Reader" source connector. Shared between the server,
 * the Swing administrator and (as JSON) the web administrator.
 */
public class Pop3ReceiverProperties extends ConnectorProperties implements PollConnectorPropertiesInterface, SourceConnectorPropertiesInterface {
    public static final String NAME = "POP3 Reader";

    private PollConnectorProperties pollConnectorProperties;
    private SourceConnectorProperties sourceConnectorProperties;

    private String host;
    private String port;
    private boolean useSsl;
    private String username;
    private String password;
    private boolean deleteAfterFetch;

    public Pop3ReceiverProperties() {
        pollConnectorProperties = new PollConnectorProperties();
        // The engine default of 5 seconds is far too eager for a mailbox.
        pollConnectorProperties.setPollingFrequency(60000);
        sourceConnectorProperties = new SourceConnectorProperties();

        host = "";
        port = "995";
        useSsl = true;
        username = "";
        password = "";
        deleteAfterFetch = false;
    }

    @Override
    public String getProtocol() {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isDeleteAfterFetch() {
        return deleteAfterFetch;
    }

    public void setDeleteAfterFetch(boolean deleteAfterFetch) {
        this.deleteAfterFetch = deleteAfterFetch;
    }

    @Override
    public String toFormattedString() {
        return null;
    }

    @Override
    public PollConnectorProperties getPollConnectorProperties() {
        return pollConnectorProperties;
    }

    @Override
    public SourceConnectorProperties getSourceConnectorProperties() {
        return sourceConnectorProperties;
    }

    @Override
    public boolean canBatch() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(host, port, useSsl, username, deleteAfterFetch);
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {}
    // @formatter:on

    @Override
    public void migrate3_1_0(DonkeyElement element) {
        super.migrate3_1_0(element);
    }

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = super.getPurgedProperties();
        purgedProperties.put("pollConnectorProperties", pollConnectorProperties.getPurgedProperties());
        purgedProperties.put("sourceConnectorProperties", sourceConnectorProperties.getPurgedProperties());
        purgedProperties.put("useSsl", useSsl);
        purgedProperties.put("deleteAfterFetch", deleteAfterFetch);
        return purgedProperties;
    }
}
