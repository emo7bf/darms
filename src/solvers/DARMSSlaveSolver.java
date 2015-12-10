package solvers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utilities.DARMSPureStrategyGenerator;

import models.DARMSModel;
import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

public class DARMSSlaveSolver implements DARMSSlave{
	private DARMSModel model;
	
	private IloCplex cplex;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>> sMap;
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> reducedCosts;
	
	private List<IloRange> constraints;
	
	private List<Integer> timeWindows;
	
	private boolean betterResponse;
	
	public DARMSSlaveSolver(DARMSModel model, boolean betterResponse) throws Exception{
		this.model = model;
		this.betterResponse = betterResponse;
		this.timeWindows = model.getTimeWindows();
		
		sMap = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>>();
		
		cplex = new IloCplex();
		cplex.setName("DARMS-Slave");
		cplex.setOut(null);
		
		initVars();
		initConstraints();
	}
	
	public DARMSSlaveSolver(DARMSModel model, boolean betterResponse, List<Integer> timeWindows) throws Exception{
		this.model = model;
		this.betterResponse = betterResponse;
		this.timeWindows = timeWindows;
		
		sMap = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>>();
		
		cplex = new IloCplex();
		cplex.setName("DARMS-Slave");
		cplex.setOut(null);
		
		initVars();
		initConstraints();
	}
	
	private void initVars() throws IloException{
		List<IloNumVar> varList = new ArrayList<IloNumVar>();
		
		for(int t : timeWindows){
			sMap.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>());
			
			for(Flight f : model.getFlights(t)){
				Map<Integer, Map<RiskCategory, Integer>> categoryDistribution = f.getTemporalPassengerDistribution();
				
				sMap.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, IloNumVar>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					sMap.get(t).get(f).put(c, new HashMap<ScreeningOperation, IloNumVar>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						IloNumVar var;
						
						if(betterResponse){
							var = cplex.numVar(0.0, categoryDistribution.get(t).get(c), IloNumVarType.Float, "s_t" + t + "_f" +  f.id() + "_c" + c.id() + "_o" + o.getID());
						}
						else{
							var = cplex.numVar(0.0, categoryDistribution.get(t).get(c), IloNumVarType.Int, "s_t" + t + "_f" +  f.id() + "_c" + c.id() + "_o" + o.getID());
						}
						
						sMap.get(t).get(f).get(c).put(o, var);
						varList.add(var);
					}
				}
			}
		}
		
		IloNumVar[] v = new IloNumVar[varList.size()];

		cplex.add(varList.toArray(v));
	}
	
	private void initConstraints() throws IloException{
		constraints = new ArrayList<IloRange>();
		
		sumDefenderScreeningActionRow();
		sumDefenderScreeningThroughputRow();
		
		IloRange[] c = new IloRange[constraints.size()];

		cplex.add(constraints.toArray(c));
	}
	
	private void initObjective() throws IloException{
		IloObjective obj = cplex.getObjective();
		
		if(obj != null){
			cplex.delete(obj);
		}
		
		IloNumExpr expr = cplex.constant(0);
		
		for(int t : timeWindows){
			for(Flight f : model.getFlights(t)){
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, cplex.prod(reducedCosts.get(t).get(f).get(c).get(o), sMap.get(t).get(f).get(c).get(o)));
					}
				}
			}
		}
		
		cplex.addMinimize(expr);
	}
	
	public void solve() throws Exception{
		cplex.solve();
			
		if(!cplex.isPrimalFeasible()){
			throw new Exception("Infeasible. Capacity constraints exceeded.");
		}
	}
	
	private void sumDefenderScreeningActionRow() throws IloException{
		for(int t : timeWindows){
			for(Flight f : model.getFlights(t)){
				Map<Integer, Map<RiskCategory, Integer>> categoryDistribution = f.getTemporalPassengerDistribution();
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					int numPassengers = categoryDistribution.get(t).get(c);
					
					IloNumExpr expr = cplex.constant(0);
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, sMap.get(t).get(f).get(c).get(o));
					}
					
					constraints.add(cplex.eq(expr, numPassengers, "ST" + t + "F" + f.id() + "C" + c.id() + "SUM"));
				}
			}
		}
	}
	
	private void sumDefenderScreeningThroughputRow() throws IloException{
		Map<ScreeningResource, Integer> screeningResources = model.getScreeningResources();
		
		for(int t : timeWindows){
			for(ScreeningResource r : screeningResources.keySet()){
				IloNumExpr expr = cplex.constant(0);
				
				for(Flight f : model.getFlights(t)){
					for(RiskCategory c : model.getAdversaryDistribution().keySet()){
						for(ScreeningOperation o : model.getScreeningOperations()){
							if(o.getResources().contains(r)){
								expr = cplex.sum(expr, sMap.get(t).get(f).get(c).get(o));
							}
						}
					}
				}
				
				double capacity = r.capacity() * screeningResources.get(r); //TODO: Adjust capacity according to time granularity
				
				constraints.add(cplex.le(expr, capacity, "ST" + t + "R" + r.id() + "THROUGHPUT"));
			}
		}
	}
	
	public void writeProblem(String filename) throws IloException{
		cplex.exportModel(filename);
	}
	
	public void writeSolution(String filename) throws IloException{
		cplex.writeSolution(filename);
	}
	
	public PureStrategy getPureStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> screeningStrategy = getDefenderScreeningStrategy(); 
		
		if(screeningStrategy == null){
			return null;
		}
		else{
			return new PureStrategy(screeningStrategy);
		}
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> getDefenderScreeningStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>>();
		
		boolean validScreeningStrategy = true;
		
		for(int t : timeWindows){
			defenderScreeningStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>());
			
			for(Flight f : model.getFlights(t)){
				defenderScreeningStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Integer>>());
				
				for(RiskCategory c : f.getPassengerDistribution().keySet()){
					defenderScreeningStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Integer>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						double value = cplex.getValue(sMap.get(t).get(f).get(c).get(o)) + 0.0001;
						
						if(value % 1.0 > 0.00015){
							//System.out.println("Non-Integer Allocations: " + t + " " + f + " " + c + " " + o + " " + value);
							validScreeningStrategy = false;
						}
						
						defenderScreeningStrategy.get(t).get(f).get(c).put(o, (int) value);
					}
				}
			}
		}
		
		if(validScreeningStrategy){
			DARMSPureStrategyGenerator generator = new DARMSPureStrategyGenerator(model, defenderScreeningStrategy);
			
			if(!generator.validatePureStrategy(defenderScreeningStrategy)){
				System.out.println("Invalid pure strategy.");
			}
			
			return defenderScreeningStrategy;
		}
		else{
			DARMSPureStrategyGenerator generator = new DARMSPureStrategyGenerator(model, defenderScreeningStrategy);
			
			return generator.generateRandomizedScreeningStrategies(1).get(0);
			
			//return null;
		}
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
		this.reducedCosts = reducedCosts;
		
		initObjective();
	}
	
	public void initializeReducedCosts() throws IloException{
		reducedCosts = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : timeWindows){
			reducedCosts.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : model.getFlights(t)){
				reducedCosts.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : f.getPassengerDistribution().keySet()){
					reducedCosts.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						reducedCosts.get(t).get(f).get(c).put(o, 1.0);
					}
				}
			}
		}
		
		initObjective();
	}
	
	public double getReducedCost() throws IloException{
		return cplex.getObjValue();
	}
}