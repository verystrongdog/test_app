package com.verystrongdog.packagesyncprobe

import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeReportTest {
    @Test
    fun emptyReportStartsWithoutSnapshots() {
        val report = ProbeReport.empty()

        assertTrue(report.contactsSummary == null)
        assertTrue(report.packageSummary == null)
        assertTrue(report.lastUploadSummary == null)
        assertTrue(report.notificationSummary == null)
        assertTrue(report.auditExportSummary == null)
    }

    @Test
    fun reportCarriesNewModuleFields() {
        val report = ProbeReport(
            contactsSummary = "contacts",
            packageSummary = "packages",
            phoneStateSummary = "phone",
            notificationSummary = "notification",
            foregroundServiceSummary = "foreground",
            lastUploadSummary = "upload",
            auditExportSummary = "audit",
        )

        assertTrue(report.phoneStateSummary == "phone")
        assertTrue(report.notificationSummary == "notification")
        assertTrue(report.foregroundServiceSummary == "foreground")
        assertTrue(report.auditExportSummary == "audit")
    }
}
