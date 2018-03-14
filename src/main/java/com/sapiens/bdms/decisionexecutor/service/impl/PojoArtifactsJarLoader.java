package com.sapiens.bdms.decisionexecutor.service.impl;

import com.google.common.collect.Maps;
import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;
import com.sapiens.bdms.decisionexecutor.service.face.ArtifactsJarLoader;
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
public class PojoArtifactsJarLoader implements ArtifactsJarLoader {

	private final Map<String, URLClassLoader> classLoadersByJarOrClassPath = Maps.newHashMap();

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${artifacts.jar.location}")
	private String defaultArtifactsJarLocation;

	@Override
	public Class getArtifactClass(String fullClassName) throws ClassNotFoundException {
		Class found = null;
		for (URLClassLoader classLoader : classLoadersByJarOrClassPath.values()) {
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

	@Override
	public int loadArtifactJarsFromDefaultLocation(boolean reloadIfAlreadyLoaded) throws MissingFileException {
		int loaded = loadAndSetToMap(defaultArtifactsJarLocation, reloadIfAlreadyLoaded);
		if(loaded > 0 || reloadIfAlreadyLoaded){
			logger.info("Loaded " + loaded + " artifact files from \"" + defaultArtifactsJarLocation + "\"");
		}
		return loaded;
	}

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
		return classLoadersByJarOrClassPath.isEmpty();
	}

	private int loadAndSetToMap(String pathString,
								boolean reloadIfAlreadyLoaded) throws MissingFileException {
		synchronized (classLoadersByJarOrClassPath) {
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

	private int recursivelyLoadAndSetToMap(Path path, boolean reloadIfAlreadyLoaded) {
		if (path.toFile().isFile()) {
			return loadAndSetToMap(path, reloadIfAlreadyLoaded) ? 1 : 0;
		}
		int count = 0;
		//is a dir
		for (File file : path.toFile().listFiles()) {
			count += recursivelyLoadAndSetToMap(Paths.get(file.toURI()), reloadIfAlreadyLoaded);
		}
		return count;
	}

	private boolean loadAndSetToMap(Path jarOrClassPath, boolean reloadIfAlreadyLoaded) {
		String jarOrClassName = jarOrClassPath.toAbsolutePath().toString();
		boolean isAlreadyLoaded = classLoadersByJarOrClassPath.containsKey(jarOrClassName);

		if (!reloadIfAlreadyLoaded && isAlreadyLoaded) {
			return false;
		}

		if (!isAJarOrClassFile(jarOrClassPath)) {
			logger.warn("Could not load file \"" + jarOrClassName + "\" located in the artifacts folder since it is not a " +
								"jar or class file (does not have the a compatible extension)");
			return false;
		}

		URLClassLoader classLoader;
		try {
			classLoader = new URLClassLoader(new URL[]{jarOrClassPath.toUri().toURL()}, this.getClass().getClassLoader());
		} catch (MalformedURLException e) {
			logger.error("Failed to load artifacts jar \"" + jarOrClassName + "\": " + e.getMessage());
			return false;
		}

		if (isAlreadyLoaded) {
			try {
				classLoadersByJarOrClassPath.get(jarOrClassName).close();
				classLoadersByJarOrClassPath.remove(jarOrClassName);
				logger.info("Unloaded older artifact file: \""+ jarOrClassName + "\"");
			} catch (IOException e) {
				logger.error("Failed to reload existing artifacts jar \"" + jarOrClassName + "\", might currently be in use - it will not be reloaded.", e);
				return false;
			}
		}
		classLoadersByJarOrClassPath.put(jarOrClassName, classLoader);
		logger.info("Loaded artifact/s file: \""+ jarOrClassName + "\"");
		return true;
	}

	private boolean isAJarOrClassFile(Path jarOrClassPath) {
		String jarOrClassFileName = jarOrClassPath.toAbsolutePath().toString();
		return jarOrClassFileName.endsWith(".jar") ||
				jarOrClassFileName.endsWith(".JAR") ||
				jarOrClassFileName.endsWith(".class") ||
				jarOrClassFileName.endsWith(".CLASS");
	}

	private String toFullPath(String path) {
		return Paths.get(path).toAbsolutePath().normalize().toString();
	}

}
