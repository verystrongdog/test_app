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
    }
}
