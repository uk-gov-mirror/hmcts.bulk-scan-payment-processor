package uk.gov.hmcts.reform.bulkscan.payment.processor.ccd;

import com.google.common.collect.ImmutableMap;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

// TODO: logging + tests
@Component
public class CcdClient {

    public static final Logger log = LoggerFactory.getLogger(CcdClient.class);

    private final CoreCaseDataApi ccdApi;
    private final CcdAuthenticatorFactory authenticatorFactory;

    public CcdClient(
        CoreCaseDataApi ccdApi,
        CcdAuthenticatorFactory authenticator
    ) {
        this.ccdApi = ccdApi;
        this.authenticatorFactory = authenticator;
    }

    public void unsetAwaitingProcessingFlag(
        String exceptionRecordCcdId,
        String service,
        String jurisdiction
    ) {
        CcdAuthenticator authenticator = authenticatorFactory.createForJurisdiction(jurisdiction);
        String caseTypeId = service.toUpperCase() + "_ExceptionRecord";
        String eventId = "stopWaitingForPaymentDCNProcessing";

        StartEventResponse startEventResponse =
            startEvent(authenticator, jurisdiction, caseTypeId, exceptionRecordCcdId, eventId);

        CaseDataContent caseDataContent = CaseDataContent.builder()
            .data(ImmutableMap.of("TODO", "No"))
            .event(Event.builder().summary("TODO").id(startEventResponse.getEventId()).build())
            .eventToken(startEventResponse.getToken())
            .build();

        submitEvent(authenticator, jurisdiction, caseTypeId, exceptionRecordCcdId, caseDataContent);
    }

//    // TODO: should it stay public?
//    public CcdAuthenticator authenticateJurisdiction(String jurisdiction) {
//        return authenticatorFactory.createForJurisdiction(jurisdiction);
//    }

    public StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseTypeId,
        String caseRef,
        String eventTypeId
    ) {
        return ccdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            eventTypeId
        );
    }

    public void submitEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseTypeId,
        String caseRef,
        CaseDataContent caseDataContent
    ) {
        ccdApi.submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            true,
            caseDataContent
        );
    }
}

