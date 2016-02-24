package com.codellion.umbrellamuzei.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;

import com.codellion.umbrellamuzei.model.flickr.PhotoInfoResponse;
import com.codellion.umbrellamuzei.service.FlickrService;
import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.SearchParameters;
import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Random;

import retrofit.ErrorHandler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

import com.codellion.umbrellamuzei.service.OpenWeatherService;
import com.codellion.umbrellamuzei.model.ow.OwResponse;


/**
 * Created by Codellion on 12/03/14.
 */
public class UmbrellaServiceArtSource extends RemoteMuzeiArtSource {
    private static final String TAG = "UmbrellaForDroid";
    private static final String SOURCE_NAME = "UmbrellaServiceArtSource";

    private static final int ROTATE_TIME_MILLIS = 2 * 60 * 60 * 1000; // rotate every 2 hours

    static final String Clear = "clear";
    static final String Cloudy = "cloudy";
    static final String Rain = "rain";
    static final String Storm = "storm";
    static final String Fog = "fog";
    static final String Snow = "snow";

    static final String Day = "day";
    static final String Night = "night";

    static HashMap<String, String> WeatherCodes;
    static
    {
        WeatherCodes = new HashMap<String, String>();
        WeatherCodes.put("01",  Clear);

        WeatherCodes.put("02",  Cloudy);
        WeatherCodes.put("03",  Cloudy);
        WeatherCodes.put("04",  Cloudy);

        WeatherCodes.put("09",  Rain);
        WeatherCodes.put("10",  Rain);

        WeatherCodes.put("11",  Storm);

        WeatherCodes.put("50",  Fog);

        WeatherCodes.put("13",  Snow);
    }

    Location location;
    Photo photo;

