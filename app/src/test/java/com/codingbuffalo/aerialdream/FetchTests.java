package com.codingbuffalo.aerialdream;

import com.codingbuffalo.aerialdream.data.Apple2015Repository;
import com.codingbuffalo.aerialdream.data.Apple2015Video;
import com.codingbuffalo.aerialdream.data.Apple2017Repository;
import com.codingbuffalo.aerialdream.data.Apple2017Video;

import junit.framework.Assert;

import org.junit.Test;

import java.util.List;

public class FetchTests {
    @Test
    public void fetchApple2015Videos() throws Exception {
        Apple2015Repository repository = new Apple2015Repository();

        List<Apple2015Video> videos = repository.fetchVideos();
        Assert.assertTrue(videos.size() >= 55);
    }

    @Test
    public void fetchApple2017Videos() throws Exception {
        Apple2017Repository repository = new Apple2017Repository();

        List<Apple2017Video> videos = repository.fetchVideos();
        Assert.assertTrue(videos.size() >= 9);
    }
}
