package com.sapiens.bdms.decisionexecutor.service.face;

import com.sapiens.bdms.decisionexecutor.exception.MissingFileException;

public interface ArtifactsJarLoader {
	Class getArtifactClass(String fullClassName) throws ClassNotFoundException;
	int loadArtifactJarsFromDefaultLocation(boolean reloadIfAlreadyLoaded) throws MissingFileException;
	int loadArtifactJarsFrom(String path, boolean reloadIfAlreadyLoaded) throws MissingFileException;
	boolean isClassLoadersEmpty();
}
