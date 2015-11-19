package com.videoservice.video.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;
import com.videoservice.video.client.ImageSvcApi;
import com.videoservice.video.model.Video;
import retrofit.http.Multipart;
import retrofit.http.Streaming;
import magick.*;

@Controller
public class ImageController {

    public static int ERR_VIDEO_NOT_EXISTED = -2521;
    //video metadata manager
    private static VideoRepository videoRepository;

    //video data manager
    private VideoFileManager videoDataRepository;
    
    private ImageController() throws IOException {
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
    @RequestMapping(value = ImageSvcApi.VIDEO_SVC_PATH,  method = RequestMethod.GET)
    public @ResponseBody Collection<Video> getVideoList(){
        return Lists.newArrayList(videoRepository.findAll());
    }

   
    
    @Streaming
    @Multipart
    @RequestMapping(value = ImageSvcApi.IMAGE_DATA_PATH, method = RequestMethod.POST)
    public @ResponseBody void setImageData(
            @PathVariable(ImageSvcApi.ID_EFFECT) long id,
            @RequestParam(value = ImageSvcApi.DATA_PARAMETER) MultipartFile imageData, Principal principal,
            HttpServletResponse response ) throws IOException, MagickException {
    	//apply effects
        byte[] imageBlob = getImageWithFilter(id, imageData.getBytes());
        //put content type
    	response.setContentType(imageData.getContentType());
    	//put bytes in response
    	response.getOutputStream().write(imageBlob);
    }

 
    
    /**
     * Apply filter with id
     * @param id_filter
     * @param imageBlob
     * @return
     * @throws MagickException
     */
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
            System.out.println("%%%%%%%%%%%%%%% Noise");
    		break;
    	case 1:
    		//blur
    		image = image.blurImage(0, 30);
            System.out.println("%%%%%%%%%%%%%%% Blur");

    		break;
    	case 2:
    		//charcoal image
    		image = image.charcoalImage(10, 20);
            System.out.println("%%%%%%%%%%%%%%% Characoal");

    		break;
    	case 3:
    		//grayscale
            image.setGrayscale();
            System.out.println("%%%%%%%%%%%%%%% Grayscale");
    		break;
    	case 4:
    		//MultiplicativeGaussianNoise
    		image =image.edgeImage(0);
            System.out.println("%%%%%%%%%%%%%%% Edge");
            break;
    	case 5:
    		image.solarizeImage(100);
            System.out.println("%%%%%%%%%%%%%%% Solarize");
    		break;
    	default:
    		break;
    	}
    	return image.imageToBlob(info);
    }
    
    public static class VideoRepository {

        // We make sure that our data structure is thread-safe to avoid
        // race conditions
        private Map<Long, Video> video_ = new ConcurrentHashMap<Long, Video>();

       

       
        // Return all videos in the repo
        public Iterable<Video> findAll() {
            return video_.values();
        }

       
        
    }
}
