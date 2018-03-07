package com.sapiens.bdms.decisionexecutor.service.face;

import java.util.Map;

public interface DecisionExecutorService {
	String executeDecision(String conclusionName, String view, String version, Map<String, String> factValueByNameInputs);
	String executeFlow(String flowName, String version, Map<String, String> factValueByNameInputs);
}
