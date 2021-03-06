# decision-executor
A RESTful spring boot app to execute any decision or flow jar artifact placed in its lib folder or any other predefined folder

<u>**Build**</u>

1. Build the project to generate required dependencies:<br>
**mvn clean generate-resources**
<br><br>
After this build the "exe helper" jar is installed into Maven's local repository (located by default in the \<user directory\>/.m2)
 
2. Build the project to generate the standalone Spring Boot application:<br>
**mvn clean install**

3. The application release bundle was created in the project target folder (\<project location\>/target/decision-executor-1.0.0-release.tar.gz).

4. Extract decision-executor-1.0.0-release.tar.gz

5. The release content includes:<br>
5.1. "config" folder - Includes the "log4j.xml" (logging configuration) and "application.properties" which can be set with any user defined properties to override defaults.<br>
5.2. "lib" folder - The default location to place the decision/s and flow/s artifact jars.<br>
5.3. "logs" folder - The default location the logs will be written to.

<u>**Configuration**</u>

1. Logging: The log4j configuration is located in the "config" folder.<br>
It comes with a default configuration of INFO level logging to the console and "logs/app.log" log file + TRACE level of the artifact execution into "logs/execution.log"<br>
In order to have the full details of the execution logged, the execution loggers log level in log4j.xml can be changed accordingly.<br>
The available execution loggers are: "flow.execution", "decision.execution", "decision.execution.row", "decision.facttype.init", "decision.facttype.model".<br>
The default is maximum details of execution with each logger set with TRACE

2. Custom properties: In the config, the file "application.properties" can be set with any properties below to override defaults.<br>
2.1. **artifacts.jar.location** - The main location from which the application loads and re-loads the decisions/flows artifacts jars. The default is \<release folder full path\>/lib.<br>
2.2. **date.fact.input.value.datetime.format** - The format in which the application expects the date input fact values to be sent.

<u>**Artifacts Requirement**</u>
Artifact jars placed in the "artifacts.jar.location" (see above) must be such that were generated using DECISION DM Java Adapter with its default properties setting.<br>
More specifically, the below adapter properties must stay with their default value:
- **Decision Input Bean Generator** - must stay "noInputBeanGenerator"
- **Include Decision in Generated Package** - must stay "No"
- **Include Release Name in Generated Package** - must stay "No"
- **Include Tag Name in Generated Package** - must stay "No"
- **Generated JARs** - must be either "Each decision in separate jar" or "All decisions in one jar"
- **Java Compilation Version** - must stay "1.8"

<u>**Start the application**</u>

The application has a built in Tomcat servlet that will listen to port 8080.<br>
Run the application in command line from where you extracted the release with this command:
<br><br>
**java -Dlog4j.configuration="file:/\<release folder full path\>/config/log4j.xml" -jar decision-executor-1.0.0.jar**
<br><br>
e.g: java -Dlog4j.configuration="file://user/home/Decision Executor/config/log4j.xml" -jar decision-executor-1.0.0.jar

<u>**Usage**</u>
1. Execution: The decision and/or flow artifacts wrapped in the input jar/s can be executed with this app REST API by calling:<br><br>
1.1. POST call to **http://localhost:8080/execute/decision/{packagePrefix}/{conclusionName}/{view}/{version}** <br>
along with a JSON map of the persistent fact values by fact names as the request body.<br><br>
Note - **{packagePrefix}** is the value set to the property "Generated Package Prefix" in the DM Java Adapter with which the artifact's jar was exported
e.g:<br>
**http://localhost:8080/execute/decision/com.sapiens/CustomerTurnoverAmount/Base/1.0<br>
{<br>
    "Fact A Name": "val1",<br>
    "Fact B Name": "val2"<br>
    "List Fact C Name": ["val1","val2","val3"]<br>
    "Date Fact B Name": "01/28/2018 15:23:23"<br>
}**<br><br>
Note: The format for the date values **must be** according to what was set in the "date.fact.input.value.datetime.format" property (default is MM/dd/yyyy HH:mm:ss)<br><br>
1.2. In the same way POST call to **http://localhost:8080/execute/flow/{packagePrefix}/{flowName}/{version}**

2. Artifacts loading:<br><br>
2.1. GET call to **http://localhost:8080/reload/artifacts/jars/from/default/path?forceReload=?** with the "forceReload" query parameter<br>
Will scan the artifacts jar location (set in the "artifacts.jar.location") and load new artifact jar files that were added.<br>
2.1.1. **forceReload** - Optional query parameter to determine if to load and override jar files that were already loaded into memory. Default is true.<br><br>
2.2. GET call to **http://localhost:8080/reload/artifacts/jars/from/{path}?forceReload=?** with the "forceReload" query parameter<br>
Same as the above, but from a given path in "{path}".<br>
Note that the "{path}" value must be URL encoded properly.<br><br>
e.g:<br>
http://localhost:8080/reload/artifacts/jars/from/%2Fpath%2Fto%2Fmy+lib?forceReload=true
