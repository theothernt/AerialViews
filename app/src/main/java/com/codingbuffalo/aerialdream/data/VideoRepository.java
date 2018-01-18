package com.codingbuffalo.aerialdream.data;

import com.codingbuffalo.aerialdream.data.protium.RestRepository;

import java.io.IOException;
import java.util.List;

public abstract class VideoRepository extends RestRepository {
    public abstract List<? extends Video> fetchVideos() throws IOException;
}
