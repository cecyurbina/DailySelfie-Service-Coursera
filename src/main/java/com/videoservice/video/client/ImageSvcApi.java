package com.videoservice.video.client;

import java.util.Collection;


import com.videoservice.video.model.Video;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Streaming;
import retrofit.mime.TypedFile;

/**
 * This interface defines an API for a VideoSvc. The
 * interface is used to provide a contract for client/server
 * interactions. The interface is annotated with Retrofit
 * annotations so that clients can automatically convert the
 * 
 * 
 * @author jules
 *
 */
public interface ImageSvcApi {

	public static final String DATA_PARAMETER = "data";

	public static final String ID_PARAMETER = "id";
	
	public static final String ID_EFFECT = "effect";

	public static final String TOKEN_PATH = "/oauth/token";

	// The path where we expect the VideoSvc to live
	public static final String VIDEO_SVC_PATH = "/video";
	
	public static final String IMAGE_SVC_PATH = "/image";
			
	public static final String IMAGE_DATA_PATH = IMAGE_SVC_PATH + "/{"+ ImageSvcApi.ID_EFFECT +"}/data";

	
	@GET(VIDEO_SVC_PATH)
	public Collection<Video> getVideoList();
	
	
	@Streaming
	@Multipart
	@POST(IMAGE_DATA_PATH)
	public Response setImageData(@Path(ID_EFFECT) long id, @Part(DATA_PARAMETER) TypedFile imageData);

	
}
