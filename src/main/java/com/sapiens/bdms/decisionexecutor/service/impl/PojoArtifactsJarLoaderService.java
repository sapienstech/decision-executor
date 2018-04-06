package com.sapiens.bdms.decisionexecutor.service.impl;

import com.google.common.collect.Maps;
import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactsJarLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class PojoArtifactsJarLoaderService implements ArtifactsJarLoaderService {

	private final Map<String, URLClassLoader> classLoadersByJarPath = Maps.newHashMap();

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

	/***
	 * Tries to load the class according to given classpath by iterating all existing Jars class loaders
	 * @param fullClassName full classpath to the required class
	 * @return The class found
	 * @throws ClassNotFoundException if no class was found in any of the class loaders
	 */
	@Override
	public Class getArtifactClass(String fullClassName) throws ClassNotFoundException {
		Class found = null;
		for (URLClassLoader classLoader : classLoadersByJarPath.values()) {
			try {
				found = Class.forName(fullClassName, true, classLoader);
			} catch (ClassNotFoundException e) {
				// try next classLoader
			}
		}
		if (found == null) {
			throw new ClassNotFoundException("Could not find class \"" + fullClassName + "\" in any class loader.");
		} else {
			return found;
		}
	}

	/***
	 * Calls "loadArtifactJarsFrom" with the configured default jars location
	 * @param reloadIfAlreadyLoaded
	 * @return
	 * @throws MissingFileException
	 */
	@Override
	public int loadArtifactJarsFromDefaultLocation(boolean reloadIfAlreadyLoaded) throws MissingFileException {
		return loadArtifactJarsFrom(defaultArtifactsJarLocation, reloadIfAlreadyLoaded);
	}

	/***
	 * Loads all Jar files found in given path
	 * @param path Path to load Jars from
	 * @param reloadIfAlreadyLoaded Reload even if Jar already loaded before
	 * @return Number of Jars loaded
	 * @throws MissingFileException if no Jar was found in given path
	 */
	@Override
	public int loadArtifactJarsFrom(String path, boolean reloadIfAlreadyLoaded) throws MissingFileException {
		int loaded = loadAndSetToMap(path, reloadIfAlreadyLoaded);
		if(loaded > 0 || reloadIfAlreadyLoaded){
			logger.info("Loaded " + loaded + " artifact files from \"" + path + "\"");
		}
		return loaded;
	}

	@Override
	public boolean isClassLoadersEmpty() {
		return classLoadersByJarPath.isEmpty();
	}

	/***
	 * Loads all Jars found in given path into a map by jar name
	 */
	private int loadAndSetToMap(String pathString,
								boolean reloadIfAlreadyLoaded) throws MissingFileException {
		synchronized (classLoadersByJarPath) {
			Path path = Paths.get(pathString);
			File file = path.toFile();

			if (isFileDoesNotExistOrIsAnEmptyDir(file)) {
				throw new MissingFileException("File or directory \"" + toFullPath(pathString) + "\" does not exist");
			}
			return recursivelyLoadAndSetToMap(path, reloadIfAlreadyLoaded);
		}
	}

	private boolean isFileDoesNotExistOrIsAnEmptyDir(File file) {
		return !file.exists() ||
				(file.exists() && file.isDirectory() && file.list().length == 0);
	}

	/***
	 * Saves a reference to all Jars found in given path and all sub-paths within it in a map by jar name
	 * @return Number of Jars loaded
	 */
	private int recursivelyLoadAndSetToMap(Path path, boolean reloadIfAlreadyLoaded) {
		if (path.toFile().isFile()) {
			return loadAndSetToMapSingleJar(path, reloadIfAlreadyLoaded) ? 1 : 0;
		}
		int count = 0;
		//is a dir
		for (File file : path.toFile().listFiles()) {
			count += recursivelyLoadAndSetToMap(Paths.get(file.toURI()), reloadIfAlreadyLoaded);
		}
		return count;
	}

	/***
	 * Loads a single jar reference into a URLClassLoader and saves it into a map by the jar full path
	 * @return true if loaded, false otherwise
	 */
	private boolean loadAndSetToMapSingleJar(Path jarPath, boolean reloadIfAlreadyLoaded) {
		String jarName = jarPath.toAbsolutePath().toString();
		boolean isAlreadyLoaded = classLoadersByJarPath.containsKey(jarName);

		if (!reloadIfAlreadyLoaded && isAlreadyLoaded) {
			return false;
		}

		if (!isAJarFile(jarPath)) {
			logger.warn("Could not load file \"" + jarName + "\" located in the artifacts folder since it is not a " +
								"jar or class file (does not have the a compatible extension)");
			return false;
		}

		URLClassLoader classLoader;
		try {
			classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, this.getClass().getClassLoader());
		} catch (MalformedURLException e) {
			logger.error("Failed to load artifacts jar \"" + jarName + "\": " + e.getMessage());
			return false;
		}

		if (isAlreadyLoaded) {
			try {
				classLoadersByJarPath.get(jarName).close();
				classLoadersByJarPath.remove(jarName);
				logger.info("Unloaded older artifact file: \""+ jarName + "\"");
			} catch (IOException e) {
				logger.error("Failed to reload existing artifacts jar \"" + jarName + "\", might currently be in use - it will not be reloaded.", e);
				return false;
			}
		}
		classLoadersByJarPath.put(jarName, classLoader);
		logger.info("Loaded artifact/s file: \""+ jarName + "\"");
		return true;
	}

	private boolean isAJarFile(Path jarPath) {
		String jarFileName = jarPath.toAbsolutePath().toString();
		return jarFileName.endsWith(".jar") || jarFileName.endsWith(".JAR");
	}

	private String toFullPath(String path) {
		return Paths.get(path).toAbsolutePath().normalize().toString();
	}

}
