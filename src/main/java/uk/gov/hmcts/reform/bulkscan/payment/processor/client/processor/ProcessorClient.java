package uk.gov.hmcts.reform.bulkscan.payment.processor.client.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.payment.processor.client.processor.request.PaymentRequest;
import uk.gov.hmcts.reform.bulkscan.payment.processor.service.servicebus.model.PaymentInfo;

import java.util.List;

@Service
public class ProcessorClient {
    private static final Logger logger = LoggerFactory.getLogger(ProcessorClient.class);
    private final AuthTokenGenerator authTokenGenerator;
    private final BulkScanProcessorApiProxy proxy;
    private final RetryTemplate retryTemplate;

    public ProcessorClient(AuthTokenGenerator authTokenGenerator, BulkScanProcessorApiProxy proxy,
                           RetryTemplate retryTemplate) {
        this.authTokenGenerator = authTokenGenerator;
        this.proxy = proxy;
        this.retryTemplate = retryTemplate;
    }

    @Async("AsyncExecutor")
    public void updatePayments(List<PaymentInfo> payments) {
        PaymentRequest request = new PaymentRequest(payments);
        String authToken = authTokenGenerator.generate();
        try {
            retryTemplate.execute(context -> {
                logger.info("Started to update payment DCNS {} ", request.payments);
                proxy.updateStatus(authToken, request);
                logger.info("Updated payment DCNS {} ", request.payments);
                return true;
            });
        } catch (Exception exception) {
            logger.error("Exception on payment status update for DCNS {} ", payments, exception);
        }

    }
}
