package solvers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.DARMSModel;
import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;

import ilog.concert.IloException;

public class DARMSDecomposedSlaveSolver implements DARMSSlave{
	private DARMSModel model;
	private Map<Integer, DARMSSlaveSolver> slaveSolverMap;
	
	public DARMSDecomposedSlaveSolver(DARMSModel model, boolean betterResponse) throws Exception{
		this.model = model;
		
		slaveSolverMap = new HashMap<Integer, DARMSSlaveSolver>();

		for(Integer t : model.getTimeWindows()){
			List<Integer> timeWindow = new ArrayList<Integer>();
			
			timeWindow.add(t);
			slaveSolverMap.put(t, new DARMSSlaveSolver(model, betterResponse, timeWindow));
		}
	}
	
	public void solve() throws Exception{
		for(int t : slaveSolverMap.keySet()){
			slaveSolverMap.get(t).solve();
		}
	}
	
	public void writeProblem(String filename) throws IloException{
		int index = filename.lastIndexOf(".");
		
		String prefix = filename.substring(0, index);
		String suffix = filename.substring(index);
		
		for(int t : slaveSolverMap.keySet()){
			String file = prefix + "_t" + t + suffix;
			
			slaveSolverMap.get(t).writeProblem(file);
		}
	}
	
	public void writeSolution(String filename) throws IloException{
		int index = filename.lastIndexOf(".");
		
		String prefix = filename.substring(0, index);
		String suffix = filename.substring(index);
		
		for(int t : slaveSolverMap.keySet()){
			String file = prefix + "_t" + t + suffix;
		
			slaveSolverMap.get(t).writeSolution(file);
		}
	}
	
	public PureStrategy getPureStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		for(int t : slaveSolverMap.keySet()){
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> screeningStrategy = slaveSolverMap.get(t).getDefenderScreeningStrategy();
			
			if(screeningStrategy == null){
				return null;
			}
			
			defenderScreeningStrategy.putAll(screeningStrategy);
		}
		
		return new PureStrategy(defenderScreeningStrategy);
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> getDefenderScreeningStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		for(int t : slaveSolverMap.keySet()){
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> screeningStrategy = slaveSolverMap.get(t).getDefenderScreeningStrategy();
			
			defenderScreeningStrategy.putAll(screeningStrategy);
		}
		
		return defenderScreeningStrategy;
	}
	
	public void writeDefenderScreeningStrategy(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		List<ScreeningOperation> screeningOperations = model.getScreeningOperations();
		
		String line = "TimeWindow, Flight, RiskCategory";
		
		for(ScreeningOperation o : screeningOperations){
			line += ", " + o;
		}
		
		fw.write(line);
		
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> defenderScreeningStrategy = getDefenderScreeningStrategy();
		
		for(int t : defenderScreeningStrategy.keySet()){
			for(Flight f : defenderScreeningStrategy.get(t).keySet()){
				for(RiskCategory c : riskCategories){
					line = "\n" + t + ", " + f + ", " + c;
					
					for(ScreeningOperation o : screeningOperations){
						line += ", " + defenderScreeningStrategy.get(t).get(f).get(c).get(o);
					}
					
					fw.write(line);
				}
			}
		}
		
		fw.close();
	}
	
	public void setReducedCosts(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> reducedCosts) throws IloException{
		for(int t : slaveSolverMap.keySet()){
			Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> rc = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
			
			rc.put(t, reducedCosts.get(t));
			
			slaveSolverMap.get(t).setReducedCosts(rc);
		}
	}
	
	public void initializeReducedCosts() throws IloException{
		for(int t : slaveSolverMap.keySet()){
			slaveSolverMap.get(t).initializeReducedCosts();
		}
	}
	
	public double getReducedCost() throws IloException{
		double reducedCost = 0;
		
		for(int t : slaveSolverMap.keySet()){
			reducedCost += slaveSolverMap.get(t).getReducedCost();
		}
		
		return reducedCost;
	}
}