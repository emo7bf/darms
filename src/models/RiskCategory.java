package models;
public class RiskCategory implements Comparable<RiskCategory>{
	private String description;
	private int id;
	
	private static int ID = 1;
	    
	public RiskCategory(String description){
		this.description = description;
		this.id = ID;
		
		ID++;
	}
	    
	public String toString(){
		return description;
	}
	
	public int id(){
		return id;
	}
	
	public int compareTo(RiskCategory c){
		if(c.id() == this.id()){
			return 0;
		}
		else if(c.id() < this.id()){
			return 1;
		}
		
		return -1;
	}
	
	public static void reset(){
		ID = 1;
	}
}