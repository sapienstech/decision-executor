package com.sapiens.bdms.decisionexecutor.service.impl;

import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactExecutorService;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactInputsInitializerService;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactsJarLoaderService;
import com.sapiens.bdms.java.exe.helper.base.Decision;
import com.sapiens.bdms.java.exe.helper.base.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.sapiens.bdms.decisionexecutor.GeneralConstants.README_URL;

@Service
public class PojoArtifactExecutorService implements ArtifactExecutorService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${format.view.placeholder}")
	private String formatViewPlaceholder;

	@Value("${format.version.placeholder}")
	private String formatVersionPlaceholder;

	@Value("${format.prefix.placeholder}")
	private String formatPrefixPlaceholder;

	@Value("${version.dot.replacement}")
	private String versionDotReplacement;

	@Value("${decision.classpath.format}")
	private String decisionClasspathFormat;

	@Value("${flow.classpath.format}")
	private String flowClasspathFormat;

	@Value("${artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

	@Resource
	private ArtifactsJarLoaderService pojoArtifactsJarLoaderService;

	@Resource
	private ArtifactInputsInitializerService pojoArtifactInputsInitializerService;

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
	@Override
	public Object executeDecision(String packagePrefix,
								  String conclusionName,
								  String view,
								  String version,
								  Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		refreshClassLoaders();
		String decisionClasspath = resolveDecisionClasspath(packagePrefix, conclusionName, view, version);
		Class clazz;
		try {
			clazz = pojoArtifactsJarLoaderService.getArtifactClass(decisionClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for decision of conclusion \"%s\", view \"%s\" and version \"%s\" on package \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar/s located in the configured artifacts jar location (%s by default), \n" +
																   "as described in %s",
														   conclusionName, view, version, packagePrefix, getDefaultArtifactsJarLocation(), README_URL));
		}
		Decision decision = (Decision) clazz.newInstance();
		pojoArtifactInputsInitializerService.setFactInputs(factValueByNameInputs, clazz, decision, decision.getName());

		final Object conclusion = decision.execute();

		// add messages to the final result
		Map<String, Object> conclusionWithMessages = new HashMap<>();
		conclusionWithMessages.put("conclusion", conclusion);
		conclusionWithMessages.put("messages", decision.getConclusionMessagesCollection());

		return conclusionWithMessages;
	}

	/***
	 * Execute a Flow according to given parameters and return result with messages
	 * @param packagePrefix The Java package prefix as was set to the "Generated Package Prefix" property in the
	 *                      DM Java Adapter with which this Decision class was exported
	 * @param version The Flow's version
	 * @param factValueByNameInputs The Map of the execution input values by their Fact Type name
	 * @return Execution result as map of execution result with row hits and messages by Fact Type Name
	 */
	@Override
	public Map<String, Object> executeFlow(String packagePrefix, String flowName, String version,
										   Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		refreshClassLoaders();
		String flowClasspath = resolveFlowClasspath(packagePrefix, flowName, version);
		Class clazz;
		try {
			clazz = pojoArtifactsJarLoaderService.getArtifactClass(flowClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for flow \"%s\" and version \"%s\" on package \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar/s located in the configured artifacts jar location (%s by default), \n" +
																   "as described in %s",
														   flowName, version, packagePrefix, getDefaultArtifactsJarLocation(), README_URL));
		}
		Flow flow = (Flow) clazz.newInstance();
		pojoArtifactInputsInitializerService.setFactInputs(factValueByNameInputs, clazz, flow, flow.getName());

		// execute and return execution results
		return flow.execute();
	}

	/***
	 * Builds the expected Decision full classpath by replacing given parameters into the classpath format
	 * @param packagePrefix
	 * @param conclusionName
	 * @param view
	 * @param version
	 * @return Expected Decision classpath within the Jar
	 */
	private String resolveDecisionClasspath(String packagePrefix, String conclusionName, String view, String version) {
		String versionNormalized = version.replace(".", versionDotReplacement);
		return decisionClasspathFormat.replace(formatViewPlaceholder, view)
									  .replace(formatPrefixPlaceholder, packagePrefix)
									  .replace(formatVersionPlaceholder, versionNormalized) + "." + conclusionName;
	}

	/***
	 * Builds the expected Flow full classpath by replacing given parameters into the classpath format
	 * @param packagePrefix
	 * @param flowName
	 * @param version
	 * @return Expected Flow classpath within the Jar
	 */
	private String resolveFlowClasspath(String packagePrefix, String flowName, String version) {
		String versionNormalized = version.replace(".", versionDotReplacement);
		return flowClasspathFormat.replace(formatPrefixPlaceholder, packagePrefix)
								  .replace(formatVersionPlaceholder, versionNormalized) + "." + flowName;
	}

	/***
	 * reload any artifacts Jar that might have benn added to the Jars location
	 */
	private void refreshClassLoaders(){
		try {
			pojoArtifactsJarLoaderService.loadArtifactJarsFromDefaultLocation(false);
		} catch (MissingFileException e) {
			if(pojoArtifactsJarLoaderService.isClassLoadersEmpty()){
				throw new RuntimeException("Unable to execute any artifact - default artifacts jar location " +
												   "\""+ getDefaultArtifactsJarLocation() +"\" is missing " +
												   "or empty and application was not manually loaded with any other jar location. " +
												   "Add the artifacts jar/s to the above location or call \"reload/artifacts/jars/from/{path}\" for a different path.");
			}
		}
	}

	private String getDefaultArtifactsJarLocation() {
		return Paths.get(defaultArtifactsJarLocation).toAbsolutePath().toString();
	}
}
