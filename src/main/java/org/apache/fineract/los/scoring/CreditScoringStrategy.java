package org.apache.fineract.los.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pluggable interface for credit scoring strategies.
 *
 * <p>The default implementation ({@link RuleBasedCreditScoringStrategy}) uses
 * weighted financial factors suitable for low-resource MFI environments — no
 * external API calls, no ML inference, no cloud dependencies.
 *
 * <p>Future implementations can swap in:
 * <ul>
 *   <li>Credit bureau connectors (CIBIL, Experian, Equifax)</li>
 *   <li>ML model inference (scikit-learn, TensorFlow Serving)</li>
 *   <li>Configurable YAML-driven rule engines</li>
 * </ul>
 *
 * To register a custom strategy, implement this interface and mark it {@code @Primary}
 * or use Spring's {@code @Qualifier} to select it.
 */
public interface CreditScoringStrategy {

    /**
     * Computes a credit score for the given applicant profile.
     *
     * @param profile applicant's financial and personal data
     * @return {@link CreditScoreResult} with score, risk category, and factor breakdown
     */
    CreditScoreResult score(ApplicantScoringProfile profile);
}


// ---------------------------------------------------------------------------
// Default implementation
// ---------------------------------------------------------------------------

/**
 * Rule-based credit scoring implementation.
 *
 * <p>Scoring factors and weights (configurable via application.properties):
 * <ul>
 *   <li>Income-to-loan ratio   — 30%</li>
 *   <li>Existing loan burden   — 25%</li>
 *   <li>Employment stability   — 20%</li>
 *   <li>Repayment history      — 15%</li>
 *   <li>Loan purpose risk      — 10%</li>
 * </ul>
 *
 * <p>Risk bands:
 * <ul>
 *   <li>750–1000 → LOW    (auto-approve eligible)</li>
 *   <li>500–749  → MEDIUM (manual review required)</li>
 *   <li>0–499    → HIGH   (refer or reject)</li>
 * </ul>
 */
class RuleBasedCreditScoringStrategy implements CreditScoringStrategy {

    private static final BigDecimal WEIGHT_INCOME_RATIO       = new BigDecimal("0.30");
    private static final BigDecimal WEIGHT_LOAN_BURDEN        = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_EMPLOYMENT         = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_REPAYMENT_HISTORY  = new BigDecimal("0.15");
    private static final BigDecimal WEIGHT_LOAN_PURPOSE       = new BigDecimal("0.10");

    @Override
    public CreditScoreResult score(ApplicantScoringProfile profile) {
        Map<String, FactorScore> factors = new LinkedHashMap<>();

        int incomeRatioScore      = scoreIncomeRatio(profile);
        int loanBurdenScore       = scoreLoanBurden(profile);
        int employmentScore       = scoreEmployment(profile);
        int repaymentHistoryScore = scoreRepaymentHistory(profile);
        int loanPurposeScore      = scoreLoanPurpose(profile);

        factors.put("incomeToLoanRatio",   new FactorScore(incomeRatioScore,      WEIGHT_INCOME_RATIO));
        factors.put("existingLoanBurden",  new FactorScore(loanBurdenScore,        WEIGHT_LOAN_BURDEN));
        factors.put("employmentStability", new FactorScore(employmentScore,         WEIGHT_EMPLOYMENT));
        factors.put("repaymentHistory",    new FactorScore(repaymentHistoryScore,   WEIGHT_REPAYMENT_HISTORY));
        factors.put("loanPurposeRisk",     new FactorScore(loanPurposeScore,        WEIGHT_LOAN_PURPOSE));

        int compositeScore = factors.entrySet().stream()
            .mapToInt(e -> (int) (e.getValue().score() * e.getValue().weight().doubleValue()))
            .sum();

        RiskCategory risk = compositeScore >= 750 ? RiskCategory.LOW
                          : compositeScore >= 500 ? RiskCategory.MEDIUM
                          : RiskCategory.HIGH;

        return new CreditScoreResult(compositeScore, risk, factors);
    }

    /** Score 0–100. High ratio = lower score (loan too large relative to income). */
    private int scoreIncomeRatio(ApplicantScoringProfile p) {
        if (p.monthlyIncome().compareTo(BigDecimal.ZERO) == 0) return 0;
        BigDecimal ratio = p.requestedAmount()
            .divide(p.monthlyIncome().multiply(BigDecimal.valueOf(12)), 2, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.valueOf(0.5))  <= 0) return 100;
        if (ratio.compareTo(BigDecimal.ONE)           <= 0) return 80;
        if (ratio.compareTo(BigDecimal.valueOf(2))    <= 0) return 60;
        if (ratio.compareTo(BigDecimal.valueOf(3))    <= 0) return 40;
        return 20;
    }

    /** Score 0–100. High obligations relative to income = lower score. */
    private int scoreLoanBurden(ApplicantScoringProfile p) {
        if (p.monthlyIncome().compareTo(BigDecimal.ZERO) == 0) return 0;
        BigDecimal burdenRatio = p.existingMonthlyObligations()
            .divide(p.monthlyIncome(), 2, RoundingMode.HALF_UP);
        if (burdenRatio.compareTo(BigDecimal.valueOf(0.2)) <= 0) return 100;
        if (burdenRatio.compareTo(BigDecimal.valueOf(0.4)) <= 0) return 70;
        if (burdenRatio.compareTo(BigDecimal.valueOf(0.6)) <= 0) return 40;
        return 10;
    }

    /** Score 0–100 based on employment type and tenure. */
    private int scoreEmployment(ApplicantScoringProfile p) {
        int base = switch (p.employmentStatus()) {
            case SALARIED_PERMANENT  -> 90;
            case SALARIED_CONTRACT   -> 70;
            case SELF_EMPLOYED       -> 60;
            case SEASONAL            -> 40;
            case UNEMPLOYED          -> 0;
        };
        // Tenure bonus: +5 per year capped at 20
        int tenureBonus = Math.min(20, (p.employmentDurationMonths() / 12) * 5);
        return Math.min(100, base + tenureBonus);
    }

    /** Score 0–100 based on historical repayment from Fineract records. */
    private int scoreRepaymentHistory(ApplicantScoringProfile p) {
        return switch (p.repaymentHistoryRating()) {
            case EXCELLENT  -> 100;
            case GOOD       -> 80;
            case FAIR       -> 55;
            case POOR       -> 25;
            case NO_HISTORY -> 50; // neutral for first-time borrowers
        };
    }

    /** Score 0–100 based on loan purpose risk category. */
    private int scoreLoanPurpose(ApplicantScoringProfile p) {
        return switch (p.loanPurpose()) {
            case EDUCATION        -> 90;
            case BUSINESS         -> 80;
            case AGRICULTURE      -> 75;
            case HOME_IMPROVEMENT -> 70;
            case PERSONAL         -> 55;
            case DEBT_REFINANCING -> 40;
        };
    }
}
