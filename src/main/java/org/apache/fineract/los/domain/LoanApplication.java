package org.apache.fineract.los.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Root aggregate for the Loan Origination lifecycle.
 *
 * <p>Manages the full journey of a loan application from DRAFT through to DISBURSED.
 * All state transitions are enforced by {@link LoanOriginationStateMachine}.
 *
 * <p>Multi-tenancy: every application is scoped to a {@code tenantId} matching
 * the X-Fineract-Platform-TenantId header convention used by Apache Fineract.
 *
 * @see LoanOriginationStatus
 * @see LoanOriginationStateMachine
 */
@Entity
@Table(name = "loan_application", indexes = {
    @Index(name = "idx_loan_app_tenant_status", columnList = "tenant_id, status"),
    @Index(name = "idx_loan_app_applicant",     columnList = "applicant_id")
})
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loan_app_seq")
    @SequenceGenerator(name = "loan_app_seq", sequenceName = "loan_application_seq", allocationSize = 1)
    private Long id;

    /** Human-readable reference, e.g. LOS-2026-00101 */
    @Column(name = "application_ref", nullable = false, unique = true)
    private String applicationRef;

    /** Maps to Fineract client ID for disbursement bridge */
    @Column(name = "applicant_id", nullable = false)
    private String applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanOriginationStatus status = LoanOriginationStatus.DRAFT;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "tenor_months", nullable = false)
    private Integer tenorMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_purpose", length = 30)
    private LoanPurpose loanPurpose;

    /** Fineract loan product ID — resolved during origination */
    @Column(name = "product_id")
    private Long productId;

    /** Populated by DisbursementBridgeService on successful Fineract loan creation */
    @Column(name = "fineract_loan_id")
    private Long fineractLoanId;

    /** Tenant isolation — matches X-Fineract-Platform-TenantId */
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // State transition methods — all validated by the state machine
    // -------------------------------------------------------------------------

    public void submit() {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.SUBMITTED);
        this.status     = LoanOriginationStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void startReview() {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.UNDER_REVIEW);
        this.status = LoanOriginationStatus.UNDER_REVIEW;
    }

    public void approve() {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.APPROVED);
        this.status = LoanOriginationStatus.APPROVED;
    }

    public void reject() {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.REJECTED);
        this.status = LoanOriginationStatus.REJECTED;
    }

    public void refer() {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.REFERRED);
        this.status = LoanOriginationStatus.REFERRED;
    }

    public void disburse(Long fineractLoanId) {
        LoanOriginationStateMachine.validateTransition(this.status, LoanOriginationStatus.DISBURSED);
        this.status         = LoanOriginationStatus.DISBURSED;
        this.fineractLoanId = fineractLoanId;
        this.disbursedAt    = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters (setters intentionally omitted — use state methods above)
    // -------------------------------------------------------------------------

    public Long getId()                    { return id; }
    public String getApplicationRef()      { return applicationRef; }
    public String getApplicantId()         { return applicantId; }
    public LoanOriginationStatus getStatus() { return status; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public Integer getTenorMonths()        { return tenorMonths; }
    public LoanPurpose getLoanPurpose()    { return loanPurpose; }
    public Long getProductId()             { return productId; }
    public Long getFineractLoanId()        { return fineractLoanId; }
    public String getTenantId()            { return tenantId; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
    public Instant getSubmittedAt()        { return submittedAt; }
    public Instant getDisbursedAt()        { return disbursedAt; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final LoanApplication app = new LoanApplication();

        public Builder applicationRef(String ref)           { app.applicationRef = ref; return this; }
        public Builder applicantId(String id)               { app.applicantId = id; return this; }
        public Builder requestedAmount(BigDecimal amount)   { app.requestedAmount = amount; return this; }
        public Builder tenorMonths(Integer months)          { app.tenorMonths = months; return this; }
        public Builder loanPurpose(LoanPurpose purpose)     { app.loanPurpose = purpose; return this; }
        public Builder productId(Long productId)            { app.productId = productId; return this; }
        public Builder tenantId(String tenantId)            { app.tenantId = tenantId; return this; }

        public LoanApplication build() {
            if (app.applicationRef == null) throw new IllegalStateException("applicationRef required");
            if (app.applicantId == null)    throw new IllegalStateException("applicantId required");
            if (app.requestedAmount == null) throw new IllegalStateException("requestedAmount required");
            if (app.tenantId == null)       throw new IllegalStateException("tenantId required");
            return app;
        }
    }
}
