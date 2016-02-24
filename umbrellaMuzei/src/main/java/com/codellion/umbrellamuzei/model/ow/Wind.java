
package com.codellion.umbrellamuzei.model.ow;

import java.util.List;

public class Wind{
   	private Number deg;
   	private Number gust;
   	private Number speed;

 	public Number getDeg(){
		return this.deg;
	}
	public void setDeg(Number deg){
		this.deg = deg;
	}
 	public Number getGust(){
		return this.gust;
	}
	public void setGust(Number gust){
		this.gust = gust;
	}
 	public Number getSpeed(){
		return this.speed;
	}
	public void setSpeed(Number speed){
		this.speed = speed;
	}
}
