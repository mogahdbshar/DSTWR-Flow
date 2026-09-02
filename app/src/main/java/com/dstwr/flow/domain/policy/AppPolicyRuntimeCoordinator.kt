package com.dstwr.flow.domain.policy

import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.usage.UsageWindowRepository

/** Coordinates persisted policies, current time and Android usage counters. */
class AppPolicyRuntimeCoordinator(
    private val policyRepository: AppPolicyRepository,
    private val usageWindowRepository: UsageWindowRepository,
    private val evaluator: AppPolicyEvaluator = AppPolicyEvaluator()
) {
    suspend fun evaluate(
        packageName: String,
        uid: Int,
        emergencyBlock: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): RuntimeDecision {
        val policy = policyRepository.get(packageName)
        val time = PolicyTimeWindowFactory.fromMillis(nowMillis)
        val usage = usageWindowRepository.queryCurrentWindows(uid, nowMillis)
        val policyUsage = PolicyUsage.from(usage.daily, usage.monthly)
        val decision = evaluator.evaluate(
            policy = policy,
            minuteOfDay = time.minuteOfDay,
            usage = policyUsage,
            emergencyBlock = emergencyBlock
        )
        return RuntimeDecision(
            packageName = packageName,
            uid = uid,
            decision = decision,
            usage = policyUsage
        )
    }

    suspend fun evaluateAll(
        apps: List<RuntimeApp>,
        emergencyBlock: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RuntimeDecision> = apps.map { app ->
        evaluate(app.packageName, app.uid, emergencyBlock, nowMillis)
    }
}

data class RuntimeApp(
    val packageName: String,
    val uid: Int
)

data class RuntimeDecision(
    val packageName: String,
    val uid: Int,
    val decision: AppPolicyEvaluator.Decision,
    val usage: PolicyUsage
)
