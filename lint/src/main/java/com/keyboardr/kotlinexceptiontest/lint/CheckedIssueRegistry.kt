@file:Suppress("UnstableApiUsage")

package com.keyboardr.kotlinexceptiontest.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class CheckedIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = CheckedExceptionNotCaughtDetector.ISSUES

    override val api: Int = CURRENT_API
}