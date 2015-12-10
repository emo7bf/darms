package models;

import java.util.HashMap;
import java.util.Map;

public class PureStrategy{
	private int id;
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> screeningStrategy;
	
	public static int ID = 1;
	
	public PureStrategy(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> sMap){
		this.screeningStrategy = sMap;
		this.id = ID;
		
		ID++;
	}
	
	public int id(){
		return id;
	}
	
	public int get(int t, Flight f, RiskCategory c, ScreeningOperation o){
		return screeningStrategy.get(t).get(f).get(c).get(o);
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> getUnassignedPassengers(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> defenderScreeningStrategy){
		Map<Integer, Map<Flight, Map<RiskCategory, Integer>>> unassignedPassengers = new HashMap<Integer, Map<Flight, Map<RiskCategory, Integer>>>();
		
		for(int t : defenderScreeningStrategy.keySet()){
			unassignedPassengers.put(t,new HashMap<Flight, Map<RiskCategory, Integer>>());
			
			for(Flight f : defenderScreeningStrategy.get(t).keySet()){
				Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
				
				unassignedPassengers.get(t).put(f, new HashMap<RiskCategory, Integer>());
				
				for(RiskCategory c : defenderScreeningStrategy.get(t).get(f).keySet()){
					int remainingUnassignedPassengers = distribution.get(t).get(c);
					
					for(ScreeningOperation o : defenderScreeningStrategy.get(t).get(f).get(c).keySet()){
						remainingUnassignedPassengers -= defenderScreeningStrategy.get(t).get(f).get(c).get(o);
					}
					
					unassignedPassengers.get(t).get(f).put(c, remainingUnassignedPassengers);
				}
			}
		}
					
		return unassignedPassengers;
	}
	
	public int compareTo(PureStrategy p){
		boolean identical = true;
		
		if(this.screeningStrategy.keySet().size() != p.screeningStrategy.keySet().size()){
			identical = false;
		}
		
		if(identical){
			for(int t : this.screeningStrategy.keySet()){
				if(!p.screeningStrategy.containsKey(t)){
					identical = false;
				}
				
				if(!identical){
					break;
				}
					
				for(Flight f : this.screeningStrategy.get(t).keySet()){
					if(!p.screeningStrategy.get(t).containsKey(f)){
						identical = false;
					}
					
					if(!identical){
						break;
					}
						
					for(RiskCategory c : this.screeningStrategy.get(t).get(f).keySet()){
						if(!p.screeningStrategy.get(t).get(f).containsKey(c)){
							identical = false;
						}
						
						if(!identical){
							break;
						}
							
						for(ScreeningOperation o : this.screeningStrategy.get(t).get(f).get(c).keySet()){
							if(!p.screeningStrategy.get(t).get(f).get(c).containsKey(o)){
								identical = false;
								break;
							}
								
							if(p.get(t, f, c, o) != this.get(t, f, c, o)){
								identical = false;
								break;
							}
						}
					}
				}
			}
		}
		
		if(identical){
			return 0;
		}
		else if(this.id() < p.id()){
			return -1;
		}
		else{
			return 1;
		}
	}
	
	@Override
	public boolean equals(Object obj){
		if(obj instanceof PureStrategy){
			PureStrategy p = ((PureStrategy)obj);
			
			return (this.compareTo(p) == 0);
		}
	
		return false;
	}
	
	public String toString(){
		return screeningStrategy.toString();
	}
	
	public static void reset(){
		ID = 1;
	}
}