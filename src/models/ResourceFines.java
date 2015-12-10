package models;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ResourceFines {
	private Map<Integer, Map<ScreeningResource, Double>> fines;
	private int trials;
	private List<Integer> twindows;
	private String filename;
	private Set<ScreeningResource> resources;

	public ResourceFines(int t){
		this.trials = t;
	}
	
	public void generateFines( String dist, double min, double max, int numTrials, int thisTest, DARMSModel model) throws Exception{
		// dist: random -- all numbers are random
		Random dice = new Random();
		// this.trials = numTrials;
		this.resources = model.getScreeningResources().keySet();
		this.twindows = model.getTimeWindows();
		
		Map<Integer, Map<ScreeningResource, Double>> f1 = new HashMap<Integer, Map<ScreeningResource, Double>>();
		
		if( dist.equals("random") ){

			for( int t : model.getTimeWindows() ){
				f1.put(t, new  HashMap<ScreeningResource, Double>() );
				for( ScreeningResource r : model.getScreeningResources().keySet() ){
					double cost = dice.nextDouble()*(max - min) + min;
					f1.get(t).put(r, cost);
				}
			}
			this.fines = f1;
			this.writeToFile();
		}
		
		if( dist.equals("uniform")){
			
			for( int t : model.getTimeWindows() ){
				f1.put(t, new  HashMap<ScreeningResource, Double>() );
				for( ScreeningResource r : model.getScreeningResources().keySet() ){
					double cost = (thisTest*(max - min))/numTrials + min;
					f1.get(t).put(r, cost);
				}
			}
			this.fines = f1;
			this.writeToFile();
		}			
	}

	private void writeToFile() throws Exception {
		
		FileWriter fw = new FileWriter(new File("ResourceFines" + trials + ".csv"));
		
		String line = "Trial, TimeWindow, Resource, Fine";

		for( int t : this.twindows ){
			for( ScreeningResource r : this.resources ){
				line += "\n" + this.trials + ", " + t + ", " + r + ", " + this.fines.get(t).get(r);
			}
		}
		fw.write(line);
		fw.close();
	}
	
	public Map<Integer, Map<ScreeningResource, Double>> getFines() {
		return this.fines;
	}
}
