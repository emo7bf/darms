package solvers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utilities.DARMSPureStrategyGenerator;

import models.AttackMethod;
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
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

public class DARMSBaselineSolver{
	private DARMSModel model;
	
	private IloCplex cplex;
	
	private Map<PureStrategy, IloNumVar> pMap;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>> xMap;
	private Map<RiskCategory, IloNumVar> dMap;
	
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverage;
	private Map<RiskCategory, Double> defenderPayoffs;
	private Map<RiskCategory, Double> adversaryPayoffs;
	private Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryStrategies;
	
	private List<PureStrategy> pureStrategyList;
	
	private List<IloRange> constraints;
	
	private static final int MM = 100000;
	
	public DARMSBaselineSolver(DARMSModel model) throws Exception{
		this.model = model;
		
		pMap = new HashMap<PureStrategy, IloNumVar>();
		dMap = new HashMap<RiskCategory, IloNumVar>();
		xMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>>();

		cplex = new IloCplex();
		cplex.setName("DARMS");
		cplex.setOut(null);
		
		DARMSPureStrategyGenerator generator = new DARMSPureStrategyGenerator(model);
		
		pureStrategyList = generator.generatePureStrategies();
		
		initVars();
		initConstraints();
		initObjective();
	}
	
	private void initVars() throws IloException{
		List<IloNumVar> varList = new ArrayList<IloNumVar>();
		
		for(PureStrategy p : pureStrategyList){
			IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "p_" + p.id());
			
			pMap.put(p, var);
			varList.add(var);
		}
		
		for(int t : model.getTimeWindows()){
			xMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				xMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, IloNumVar>>());
				
