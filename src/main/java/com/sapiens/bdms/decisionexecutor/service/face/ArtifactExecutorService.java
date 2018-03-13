package com.sapiens.bdms.decisionexecutor.service.face;

import java.util.Map;

public interface ArtifactExecutorService {
	Object executeDecision(String packagePrefix, String conclusionName, String view, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
	Map<String, Object> executeFlow(String packagePrefix, String flowName, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
}
