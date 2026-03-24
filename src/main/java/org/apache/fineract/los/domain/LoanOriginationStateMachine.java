package org.apache.fineract.los.domain;

import java.util.Map;
import java.util.Set;

/**
 * Enforces valid state transitions for the loan origination lifecycle.
 *
 * <p>Design mirrors {@code DefaultLoanLifecycleStateMachine} in Apache Fineract core,
 * but operates entirely within the external LOS service.
 *
 * <p>Valid transitions:
 * <pre>
 * DRAFT        → SUBMITTED
 * SUBMITTED    → UNDER_REVIEW
 * UNDER_REVIEW → APPROVED | REJECTED | REFERRED
 * REFERRED     → UNDER_REVIEW
 * APPROVED     → DISBURSED
 * </pre>
 */
public final class LoanOriginationStateMachine {

    private static final Map<LoanOriginationStatus, Set<LoanOriginationStatus>> ALLOWED =
        Map.of(
            LoanOriginationStatus.DRAFT,        Set.of(LoanOriginationStatus.SUBMITTED),
            LoanOriginationStatus.SUBMITTED,    Set.of(LoanOriginationStatus.UNDER_REVIEW),
            LoanOriginationStatus.UNDER_REVIEW, Set.of(
                LoanOriginationStatus.APPROVED,
                LoanOriginationStatus.REJECTED,
                LoanOriginationStatus.REFERRED
            ),
            LoanOriginationStatus.REFERRED,     Set.of(LoanOriginationStatus.UNDER_REVIEW),
            LoanOriginationStatus.APPROVED,     Set.of(LoanOriginationStatus.DISBURSED),
            LoanOriginationStatus.REJECTED,     Set.of(),
            LoanOriginationStatus.DISBURSED,    Set.of()
        );

    private LoanOriginationStateMachine() {}

    /**
     * Validates that a transition from {@code current} to {@code target} is permitted.
     *
     * @throws InvalidLoanStateTransitionException if the transition is not allowed
     */
    public static void validateTransition(LoanOriginationStatus current, LoanOriginationStatus target) {
        Set<LoanOriginationStatus> allowed = ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidLoanStateTransitionException(
                String.format("Cannot transition loan application from [%s] to [%s]. Allowed targets: %s",
                    current, target, allowed)
            );
        }
    }

    public static boolean canTransition(LoanOriginationStatus current, LoanOriginationStatus target) {
        return ALLOWED.getOrDefault(current, Set.of()).contains(target);
    }
}
