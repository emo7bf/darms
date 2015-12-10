package models;

public class AttackMethod {
	private String description;
	private int id;
	
	private static int ID = 1;
	
	public AttackMethod(String description){
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
	
	public static void reset(){
		ID = 1;
	}
}