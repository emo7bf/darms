package solvers;
/*
 * @author Matthew Brown
 * 
 */

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import models.DARMSModel;
import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;

public class DARMSOneNormSolver{	
	private DARMSModel model;
	private List<PureStrategy> pureStrategyList; 
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginalToCheck;
	
	private IloCplex cplex;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>> yVarMap;
	private IloNumVar uVar;
	
	private List<Integer> allTimeWindows;
	private List<Integer> currentTimeWindows;
	
	private Map<Integer, List<Flight>> flights;
	
	private List<IloRange> constraints;
	
	private Map<PureStrategy, IloRange> constraintMap;
	
	private static final int MM = 100000;

	public DARMSOneNormSolver(DARMSModel model) throws Exception{
		this.model = model;
		
		pureStrategyList = new ArrayList<PureStrategy>();
		constraints = new ArrayList<IloRange>();
		constraintMap = new HashMap<PureStrategy, IloRange>();
		flights = new HashMap<Integer, List<Flight>>();
		
		for(int t : model.getTimeWindows()){
			for(Flight f : model.getFlights()){
				Map<Integer, Map<RiskCategory, Integer>> d = f.getTemporalPassengerDistribution();
				
				if(d.containsKey(t)){
					if(!flights.containsKey(t)){
						flights.put(t, new ArrayList<Flight>());
					}
					
					flights.get(t).add(f);
				}
			}
		}
		
		allTimeWindows = new ArrayList<Integer>(flights.keySet());
		
		Collections.sort(allTimeWindows);
		
		currentTimeWindows = allTimeWindows;
		
		cplex = new IloCplex();
		cplex.setName("DARMS-OneNorm");
		cplex.setOut(null);
		
		initVars();
	}
	
	public void solve() throws Exception{
		cplex.solve();
	}
	
