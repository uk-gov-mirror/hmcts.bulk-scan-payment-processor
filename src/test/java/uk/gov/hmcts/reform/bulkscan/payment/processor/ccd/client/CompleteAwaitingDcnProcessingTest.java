package uk.gov.hmcts.reform.bulkscan.payment.processor.ccd.client;

import com.microsoft.applicationinsights.core.dependencies.google.common.collect.ImmutableMap;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.payment.processor.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.payment.processor.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.payment.processor.ccd.CcdClient;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CompleteAwaitingDcnProcessingTest {

    private static final String AWAITING_DCN_PROCESSING_FIELD_NAME = "awaitingPaymentDCNProcessing";
    private static final String COMPLETE_AWAITING_PROCESSING_EVENT_ID = "completeAwaitingPaymentDCNProcessing";
    private static final String EVENT_SUMMARY = "Complete payment DCN processing";

    @Mock
    private CoreCaseDataApi ccdApi;

    @Mock
    private CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private CcdClient ccdClient;

    @BeforeEach
    void setUp() {
        ccdClient = new CcdClient(ccdApi, ccdAuthenticatorFactory);
    }

    @Test
    void should_start_and_submit_event_in_ccd() {
        // given
        String exceptionRecordId = "1231244243242343";
        String service = "bulkscan";
        String jurisdiction = "BULKSCAN";
        String serviceToken = "serviceToken1";
        String userId = "userId1";
        String userToken = "userToken1";
        String eventToken = "eventToken1";

        setUpCcdAuthenticationFactory(userId, userToken, serviceToken);

        StartEventResponse startEventResponse = mockStartEventResponse(eventToken);
        given(ccdApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .willReturn(startEventResponse);

        // when
        ccdClient.completeAwaitingDcnProcessing(exceptionRecordId, service, jurisdiction);

        // then
        verify(ccdAuthenticatorFactory).createForJurisdiction(jurisdiction);

        String expectedCaseTypeId = "BULKSCAN_ExceptionRecord";

        verify(ccdApi).startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            expectedCaseTypeId,
            exceptionRecordId,
            COMPLETE_AWAITING_PROCESSING_EVENT_ID
        );

        ArgumentCaptor<CaseDataContent> caseDataContentCaptor = ArgumentCaptor.forClass(CaseDataContent.class);
        verify(ccdApi).submitEventForCaseWorker(
            eq(userToken),
            eq(serviceToken),
            eq(userId),
            eq(jurisdiction),
            eq(expectedCaseTypeId),
            eq(exceptionRecordId),
            eq(true),
            caseDataContentCaptor.capture()
        );

        assertThat(caseDataContentCaptor.getValue()).isEqualToComparingFieldByFieldRecursively(
            getExpectedCaseDataContentForSubmitEvent(exceptionRecordId, eventToken)
        );
    }

    @Test
    void should_fail_when_when_start_event_fails() {
        // given
        setUpCcdAuthenticationFactory("userId1", "userToken1", "serviceToken1");

        FeignException startEventException = new FeignException.InternalServerError("Test exception", new byte[]{});

        willThrow(startEventException)
            .given(ccdApi)
            .startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any());

        // then
        assertThatThrownBy(() ->
            ccdClient.completeAwaitingDcnProcessing("1231244243242343", "bulkscan", "BULKSCAN")
        ).isSameAs(startEventException);
    }

    @Test
    void should_fail_when_when_submit_event_fails() {
        // given
        setUpCcdAuthenticationFactory("userId1", "userToken1", "serviceToken1");

        StartEventResponse startEventResponse = mockStartEventResponse("eventToken1");
        given(ccdApi.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .willReturn(startEventResponse);

        FeignException submitEventException = new FeignException.InternalServerError("Test exception", new byte[]{});

        willThrow(submitEventException)
            .given(ccdApi)
            .submitEventForCaseWorker(any(), any(), any(), any(), any(), any(), anyBoolean(), any());

        // then
        assertThatThrownBy(() ->
            ccdClient.completeAwaitingDcnProcessing("1231244243242343", "bulkscan", "BULKSCAN")
        ).isSameAs(submitEventException);
    }

    private StartEventResponse mockStartEventResponse(String eventToken) {
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getEventId()).willReturn(COMPLETE_AWAITING_PROCESSING_EVENT_ID);
        given(startEventResponse.getToken()).willReturn(eventToken);
        return startEventResponse;
    }

    private void setUpCcdAuthenticationFactory(String userId, String userToken, String serviceToken) {
        UserDetails userDetails = mock(UserDetails.class);
        given(userDetails.getId()).willReturn(userId);

        CcdAuthenticator ccdAuthenticator = new CcdAuthenticator(() -> serviceToken, userDetails, () -> userToken);
        given(ccdAuthenticatorFactory.createForJurisdiction(any())).willReturn(ccdAuthenticator);
    }

    private CaseDataContent getExpectedCaseDataContentForSubmitEvent(String exceptionRecordId, String eventToken) {
        return CaseDataContent
            .builder()
            .data(ImmutableMap.of(AWAITING_DCN_PROCESSING_FIELD_NAME, "No"))
            .caseReference(exceptionRecordId)
            .event(Event.builder().id(COMPLETE_AWAITING_PROCESSING_EVENT_ID).summary(EVENT_SUMMARY).build())
            .ignoreWarning(true)
            .eventToken(eventToken)
            .build();
    }
}