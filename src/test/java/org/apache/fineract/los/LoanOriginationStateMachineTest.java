package org.apache.fineract.los;

import org.apache.fineract.los.domain.InvalidLoanStateTransitionException;
import org.apache.fineract.los.domain.LoanOriginationStateMachine;
import org.apache.fineract.los.domain.LoanOriginationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the loan origination state machine.
 *
 * Covers all valid transitions, all invalid transitions,
 * and terminal state enforcement.
 */
@DisplayName("LoanOriginationStateMachine")
class LoanOriginationStateMachineTest {

    // -------------------------------------------------------------------------
    // Valid transitions — must not throw
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "DRAFT,        SUBMITTED",
        "SUBMITTED,    UNDER_REVIEW",
        "UNDER_REVIEW, APPROVED",
        "UNDER_REVIEW, REJECTED",
        "UNDER_REVIEW, REFERRED",
        "REFERRED,     UNDER_REVIEW",
        "APPROVED,     DISBURSED"
    })
    @DisplayName("Valid transitions should succeed")
    void validTransitions(String from, String to) {
        LoanOriginationStatus current = LoanOriginationStatus.valueOf(from);
        LoanOriginationStatus target  = LoanOriginationStatus.valueOf(to);

        assertThatNoException()
            .isThrownBy(() -> LoanOriginationStateMachine.validateTransition(current, target));
    }

    // -------------------------------------------------------------------------
    // Invalid transitions — must throw
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1} should be rejected")
    @CsvSource({
        "DRAFT,        APPROVED",
        "DRAFT,        DISBURSED",
        "SUBMITTED,    APPROVED",
        "SUBMITTED,    REJECTED",
        "APPROVED,     REJECTED",
        "APPROVED,     UNDER_REVIEW",
        "REJECTED,     APPROVED",
        "REJECTED,     SUBMITTED",
        "DISBURSED,    APPROVED",
        "DISBURSED,    UNDER_REVIEW"
    })
    @DisplayName("Invalid transitions should throw InvalidLoanStateTransitionException")
    void invalidTransitions(String from, String to) {
        LoanOriginationStatus current = LoanOriginationStatus.valueOf(from);
        LoanOriginationStatus target  = LoanOriginationStatus.valueOf(to);

        assertThatThrownBy(() -> LoanOriginationStateMachine.validateTransition(current, target))
            .isInstanceOf(InvalidLoanStateTransitionException.class)
            .hasMessageContaining(from)
            .hasMessageContaining(to);
    }

    // -------------------------------------------------------------------------
    // Terminal states — REJECTED and DISBURSED have no valid outgoing transitions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("REJECTED is a terminal state with no valid outgoing transitions")
    void rejectedIsTerminal() {
        for (LoanOriginationStatus target : LoanOriginationStatus.values()) {
            assertThat(LoanOriginationStateMachine.canTransition(LoanOriginationStatus.REJECTED, target))
                .as("REJECTED → %s should be false", target)
                .isFalse();
        }
    }

    @Test
    @DisplayName("DISBURSED is a terminal state with no valid outgoing transitions")
    void disbursedIsTerminal() {
        for (LoanOriginationStatus target : LoanOriginationStatus.values()) {
            assertThat(LoanOriginationStateMachine.canTransition(LoanOriginationStatus.DISBURSED, target))
                .as("DISBURSED → %s should be false", target)
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // canTransition helper
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("canTransition returns true for valid transition")
    void canTransitionReturnsTrueForValid() {
        assertThat(LoanOriginationStateMachine.canTransition(
            LoanOriginationStatus.DRAFT, LoanOriginationStatus.SUBMITTED)).isTrue();
    }

    @Test
    @DisplayName("canTransition returns false for invalid transition")
    void canTransitionReturnsFalseForInvalid() {
        assertThat(LoanOriginationStateMachine.canTransition(
            LoanOriginationStatus.DRAFT, LoanOriginationStatus.APPROVED)).isFalse();
    }
}
