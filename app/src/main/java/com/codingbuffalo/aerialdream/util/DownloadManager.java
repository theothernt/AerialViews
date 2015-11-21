package com.codingbuffalo.aerialdream.util;

import android.os.Handler;
import android.support.annotation.UiThread;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okio.BufferedSink;
import okio.Okio;

public class DownloadManager {
	private static final long BUFFER_SIZE = 2048;
	private static final long FLUSH_SIZE  = 1048 * 1048;    // 1MB
	private static final long UPDATE_RATE = 1000;
	
	private ExecutorService mExecutorService;
	private Handler         mHandler;
	
	@UiThread
	public DownloadManager(int threadCount) {
		mExecutorService = Executors.newFixedThreadPool(threadCount);
		mHandler = new Handler();
	}
	
	public void download(final String url, final String path, final Callback callback) {
		mExecutorService.execute(new Runnable() {
			@Override
			public void run() {
				downloadFile(url, path, callback);
			}
		});
	}
	
	private void downloadFile(String url, String path, Callback callback) {
		try {
			File downloadDir = new File(path);
			downloadDir.mkdirs();
			
			// Naively read filename from url
			String filename = url.substring(url.lastIndexOf('/') + 1);
			
			File file = new File(downloadDir, filename);
			
			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder().url(url).build();
			Response response = null;
			
			response = client.newCall(request).execute();
			
			BufferedSink sink = Okio.buffer(Okio.sink(file));
			
			long totalLength = response.body().contentLength();
			notifyStarted(callback, totalLength);
			
			long downloadedLength = 0;
			long bufferSize = 0;
			long lastUpdate = 0;
			while (true) {
				long length = response.body().source().read(sink.buffer(), BUFFER_SIZE);
				
				if (length < 0) break;
				
				downloadedLength += length;
				bufferSize += length;
				
				// Avoid overflowing the cache
				if (bufferSize >= FLUSH_SIZE) {
					sink.flush();
					bufferSize = 0;
				}
				
				if (System.currentTimeMillis() > (lastUpdate + UPDATE_RATE)) {
					notifyProgress(callback, downloadedLength);
					lastUpdate = System.currentTimeMillis();
				}
			}
			
			sink.close();
			
			notifyComplete(callback, file);
		} catch (IOException e) {
			e.printStackTrace();
			notifyError(callback, e);
		}
	}
	
	private void notifyStarted(final Callback callback, final long totalLength) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				callback.onDownloadStarted(totalLength);
			}
		});
	}
	
	private void notifyProgress(final Callback callback, final long downloadedLength) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				callback.onDownloadProgress(downloadedLength);
			}
		});
	}
	
	private void notifyComplete(final Callback callback, final File file) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				callback.onDownloadComplete(file);
			}
		});
	}
	
	private void notifyError(final Callback callback, final Exception e) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				callback.onDownloadError(e);
			}
		});
	}
	
	public interface Callback {
		void onDownloadStarted(long totalLength);
		
		void onDownloadProgress(long downloadedLength);
		
		void onDownloadComplete(File file);
		
		void onDownloadError(Exception e);
	}
}
