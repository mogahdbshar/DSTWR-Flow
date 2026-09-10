package com.dstwr.flow.ui.apps

import com.dstwr.flow.data.apps.InstalledApp
import com.dstwr.flow.domain.model.AppPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {
    private val apps = listOf(
        AppRow(InstalledApp("com.example.youtube", "YouTube", 10, false), AppPolicy("com.example.youtube", blocked = true)),
        AppRow(InstalledApp("com.example.maps", "Maps", 11, false), AppPolicy("com.example.maps")),
        AppRow(InstalledApp("com.example.mail", "Mail", 12, false), AppPolicy("com.example.mail", dailyQuotaBytes = 1024L))
    )

    @Test
    fun queryMatchesLabelOrPackage() {
        assertEquals(listOf("com.example.youtube"), AppListFilter.filter(apps, "youtube").map { it.app.packageName })
        assertEquals(listOf("com.example.maps"), AppListFilter.filter(apps, "MAPS").map { it.app.packageName })
    }

    @Test
    fun blockedOnlyReturnsBlockedApps() {
        assertEquals(listOf("com.example.youtube"), AppListFilter.filter(apps, "", blockedOnly = true).map { it.app.packageName })
    }

    @Test
    fun configuredOnlyExcludesDefaultPolicies() {
        assertEquals(
            listOf("com.example.youtube", "com.example.mail"),
            AppListFilter.filter(apps, "", configuredOnly = true).map { it.app.packageName }
        )
    }
}