    public UmbrellaServiceArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);

        LocationManager serviceLocation = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = serviceLocation
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if(enabled)
        {
            location = serviceLocation.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        try {

            if(location == null)
            {
                Log.w(TAG, "No location enabled");
                scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                return;
            }

            RestAdapter restAdapter = new RestAdapter.Builder()
                    .setEndpoint("http://api.openweathermap.org")
                    .setRequestInterceptor(new RequestInterceptor() {
                        @Override
                        public void intercept(RequestFacade request) {
                            if(location != null)
                            {
                                DecimalFormat df = new DecimalFormat("#.#####");

                                request.addQueryParam("lat", df.format(location.getLatitude()).replace(",", "."));
                                request.addQueryParam("lon", df.format(location.getLongitude()).replace(",", "."));
                            }
                        }
                    })
                    .setErrorHandler(new ErrorHandler() {
                        @Override
                        public Throwable handleError(RetrofitError retrofitError) {

                            Log.e(TAG, retrofitError.getResponse().getReason());

                            int statusCode = retrofitError.getResponse().getStatus();
                            if (retrofitError.isNetworkError()
                                    || (500 <= statusCode && statusCode < 600)) {
                                return new RetryException();
                            }
                            scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                            return retrofitError;
                        }
                    })
                    .build();

            OpenWeatherService service = restAdapter.create(OpenWeatherService.class);
            OwResponse response = service.getWeatherByCoords();

            if (response == null || response.getWeather() == null) {
                throw new RetryException();
            }

            if (response.getWeather().size() == 0) {
                Log.w(TAG, "No weather returned from API.");
                scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                return;
            }

            String apiKey = "1170789f42627a5234bed656944393dc";
            Flickr f = new Flickr(apiKey, "5404e184b7d395e7", new REST());
            SearchParameters paramsS = new SearchParameters();

            DecimalFormat df = new DecimalFormat("#.#####");

            paramsS.setBBox(df.format(location.getLongitude() - 0.5).replace(",", "."),
                    df.format(location.getLatitude() - 0.5).replace(",", "."),
                    df.format(location.getLongitude() + 0.5).replace(",", "."),
                    df.format(location.getLatitude() + 0.5).replace(",", "."));
            paramsS.setAccuracy(Flickr.ACCURACY_CITY);
            paramsS.setSort(SearchParameters.INTERESTINGNESS_DESC);
            paramsS.setGroupId("1463451@N25");

            String conditionCode = "";
            String timeCode = Day;

            if (response.getWeather().size() > 0)
            {
                String curWeatherCode = response.getWeather().get(0).getIcon();

                conditionCode = curWeatherCode.substring(0, 2);
                conditionCode = WeatherCodes.get(conditionCode);

                if (curWeatherCode.toLowerCase().endsWith("n"))
                {
                    timeCode = Night;
                }

                paramsS.setTags(new String[]{timeCode, conditionCode});
                paramsS.setTagMode("all");
            }

            PhotoList photos = f.getPhotosInterface().search(paramsS, 50, 1);

            if(photos != null && photos.size() > 0)
            {
                Random rdn = new Random();
                int index = rdn.nextInt(photos.size() - 1);

                photo = (Photo) photos.get(index);

                if(!TextUtils.equals(photo.getId(), currentToken))
                {
                    RestAdapter restAdapterF = new RestAdapter.Builder()
                            .setEndpoint("http://api.flickr.com")
                            .setRequestInterceptor(new RequestInterceptor() {
                                @Override
                                public void intercept(RequestFacade request) {
                                    request.addQueryParam("method", "flickr.photos.getInfo");
                                    request.addQueryParam("api_key","1170789f42627a5234bed656944393dc");
                                    request.addQueryParam("photo_id",photo.getId());
                                    request.addQueryParam("format", "json");
                                    request.addQueryParam("nojsoncallback", "1");
                                }
                            })
                            .build();

                    FlickrService serviceF = restAdapterF.create(FlickrService.class);
                    PhotoInfoResponse responseF = serviceF.getPhotoInfo();

                    String owner = "";

                    if(responseF != null && responseF.getPhoto() != null
                            && responseF.getPhoto().getOwner() != null)
                    {
                        owner = responseF.getPhoto().getOwner().getRealname();

                        if(owner.equals(""))
                            owner = responseF.getPhoto().getOwner().getUsername();
                    }

                    //photo = f.getPhotosInterface().getInfo(photo.getId(), "");

                    publishArtwork(new Artwork.Builder()
                            .title(photo.getTitle())
                            .byline(owner)
                            .imageUri(Uri.parse(photo.getLargeUrl()))
                            .token(photo.getId())
                            .viewIntent(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(photo.getUrl())))
                            .build());
                }

                scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
            }
            else
            {
                String token = String.format("%s_%s.jpg", conditionCode, timeCode);

                if(!TextUtils.equals(token, currentToken))
                {
                    File fileNew = new File(getFilesDir(), token);

                    if(!fileNew.exists())
                    {
                        AssetManager assetManager = getAssets();
                        AssetFileDescriptor fDes = assetManager.openFd(token);
                        InputStream in = assetManager.open(token);

                        byte[] fileData = new byte[(int) fDes.getLength()];
                        DataInputStream dis = new DataInputStream(in);
                        dis.readFully(fileData);
                        dis.close();

                        FileOutputStream fOut = openFileOutput(token, Context.MODE_PRIVATE);
                        fOut.write(fileData);
                        fOut.close();

                        fileNew = new File(getFilesDir(), token);
                    }

                    Uri imageFile = FileProvider.getUriForFile(this, "com.codellion.app.umbrellamuzei.fileprovider", fileNew);
                    grantUriPermission("net.nurik.roman.muzei", imageFile, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    publishArtwork(new Artwork.Builder()
                            .title(response.getName())
                            .byline("Umbrella for Muzei")
                            .imageUri(imageFile)
                            .token(token)
                            .build());
                }

                scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
            }
        }
        catch (Exception ex)
        {
            Log.e(TAG, ex.getLocalizedMessage());
            scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
        }
    }
}

