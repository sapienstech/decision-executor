package com.sapiens.bdms.decisionexecutor.ws;

import com.google.common.collect.Maps;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactExecutorService;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactsJarLoader;
import com.sapiens.bdms.decisionexecutor.ws.model.FlowExecutionFactResultDto;
import com.sapiens.bdms.java.exe.helper.base.FactType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
public class DecisionExecutorRestController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Resource
	private ArtifactExecutorService pojoArtifactExecutorService;

	@Resource
	private ArtifactsJarLoader pojoArtifactsJarLoader;

	@Value("${artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

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
	@RequestMapping(value = "/execute/decision/{packagePrefix}/{conclusionName}/{view}/{version}", method = POST)
	public Object executeDecision(@PathVariable String conclusionName,
								  @PathVariable String packagePrefix,
								  @PathVariable String view,
								  @PathVariable String version,
								  @RequestBody Map<String, Object> factValueByNameInputs) {
		try {
			return pojoArtifactExecutorService.executeDecision(packagePrefix, conclusionName, view, version, factValueByNameInputs);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	/***
	 * Execute a Flow according to given parameters and return result with messages
	 * @param packagePrefix The Java package prefix as was set to the "Generated Package Prefix" property in the
	 *                      DM Java Adapter with which this Decision class was exported
	 * @param version The Flow's version
	 * @param factValueByNameInputs The Map of the execution input values by their Fact Type name
	 * @return Execution result as map of execution result with row hits and messages by Fact Type Name
	 */
	@RequestMapping(value = "/execute/flow/{packagePrefix}/{flowName}/{version}", method = POST)
	public Map<String, Object> executeFlow(@PathVariable String flowName,
										   @PathVariable String packagePrefix,
										   @PathVariable String version,
										   @RequestBody Map<String, Object> factValueByNameInputs) {

		try {
			Map<String, Object> result = pojoArtifactExecutorService.executeFlow(packagePrefix, flowName, version, factValueByNameInputs);
			return normalizeFlowResult(result);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			Map<String, Object> error = Maps.newHashMap();
			error.put("Error", e.getMessage());
			return error;
		}
	}

	/***
	 * Scans the artifacts jar in given location and re-loads the execution artifacts to memory.
	 * Will update existing artifacts unless specified otherwise in the "forceReload" parameter
	 * @param path Custom location to reload the jars
	 * @param forceReload Optional query parameter to determine if to load and override jar files that were already loaded into memory.
	 *                    Default is true.
	 * @return Action result string.
	 */
	@RequestMapping(value = "reload/artifacts/jars/from/{path}", method = GET)
	public String reloadArtifactsJarsFrom(@PathVariable String path, @RequestParam(defaultValue = "true") boolean forceReload) {
		try {
			int loaded = pojoArtifactsJarLoader.loadArtifactJarsFrom(path, forceReload);
			return "Loaded " + loaded + " artifacts from " + Paths.get(path).toAbsolutePath().toString();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	/***
	 * Scans the artifacts jar default location and re-loads the execution artifacts to memory.
	 * Will update existing artifacts unless specified otherwise in the "forceReload" parameter
	 * @param forceReload Optional query parameter to determine if to load and override jar files that were already loaded into memory.
	 *                    Default is true.
	 * @return Action result string.
	 */
	@RequestMapping(value = "reload/artifacts/jars/from/default/path", method = GET)
	public String reloadArtifactsJarsFromDefaultPath(@RequestParam(defaultValue = "true") boolean forceReload) {
		try {
			int loaded = pojoArtifactsJarLoader.loadArtifactJarsFromDefaultLocation(forceReload);
			return "Loaded " + loaded + " artifacts from default path (\"" + Paths.get(defaultArtifactsJarLocation).toAbsolutePath().toString() + "\")";
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	/***
	 * Transforms the FactType objects into FlowExecutionFactResultDto,
	 * which is basically the same except that it does not include the "parent" property
	 * which cause jackson to fail JSON parsing.
	 * Another change - removes empty fact type results
	 * @param flowResult
	 * @return
	 */
	private Map<String, Object> normalizeFlowResult(Map<String, Object> flowResult) {
		Map<String, Object> result = Maps.newHashMap();

		flowResult.forEach((key, val) -> {
			FlowExecutionFactResultDto dto = new FlowExecutionFactResultDto((FactType) val);
			if(hasAValue(dto)){
				result.put(key, dto);
			}
		});

		return result;
	}

	private boolean hasAValue(FlowExecutionFactResultDto dto) {
		Object value = dto.getValue();
		return value != null &&
				isNotEmptyString(value) &&
				isNotEmptyCollection(value);
	}

	private boolean isNotEmptyString(Object value){
		return !(value.getClass().isAssignableFrom(String.class) && ((String) value).isEmpty());
	}

	private boolean isNotEmptyCollection(Object value){
		return !(Collection.class.isAssignableFrom(value.getClass()) && ((Collection) value).isEmpty());
	}

}
