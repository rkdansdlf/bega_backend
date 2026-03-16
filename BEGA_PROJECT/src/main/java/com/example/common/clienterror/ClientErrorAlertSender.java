package com.example.common.clienterror;

public interface ClientErrorAlertSender {

    ClientErrorAlertChannel channel();

    boolean isConfigured(ClientErrorMonitoringProperties.Alerts alerts);

    ClientErrorAlertDeliveryResult send(ClientErrorAlertPayload payload, ClientErrorMonitoringProperties.Alerts alerts);
}
