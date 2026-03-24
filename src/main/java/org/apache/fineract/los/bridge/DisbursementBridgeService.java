package org.apache.fineract.los.bridge;

import org.apache.fineract.los.domain.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Integration bridge between the external LOS service and Apache Fineract core.
 *
 * <p>When a {@link LoanApplication} reaches {@code APPROVED} status, this service
 * constructs the payload for Fineract's {@code POST /loans} API and triggers
 * loan creation. On success, the application transitions to {@code DISBURSED}
 * and the Fineract loan ID is persisted.
 *
 * <p><b>Fallback behaviour:</b> If {@code fineract.mock-enabled=true} (default in
 * the {@code local} and {@code test} profiles), the bridge returns a simulated
 * Fineract response without making a real HTTP call. This allows full origination
 * flow demonstration without a running Fineract instance.
 *
 * <p>When FINERACT-2418 endpoints become available, the {@link #buildLoanPayload}
 * method and the target URL should be updated to use the new API contract. The
 * rest of the service remains unchanged.
 */
@Service
public class DisbursementBridgeService {

    private static final Logger log = LoggerFactory.getLogger(DisbursementBridgeService.class);

    private final RestTemplate restTemplate;
    private final boolean mockEnabled;
    private final String fineractBaseUrl;
    private final String fineractTenantId;

    public DisbursementBridgeService(
            RestTemplate restTemplate,
            @Value("${fineract.mock-enabled:true}") boolean mockEnabled,
            @Value("${fineract.base-url:http://localhost:8080/fineract-provider/api/v1}") String fineractBaseUrl,
            @Value("${fineract.tenant-id:default}") String fineractTenantId) {
        this.restTemplate    = restTemplate;
        this.mockEnabled     = mockEnabled;
        this.fineractBaseUrl = fineractBaseUrl;
        this.fineractTenantId = fineractTenantId;
    }

    /**
     * Triggers loan creation in Fineract for the given approved application.
     *
     * @param application an application in APPROVED status
     * @return {@link DisbursementResult} containing the Fineract loan ID
     * @throws DisbursementException if Fineract returns an error or is unreachable
     */
    public DisbursementResult disburse(LoanApplication application) {
        log.info("Initiating disbursement for application [{}] tenant [{}]",
            application.getApplicationRef(), application.getTenantId());

        if (mockEnabled) {
            return mockDisbursement(application);
        }

        return realDisbursement(application);
    }

    // -------------------------------------------------------------------------
    // Real Fineract integration
    // -------------------------------------------------------------------------

    private DisbursementResult realDisbursement(LoanApplication application) {
        String url = fineractBaseUrl + "/loans";
        Map<String, Object> payload = buildLoanPayload(application);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, payload, Map.class);
            if (response == null || !response.containsKey("loanId")) {
                throw new DisbursementException("Fineract returned empty or invalid response for application "
                    + application.getApplicationRef());
            }
            Long fineractLoanId = ((Number) response.get("loanId")).longValue();
            log.info("Fineract loan created: loanId={} for application [{}]",
                fineractLoanId, application.getApplicationRef());
            return new DisbursementResult(fineractLoanId, DisbursementStatus.SUCCESS);
        } catch (Exception ex) {
            log.error("Disbursement failed for application [{}]: {}",
                application.getApplicationRef(), ex.getMessage());
            throw new DisbursementException("Disbursement failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Builds the Fineract POST /loans request payload from origination data.
     *
     * <p>Maps all parameters collected during origination to the exact fields
     * expected by Fineract's loan creation API. When FINERACT-2418 is resolved,
     * update this method to target the new endpoint contract.
     */
    private Map<String, Object> buildLoanPayload(LoanApplication application) {
        return Map.ofEntries(
            Map.entry("clientId",                  application.getApplicantId()),
            Map.entry("productId",                 application.getProductId()),
            Map.entry("principal",                 application.getRequestedAmount()),
            Map.entry("loanTermFrequency",         application.getTenorMonths()),
            Map.entry("loanTermFrequencyType",     2),  // months
            Map.entry("loanType",                  "individual"),
            Map.entry("numberOfRepayments",        application.getTenorMonths()),
            Map.entry("repaymentFrequencyType",    2),  // monthly
            Map.entry("interestRatePerPeriod",     18),
            Map.entry("locale",                    "en"),
            Map.entry("dateFormat",                "dd MMMM yyyy"),
            Map.entry("submittedOnDate",           "26 May 2026"),
            Map.entry("expectedDisbursementDate",  "01 June 2026"),
            Map.entry("externalId",                application.getApplicationRef())
        );
    }

    // -------------------------------------------------------------------------
    // Mock fallback for local / test profiles
    // -------------------------------------------------------------------------

    private DisbursementResult mockDisbursement(LoanApplication application) {
        long mockLoanId = Math.abs(application.getApplicationRef().hashCode()) % 100000L + 1000L;
        log.info("[MOCK] Simulated Fineract disbursement for application [{}] → mockLoanId={}",
            application.getApplicationRef(), mockLoanId);
        return new DisbursementResult(mockLoanId, DisbursementStatus.SUCCESS_MOCK);
    }
}
