package dev.streamline.client;

/**
 * Raised when a record violates a topic's data contract.
 */
public class MoonshotException extends StreamlineException {

    private MoonshotException(String message, String errorCode, String hint) {
        super(message, null, errorCode, hint);
    }

    /** Raised when a record violates a topic's data contract. */
    public static MoonshotException contractViolation(String topic, String details) {
        return new MoonshotException(
            "Contract violation on topic '" + topic + "': " + details,
            "CONTRACT_VIOLATION",
            "Validate the record against the topic's registered schema"
        );
    }

    /** Raised when attestation signature verification fails. */
    public static MoonshotException attestationVerificationFailed(String details) {
        return new MoonshotException(
            "Attestation verification failed: " + details,
            "ATTESTATION_VERIFICATION_FAILED",
            "Check the signing key and attestation configuration"
        );
    }

    /** Raised when an agent lacks permission to access memory. */
    public static MoonshotException memoryAccessDenied(String agent) {
        return new MoonshotException(
            "Memory access denied for agent: " + agent,
            "MEMORY_ACCESS_DENIED",
            "Verify agent permissions for memory operations"
        );
    }

    /** Raised when a branch exceeds its storage or lifetime quota. */
    public static MoonshotException branchQuotaExceeded(String branch, String details) {
        return new MoonshotException(
            "Branch quota exceeded for '" + branch + "': " + details,
            "BRANCH_QUOTA_EXCEEDED",
            "Increase branch quotas or clean up unused branches"
        );
    }

    /** Raised when semantic search is unavailable. */
    public static MoonshotException semanticSearchUnavailable(String details) {
        return new MoonshotException(
            "Semantic search unavailable: " + details,
            "SEMANTIC_SEARCH_UNAVAILABLE",
            "Check embedding provider connectivity and configuration"
        );
    }
}
