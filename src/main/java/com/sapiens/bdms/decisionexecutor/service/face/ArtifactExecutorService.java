package com.sapiens.bdms.decisionexecutor.service.face;

import java.util.Map;

public interface ArtifactExecutorService {
	/***
	 * Execute a Decision View according to given parameters and return result with messages
	 * @param conclusionName The decision conclusion
	 * @param packagePrefix The Java package prefix as was set to the "Generated Package Prefix" property in the
	 *                      DM Java Adapter with which this Decision class was exported
	 * @param view The Decision's View
	 * @param version The Decision's version
	 * @param factValueByNameInputs The Map of the execution input values by their Fact Type name
	 * @return Execution result as map of values by Fact Type Name and messages
	 */
	Object executeDecision(String packagePrefix, String conclusionName, String view, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
	/***
	 * Execute a Flow according to given parameters and return result with messages
	 * @param packagePrefix The Java package prefix as was set to the "Generated Package Prefix" property in the
	 *                      DM Java Adapter with which this Decision class was exported
	 * @param version The Flow's version
	 * @param factValueByNameInputs The Map of the execution input values by their Fact Type name
	 * @return Execution result as map of execution result with row hits and messages by Fact Type Name
	 */
	Map<String, Object> executeFlow(String packagePrefix, String flowName, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException;
}
