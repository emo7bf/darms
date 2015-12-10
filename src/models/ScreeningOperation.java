package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ScreeningOperation implements Comparable<ScreeningOperation>{
	private Set<ScreeningResource> resources;
	private int id;
	
	public static int ID = 1;
	
	public ScreeningOperation(Set<ScreeningResource> resources){
		this.resources = resources;
		this.id = ID;
		
		ID++;
	}
	    
	public int getID(){
		return id;
	}
	
	public double screeningTime(){
		double screeningTime = 0.0;
		
		for(ScreeningResource r : resources){
			screeningTime += r.screeningTime();
		}
		
		return screeningTime;
	}
	
	public double effectiveness(RiskCategory c, AttackMethod m){
		double undetectedProbability = 1.0;
		
		for(ScreeningResource r : resources){
			undetectedProbability *= (1.0 - r.effectiveness(c, m));
		}
		
		return 1.0 - undetectedProbability;
	}
	
	public Set<ScreeningResource> getResources(){
		return resources;
	}
	
	public String toString(){
		List<ScreeningResource> resourceList = new ArrayList<ScreeningResource>(resources); 
		
		Collections.sort(resourceList);
		
		String s = resourceList.get(0).toString();
		
		for(int i = 1; i < resourceList.size(); i++){
			s += "/"  +resourceList.get(i).toString();
		}
		
		s = "O" + id;

		return s;
	}
	
	public int compareTo(ScreeningOperation o){
		if(o.getResources().equals(this.getResources())){
				return 0;
		}
		
		return -1;
	}
	
	public static void reset(){
		ID = 1;
	}
}