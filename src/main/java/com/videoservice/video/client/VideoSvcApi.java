package com.videoservice.video.client;

import java.util.Collection;

import com.videoservice.video.model.AverageVideoRating;
import com.videoservice.video.model.UserVideoRating;
import com.videoservice.video.model.Video;
import com.videoservice.video.model.VideoStatus;

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
public interface VideoSvcApi {

	public static final String DATA_PARAMETER = "data";

	public static final String ID_PARAMETER = "id";
	
	public static final String ID_EFFECT = "effect";


	public static final String TOKEN_PATH = "/oauth/token";

	// The path where we expect the VideoSvc to live
	public static final String VIDEO_SVC_PATH = "/video";
	
	public static final String IMAGE_SVC_PATH = "/image";


	// The path where we expect the VideoSvc to live
	public static final String VIDEO_DATA_PATH = VIDEO_SVC_PATH + "/{"+ VideoSvcApi.ID_PARAMETER +"}/data";
	
	public static final String VIDEO_GET_BY_ID = VIDEO_SVC_PATH + "/{"+ VideoSvcApi.ID_PARAMETER +"}";
	
	public static final String RATING_PARAMETER = "rating";
	
	public static final String VIDEO_RATE = VIDEO_SVC_PATH+"/{"+ VideoSvcApi.ID_PARAMETER +"}/rating/{"+ VideoSvcApi.RATING_PARAMETER +"}";
	
	public static final String VIDEO_GET_RATING = VIDEO_SVC_PATH+"/{"+ VideoSvcApi.ID_PARAMETER +"}/rating"; 

	public static final String IMAGE_DATA_PATH = IMAGE_SVC_PATH + "/{"+ VideoSvcApi.ID_EFFECT +"}/data";

	
	@GET(VIDEO_SVC_PATH)
	public Collection<Video> getVideoList();
	
	@GET(VIDEO_SVC_PATH + "/{id}")
	public Video getVideoById(@Path("id") long id);
	
	@POST(VIDEO_SVC_PATH)
	public Video addVideo(@Body Video v);
	
	@POST(VIDEO_SVC_PATH+"/{id}/rating/{rating}")
	public AverageVideoRating rateVideo(@Path("id") long id, @Path("rating") int rating);
	
	@GET(VIDEO_SVC_PATH+"/{id}/rating")
	public AverageVideoRating getVideoRating(@Path("id") long id);
	
	@Multipart
	@POST(VIDEO_DATA_PATH)
	public VideoStatus setVideoData(@Path(ID_PARAMETER) long id, @Part(DATA_PARAMETER) TypedFile videoData);
	
	@Streaming
	@Multipart
	@POST(IMAGE_DATA_PATH)
	public Response setImageData(@Path(ID_EFFECT) long id, @Part(DATA_PARAMETER) TypedFile imageData);

	/**
	 * This method uses Retrofit's @Streaming annotation to indicate that the
	 * method is going to access a large stream of data (e.g., the mpeg video 
	 * data on the server). The client can access this stream of data by obtaining
	 * an InputStream from the Response as shown below:
	 * 
	 * VideoSvcApi client = ... // use retrofit to create the client
	 * Response response = client.getData(someVideoId);
	 * InputStream videoDataStream = response.getBody().in();
	 * 
	 * @param id
	 * @return
	 */
	@Streaming
    @GET(VIDEO_DATA_PATH)
    Response getVideoData(@Path(ID_PARAMETER) long id);
	
}
