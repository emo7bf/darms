package models;
import java.util.Map;


public class PassengerDistribution implements Comparable<PassengerDistribution>{
	private int id;
	private Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> distribution;
	
	private static int ID = 1;
	
	public PassengerDistribution(Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> distribution){
		this.distribution = distribution;
		
		id = ID;
		
		ID++;
	}
	
	public int getTotalPassengers(){
		int totalPassengers = 0;
		
		for(int t : distribution.keySet()){
			for(Flight f : distribution.get(t).keySet()){
				for(RiskCategory c : distribution.get(t).get(f).keySet()){
					totalPassengers += distribution.get(t).get(f).get(c);
				}
			}
		}
		
		return totalPassengers;
	}
	
	public int id(){
		return id;
	}
	
	public int get(Integer t, Flight f, RiskCategory c){
		return distribution.get(t).get(f).get(c);
	}
	
	public int compareTo(PassengerDistribution d){
		if(d.id() == this.id()){
			return 0;
		}
		else if(d.id() < this.id()){
			return 1;
		}
		
		return -1;
	}
	
	public String toString(){
		return "Distribution" + id;
	}
}