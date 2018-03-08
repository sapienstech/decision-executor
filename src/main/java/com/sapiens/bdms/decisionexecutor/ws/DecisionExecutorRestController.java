package com.sapiens.bdms.decisionexecutor.ws;

import com.google.common.collect.Lists;
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
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
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

	@Value("${default.artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

	@RequestMapping(value = "/execute/decision/{conclusionName}/{view}/{version}", method = POST)
	public Object executeDecision(@PathVariable String conclusionName,
								  @PathVariable String view,
								  @PathVariable String version,
								  @RequestBody Map<String, Object> factValueByNameInputs) {
		try {
			return pojoArtifactExecutorService.executeDecision(conclusionName, view, version, factValueByNameInputs);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	@RequestMapping(value = "/execute/flow/{flowName}/{version}", method = POST)
	public Map<String, Object> executeFlow(@PathVariable String flowName,
										   @PathVariable String version,
										   @RequestBody Map<String, Object> factValueByNameInputs) {

		try {
			Map<String, Object> result = pojoArtifactExecutorService.executeFlow(flowName, version, factValueByNameInputs);
			return normalizeFlowResult(result);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			Map<String, Object> error = Maps.newHashMap();
			error.put("Error", e.getMessage());
			return error;
		}
	}

	private Map<String, Object> normalizeFlowResult(Map<String, Object> flowResult) {
		Map<String, Object> result = Maps.newHashMap();

		flowResult.forEach((key, val) -> {
			FlowExecutionFactResultDto dto = new FlowExecutionFactResultDto((FactType) val);
			result.put(key, dto);
		});

		return result;
	}

	@RequestMapping(value = "reload/artifacts/jars/from/{path}", method = GET)
	public String reloadArtifactsJarsFrom(@PathVariable String path, @RequestParam boolean forceReload) {
		try {
			int loaded = pojoArtifactsJarLoader.loadArtifactJarsFrom(path, forceReload);
			return "Loaded " + loaded + " artifacts from " + Paths.get(path).toAbsolutePath().toString();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	@RequestMapping(value = "reload/artifacts/jars/from/default/path", method = GET)
	public String reloadArtifactsJarsFrom(@RequestParam boolean forceReload) {
		try {
			int loaded = pojoArtifactsJarLoader.loadArtifactJarsFromDefaultLocation(forceReload);
			return "Loaded " + loaded + " artifacts from default path (\"" + Paths.get(defaultArtifactsJarLocation).toAbsolutePath().toString() + "\")";
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return "Error: " + e.getMessage();
		}
	}

	@RequestMapping("/rest/test")
	public Map<String, Object> restTest() {
		logger.info("in restTest");

		HashMap<String, Object> map = new HashMap<>();
		map.put("string", "val1");
		map.put("int", 1);
		map.put("dbl", new Double("7.7"));
		map.put("date", new Date());
		map.put("int list", Lists.newArrayList(1, 2, 3));
		map.put("string list", Lists.newArrayList("1", "2", "3"));
		map.put("bd", new BigDecimal("5.333"));
		return map;
	}
}
