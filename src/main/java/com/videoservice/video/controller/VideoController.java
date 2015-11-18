package com.videoservice.video.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import com.videoservice.video.client.VideoSvcApi;
import com.videoservice.video.model.AverageVideoRating;
import com.videoservice.video.model.UserVideoRating;
import com.videoservice.video.model.Video;
import com.videoservice.video.model.VideoStatus;

import retrofit.http.Multipart;
import retrofit.http.Streaming;
import magick.*;

@Controller
public class VideoController {

    public static int ERR_VIDEO_NOT_EXISTED = -2521;
    //video metadata manager
    private static VideoRepository videoRepository;

    //video data manager
    private VideoFileManager videoDataRepository;
    
    private VideoController() throws IOException {
        videoRepository = new VideoRepository();
        videoDataRepository = new VideoFileManager();
    }

    /**
     * GET /video
     * Returns the list of videos that have been added to the server as JSON.
     * The list of videos does not have to be persisted across restarts of the 
     * server. The list of Video objects should be able to be un-marshalled
     * by the client into a Collection.
     * The return content-type should be application/json, which will be the
     * default if you use @ResponseBody
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH,  method = RequestMethod.GET)
    public @ResponseBody Collection<Video> getVideoList(){
        return Lists.newArrayList(videoRepository.findAll());
    }

    /**
     * GET /video/{id}
     * Returns the video with the given id or 404 if the video is not found.
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_GET_BY_ID,  method = RequestMethod.GET)
    public @ResponseBody Video getVideoById(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, HttpServletResponse response ) throws IOException{
        Video video = videoRepository.findOne(id);
        
        if (video != null) {
            return video;
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
            return null;
        }
    }

    /**
     * POST /video
     * The video metadata is provided as an application/json request body. The
     * JSON should generate a valid instance of the Video class when deserialized
     * by Spring's default Jackson library.
     * Returns the JSON representation of the Video object that was stored along
     * with any updates to that object made by the server.
     * The server should generate a unique identifier for the Video object and
     * assign it to the Video by calling its setId(...) method.
     * No video should have ID = 0. All IDs should be > 0.
     * The returned Video JSON should include this server-generated identifier so
     * that the client can refer to it when uploading the binary mpeg video content for the Video.
     * The server should also generate a "data url" for the Video. The "data url"
     * is the url of the binary data for a Video (e.g., the raw mpeg data). The URL
     * should be the full URL for the video and not just the path.
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_SVC_PATH, method = RequestMethod.POST)
    public @ResponseBody Video addVideo(@RequestBody Video v, Principal principal) {
        v = videoRepository.save(v, principal.getName());
        return v;
    }

    /**
     * Only allows each user (e.g., authenticated Principal) to have a single rating
     * for a video. If a user has an existing rating for a Video, the existing rating
     * should be overwritten
     * Allows a user to rate a video. Returns 200 Ok on success or 404 if the video is not found.
     * @param id
     * @param rating
     * @param response
     * @return
     * @throws IOException
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_RATE, method = RequestMethod.POST)
    public @ResponseBody AverageVideoRating rateVideo(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, 
            @PathVariable(VideoSvcApi.RATING_PARAMETER) int rating, Principal principal, HttpServletResponse response ) throws IOException{
        Video video = videoRepository.findOne(id);
        
        if (video != null) {
             videoRepository.setVideoRating(id, rating, principal.getName());
             response.setStatus(HttpServletResponse.SC_OK);
             return new AverageVideoRating(videoRepository.getVideoRating(id, principal.getName()), id, videoRepository.getTotalRatings(id));
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
            return null;
        }
    }

    /**
     * Returns the AverageVideoRating for a Video, which contains the average star count for the 
     * video across all users and the total number of users that have rated the video
     * @param id
     * @param response
     * @return
     * @throws IOException
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_GET_RATING,  method = RequestMethod.GET)
    public @ResponseBody AverageVideoRating getVideoRating(@PathVariable(VideoSvcApi.ID_PARAMETER) long id, Principal principal,
            HttpServletResponse response ) throws IOException{
        Video video = videoRepository.findOne(id);
        
        if (video != null) {
            return new AverageVideoRating(videoRepository.getVideoRating(id, principal.getName()), id, videoRepository.getTotalRatings(id));
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
            return null;
        }
    }

    /**
     * POST /video/{id}/data
     * The binary mpeg data for the video should be provided in a multipart
     * request as a part with the key "data". The id in the path should be
     * replaced with the unique identifier generated by the server for the
     * Video. A client MUST create a Video first by sending a POST to /video
     * and getting the identifier for the newly created Video object before
     * sending a POST to /video/{id}/data.
     * The endpoint should return a VideoStatus object with state=VideoState.READY
     * if the request succeeds and the appropriate HTTP error status otherwise.
     * VideoState.PROCESSING is not used in this assignment but is present in VideoState.
     * Rather than a PUT request, a POST is used because, by default, Spring
     * does not support a PUT with multipart data due to design decisions in the Commons File Upload library.
     */
    @Multipart
    @RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.POST)
    public @ResponseBody VideoStatus setVideoData(
            @PathVariable(VideoSvcApi.ID_PARAMETER) long id,
            @RequestParam(value = VideoSvcApi.DATA_PARAMETER) MultipartFile videoData, Principal principal,
            HttpServletResponse response ) throws IOException {
        // Always check to ensure a Video exist before allowing an upload
        VideoStatus status = null;
        Video video = videoRepository.findOne(id);
        
        if (video != null) {
            if (videoRepository.findOwner(video).compareTo(principal.getName()) == 0) {
                videoDataRepository.saveVideoData(video, videoData.getInputStream());
                status = new VideoStatus(VideoStatus.VideoState.READY);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Video not found");
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
        }
        return status;
    }
    
    @Streaming
    @Multipart
    @RequestMapping(value = VideoSvcApi.IMAGE_DATA_PATH, method = RequestMethod.POST)
    public @ResponseBody void setImageData(
            @PathVariable(VideoSvcApi.ID_EFFECT) long id,
            @RequestParam(value = VideoSvcApi.DATA_PARAMETER) MultipartFile imageData, Principal principal,
            HttpServletResponse response ) throws IOException, MagickException {
        // Always check to ensure a Video exist before allowing an upload
    	
    	
    	
    	//String absPath = "src/test/resources/hola.jpg";
		//ImageInfo origInfo = new ImageInfo(absPath); //load image info
    	
        //image = image.scaleImage(100, 100); //to Scale image
        
        byte[] imageBlob = getImageWithFilter(id, imageData.getBytes());
        
    	response.setContentType(imageData.getContentType());
    	response.getOutputStream().write(imageBlob);
    }

    /**
     * GET /video/{id}/data
     * Returns the binary mpeg data (if any) for the video with the given
     * identifier. If no mpeg data has been uploaded for the specified video,
     * then the server should return a 404 status code.
     */
    @Streaming
    @RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.GET)
    public @ResponseBody void getVideoData(@PathVariable(VideoSvcApi.ID_PARAMETER) long id,
            HttpServletResponse response) throws IOException {
        // Always check to ensure a video exists before attempting a download
        Video v = videoRepository.findOne(id);
        if (v != null){
            // Set the content type of the output stream to match what was provided
            // when the data was uploaded
            response.setContentType(v.getContentType());

            // Copy the Video date to the output stream that is going back to the
            // client.
            if (!videoDataRepository.hasVideoData(v)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
                return;
            }
            videoDataRepository.copyVideoData(v, response.getOutputStream());
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
        }
    }
    
    public static class VideoRepository {
        private static final AtomicLong currentId_ = new AtomicLong(0L);

        // We make sure that our data structure is thread-safe to avoid
        // race conditions
        private Map<Long, Video> video_ = new ConcurrentHashMap<Long, Video>();
        private Map<Long, String> videoOwner_ = new ConcurrentHashMap<Long, String>();
        private Map<Long, List <UserVideoRating>> videoStarRatings_ = new ConcurrentHashMap<Long, List <UserVideoRating>>();

        private void checkAndSetId(Video entity) {
            if(entity.getId() == 0){
                entity.setId(currentId_.incrementAndGet());
                }
    }

        public Video save(Video entity, String owner) {
            int key = checkVideoExisted(entity);
            if (key == ERR_VIDEO_NOT_EXISTED) {
                checkAndSetId(entity); // Make sure that the video has an ID
                video_.put(entity.getId(), entity);
                videoOwner_.put(entity.getId(), owner);
                videoStarRatings_.put(entity.getId(), new ArrayList<UserVideoRating>());
                return entity;
            } else {
                if (videoOwner_.containsKey(owner)) {
                    video_.put((long) key, entity);
                    return entity;
                } else {
                    return null;
                }
            }
        }
        
        public int checkVideoExisted(Video v){
            for (Long key:video_.keySet()) {
                Video value=video_.get(key);
                if (v.getTitle().compareTo(value.getTitle()) == 0) {
                    return key.intValue();
                }
            }
            return ERR_VIDEO_NOT_EXISTED;
        }

        // Lookup a video by its ID
        public Video findOne(long id) {
            return video_.get(id);
        }

        public String findOwner (Video v) {
            for (Long key:video_.keySet()) {
                Video value=video_.get(key);
                if (v.getTitle().compareTo(value.getTitle()) == 0) {
                    return videoOwner_.get(key);
                }
            }
            return null;
        }
        
        // Return all videos in the repo
        public Iterable<Video> findAll() {
            return video_.values();
        }

        public void setVideoRating(long id, double rating, String user) {
            List <UserVideoRating> ratingList = videoStarRatings_.get(id);
            
            if (ratingList != null) {
                // Check if the user has already rated or not
                int listSize = ratingList.size();
                for (int i = 0;i<listSize;i++) {
                    if (ratingList.get(i).getUser().compareTo(user) == 0) {
                        ratingList.get(i).setRating(rating);
                        return;
                    }
                }
                ratingList.add(new UserVideoRating(id, rating, user));
            }
        }

        private double calculateAverage(List <UserVideoRating> starRatingsList) {
            int listSize = starRatingsList.size();
            if (starRatingsList == null || starRatingsList.isEmpty()) {
                return 0;
            }

            float sum = 0;
            for (int i = 0;i<listSize;i++) {
                sum += (float) starRatingsList.get(i).getRating();
            }
            
            double returnValue = (double) (sum / listSize);
            return returnValue;
    	}

        public double getVideoRating (long id, String user) {
            double rating = calculateAverage(videoStarRatings_.get(id));
            Video video = videoRepository.findOne(id);
            video.setRating((int)rating);
            System.out.println("%%%%%%%%%%%%%%% " + user);
            videoRepository.save(video, user);
            return rating;
        }
        
        public int getTotalRatings(long id){
            return videoStarRatings_.get(id).size();
        }
    }
    
    // Utility method to get the URL from a video
    private String getVideoUrl(Video v) {
        String base = getUrlBaseForLocalServer();
        // We don't want to accidentally let our VideoSvcApi and server-side 
        // URL format drif out the sync. To avoid this, we use the Retrofit
        // path format as a template and String search/replace to generate valid
        // URLs
        return base + VideoSvcApi.VIDEO_DATA_PATH.replace("{"+ VideoSvcApi.DATA_PARAMETER + "}", "" + v.getId());
    }

    // Utility method to fetch the URL that refers to the server
    private String getUrlBaseForLocalServer() {
        HttpServletRequest request = 
           ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String base = "http://"+request.getServerName() 
           + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
        return base;
    }
    
    private byte[] getImageWithFilter(long id_filter, byte[] imageBlob) throws MagickException{
    	int anInt = new BigDecimal(id_filter).intValueExact(); // throws ArithmeticException
    	ImageInfo info=new ImageInfo();
    	info.setMagick("jpeg");
        MagickImage image = new MagickImage(info, imageBlob); //load image
    	
        switch(anInt){
    	case 0:
    		//contrast
    		//MultiplicativeGaussianNoise
    		image = image.addNoiseImage(3);
            System.out.println("%%%%%%%%%%%%%%% filtro 0");
    		break;
    	case 1:
    		//blur
    		image = image.blurImage(0, 30);
            System.out.println("%%%%%%%%%%%%%%% filtro 1");

    		break;
    	case 2:
    		//charcoal image
    		image = image.charcoalImage(10, 20);
            System.out.println("%%%%%%%%%%%%%%% filtro 2");

    		break;
    	case 3:
    		//grayscale
            image.setGrayscale();
            System.out.println("%%%%%%%%%%%%%%% filtro 3");
    		break;
    	case 4:
    		//MultiplicativeGaussianNoise
    		image =image.edgeImage(0);
            System.out.println("%%%%%%%%%%%%%%% filtro 4");
            break;
    	case 5:
    		image.solarizeImage(100);
    		break;
    	default:
    		break;
    	}
    	return image.imageToBlob(info);
    }
}
