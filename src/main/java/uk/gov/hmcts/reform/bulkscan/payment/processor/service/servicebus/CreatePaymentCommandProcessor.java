package uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus;

import com.microsoft.azure.servicebus.IMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.payment.processor.client.payhub.PayHubClientException;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResult;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.PaymentMessageHandler;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.model.CreatePaymentMessage;

import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

@Service
public class CreatePaymentCommandProcessor extends PaymentCommandProcessor<CreatePaymentMessage> {
    private static final Logger log = LoggerFactory.getLogger(CreatePaymentCommandProcessor.class);

    public CreatePaymentCommandProcessor(
        PaymentMessageHandler paymentMessageHandler,
        PaymentMessageParser paymentMessageParser
    ) {
        super(paymentMessageHandler, paymentMessageParser);
    }

    public MessageProcessingResult processCommand(IMessage message) {
        log.info("Started processing payment message with ID {}", message.getMessageId());

        CreatePaymentMessage payment = null;

        try {
            payment = paymentMessageParser.parse(message.getMessageBody(), CreatePaymentMessage.class);
            paymentMessageHandler.handlePaymentMessage(payment);
            log.info(
                "Processed payment message with ID {}. Envelope ID: {}",
                message.getMessageId(),
                payment.envelopeId
            );
            return new MessageProcessingResult(SUCCESS);
        } catch (InvalidMessageException ex) {
            log.error("Rejected payment message with ID {}, because it's invalid", message.getMessageId(), ex);
            return new MessageProcessingResult(UNRECOVERABLE_FAILURE, ex);
        } catch (PayHubClientException ex) {
            if (ex.getStatus() == HttpStatus.CONFLICT) {
                log.info(
                    "Payment Processed with Http 409, message ID {}. Envelope ID: {}",
                    message.getMessageId(),
                    payment == null ? "" : payment.envelopeId
                );
                return new MessageProcessingResult(SUCCESS, ex);
            }
            logMessageProcessingError(message, payment, ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE, ex);
        } catch (Exception ex) {
            logMessageProcessingError(message, payment, ex);
            return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE);
        }
    }

    private void logMessageProcessingError(IMessage message, CreatePaymentMessage paymentMessage, Exception exception) {
        String baseMessage = String.format("Failed to process payment message with ID %s.", message.getMessageId());

        String fullMessage = paymentMessage != null
            ? baseMessage + String.format(
            " CCD Case Number: %s, Jurisdiction: %s",
            paymentMessage.ccdReference,
            paymentMessage.jurisdiction
        )
            : baseMessage;

        log.error(fullMessage, exception);
    }
}
