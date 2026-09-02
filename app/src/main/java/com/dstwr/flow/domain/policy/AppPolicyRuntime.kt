package com.dstwr.flow.domain.policy

import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.usage.UsageStatsRepository
import com.dstwr.flow.domain.model.AppPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Connects persisted policies with current Android usage counters.
 * It produces decisions only. It does not start or stop the VPN itself.
 */
class AppPolicyRuntime(
    private val policyRepository: AppPolicyRepository,
    private val usageRepository: UsageStatsRepository,
    private val evaluator: AppPolicyEvaluator = AppPolicyEvaluator()
) {
    suspend fun evaluatePackage(
        packageName: String,
        uid: Int,
        emergencyBlock: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): AppPolicyEvaluator.Decision = withContext(Dispatchers.IO) {
        val time = PolicyTime.fromMillis(nowMillis)
        val policy = policyRepository.get(packageName)
        val daily = usageRepository.queryUid(uid, time.dayStartMillis, time.nowMillis)
        val monthly = usageRepository.queryUid(uid, time.monthStartMillis, time.nowMillis)
        evaluator.evaluate(
            policy = policy,
            minuteOfDay = time.minuteOfDay,
            dailyUsageBytes = daily.totalBytes,
            monthlyUsageBytes = monthly.totalBytes,
            emergencyBlock = emergencyBlock
        )
    }

    suspend fun evaluatePolicies(
        apps: List<Pair<String, Int>>,
        emergencyBlock: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, AppPolicyEvaluator.Decision> = withContext(Dispatchers.IO) {
        val time = PolicyTime.fromMillis(nowMillis)
        val policies = policyRepository.getAll().associateBy { it.packageName }
        apps.associate { (packageName, uid) ->
            val policy: AppPolicy? = policies[packageName]
            val daily = usageRepository.queryUid(uid, time.dayStartMillis, time.nowMillis)
            val monthly = usageRepository.queryUid(uid, time.monthStartMillis, time.nowMillis)
            packageName to evaluator.evaluate(
                policy = policy,
                minuteOfDay = time.minuteOfDay,
                dailyUsageBytes = daily.totalBytes,
                monthlyUsageBytes = monthly.totalBytes,
                emergencyBlock = emergencyBlock
            )
        }
    }
}
