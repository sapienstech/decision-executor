package com.sapiens.bdms.decisionexecutor.service.impl;

import com.google.common.collect.Maps;
import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactExecutorService;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactsJarLoader;
import com.sapiens.bdms.java.exe.helper.base.Decision;
import com.sapiens.bdms.java.exe.helper.base.Flow;
import com.sapiens.bdms.java.exe.helper.base.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	@Value("${date.fact.input.value.datetime.format}")
	private String datetimeFormat;

	@Value("${artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

	@Resource
	private ArtifactsJarLoader pojoArtifactsJarLoader;

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
			clazz = pojoArtifactsJarLoader.getArtifactClass(decisionClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for decision of conclusion \"%s\", view \"%s\" and version \"%s\" on package \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar/s located in the configured artifacts jar location (%s by default), \n" +
																   "as described in %s",
														   conclusionName, view, version, packagePrefix, getDefaultArtifactsJarLocation(), README_URL));
		}
		Decision decision = (Decision) clazz.newInstance();
		setFactInputs(factValueByNameInputs, clazz, decision, decision.getName());

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
			clazz = pojoArtifactsJarLoader.getArtifactClass(flowClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for flow \"%s\" and version \"%s\" on package \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar/s located in the configured artifacts jar location (%s by default), \n" +
																   "as described in %s",
														   flowName, version, packagePrefix, getDefaultArtifactsJarLocation(), README_URL));
		}
		Flow flow = (Flow) clazz.newInstance();
		setFactInputs(factValueByNameInputs, clazz, flow, flow.getName());

		// execute and return execution results
		return flow.execute();
	}

	/***
	 * reload any artifacts Jar that might have benn added to the Jars location
	 */
	private void refreshClassLoaders(){
		try {
			pojoArtifactsJarLoader.loadArtifactJarsFromDefaultLocation(false);
		} catch (MissingFileException e) {
			if(pojoArtifactsJarLoader.isClassLoadersEmpty()){
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

	/***
	 * Sets given input values by fact name into the artifact instance to be executed
	 * @param factValueByNameInputs Map of String fact values (or list of fact values) by fact name
	 * @param artifactClass Java class of the artifact
	 * @param artifactInstance Constructed instance of the artifact
	 * @param artifactName The artifact name
	 */
	private void setFactInputs(Map<String, Object> factValueByNameInputs,
							   Class artifactClass,
							   Group artifactInstance,
							   String artifactName) {
		assertFactNames(factValueByNameInputs.keySet(), artifactInstance, artifactName);

		// will hold each fact type name with its value parsed to its matching Java data type
		Map<String, Object> parsedInputsByFactToSet = Maps.newHashMap();

		for (String ftName : factValueByNameInputs.keySet()) {
			Object ftValue = factValueByNameInputs.get(ftName);

			String factNameInMethod = normalizeToCamelCase(ftName);

			Method ftGetter = resolveGetter(artifactClass, factNameInMethod);
			Object parsedValue = getParsedValue(ftName, ftValue, ftGetter.getReturnType(), artifactClass);

			String normalizeToFactFieldName = normalizeToCamelCase(ftName);
			parsedInputsByFactToSet.put(normalizeToFactFieldName, parsedValue);
		}

		//Call the artifact generic method to set input fact types
		artifactInstance.setFactTypes(parsedInputsByFactToSet);
	}

	/***
	 * Finds the given fact name getter method in given class
	 * @param artifactClass The class to search in
	 * @param normalizedFactName The fact name normalized as camel case with first letter as upper case
	 * @return The getter method
	 */
	private Method resolveGetter(Class artifactClass, String normalizedFactName) {
		return Arrays.stream(artifactClass.getDeclaredMethods()).filter(
				method -> method.getName().equals("get" + normalizedFactName)
		).findFirst().orElseThrow(
				() -> new RuntimeException("Could not find getter method for fact field name " + normalizedFactName)
		);
	}

	/***
	 * Parsing given string (or list of strings) fact value according to its data type
	 * @param ftName The camel case normalized fact name
	 * @param ftValue The string fact value (or list of strings in case of list fact type)
	 * @param ftType The fact Java data type
	 * @param artifactClass The class of the artifact containing the fact type
	 * @return The fact value parsed to the fact data type
	 */
	private Object getParsedValue(String ftName, Object ftValue, Class<?> ftType, Class artifactClass) {
		if (isAListFactType(ftType)) {
			Class<?> listMemberType = resolveListMemberType(ftName, artifactClass);
			return getCollectionParsedValue(ftName, (Collection) ftValue, listMemberType);
		}
		return parsePrimitive(ftName, ftValue, ftType);
	}

	private boolean isAListFactType(Class<?> ftType) {
		return Collection.class.isAssignableFrom(ftType);
	}

	/***
	 * Figure out the data type of the members contained in the list fact type
	 * @param ftName Name of the list fact type
	 * @param artifactClass The class of the artifact containing the list fact type
	 * @return The members class
	 */
	private Class<?> resolveListMemberType(String ftName, Class artifactClass) {
		String normalizedFactName = normalizeToCamelCase(ftName);
		Method addMethod = resolveAddMethod(artifactClass, normalizedFactName);

		//The members data type is the data type of the parameter passed to the "add" method
		return addMethod.getParameterTypes()[0];
	}

	/***
	 * Finds the given list fact type add method in given class
	 * @param artifactClass The class to search in
	 * @param normalizedFactName The list fact name normalized as camel case with first letter as upper case
	 * @return The add method
	 */
	private Method resolveAddMethod(Class artifactClass, String normalizedFactName) {
		return Arrays.stream(artifactClass.getDeclaredMethods()).filter(
				method -> method.getName().startsWith("add" + normalizedFactName) &&
						method.getParameterCount() == 1
		).findFirst().orElseThrow(
				() -> new RuntimeException("Could not find add method method for fact field name " + normalizedFactName)
		);
	}

	/***
	 * Parses the given raw string value to the given primitive (String, BigDecimal, Double, Integer, Boolean or Date) date type
	 * @param ftName The fact type name
	 * @param ftValue The fact type raw string value
	 * @param ftType The fact type Java data type
	 * @return
	 */
	private Object parsePrimitive(String ftName, Object ftValue, Class<?> ftType) {
		if (ftType.isAssignableFrom(String.class)) {
			return ftValue;
		}
		else if (ftType.isAssignableFrom(BigDecimal.class)) {
			return new BigDecimal(String.valueOf(ftValue));
		}
		else if (ftType.isAssignableFrom(Double.class)) {
			return Double.parseDouble(String.valueOf(ftValue));
		}
		else if (ftType.isAssignableFrom(Integer.class)) {
			return Integer.parseInt(String.valueOf(ftValue));
		}
		else if (ftType.isAssignableFrom(Date.class)) {
			return parseDate(ftName, ftValue);
		}
		else if (ftType.isAssignableFrom(Boolean.class)) {
			return Boolean.parseBoolean(String.valueOf(ftValue));
		}
		throw new RuntimeException("Could not find any assignable Java object for fact \"" + ftName + "\" of type: " + ftType.getName());
	}

	private Object parseDate(String ftName, Object ftValue) {
		SimpleDateFormat formatter = new SimpleDateFormat(datetimeFormat);
		try {
			return formatter.parse(String.valueOf(ftValue));
		} catch (ParseException e) {
			throw new RuntimeException(String.format("Could not parse given fact value \"%s\" to valid date for the Date fact \"%s\".\n" +
															 "Make sure the value is correct and applies to the format: \"%s\"",
													 ftValue, ftName, datetimeFormat));
		}
	}

	/***
	 * Parsing given list of string fact values according to their data type
	 * @param ftName The camel case normalized fact name
	 * @param ftValues The list of string fact values
	 * @param listMemberType The list value members Java data type
	 * @return The list object with fact values parsed to the members data type
	 */
	private Object getCollectionParsedValue(String ftName, Collection ftValues, Class<?> listMemberType) {
		List result = createMatchingEmptyList(ftName, listMemberType);

		ftValues.forEach(val -> {
			Object memberValue = parsePrimitive(ftName, val, listMemberType);

			if (!memberValue.getClass().isAssignableFrom(listMemberType)) {
				throw new RuntimeException(String.format("Inconsistent types between member value type: \"%s\" to list member type \"%s\".\n" +
																 "Given fact value: \"%s\", fact name: \"%s\".",
														 memberValue.getClass().getName(), listMemberType.getName(), ftValues, ftName));
			}
			result.add(memberValue);
		});
		return result;
	}

	/***
	 * Creates a new ArrayList with the matching member type.
	 * @param ftName The list fact type name
	 * @param listMemberType The list value members Java data type
	 * @return The matching created list.
	 */
	private List createMatchingEmptyList(String ftName, Class<?> listMemberType) {
		if (listMemberType.isAssignableFrom(String.class)) {
			return new ArrayList<String>();
		} else if (listMemberType.isAssignableFrom(BigDecimal.class)) {
			return new ArrayList<BigDecimal>();
		} else if (listMemberType.isAssignableFrom(Double.class)) {
			return new ArrayList<Double>();
		} else if (listMemberType.isAssignableFrom(Integer.class)) {
			return new ArrayList<Integer>();
		} else if (listMemberType.isAssignableFrom(Date.class)) {
			return new ArrayList<Date>();
		} else if (listMemberType.isAssignableFrom(Boolean.class)) {
			return new ArrayList<Boolean>();
		}
		throw new RuntimeException("Could not find any assignable Java object for members of list fact \"" + ftName + "\" of type: " + listMemberType.getName());
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
	 * Validates that all input fact names really exists on the required artifact to execute
	 * @param givenFactNames Given fact names as inputs
	 * @param artifactInstance The artifact instance
	 * @param artifactName The artifact name
	 */
	private void assertFactNames(Set<String> givenFactNames, Group artifactInstance, String artifactName) {
		Set<String> actualNames = artifactInstance.getFactTypesRecursively().values().iterator().next().keySet();

		for (String givenFactName : givenFactNames) {

			String normalized = normalizeToCamelCase(givenFactName);
			if (!actualNames.contains(normalized)) {
				throw new RuntimeException(String.format("Given Fact Type name \"%s\" was not found on the requested artifact to execute \"%s\".\n" +
																 "The available fact types to set are: \"%s\".",
														 normalized, artifactName, actualNames.toString()));
			}
		}
	}

	/***
	 * Transform given fact name to camel case with first letter as upper case
	 * by dropping spaces and any non alphanumeric character
	 * @param factName
	 * @return
	 */
	private String normalizeToCamelCase(String factName) {
		String[] spaceDelimited = factName.split(" ");
		StringBuilder builder = new StringBuilder();

		// first normalize and set all as upper camel case
		for (String word : spaceDelimited) {
			word = word.replaceAll("[^a-zA-Z0-9]", ""); //removes any non alphanumeric char
			String capitalized = setFirstLetterAsUpperCase(word);
			builder.append(capitalized);
		}
		// than set entire name's first letter as upper or lower as requested
		return setFirstLetterAsUpperCase(builder.toString());
	}

	private String setFirstLetterAsUpperCase(String factName) {
		String firstLetter = factName.substring(0, 1);
		factName = factName.replaceFirst(firstLetter, firstLetter.toUpperCase());
		return factName;
	}
}
