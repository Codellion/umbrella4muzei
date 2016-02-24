
package com.codellion.umbrellamuzei.model.flickr;

import java.util.List;

public class PhotoInfoResponse{
   	private Photo photo;
   	private String stat;

 	public Photo getPhoto(){
		return this.photo;
	}
	public void setPhoto(Photo photo){
		this.photo = photo;
	}
 	public String getStat(){
		return this.stat;
	}
	public void setStat(String stat){
		this.stat = stat;
	}
}
