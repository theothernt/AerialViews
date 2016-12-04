package com.codingbuffalo.aerialdream;

import com.codingbuffalo.aerialdream.data.Video;
import com.codingbuffalo.aerialdream.data.VideoRepository;

import junit.framework.Assert;

import org.junit.Test;

import java.util.List;

public class FetchTest {
    @Test
    public void fetchVideos() throws Exception {
        VideoRepository repository = new VideoRepository();

        List<Video> videos = repository.fetchVideos();
        Assert.assertNotNull(videos);
    }
}
