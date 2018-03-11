package com.sapiens.bdms.decisionexecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static com.sapiens.bdms.decisionexecutor.GeneralConstants.CONF_DIR;

@SpringBootApplication
public class DecisionExecutorApplication {

	public static void main(String[] args) {
		setUserCustomProps();
		SpringApplication.run(DecisionExecutorApplication.class, args);
	}

	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		MappingJackson2HttpMessageConverter converter =
				new MappingJackson2HttpMessageConverter(mapper);
		return converter;
	}

	private static void setUserCustomProps(){
		Path confDir = Paths.get(CONF_DIR);
		if(!confDir.toFile().exists() || !confDir.toFile().isDirectory()){
			System.err.println("WARNING: Missing configuration directory: \""+confDir.toAbsolutePath().toString()+"\". " +
									   "Default values will be used");
			return;
		}
		int propsCount = 0;
		for (File file : confDir.toFile().listFiles()) {
			if(file.getName().endsWith("properties") || file.getName().endsWith("properties")){
				Properties properties = getAllPropertiesFromFile(file.toURI());
				if(!properties.isEmpty()){
					propsCount++;
				}
				properties.forEach((key, val) -> {
					System.err.println("INFO: Setting/Overriding property \"" + key + "\" with \"" + val + "\"");
					System.setProperty((String)key, (String)val);
				});
			}
		}
		if(propsCount == 0){
			System.err.println("WARNING: Missing or only empty properties files in configuration directory: \""+confDir.toAbsolutePath().toString()+"\". " +
									   "Default values will be used");
		}
	}

	private static Properties getAllPropertiesFromFile(URI propertiesFile) {
		Properties properties = new Properties();

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(propertiesFile.toURL().openStream()));
			properties.load(reader);
		} catch (IOException e) {
			System.err.println("WARNING: Failed loading " + propertiesFile + " property file");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		return properties;
	}
}
