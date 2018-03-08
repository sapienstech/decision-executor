package com.sapiens.bdms.decisionexecutor.service.impl;

import com.sapiens.bdms.decisionexecutor.service.face.ArtifactExecutorService;
import com.sapiens.bdms.java.exe.helper.base.Decision;
import com.sapiens.bdms.java.exe.helper.base.Flow;
import com.sapiens.bdms.java.exe.helper.base.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PojoArtifactExecutorService implements ArtifactExecutorService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

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

	@Value("${flow.classpath.format}")
	private String flowClasspathFormat;

	@Value("${date.fact.input.value.datetime.format}")
	private String datetimeFormat;

	@Override
	public Object executeDecision(String conclusionName,
								  String view,
								  String version,
								  Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException {

		String decisionClasspath = resolveDecisionClasspath(conclusionName, view, version);
		Class clazz;
		try {
			clazz = Class.forName(decisionClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for decision of conclusion \"%s\", view \"%s\" and version \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar packaged and built correctly with this application, \n" +
																   "as described in https://github.com/sapienstech/decision-executor/blob/master/README.md",
														   conclusionName, view, version));
		}
		Decision decision = (Decision) clazz.newInstance();
		setFactInputs(factValueByNameInputs, clazz, decision, decision.getName());

		return decision.execute();
	}

	@Override
	public Map<String, Object> executeFlow(String flowName, String version, Map<String, Object> factValueByNameInputs) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		String flowClasspath = resolveFlowClasspath(flowName, version);
		Class clazz;
		try {
			clazz = Class.forName(flowClasspath);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException(String.format("Class for flow \"%s\" and version \"%s\" not found.\n" +
																   "Make sure the above is accurate and the artifact jar packaged and built correctly with this application, \n" +
																   "as described in https://github.com/sapienstech/decision-executor/blob/master/README.md",
														   flowName, version));
		}
		Flow flow = (Flow) clazz.newInstance();
		setFactInputs(factValueByNameInputs, clazz, flow, flow.getName());

		return flow.execute();
	}

	private void setFactInputs(Map<String, Object> factValueByNameInputs,
							   Class artifactClass,
							   Group artifactInstance,
							   String artifactName) {
		assertFactNames(factValueByNameInputs.keySet(), artifactInstance, artifactName);

		factValueByNameInputs.forEach((ftName, ftValue) -> {
			String normalizeToFactNameInMethod = normalizeToCamelCase(ftName, true);

			Method ftGetter = Arrays.stream(artifactClass.getDeclaredMethods()).filter(
					method -> method.getName().startsWith("get" + normalizeToFactNameInMethod)
			).findFirst().orElseThrow(
					() -> new RuntimeException("Could not find getter method for fact field name " + normalizeToFactNameInMethod)
			);
			Object parsedValue = getParsedValue(ftName, ftValue, ftGetter.getReturnType(), artifactClass);

			String normalizeToFactFieldName = normalizeToCamelCase(ftName, false);
			artifactInstance.setFactType(normalizeToFactFieldName, parsedValue);
		});
	}

	private Object getParsedValue(String ftName, Object ftValue, Class<?> returnType, Class artifactClass) {
		if (Collection.class.isAssignableFrom(returnType)) {
			Class<?> listMemberType = resolveListMemberType(ftName, artifactClass);
			return getCollectionParsedValue(ftName, ftValue, listMemberType);
		}
		return parsePrimitive(ftName, ftValue, returnType);
	}

	private Class<?> resolveListMemberType(String ftName, Class artifactClass) {
		String normalizeToFactNameInMethod = normalizeToCamelCase(ftName, true);

		Method addMethod = Arrays.stream(artifactClass.getDeclaredMethods()).filter(
				method -> method.getName().startsWith("add" + normalizeToFactNameInMethod) &&
						method.getParameterCount() == 1
		).findFirst().orElseThrow(
				() -> new RuntimeException("Could not find add method method for fact field name " + normalizeToFactNameInMethod)
		);

		return addMethod.getParameterTypes()[0];
	}

	private Object parsePrimitive(String ftName, Object ftValue, Class<?> returnType) {
		if (returnType.isAssignableFrom(String.class)) {
			return ftValue;
		}
		else if (returnType.isAssignableFrom(BigDecimal.class)) {
			return new BigDecimal(String.valueOf(ftValue));
		}
		else if (returnType.isAssignableFrom(Double.class)) {
			return Double.parseDouble(String.valueOf(ftValue));
		}
		else if (returnType.isAssignableFrom(Integer.class)) {
			return Integer.parseInt(String.valueOf(ftValue));
		}
		else if (returnType.isAssignableFrom(Date.class)) {

			SimpleDateFormat formatter = new SimpleDateFormat(datetimeFormat);
			try {
				return formatter.parse(String.valueOf(ftValue));
			} catch (ParseException e) {
				throw new RuntimeException(String.format("Could not parse given fact value \"%s\" to valid date for the Date fact \"%s\".\n" +
																 "Make sure the value is correct and applies to the format: \"%s\"",
														 ftValue, ftName, datetimeFormat));
			}
		} else if (returnType.isAssignableFrom(Boolean.class)) {
			return Boolean.parseBoolean(String.valueOf(ftValue));
		}
		throw new RuntimeException("Could not find any assignable Java object for fact \"" + ftName + "\" of type: " + returnType.getName());
	}

	private Object getCollectionParsedValue(String ftName, Object ftValue, Class<?> listMemberType) {
		Collection values = (Collection) ftValue;
		List result = getMatchingListObject(ftName, listMemberType);

		values.forEach(val -> {
			Object memberValue = parsePrimitive(ftName, val, listMemberType);

			if (!memberValue.getClass().isAssignableFrom(listMemberType)) {
				throw new RuntimeException(String.format("Inconsistent types between member value type: \"%s\" to list member type \"%s\".\n" +
																 "Given fact value: \"%s\", fact name: \"%s\".",
														 memberValue.getClass().getName(), listMemberType.getName(), ftValue, ftName));
			}
			result.add(memberValue);
		});
		return result;
	}

	private List getMatchingListObject(String ftName, Class<?> listMemberType) {
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

	private String resolveDecisionClasspath(String conclusionName, String view, String version) {
		String versionNormalized = version.replace(".", versionDotReplacement);
		return decisionClasspathFormat.replace(formatViewPlaceholder, view)
									  .replace(formatVersionPlaceholder, versionNormalized) + "." + conclusionName;
	}

	private String resolveFlowClasspath(String flowName, String version) {
		String versionNormalized = version.replace(".", versionDotReplacement);
		return flowClasspathFormat.replace(formatVersionPlaceholder, versionNormalized) + "." + flowName;
	}

	private void assertFactNames(Set<String> givenFactNames, Group artifactInstance, String artifactName) {
		Set<String> actualNames = artifactInstance.getFactTypes().keySet();

		for (String givenFactName : givenFactNames) {

			String normalized = normalizeToCamelCase(givenFactName, true);
			if (!actualNames.contains(normalized)) {
				throw new RuntimeException(String.format("Given Fact Type name \"%s\" was not found on the requested artifact to execute \"%s\".\n" +
																 "The available fact types to set are: \"%s\".",
														 normalized, artifactName, actualNames.toString()));
			}
		}
	}

	private String normalizeToCamelCase(String factName, boolean isFirstAsCapital) {
		String[] spaceDelimited = factName.split(" ");
		StringBuilder builder = new StringBuilder();

		// first normalize and set all as upper camel case
		for (String word : spaceDelimited) {
			word = word.replaceAll("[^a-zA-Z0-9]", ""); //removes any non alphanumeric char
			String capitalized = setFirstLetterCase(word, true);
			builder.append(capitalized);
		}
		// than set entire name's first letter as upper or lower as requested
		return setFirstLetterCase(builder.toString(), isFirstAsCapital);
	}

	private String setFirstLetterCase(String factName, boolean isFirstAsCapital) {
		String firstLetter = factName.substring(0, 1);
		String firstLetterCorrectedCase = isFirstAsCapital ? firstLetter.toUpperCase() : firstLetter.toLowerCase();
		factName = factName.replaceFirst(firstLetter, firstLetterCorrectedCase);
		return factName;
	}
}
