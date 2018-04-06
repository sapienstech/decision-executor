package com.sapiens.bdms.decisionexecutor.service.impl;

import com.google.common.collect.Maps;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactInputsInitializerService;
import com.sapiens.bdms.java.exe.helper.base.Group;
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
public class PojoArtifactInputsInitializerService implements ArtifactInputsInitializerService {

	@Value("${date.fact.input.value.datetime.format}")
	private String datetimeFormat;

	/***
	 * Sets given input values by fact name into the artifact instance to be executed
	 * @param factValueByNameInputs Map of String fact values (or list of fact values) by fact name
	 * @param artifactClass Java class of the artifact
	 * @param artifactInstance Constructed instance of the artifact
	 * @param artifactName The artifact name
	 */
	@Override
	public void setFactInputs(Map<String, Object> factValueByNameInputs,
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
