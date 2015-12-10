package models;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class PostScreeningOperation {
	private Set<PostScreeningResource> resources;
	private int id;
	
	public static int ID = 1;
	
	PostScreeningOperation(Set<PostScreeningResource> resources){
		this.resources = resources;
		this.id = ID;
		
		ID++;
	}
	    
	public int getID(){
		return id;
	}
	
	public double effectiveness(AttackMethod m){
		double undetectedProbability = 1.0;
		
		for(PostScreeningResource r : resources){
			undetectedProbability *= (1.0 - r.effectiveness(m));
		}
		
		return 1.0 - undetectedProbability;
	}
	
	public Set<PostScreeningResource> getResources(){
		return resources;
	}
	
	public String toString(){
		List<PostScreeningResource> resourceList = new ArrayList<PostScreeningResource>(resources); 
		
		String s = resourceList.get(0).toString();
		
		for(int i = 1; i < resourceList.size(); i++){
			s += "/"  +resourceList.get(i).toString();
		}

		return s;
	}
}