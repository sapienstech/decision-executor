package com.sapiens.bdms.decisionexecutor.service.impl;

import com.sapiens.bdms.decisionexecutor.service.face.DecisionExecutorService;
import com.sapiens.bdms.java.exe.helper.base.Decision;
import com.sapiens.bdms.java.exe.helper.base.FactType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PojoDecisionExecutorService implements DecisionExecutorService {

	@Value("${artifacts.classpath.location}")
	private String artifactsClasspathLocation;

	@Value("${format.view.placeholder}")
	private String formatViewPlaceholder;

	@Value("${format.version.placeholder}")
	private String formatVersionPlaceholder;

	@Value("${version.dot.replacement}")
	private String versionDotReplacement;

	@Value("${decision.classpath.format}")
	private String decisionClasspathFormat;

	@Override
	public String executeDecision(String conclusionName,
								  String view,
								  String version,
								  Map<String, String> factValueByNameInputs) {

		String decisionClasspath = resolveDecisionClasspath(conclusionName, view, version);

		try {
			Class clazz;

			try {
				clazz = Class.forName(decisionClasspath);
			} catch (ClassNotFoundException e) {
				throw new ClassNotFoundException("Class for decision of conclusion \"%s\", view \"%s\" and version \"%s\" not found." +
														 "Make sure the above is accurate and the artifact jar packaged and built correctly with this application, " +
														 "as described in https://github.com/sapienstech/decision-executor/blob/master/README.md");
			}

			Decision decision = (Decision) clazz.newInstance();
			Map<String, Object> factTypes = decision.getFactTypes();
			Map<String, Object> values = new HashMap<>();

			factTypes.forEach((key, value) -> {
				FactType<?> ft = (FactType<?>) value;
				Object ftClass = ft.getValue();
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public String executeFlow(String flowName, String version, Map<String, String> factValueByNameInputs) {
		return null;
	}

	private String resolveDecisionClasspath(String conclusionName, String view, String version){
		String versionNormalized = version.replace(".", versionDotReplacement);
		return decisionClasspathFormat.replace(formatViewPlaceholder, view)
									  .replace(formatVersionPlaceholder, versionNormalized) + "." + conclusionName;
	}
}
