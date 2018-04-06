package com.sapiens.bdms.decisionexecutor.service.face;

import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;

public interface ArtifactsJarLoader {
	/***
	 * Tries to load the class according to given classpath by iterating all existing Jars class loaders
	 * @param fullClassName full classpath to the required class
	 * @return The class found
	 * @throws ClassNotFoundException if no class was found in any of the class loaders
	 */
	Class getArtifactClass(String fullClassName) throws ClassNotFoundException;
	/***
	 * Calls "loadArtifactJarsFrom" with the configured default jars location
	 * @param reloadIfAlreadyLoaded Reload even if Jar already loaded before
	 * @return
	 * @throws MissingFileException if no Jar was found in given path
	 */
	int loadArtifactJarsFromDefaultLocation(boolean reloadIfAlreadyLoaded) throws MissingFileException;
	/***
	 * Loads all Jar files found in given path
	 * @param path Path to load Jars from
	 * @param reloadIfAlreadyLoaded Reload even if Jar already loaded before
	 * @return Number of Jars loaded
	 * @throws MissingFileException if no Jar was found in given path
	 */
	int loadArtifactJarsFrom(String path, boolean reloadIfAlreadyLoaded) throws MissingFileException;
	boolean isClassLoadersEmpty();
}
