package com.sapiens.bdms.decisionexecutor.ws;

import com.google.common.collect.Lists;
import com.sapiens.bdms.decisionexecutor.service.face.DecisionExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
public class DecisionExecutorRestController {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Resource
	private DecisionExecutorService pojoDecisionExecutorService;

	@RequestMapping(value = "/execute/decision/{conclusionName}/{view}/{version}", method = POST)
	public Object executeDecision(@PathVariable String conclusionName,
								  @PathVariable String view,
								  @PathVariable String version,
								  @RequestBody Map<String, Object> factValueByNameInputs){
		try {
			return pojoDecisionExecutorService.executeDecision(conclusionName, view, version, factValueByNameInputs);
		} catch (Exception e) {
			e.printStackTrace();
			return "Error: "+e.getMessage();
		}
	}

	@RequestMapping(value = "/execute/decision/{flowName}/{version}", method = POST)
	public Object executeFlow(@PathVariable String flowName,
							  @PathVariable String version,
							  @RequestBody Map<String, Object> factValueByNameInputs){

		try {
			return pojoDecisionExecutorService.executeFlow(flowName, version, factValueByNameInputs);
		} catch (Exception e) {
			e.printStackTrace();
			return "Error: "+e.getMessage();
		}
	}

	@RequestMapping("/rest/test")
	public Map<String, Object> restTest(){
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
