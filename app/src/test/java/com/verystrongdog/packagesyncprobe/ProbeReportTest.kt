package com.verystrongdog.packagesyncprobe

import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeReportTest {
    @Test
    fun formatIncludesCollectedFields() {
        val report = ProbeReport(
            generatedAtMillis = 1L,
            contactsSummary = "contacts ok",
            packageSummary = "packages ok",
            lastUploadSummary = "upload ok",
        )

        val formatted = report.format()

        assertTrue(formatted.contains("contacts ok"))
        assertTrue(formatted.contains("packages ok"))
        assertTrue(formatted.contains("upload ok"))
    }
}
