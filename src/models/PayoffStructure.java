package models;

import java.util.Map;
import java.util.Set;

public class PayoffStructure {
	private Map<Flight, Integer> defCovMap;
	private Map<Flight, Integer> defUncovMap;
	private Map<Flight, Integer> attCovMap;
	private Map<Flight, Integer> attUncovMap;
	
	private int id;
	
	private static int ID = 1;
	
	public PayoffStructure(Map<Flight, Integer> defCovMap,
							Map<Flight, Integer> defUncovMap,
							Map<Flight, Integer> attCovMap,
							Map<Flight, Integer> attUncovMap){
		this.defCovMap = defCovMap;
		this.defUncovMap = defUncovMap;
		this.attCovMap = attCovMap;
		this.attUncovMap = attUncovMap;
		
		this.id = ID;
		
		ID++;
	}
	
	public Set<Flight> keySet(){
		return defCovMap.keySet();
	}
	
	public int defCov(Flight f){
		return defCovMap.get(f);
	}
	
	public int defUncov(Flight f){
		return defUncovMap.get(f);
	}
	
	public int attCov(Flight f){
		return attCovMap.get(f);
	}
	
	public int attUncov(Flight f){
		return attUncovMap.get(f);
	}
	
	public int id(){
		return id;
	}
	
	public String toString(){
		return "PayoffStructure" + id;
	}
}
