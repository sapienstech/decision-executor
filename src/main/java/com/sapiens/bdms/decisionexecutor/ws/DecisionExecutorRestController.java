package com.sapiens.bdms.decisionexecutor.ws;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DecisionExecutorRestController {

	@RequestMapping("/execute/decision/{decisionName}/{view}/{version}")
	public String executeDecision(@PathVariable String decisionName,
								  @PathVariable String view,
								  @PathVariable String version,
								  @RequestBody Map<String, String> factValueByNameInputs){
		return null;
	}

	@RequestMapping("/execute/decision/{flowName}/{version}")
	public String executeFlow(@PathVariable String flowName, @PathVariable String version){
		return null;
	}
}
