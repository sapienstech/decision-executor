package com.sapiens.bdms.decisionexecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/***
 * This is the Spring boot configuration and lunch class
 */
@SpringBootApplication
public class DecisionExecutorApplication {

	public static void main(String[] args) {
		SpringApplication.run(DecisionExecutorApplication.class, args);
	}

	/***
	 * Configuration setting for jackson auto JSON serialization for HTTP requests and responses
	 * @return
	 */
	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		ObjectMapper mapper = new ObjectMapper();

		// change the default not to fail on empty beans
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(mapper);
		return converter;
	}
}
