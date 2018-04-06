package com.sapiens.bdms.decisionexecutor.service.face;

import com.sapiens.bdms.java.exe.helper.base.Group;

import java.util.Map;

public interface ArtifactInputsInitializerService {
	/***
	 * Sets given input values by fact name into the artifact instance to be executed
	 * @param factValueByNameInputs Map of String fact values (or list of fact values) by fact name
	 * @param artifactClass Java class of the artifact
	 * @param artifactInstance Constructed instance of the artifact
	 * @param artifactName The artifact name
	 */
	void setFactInputs(Map<String, Object> factValueByNameInputs, Class artifactClass, Group artifactInstance, String artifactName);
}
