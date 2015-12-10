package models;

import java.util.Map;
import java.util.Set;

public class AdversaryDistribution implements Comparable<AdversaryDistribution>{
	private int id;
	private Map<RiskCategory, Double> distribution;
	
	private static int ID = 1;
	
	public AdversaryDistribution(Map<RiskCategory, Double> distribution){
		this.distribution = distribution;
		
		id = ID;
		
		ID++;
	}
	
	public int id(){
		return id;
	}
	
	public Map<RiskCategory, Double> distribution(){
		return distribution;
	}
	
	public Set<RiskCategory> keySet(){
		return distribution.keySet();
	}
	
	public double get(RiskCategory c){
		return distribution.get(c);
	}
	
	public int compareTo(AdversaryDistribution d){
		if(d.id() == this.id()){
			return 0;
		}
		else if(d.id() < this.id()){
			return 1;
		}
		
		return -1;
	}
	
	public String toString(){
		return "AdversaryDistribution" + id;
	}
}