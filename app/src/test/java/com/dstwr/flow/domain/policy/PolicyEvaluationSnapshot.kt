package com.dstwr.flow.domain.policy

/** Stable, comparable output used by tests and future policy-monitoring code. */
data class PolicyEvaluationSnapshot(
    val blockedPackages: Set<String>
) {
    companion object {
        fun from(decisions: Collection<RuntimeDecision>): PolicyEvaluationSnapshot =
            PolicyEvaluationSnapshot(
                blockedPackages = decisions
                    .asSequence()
                    .filter { it.decision.blocked }
                    .map { it.packageName }
                    .toSet()
            )
    }
}
