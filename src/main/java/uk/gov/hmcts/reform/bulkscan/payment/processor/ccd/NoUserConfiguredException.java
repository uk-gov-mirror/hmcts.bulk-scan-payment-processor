package uk.gov.hmcts.reform.bulkscan.payment.processor.ccd;

public class NoUserConfiguredException extends RuntimeException {
    public NoUserConfiguredException(String jurisdiction) {
        super("No user configured for jurisdiction: " + jurisdiction);
    }
}
