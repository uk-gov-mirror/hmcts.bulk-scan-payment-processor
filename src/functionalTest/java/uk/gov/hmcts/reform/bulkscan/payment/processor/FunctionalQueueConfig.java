package uk.gov.hmcts.reform.bulkscan.payment.processor;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FunctionalQueueConfig {

    @Value("${queue.payments.write-connection-string}")
    private String paymentsQueueWriteConnectionString;

    @Bean("payments")
    public QueueClient paymentsWriteClient() throws ServiceBusException, InterruptedException {
        return new QueueClient(
            new ConnectionStringBuilder(paymentsQueueWriteConnectionString),
            ReceiveMode.PEEKLOCK
        );
    }
}