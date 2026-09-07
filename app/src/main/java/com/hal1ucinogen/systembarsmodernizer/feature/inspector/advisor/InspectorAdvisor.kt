package com.hal1ucinogen.systembarsmodernizer.feature.inspector.advisor

import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.InspectorReport
import com.hal1ucinogen.systembarsmodernizer.feature.inspector.model.RuleSuggestion

interface InspectorAdvisor {
    fun analyze(report: InspectorReport): List<RuleSuggestion>
}
