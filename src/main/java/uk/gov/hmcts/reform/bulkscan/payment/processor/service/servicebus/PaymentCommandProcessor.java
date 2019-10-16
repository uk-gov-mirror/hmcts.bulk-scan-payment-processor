package uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus;

import com.microsoft.azure.servicebus.IMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.PaymentMessageHandler;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.model.PaymentMessage;

public abstract class PaymentCommandProcessor<T extends PaymentMessage> {
    private static final Logger log = LoggerFactory.getLogger(PaymentCommandProcessor.class);

    protected final PaymentMessageHandler paymentMessageHandler;
    protected final PaymentMessageParser paymentMessageParser;

    protected PaymentCommandProcessor(
        PaymentMessageHandler paymentMessageHandler,
        PaymentMessageParser paymentMessageParser
    ) {
        this.paymentMessageHandler = paymentMessageHandler;
        this.paymentMessageParser = paymentMessageParser;
    }

    public abstract MessageProcessingResult processCommand(IMessage message);
}
