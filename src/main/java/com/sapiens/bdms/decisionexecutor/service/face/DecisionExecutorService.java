package com.sapiens.bdms.decisionexecutor.service.face;

import java.util.Map;

public interface DecisionExecutorService {
	Object executeDecision(String conclusionName, String view, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
	Object executeFlow(String flowName, String version, Map<String, Object> factValueByNameInputs);
}