	private void initObjective() throws IloException{
		IloObjective obj = cplex.getObjective();
		
		if(obj != null){
			cplex.delete(obj);
		}
		
		IloNumExpr expr = uVar;
		
		for(int t : currentTimeWindows){
			for(Flight f : flights.get(t)){
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, cplex.prod(marginalToCheck.get(t).get(f).get(c).get(o), yVarMap.get(t).get(f).get(c).get(o)));
					}
				}
			}
		}
		
		cplex.addMaximize(expr);
	}
	
	public void setMarginal(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginal) throws Exception{
		marginalToCheck = marginal;
		
		initObjective();
	}
	
	protected void initVars() throws Exception{
		List<IloNumVar> varList = new ArrayList<IloNumVar>();
		
		yVarMap = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>>();
		
		for(int t : currentTimeWindows){
			yVarMap.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>());
			
			for(Flight f : flights.get(t)){
				yVarMap.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, IloNumVar>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					yVarMap.get(t).get(f).put(c, new HashMap<ScreeningOperation, IloNumVar>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						IloNumVar var = cplex.numVar(-1, 1, IloNumVarType.Float, "y_t" + t + "_f" +  f.id() + "_c" + c.id() + "_o" + o.getID());
					
						yVarMap.get(t).get(f).get(c).put(o, var);
						varList.add(var);
					}
				}
			}
		}
		
		uVar = cplex.numVar(-MM, MM, IloNumVarType.Float, "u");
		
		varList.add(uVar);
		
		IloNumVar[] v = new IloNumVar[varList.size()];

		cplex.add(varList.toArray(v));
	}
	
	public void resetWeight() throws Exception{
		for(int t : currentTimeWindows){
			for(Flight f : flights.get(t)){
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					for(ScreeningOperation o : model.getScreeningOperations()){
						yVarMap.get(t).get(f).get(c).get(o).setLB(-1);
						yVarMap.get(t).get(f).get(c).get(o).setUB(1);
					}
				}
			}
		}
	}
	
	public void addPureStrategy(PureStrategy p) throws Exception{
		pureStrategyList.add(p);
		
		IloNumExpr expr = uVar;
		
		for(int t : currentTimeWindows){
			for(Flight f : flights.get(t)){
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, cplex.prod(p.get(t, f, c, o), yVarMap.get(t).get(f).get(c).get(o)));
					}
				}
			}
		}
		
		IloRange constraint = cplex.le(expr, 0, "P" + p.id() + "Row");
		
		constraints.add(constraint);
		
		constraintMap.put(p, constraint);
		
		cplex.add(constraint);
	}
	
	// Get the duals of constraint A'y + u <= 0, which are the probabilities of the joint schedules. 
	public Map<PureStrategy, Double> getMixedStrategy() throws IloException{
		Map<PureStrategy, Double> reducedCosts = new HashMap<PureStrategy, Double>();		
		
		for(PureStrategy p : pureStrategyList){
			reducedCosts.put(p, cplex.getDual(constraintMap.get(p)));
		}
		
		return reducedCosts;
	}
	
	// Get the solution y as the weight for each target in the slave problem. 
	// The objective of the slave MILP is to min -A'y - u + A'B'h
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getReducedCosts() throws Exception{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> reducedCosts = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : currentTimeWindows){
			reducedCosts.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : flights.get(t)){
				reducedCosts.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					reducedCosts.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						double reducedCost = -cplex.getValue(yVarMap.get(t).get(f).get(c).get(o));
						
						reducedCosts.get(t).get(f).get(c).put(o, reducedCost);
					}
				}
			}
		}
		
		return reducedCosts;
	}
	
	public double getReducedCostConstant() throws Exception{
		return -cplex.getValue(uVar);
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getConstraintCoeff() throws Exception{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> constCoeff = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : currentTimeWindows){
			if(!constCoeff.containsKey(t)){
				constCoeff.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			}
				
			for(Flight f : flights.get(t)){
				if(!constCoeff.get(t).containsKey(f)){
					constCoeff.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				}
					
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					if(!constCoeff.get(t).get(f).containsKey(c)){
						constCoeff.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					}
						
					for(ScreeningOperation o : model.getScreeningOperations()){
						double coeff = cplex.getValue(yVarMap.get(t).get(f).get(c).get(o));
							
						constCoeff.get(t).get(f).get(c).put(o, coeff);
					}
				}
			}
		}
		
		return constCoeff;
	}
	
	public double getConstraintConst() throws Exception{
		return cplex.getValue(uVar);
	}
	
	// Return the feasible marginal coverage on targets, which has the minimum 1-Norm distance to the given marginal. 
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getOneNormProjection() throws Exception{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> oneNormProjection = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(PureStrategy p : pureStrategyList){
			double dual = cplex.getDual(constraintMap.get(p));
			
			for(int t : currentTimeWindows){
				if(!oneNormProjection.containsKey(t)){
					oneNormProjection.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
				}
				
				for(Flight f : flights.get(t)){
					if(!oneNormProjection.get(t).containsKey(f)){
						oneNormProjection.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
					}
					
					for(RiskCategory c : model.getAdversaryDistribution().keySet()){
						if(!oneNormProjection.get(t).get(f).containsKey(c)){
							oneNormProjection.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
						}
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							double targetCoverage = 0.0;
							
							if(oneNormProjection.get(t).get(f).get(c).containsKey(o)){
								targetCoverage = oneNormProjection.get(t).get(f).get(c).get(o);
							}
							
							targetCoverage += dual * p.get(t, f, c, o);
							
							oneNormProjection.get(t).get(f).get(c).put(o, targetCoverage);
						}
					}
				}
			}
		}
	
		return oneNormProjection;	
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getMarginalStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> marginalStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(PureStrategy p : pureStrategyList){
			double dual = cplex.getDual(constraintMap.get(p));
			
			for(int t : currentTimeWindows){
				if(!marginalStrategy.containsKey(t)){
					marginalStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
				}
				
				for(Flight f : flights.get(t)){
					Map<Integer, Map<RiskCategory, Integer>> passengerDistribution = f.getTemporalPassengerDistribution();
					
					if(!marginalStrategy.get(t).containsKey(f)){
						marginalStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
					}
					
					for(RiskCategory c : model.getAdversaryDistribution().keySet()){
						if(!marginalStrategy.get(t).get(f).containsKey(c)){
							marginalStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
						}
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							double targetCoverage = 0.0;
							
							if(marginalStrategy.get(t).get(f).get(c).containsKey(o)){
								targetCoverage = marginalStrategy.get(t).get(f).get(c).get(o);
							}
							
							targetCoverage += dual * p.get(t, f, c, o) / (double)passengerDistribution.get(t).get(c);
							
							marginalStrategy.get(t).get(f).get(c).put(o, targetCoverage);
						}
					}
				}
			}
		}
	
		return marginalStrategy;	
	}
	
	public double getOneNormDistance() throws IloException{
		return cplex.getObjValue();
	}
	
	public List<PureStrategy> getPureStrategyList(){
		return pureStrategyList;
	}
	
	public int getNumberPureStrategies(){
		return pureStrategyList.size();
	}
	
	public void writeProblem(String filename) throws IloException{
		cplex.exportModel(filename);
	}
	
	public void writeSolution(String filename) throws IloException{
		cplex.writeSolution(filename);
	}
}