package uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.exceptions.UnknownMessageProcessingResultException;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType;


@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class PaymentMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageProcessor.class);

    private final CreatePaymentCommandProcessor createPaymentCommandProcessor;
    private final UpdatePaymentCommandProcessor updatePaymentCommandProcessor;
    private final IMessageReceiver messageReceiver;
    private final int maxDeliveryCount;

    public PaymentMessageProcessor(
        CreatePaymentCommandProcessor createPaymentCommandProcessor,
        UpdatePaymentCommandProcessor updatePaymentCommandProcessor,
        IMessageReceiver messageReceiver,
        @Value("${azure.servicebus.payments.max-delivery-count}") int maxDeliveryCount
    ) {
        this.createPaymentCommandProcessor = createPaymentCommandProcessor;
        this.updatePaymentCommandProcessor = updatePaymentCommandProcessor;
        this.messageReceiver = messageReceiver;
        this.maxDeliveryCount = maxDeliveryCount;
    }

    /**
     * Reads and processes next message from the queue.
     *
     * @return false if there was no message to process. Otherwise true.
     */
    public boolean processNextMessage() throws ServiceBusException, InterruptedException {
        IMessage message = messageReceiver.receive();
        if (message != null) {
            if (message.getLabel() == null) {
                deadLetterTheMessage(message, "Missing label", null);
            } else {
                switch (message.getLabel()) {
                    case "CREATE":
                        var createResult = createPaymentCommandProcessor.processCommand(message);
                        tryFinaliseProcessedMessage(message, createResult);
                        break;
                    case "UPDATE":
                        var updateResult = updatePaymentCommandProcessor.processCommand(message);
                        tryFinaliseProcessedMessage(message, updateResult);
                        break;
                    default:
                        deadLetterTheMessage(message, "Unrecognised message type: " + message.getLabel(), null);
                }
            }
        } else {
            log.info("No payment messages to process by payment processor!!");
        }

        return message != null;
    }

    private void tryFinaliseProcessedMessage(IMessage message, MessageProcessingResult processingResult) {
        try {
            finaliseProcessedMessage(message, processingResult);
        } catch (InterruptedException ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            logMessageFinaliseError(message, processingResult.resultType, ex);
        }
    }

    private void finaliseProcessedMessage(
        IMessage message,
        MessageProcessingResult processingResult
    ) throws InterruptedException, ServiceBusException {

        switch (processingResult.resultType) {
            case SUCCESS:
                messageReceiver.complete(message.getLockToken());
                log.info("Payment Message with ID {} has been completed", message.getMessageId());
                break;
            case UNRECOVERABLE_FAILURE:
                deadLetterTheMessage(
                    message,
                    "Payment Message processing error",
                    processingResult.exception.getMessage()
                );
                break;
            case POTENTIALLY_RECOVERABLE_FAILURE:
                deadLetterIfMaxDeliveryCountIsReached(message);
                break;
            default:
                throw new UnknownMessageProcessingResultException(
                    "Unknown payment message processing result type: " + processingResult.resultType
                );
        }
    }

    private void deadLetterIfMaxDeliveryCountIsReached(IMessage message)
        throws InterruptedException, ServiceBusException {

        int deliveryCount = (int) message.getDeliveryCount() + 1;

        if (deliveryCount < maxDeliveryCount) {
            // do nothing - let the message lock expire
            log.info(
                "Allowing payment message with ID {} to return to queue (delivery attempt {})",
                message.getMessageId(),
                deliveryCount
            );
        } else {
            deadLetterTheMessage(
                message,
                "Too many deliveries",
                "Reached limit of message delivery count of " + deliveryCount
            );
        }
    }

    private void deadLetterTheMessage(
        IMessage message,
        String reason,
        String description
    ) throws InterruptedException, ServiceBusException {
        messageReceiver.deadLetter(
            message.getLockToken(),
            reason,
            description
        );

        log.info(
            "Payment Message with ID {} has been dead-lettered, reason {}, description {}",
            message.getMessageId(),
            reason,
            description
        );
    }

    private void logMessageFinaliseError(
        IMessage message,
        MessageProcessingResultType processingResultType,
        Exception ex
    ) {
        log.error(
            "Failed to process payment message with ID {}. Processing result: {}",
            message.getMessageId(),
            processingResultType,
            ex
        );
    }
}
