package models;

import java.util.Map;

public class PostScreeningResource {
	private String description;
	private int id;
	private Map<AttackMethod, Double> effectiveness;
	
	private static int ID = 1;
	
	public PostScreeningResource(String description, Map<AttackMethod, Double> effectiveness){
		this.description = description;
		this.effectiveness = effectiveness;
		this.id = ID;
		
		ID++;
	}
	    
	public String toString(){
		return description;
	}
	
	public int id(){
		return id;
	}
	
	public double effectiveness(AttackMethod m){
		return effectiveness.get(m);
	}
}