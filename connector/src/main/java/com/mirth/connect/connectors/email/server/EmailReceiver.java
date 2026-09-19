package com.mirth.connect.connectors.email.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.email.shared.EmailReceiverProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DispatchResult;
import com.mirth.connect.donkey.server.channel.PollConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/**
 * Source connector that polls one POP3 or IMAP mailbox and hands every mail to the
 * channel as a plain-text XML envelope. Scheduling comes from the standard
 * polling settings of the source connector (PollConnector).
 */
public class EmailReceiver extends PollConnector {
    private final Logger logger = LogManager.getLogger(getClass());

    private final EventController eventController = ControllerFactory.getFactory().createEventController();
    private EmailReceiverProperties connectorProperties;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        connectorProperties = (EmailReceiverProperties) getConnectorProperties();
        dispatchStatus(ConnectionStatusEventType.IDLE);
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {}

    @Override
    public void onStop() throws ConnectorTaskException {}

    @Override
    public void onHalt() throws ConnectorTaskException {}

    @Override
    public void handleRecoveredResponse(DispatchResult dispatchResult) {
        finishDispatch(dispatchResult);
    }

    @Override
    protected void poll() throws InterruptedException {
        dispatchStatus(ConnectionStatusEventType.READING);

        try {
            int port;
            try {
                port = Integer.parseInt(connectorProperties.getPort().trim());
            } catch (NumberFormatException e) {
                reportError("Invalid " + connectorProperties.getMailProtocol() + " port \"" + connectorProperties.getPort() + "\"", e);
                return;
            }

            new EmailPoller(connectorProperties, port).poll(this::dispatchMail);
        } catch (Throwable t) {
            reportError(connectorProperties.getMailProtocol() + " poll of " + connectorProperties.getUsername() + "@" + connectorProperties.getHost() + " failed", t);
        } finally {
            dispatchStatus(ConnectionStatusEventType.IDLE);
        }
    }

    /** Returns true when the mail was handed to the channel (safe to delete it from the server). */
    private boolean dispatchMail(String rawXml) {
        if (isTerminated()) {
            return false;
        }

        DispatchResult dispatchResult = null;
        try {
            dispatchResult = dispatchRawMessage(new RawMessage(rawXml));
            return true;
        } catch (ChannelException e) {
            // The engine has already logged the reason; leave the mail on the server.
            return false;
        } finally {
            finishDispatch(dispatchResult);
        }
    }

    private void dispatchStatus(ConnectionStatusEventType type) {
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getSourceName(), type));
    }

    private void reportError(String message, Throwable t) {
        eventController.dispatchEvent(new ErrorEvent(getChannelId(), getMetaDataId(), null, ErrorEventType.SOURCE_CONNECTOR, getSourceName(), connectorProperties.getName(), message, t));
        logger.error(message + " (channel " + getChannelId() + ")", t);
    }
}
