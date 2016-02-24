package com.codellion.umbrellamuzei.service;

import com.codellion.umbrellamuzei.model.flickr.PhotoInfoResponse;

import retrofit.http.GET;

/**
 * Created by Codellion on 12/03/14.
 */
public interface FlickrService {

    @GET("/services/rest/")
    PhotoInfoResponse getPhotoInfo();
}
