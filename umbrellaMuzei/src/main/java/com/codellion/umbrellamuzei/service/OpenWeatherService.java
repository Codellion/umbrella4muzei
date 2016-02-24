package com.codellion.umbrellamuzei.service;

import java.util.List;
import retrofit.http.GET;
import retrofit.http.Path;

import com.codellion.umbrellamuzei.model.ow.OwResponse;

/**
 * Created by Codellion on 12/03/14.
 */
public interface OpenWeatherService  {

    @GET("/data/2.5/weather")
    OwResponse getWeatherByCoords();
}