				for(Flight f : model.getFlights(t)){
					xMap.get(t).get(c).put(f, new HashMap<AttackMethod, IloNumVar>());
					
					for(AttackMethod m : model.getAttackMethods()){
						IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_t" + t + "_c" + c.id() + "_f" + f.id() + "_m" + m.id());
						
						xMap.get(t).get(c).get(f).put(m, var);
						varList.add(var);
					}
				}
			}
		}
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			IloNumVar var1 = cplex.numVar(-MM, MM, IloNumVarType.Float, "d_c" + c.id());
				
			dMap.put(c, var1);
			
			varList.add(var1);
		}
		
		IloNumVar[] v = new IloNumVar[varList.size()];

		cplex.add(varList.toArray(v));
	}
	
	private void initConstraints() throws IloException{
		constraints = new ArrayList<IloRange>();
		
		sumDefenderScreeningActionRow();
		sumDefenderScreeningThroughputRow();
		sumDefenderCoverageRow();
		setZeroSumDefenderPayoffRow();
		
		IloRange[] c = new IloRange[constraints.size()];

		cplex.add(constraints.toArray(c));
	}
	
	private void initObjective() throws IloException{
		Map<RiskCategory, Double> adversaryDistribution = model.getAdversaryDistribution();
		
		IloNumExpr expr = cplex.constant(0);
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			expr = cplex.sum(expr, cplex.prod(dMap.get(c), adversaryDistribution.get(c)));
		}
		
		cplex.addMaximize(expr);
	}
	
	public void solve() throws Exception{
		cplex.solve();
			
		if(!cplex.isPrimalFeasible()){
			throw new Exception("Infeasible. Capacity constraints exceeded.");
		}
			
		riskCategoryCoverage = calculateRiskCategoryCoverage();
		defenderPayoffs = getDefenderPayoffs();
		adversaryPayoffs = getAdversaryPayoffs();
		adversaryStrategies = getAdversaryStrategies();
	}
	
	private void sumDefenderCoverageRow() throws IloException{
		for(int t : model.getTimeWindows()){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : model.getFlights(t)){
					Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
						
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = xMap.get(t).get(c).get(f).get(m);
							
						for(ScreeningOperation o : model.getScreeningOperations()){
							for(PureStrategy p : pureStrategyList){
								double prob = p.get(t, f, c, o) / (double)distribution.get(t).get(c);
								
								expr = cplex.sum(expr, cplex.prod(pMap.get(p), -o.effectiveness(c, m) * prob));
							}
						}
							
						constraints.add(cplex.eq(expr, 0, "X" + t + "C" + c.id() + "F" + f.id() + "M" + m.id() + "SUM"));
					}
				}
			}
		}
	}
	
	private void setZeroSumDefenderPayoffRow() throws IloException{
		for(int t : model.getTimeWindows()){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : model.getFlights(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = cplex.sum(dMap.get(c), cplex.prod(xMap.get(t).get(c).get(f).get(m), f.getDefUncovPayoff() - f.getDefCovPayoff()));
						
						constraints.add(cplex.le(expr, f.getDefUncovPayoff(), "DC" + t + "C" + c.id() + "F" + f.id() + "M" + m.id()));
					}
				}
			}
		}
	}
	
	private void sumDefenderScreeningActionRow() throws IloException{
		IloNumExpr expr = cplex.constant(0);
		
		for(PureStrategy p : pureStrategyList){
			expr = cplex.sum(expr, pMap.get(p));
		}
					
		constraints.add(cplex.eq(expr, 1.0, "PSUM"));
	}
	
	private void sumDefenderScreeningThroughputRow() throws IloException{
		Map<ScreeningResource, Integer> screeningResources = model.getScreeningResources();
		
		for(int t : model.getTimeWindows()){
			for(ScreeningResource r : screeningResources.keySet()){
				IloNumExpr expr = cplex.constant(0);
				
				for(Flight f : model.getFlights(t)){
					Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
						
					for(RiskCategory c : distribution.get(t).keySet()){
						double numPassengers = distribution.get(t).get(c);
							
						for(ScreeningOperation o : model.getScreeningOperations()){
							if(o.getResources().contains(r)){
								for(PureStrategy p : pureStrategyList){
									expr = cplex.sum(expr, cplex.prod(pMap.get(p), p.get(t, f, c, o) / numPassengers));
								}
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

	public double getDefenderPayoff(){
		double defenderPayoff = 0.0;
		
		Map<RiskCategory, Double> adversaryDistribution = model.getAdversaryDistribution();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			defenderPayoff += defenderPayoffs.get(c) * adversaryDistribution.get(c);
		}
		
		return defenderPayoff;
	}
	
	public Map<PureStrategy, Double> getDefenderScreeningStrategy() throws IloException{
		Map<PureStrategy, Double> defenderScreeningStrategy = new HashMap<PureStrategy, Double>();
		
		for(PureStrategy p : pureStrategyList){
			defenderScreeningStrategy.put(p, cplex.getValue(pMap.get(p)));
		}
		
		return defenderScreeningStrategy;
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getDefenderMarginalScreeningStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderMarginalScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		Map<PureStrategy, Double> mixedStrategy = getDefenderScreeningStrategy();
		
		for(int t : model.getTimeWindows()){
			defenderMarginalScreeningStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : model.getFlights(t)){
				defenderMarginalScreeningStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					defenderMarginalScreeningStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						double marginal = 0.0;
						
						for(PureStrategy p : pureStrategyList){
							marginal += mixedStrategy.get(p) * p.get(t, f, c, o);
						}
							
						defenderMarginalScreeningStrategy.get(t).get(f).get(c).put(o, marginal);
					}
				}
			}
		}
		
		return defenderMarginalScreeningStrategy;
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
		
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>  marginalStrategy = getDefenderMarginalScreeningStrategy();
		
		for(int t : marginalStrategy.keySet()){
			for(Flight f : marginalStrategy.get(t).keySet()){
				for(RiskCategory c : riskCategories){
					line = "\n" + t + ", " + f + ", " + c;
					
					for(ScreeningOperation o : screeningOperations){
						line += ", " + marginalStrategy.get(t).get(f).get(c).get(o);
					}
					
					fw.write(line);
				}
			}
		}
		
		fw.close();
	}
	
	public Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> getAdversaryStrategies() throws IloException{
		Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryActionsMap = new HashMap<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			double bestUtility = Double.NEGATIVE_INFINITY;
			
			for(int t : model.getTimeWindows()){
				for(Flight f : model.getFlights(t)){
					for(AttackMethod m : model.getAttackMethods()){
						double coverage = riskCategoryCoverage.get(t).get(c).get(f).get(m);
						
						double utility = (coverage * f.getAttCovPayoff()) + ((1.0 - coverage)* f.getAttUncovPayoff());
					
						if(utility > bestUtility){
							bestUtility = utility;
							
							adversaryActionsMap.put(c, new HashMap<Integer, Map<Flight, AttackMethod>>());
							adversaryActionsMap.get(c).put(t, new HashMap<Flight, AttackMethod>());
							adversaryActionsMap.get(c).get(t).put(f, m);
						}
					}
				}
			}
		}
		
		return adversaryActionsMap;
	}
	
	public void writeAdversaryStrategies(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "RiskCategory, TimeWindow, Flight, AttackMethod";
		
		fw.write(line);
		
		for(RiskCategory c : riskCategories){
			for(int t : adversaryStrategies.get(c).keySet()){
				for(Flight f : adversaryStrategies.get(c).get(t).keySet()){
					line = "\n" + c + ", " + t + ", " + f + ", " + adversaryStrategies.get(c).get(t).get(f);
						
					fw.write(line);
				}
			}
		}
		
		fw.close();
	}
	
	public Map<RiskCategory, Double> getAdversaryPayoffs() throws IloException{
		Map<RiskCategory, Double> adversaryPayoffsMap = new HashMap<RiskCategory, Double>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			adversaryPayoffsMap.put(c, -1 * cplex.getValue(dMap.get(c)));
		}
		
		return adversaryPayoffsMap;
	}
	
	public void writeAdversaryPayoffs(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
	
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		fw.write("RiskCategory, Payoff");
		
		for(RiskCategory c : riskCategories){
			fw.write("\n" + c + ", " + adversaryPayoffs.get(c));
		}
		
		fw.close();
	}
	
	public Map<RiskCategory, Double> getDefenderPayoffs() throws IloException{
		Map<RiskCategory, Double> defenderPayoffsMap = new HashMap<RiskCategory, Double>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			defenderPayoffsMap.put(c, cplex.getValue(dMap.get(c)));
		}
		
		return defenderPayoffsMap;
	}
	
	public void writeDefenderPayoffs(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<RiskCategory, Double> adversaryDistribution = model.getAdversaryDistribution();
	
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(adversaryDistribution.keySet());
		Collections.sort(riskCategories);
		
		fw.write("RiskCategory, Payoff, Probability, Utility");
		
		double totalUtility = 0.0;
		
		for(RiskCategory c : riskCategories){
			double payoff = defenderPayoffs.get(c);
			double probability = adversaryDistribution.get(c);
			double utility = payoff * probability;
			
			fw.write("\n" + c + ", " + payoff + ", " + probability + ", " + utility);
			
			totalUtility += utility;
		}
		
		fw.write("\n,, Defender Utility:," + totalUtility);
		
		fw.close();
	}
	
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> calculateRiskCategoryCoverage() throws IloException{
		Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverageMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>>();
	
		for(int t : model.getTimeWindows()){
			riskCategoryCoverageMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				riskCategoryCoverageMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, Double>>());
				
				for(Flight f : model.getFlights(t)){
					riskCategoryCoverageMap.get(t).get(c).put(f, new HashMap<AttackMethod, Double>());
					
					for(AttackMethod m : model.getAttackMethods()){
						riskCategoryCoverageMap.get(t).get(c).get(f).put(m, cplex.getValue(xMap.get(t).get(c).get(f).get(m)));
					}
				}
			}
		}
		
		return riskCategoryCoverageMap;
	}
	
	public void writeRiskCategoryCoverage(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
	
		List<AttackMethod> attackMethods = model.getAttackMethods();
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "RiskCategory, TimeWindow, Flight";
		
		for(AttackMethod m : attackMethods){
			line += ", " + m + "_coverage, " + m + "_payoff, " + m + "_utility";
		}
		
		fw.write(line);
		
		for(RiskCategory c : riskCategories){
			for(int t : model.getTimeWindows()){
				for(Flight f : model.getFlights(t)){
					line = "\n" + c + ", " + t + ", " + f;
					
					for(AttackMethod m : attackMethods){
						double coverage = riskCategoryCoverage.get(t).get(c).get(f).get(m);
						double payoff = coverage * f.getAttCovPayoff() + ((1.0 - coverage) * f.getAttUncovPayoff()); 
						double utility = payoff * model.getAdversaryDistribution().get(c);
						
						line += ", " + coverage + ", " + payoff + ", " + utility;
					}
					
					fw.write(line);
				}
			}
		}
		
		fw.close();
	}
	
	public void writeTemporalPassengerDistribution(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "TimeWindow, Flight";
		
		for(RiskCategory c : riskCategories){
			line += ", " + c;
		}
		
		line += ", " + "TOTAL";
		
		fw.write(line);
		
		for(int t : model.getTimeWindows()){
			for(Flight f : model.getFlights(t)){
				line = "\n" + t + ", " + f;
				
				Map<Integer, Map<RiskCategory, Integer>> temporalDistribution = f.getTemporalPassengerDistribution();
				
				int totalPassengers = 0;
				
				for(RiskCategory c : riskCategories){					
					line += ", " + temporalDistribution.get(t).get(c);
					
					totalPassengers += temporalDistribution.get(t).get(c);
				}
				
				line += ", " + totalPassengers;
					
				fw.write(line);
			}
		}
		
		fw.close();
	}
}