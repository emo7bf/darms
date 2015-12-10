package models;
import java.util.HashMap;
import java.util.Map;


public class ScreeningResource implements Comparable<ScreeningResource> {
	private String description;
	private int id;
	private int capacity;
	private double screeningTime;
	private Map<RiskCategory, Map<AttackMethod, Double>> effectiveness;
	
	private static int ID = 1;
	
	public ScreeningResource(String description, int capacity, double screeningTime){
		this.description = description;
		this.capacity = capacity;
		this.screeningTime = screeningTime;
		this.effectiveness = new HashMap<RiskCategory, Map<AttackMethod, Double>>();
		
		this.id = ID;
		ID++;
	}
	    
	public String toString(){
		return description;
	}
	
	public int id(){
		return id;
	}
	
	public int capacity(){
		return capacity;
	}
	
	public void setCapacity(int capacity){
		this.capacity = capacity;
	}
	
	public double screeningTime(){
		return screeningTime;
	}
	
	public void setScreeningTime(double screeningTime){
		this.screeningTime = screeningTime;
	}
	
	public double effectiveness(RiskCategory c, AttackMethod m){
		return effectiveness.get(c).get(m);
	}
	
	public void setEffectiveness(RiskCategory c, Map<AttackMethod, Double> effectiveness){
		this.effectiveness.put(c, effectiveness);
	}
	
	public void setEffectiveness(Map<AttackMethod, Map<RiskCategory, Double>> effectiveness){
		for(AttackMethod m : effectiveness.keySet()){
			for(RiskCategory c : effectiveness.get(m).keySet()){
				if(!this.effectiveness.containsKey(c)){
					this.effectiveness.put(c, new HashMap<AttackMethod, Double>());
				}
				
				double value = effectiveness.get(m).get(c);
				
				this.effectiveness.get(c).put(m, value);
			}
		}
	}
	
	public int compareTo(ScreeningResource r){
		if(r.id() == this.id()){
				return 0;
		}
		else if(r.id() > this.id()){
			return -1;
		}
		else{
			return 1;
		}
	}
	
	public static void reset(){
		ID = 1;
	}
}