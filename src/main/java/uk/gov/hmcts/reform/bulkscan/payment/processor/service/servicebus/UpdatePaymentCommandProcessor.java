package uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus;

import com.microsoft.azure.servicebus.IMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.PaymentMessageHandler;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.model.UpdatePaymentMessage;

import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Service
public class UpdatePaymentCommandProcessor extends PaymentCommandProcessor<UpdatePaymentMessage> {
    private static final Logger log = LoggerFactory.getLogger(UpdatePaymentCommandProcessor.class);

    public UpdatePaymentCommandProcessor(
        PaymentMessageHandler paymentMessageHandler,
        PaymentMessageParser paymentMessageParser
    ) {
        super(paymentMessageHandler, paymentMessageParser);
    }

    public MessageProcessingResult processCommand(IMessage message) {
        log.info("Started processing update payment message with ID {}", message.getMessageId());

        UpdatePaymentMessage payment = null;

        try {
            payment = paymentMessageParser.parse(message.getMessageBody(), UpdatePaymentMessage.class);
            paymentMessageHandler.updatePaymentCaseReference(payment);
            log.info(
                "Processed update payment message with ID {}. Envelope ID: {}",
                message.getMessageId(),
                payment.envelopeId
            );
            return new MessageProcessingResult(SUCCESS);
        } catch (InvalidMessageException ex) {
            log.error("Rejected update payment message with ID {}, because it's invalid", message.getMessageId(), ex);
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (Exception ex) {
            logMessageProcessingError(message, payment, ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
        }
    }

    private void logMessageProcessingError(
        IMessage message,
        UpdatePaymentMessage paymentMessage,
        Exception exception
    ) {

        String baseMessage = String.format(
            "Failed to process update payment message with ID %s.",
            message.getMessageId()
        );

        String fullMessage = paymentMessage != null
            ? baseMessage + String.format(
            " New Case Number: %s, Exception Record Ref: %s,Jurisdiction: %s",
            paymentMessage.newCaseRef,
            paymentMessage.exceptionRecordRef,
            paymentMessage.jurisdiction
        )
            : baseMessage;

        log.error(fullMessage, exception);
    }
}
