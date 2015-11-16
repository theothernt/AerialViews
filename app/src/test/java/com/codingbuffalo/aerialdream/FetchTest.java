package com.codingbuffalo.aerialdream;

import com.codingbuffalo.aerialdream.service.AerialVideo;
import com.codingbuffalo.aerialdream.service.VideoService;

import junit.framework.Assert;

import org.junit.Test;

import java.util.List;

public class FetchTest {
	@Test
	public void fetchVideos() throws Exception {
		List<AerialVideo> videos = VideoService.fetchVideos();
		Assert.assertNotNull(videos);
	}
}