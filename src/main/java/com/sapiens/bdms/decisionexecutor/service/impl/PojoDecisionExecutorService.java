package com.sapiens.bdms.decisionexecutor.service.impl;

import com.sapiens.bdms.decisionexecutor.service.face.DecisionExecutorService;
import com.sapiens.bdms.java.exe.helper.base.Decision;
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
		assertFactNames(factValueByNameInputs.keySet(), decision);

		factValueByNameInputs.forEach((ftName, ftValue) -> {
			String normalizeToFactFieldName = normalizeToFactFieldName(ftName);

			Method ftGetter = Arrays.stream(clazz.getDeclaredMethods()).filter(
					method -> method.getName().startsWith("get" + normalizeToFactFieldName)
			).findFirst().orElseThrow(
					() -> new RuntimeException("Could not find getter method for fact field name " + normalizeToFactFieldName)
			);
			Object parsedValue = getParsedValue(ftName, ftValue, ftGetter.getReturnType(), clazz);

			decision.setFactType(ftName, parsedValue);
		});

		return decision.execute();
	}

	@Override
	public Object executeFlow(String flowName, String version, Map<String, Object> factValueByNameInputs) {
		return null;
	}

	private Object getParsedValue(String ftName, Object ftValue, Class<?> returnType, Class artifactClass) {
		if (returnType.isAssignableFrom(Collection.class)) {
			Class<?> listMemberType = resolveListMemberType(ftName, artifactClass);
			return getCollectionParsedValue(ftName, ftValue, listMemberType);
		}
		return parsePrimitive(ftName, ftValue, returnType);
	}

	private Class<?> resolveListMemberType(String ftName, Class artifactClass) {
		String normalizeToFactFieldName = normalizeToFactFieldName(ftName);

		Method addMethod = Arrays.stream(artifactClass.getDeclaredMethods()).filter(
				method -> method.getName().startsWith("add" + normalizeToFactFieldName) &&
						method.getParameterCount() == 1
		).findFirst().orElseThrow(
				() -> new RuntimeException("Could not find add method method for fact field name " + normalizeToFactFieldName)
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

	private void assertFactNames(Set<String> givenFactNames, Decision decision) {
		Set<String> actualNames = decision.getFactTypes().keySet();

		for (String givenFactName : givenFactNames) {

			String normalized = normalizeToFactFieldName(givenFactName);
			if (!actualNames.contains(normalized)) {
				throw new RuntimeException(String.format("Given Fact Type name \"%s\" was not found on the requested decision to execute \"%s\".\n" +
																 "The available fact types to set are: \"%s\".",
														 normalized, decision.getName(), actualNames.toString()));
			}
		}
	}

	private String normalizeToFactFieldName(String factName) {
		String[] spaceDelimited = factName.split(" ");
		StringBuilder result = new StringBuilder();

		for (String word : spaceDelimited) {
			String firstLetter = word.substring(0, 1);
			String firstLetterAsCapital = firstLetter.toUpperCase();
			String capitalized = word.replaceFirst(firstLetter, firstLetterAsCapital);
			result.append(capitalized);
		}
		return result.toString();
	}
}
