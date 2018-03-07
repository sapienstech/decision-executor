# decision-executor
A RESTful spring boot app to execute any artifact placed in its classpath or given as input =]

<u>**Configure and Build**</u>

1. Build the project to generate required dependencies with the following "-D" properties:<br>
**mvn clean generate-resources -Dartifact.jar.path=? -Dartifact.name=? -Dartifact.version=?**
<br><br>
**artifact.jar.path**: jar file path to the exported input artifact jar.<br>
The default value is "\lib\decision-exported-jar.jar", so you can just rename your jar to this name and drop it in the lib folder.
<br><br>
**artifact.name**: The maven artifact name by which the input jar will be called and installed to local repository.<br>
Default value is "decision-artifact"
<br><br>
**artifact.version**: The maven artifact version by which the input jar will be installed to local repository.<br>
Default value is "1.0".
<br><br>
After this build the exported input artifact jar is installed into Maven's local repository (located by default in the \<user directory\>/.m2) under the path "com.sapiens.bdms.\<artifact.name\>".
 
2. Build the project to generate the standalone Spring Boot application:<br>
**mvn clean install**

3. Run the application in command line from the build output (\<project location\>/target):<br>
**java -jar decision-executor-1.0.0.jar**

<u>**Usage**</u><br><br>
The decision and/or flow artifacts wrapped in the input jar can be executed with this app REST API by calling:

- POST call to **http://localhost:8080/execute/decision/{conclusionName}/{view}/{version}** <br>
along with a JSON map of the persistent fact values by fact names as the request body.<br>
e.g:<br>
**http://localhost:8080/execute/decision/CustomerTurnoverAmount/Base/1.0<br>
{<br>
    "Fact A Name": "val1",<br>
    "Fact B Name": "val2"<br>
    "List Fact C Name": ["val1","val2","val3"]<br>
    "Date Fact B Name": "01/28/2018 15:23:23"<br>
}**<br>
Note: The format for date values **must be** MM/dd/yyyy HH:mm:ss
- In the same way POST call to **http://localhost:8080/execute/decision/{flowName}/{version}**

