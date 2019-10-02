package uk.gov.hmcts.reform.bulkscan.payment.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "site-mappings")
public class SiteConfiguration {
    private List<Sites> sites;

    public List<Sites> getSites() {
        return sites;
    }

    public void setSites(List<Sites> sites) {
        this.sites = sites;
    }

    public static class Sites {

        private String siteName;
        private String poBox;
        private String siteId;

        public Sites(String siteName, String poBox, String siteId) {
            this.siteName = siteName;
            this.poBox = poBox;
            this.siteId = siteId;
        }

        public Sites() {
            // Spring needs it.
        }

        public String getSiteName() {
            return siteName;
        }

        public void setSiteName(String siteName) {
            this.siteName = siteName;
        }

        public String getPoBox() {
            return poBox;
        }

        public void setPoBox(String poBox) {
            this.poBox = poBox;
        }

        public String getSiteId() {
            return siteId;
        }

        public void setSiteId(String siteId) {
            this.siteId = siteId;
        }
    }
}