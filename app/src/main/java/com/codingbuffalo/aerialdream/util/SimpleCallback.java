package com.codingbuffalo.aerialdream.util;

import java.io.File;

public abstract class SimpleCallback implements DownloadManager.Callback {
	@Override
	public void onDownloadStarted(long totalLength) {
	}
	
	@Override
	public void onDownloadProgress(long downloadedLength) {
	}
	
	@Override
	public void onDownloadComplete(File file) {
	}
	
	@Override
	public void onDownloadError(Exception e) {
	}
}
