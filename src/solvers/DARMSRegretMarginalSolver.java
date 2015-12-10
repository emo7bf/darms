package solvers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.AttackMethod;
import models.DARMSModel;
import models.PassengerDistribution;
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

public class DARMSRegretMarginalSolver{
	private DARMSModel model;
	
	private IloCplex cplex;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>> sMap;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>> xMap;
	
	private Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, IloNumVar>>> oMap;
	
	private Map<RiskCategory, IloNumVar> dMap;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderScreeningStrategy;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverage;
	private Map<RiskCategory, Double> defenderPayoffs;
	private Map<RiskCategory, Double> adversaryPayoffs;
	private Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryStrategies;
	
	private Map<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>, Double> marginalBounds;
	
	private Map<PassengerDistribution, Double> utopiaPoint;
	
	private Map<ScreeningResource, Double> overflowPenalties;
	
	private List<IloRange> constraints;
	
	private static final int MM = 100000;
	
	private double maxOverflowPercentage = 0.1;
	private double overflowPenalty = -1.0;
	
	private List<PassengerDistribution> passengerDistributionList;
	
	private List<Integer> allTimeWindows;
	private List<Integer> currentTimeWindows;
	
	private boolean decomposed;
	
	private IloNumVar obj;
	
	public DARMSRegretMarginalSolver(DARMSModel model, Map<PassengerDistribution, Double> utopiaPoint, boolean decomposed) throws Exception{
		this.model = model;
		this.utopiaPoint = utopiaPoint;
		this.decomposed = decomposed;
		
		passengerDistributionList = new ArrayList<PassengerDistribution>(utopiaPoint.keySet());
		
		Collections.sort(passengerDistributionList);
		
		verifyZeroSum();
		
		marginalBounds = new HashMap<Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>, Double>();
		
		allTimeWindows = model.getTimeWindows();
		
		overflowPenalties = new HashMap<ScreeningResource, Double>();
		
		for(ScreeningResource r : model.getScreeningResources().keySet()){
			overflowPenalties.put(r, overflowPenalty);
		}
	}
	
	private void verifyZeroSum() throws Exception{
		for(Flight f : model.getFlights()){
			int defCov = f.getDefCovPayoff();
			int defUncov = f.getDefUncovPayoff();
			int attCov = f.getAttCovPayoff();
			int attUncov = f.getAttUncovPayoff();
			
			if(defCov != -attCov || defUncov != -attUncov){
				throw new Exception("Attempting to use zero-sum formulation on a non-zero-sum game.");
			}
		}
	}
	
	private void loadProblem(List<Integer> timeWindows) throws IloException{
		sMap = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>>();
		dMap = new HashMap<RiskCategory, IloNumVar>();
		xMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>>();
		oMap = new HashMap<PassengerDistribution, Map<Integer, Map<ScreeningResource, IloNumVar>>>();

		cplex = new IloCplex();
		cplex.setName("DARMS");
		cplex.setOut(null);
		
		this.currentTimeWindows = timeWindows;
		
		initVars();
		initConstraints();
		initObjective();
	}
	
	private void initVars() throws IloException{
		List<IloNumVar> varList = new ArrayList<IloNumVar>();
		
		for(int t : currentTimeWindows){
			sMap.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>());
			
			for(Flight f : model.getFlights(t)){
				sMap.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, IloNumVar>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					sMap.get(t).get(f).put(c, new HashMap<ScreeningOperation, IloNumVar>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "s_t" + t + "_f" +  f.id() + "_c" + c.id() + "_o" + o.getID());
					
						sMap.get(t).get(f).get(c).put(o, var);
						varList.add(var);
					}
				}
			}
		}
		
		for(int t : currentTimeWindows){
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
		
		Map<ScreeningResource, Integer> screeningResources = model.getScreeningResources();
		
		for(PassengerDistribution distribution : passengerDistributionList){
			oMap.put(distribution, new HashMap<Integer, Map<ScreeningResource, IloNumVar>>());
			
			for(int t : currentTimeWindows){
				oMap.get(distribution).put(t, new HashMap<ScreeningResource, IloNumVar>());
				
				for(ScreeningResource r : screeningResources.keySet()){
					double maxOverflowCapacity = maxOverflowPercentage * r.capacity() * screeningResources.get(r);
					
					IloNumVar var = cplex.numVar(0.0, maxOverflowCapacity, IloNumVarType.Float, "o_d" + distribution.id() + "_t" + t + "_r" + r.id());
					
					oMap.get(distribution).get(t).put(r, var);
					
					varList.add(var);
				}
			}
		}
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			IloNumVar var = cplex.numVar(-MM, MM, IloNumVarType.Float, "d_c" + c.id());
					
			dMap.put(c, var);
				
			varList.add(var);
		}
		
		obj = cplex.numVar(-MM, MM, IloNumVarType.Float, "obj");
		
		varList.add(obj);
		
		IloNumVar[] v = new IloNumVar[varList.size()];

		cplex.add(varList.toArray(v));
	}
	
	private void initConstraints() throws IloException{
		constraints = new ArrayList<IloRange>();
		
		sumDefenderScreeningActionRow();
		sumDefenderScreeningThroughputRow();
		sumDefenderCoverageRow();
		sumDefenderRegretRow();
		setMarginalBoundRow();
		
		setZeroSumDefenderPayoffRow();
		
		if(!model.flightByFlight()){
			setStaticScreening();
		}
		
		IloRange[] c = new IloRange[constraints.size()];

		cplex.add(constraints.toArray(c));
	}
	
	private void initObjective() throws IloException{
		cplex.addMinimize(obj);
	}
	
	public void solve() throws Exception{
		defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		riskCategoryCoverage = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>>();
		defenderPayoffs = new HashMap<RiskCategory, Double>();
		adversaryPayoffs = new HashMap<RiskCategory, Double>();
		adversaryStrategies = new HashMap<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>>();
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		if(decomposed){
			for(int t : allTimeWindows){
				List<Integer> timeWindow = new ArrayList<Integer>();
				
				timeWindow.add(t);
				
				loadProblem(timeWindow);
				
				cplex.solve();
				
				if(!cplex.isPrimalFeasible()){
					throw new Exception("Infeasible. Capacity constraints exceeded. Time Window: " + t);
				}
				
				defenderScreeningStrategy.put(t, getDefenderScreeningStrategy().get(t));
				riskCategoryCoverage.put(t, calculateRiskCategoryCoverage().get(t));
				
				Map<RiskCategory, Double> dPayoffs = getDefenderPayoffs();
				Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> aStrategies = getAdversaryStrategies();
				Map<RiskCategory, Double> aPayoffs = getAdversaryPayoffs();
				
				for(RiskCategory c : riskCategories){
					if(!adversaryPayoffs.containsKey(c) || aPayoffs.get(c) > adversaryPayoffs.get(c)){
						defenderPayoffs.put(c, dPayoffs.get(c));
						adversaryPayoffs.put(c, aPayoffs.get(c));
						adversaryStrategies.put(c, aStrategies.get(c));
					}
				}
			}
		}
		else{
			loadProblem(allTimeWindows);
			
			cplex.solve();
			
			if(!cplex.isPrimalFeasible()){
				throw new Exception("Infeasible. Capacity constraints exceeded.");
			}
			
			defenderScreeningStrategy = getDefenderScreeningStrategy();
			riskCategoryCoverage = calculateRiskCategoryCoverage();
			defenderPayoffs = getDefenderPayoffs();
			adversaryPayoffs = getAdversaryPayoffs();
			adversaryStrategies = getAdversaryStrategies();
		}
	}
	
	private void sumDefenderRegretRow() throws IloException{
		Map<RiskCategory, Double> adversaryDistribution = model.getAdversaryDistribution();
		
		for(PassengerDistribution passengerDistribution : passengerDistributionList){
			IloNumExpr expr = obj;
			
			for(RiskCategory c : adversaryDistribution.keySet()){
				expr = cplex.sum(expr, cplex.prod(dMap.get(c), adversaryDistribution.get(c)));
			}
			
			for(int t : currentTimeWindows){
				for(ScreeningResource r : model.getScreeningResources().keySet()){
					expr = cplex.sum(expr, cplex.prod(oMap.get(passengerDistribution).get(t).get(r), overflowPenalties.get(r)));
				}
			}
			
			constraints.add(cplex.ge(expr, utopiaPoint.get(passengerDistribution), "D" + passengerDistribution.id() + "REGRET"));
		}
	}
	
	private void sumDefenderCoverageRow() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : model.getFlights(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = xMap.get(t).get(c).get(f).get(m);
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							expr = cplex.sum(expr, cplex.prod(sMap.get(t).get(f).get(c).get(o), -o.effectiveness(c, m)));
						}
						
						constraints.add(cplex.eq(expr, 0, "X" + t + "C" + c.id() + "F" + f.id() + "M" + m.id() + "SUM"));
					}
				}
			}
		}
	}
	
	private void setZeroSumDefenderPayoffRow() throws IloException{
		for(int t : currentTimeWindows){
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
	
	private void setStaticScreening() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(ScreeningOperation o : model.getScreeningOperations()){
					for(Flight f1 : model.getFlights(t)){
						IloNumExpr expr1 = sMap.get(t).get(f1).get(c).get(o);
						
						for(Flight f2 : model.getFlights(t)){
							if(f2.id() - f1.id() == 1){
								IloNumExpr expr2 = cplex.prod(-1.0, sMap.get(t).get(f2).get(c).get(o));
								
								IloNumExpr expr = cplex.sum(expr1, expr2);
								
								constraints.add(cplex.eq(expr, 0.0, "T" + t + "C" + c.id() + "O" + o.getID() + "F" + f1.id() + "F" + f2.id()));
							}
						}
					}
				}
			}
		}
	}
	
	private void sumDefenderScreeningActionRow() throws IloException{
		for(int t : currentTimeWindows){
			for(Flight f : model.getFlights(t)){
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					IloNumExpr expr = cplex.constant(0);
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, sMap.get(t).get(f).get(c).get(o));
					}
					
					constraints.add(cplex.eq(expr, 1.0, "ST" + t + "F" + f.id() + "C" + c.id() + "SUM"));
				}
			}
		}
	}
	
	private void sumDefenderScreeningThroughputRow() throws IloException{
		Map<ScreeningResource, Integer> screeningResources = model.getScreeningResources();
		
		for(PassengerDistribution distribution : passengerDistributionList){
			for(int t : currentTimeWindows){
				for(ScreeningResource r : screeningResources.keySet()){
					IloNumExpr expr = cplex.prod(-1.0, oMap.get(distribution).get(t).get(r));
					
					for(Flight f : model.getFlights(t)){
						for(RiskCategory c : model.getAdversaryDistribution().keySet()){
							int numPassengers = distribution.get(t, f, c);
							
							for(ScreeningOperation o : model.getScreeningOperations()){
								if(o.getResources().contains(r)){
									expr = cplex.sum(expr, cplex.prod(sMap.get(t).get(f).get(c).get(o), numPassengers));
								}
							}
						}
					}
					
					double capacity = r.capacity() * screeningResources.get(r); //TODO: Adjust capacity according to time granularity
					
					constraints.add(cplex.le(expr, capacity, "SD" + distribution.id() + "T" + t + "R" + r.id() + "THROUGHPUT"));
				}
			}
		}
	}
	
	//TODO
	private void setMarginalBoundRow() throws IloException{
		int marginalBound = 1;
		
		for(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> boundaryCoeff : marginalBounds.keySet()){
			IloNumExpr expr = cplex.constant(0);
			
			for(int t : currentTimeWindows){
				for(Flight f : boundaryCoeff.get(t).keySet()){
					Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
					
					for(RiskCategory c : boundaryCoeff.get(t).get(f).keySet()){
						for(ScreeningOperation o : boundaryCoeff.get(t).get(f).get(c).keySet()){
							double val = boundaryCoeff.get(t).get(f).get(c).get(o) * distribution.get(t).get(c);
							
							expr = cplex.sum(expr, cplex.prod(val, sMap.get(t).get(f).get(c).get(o)));
						}
					}
				}
			}
			
			double upperBoundConst = marginalBounds.get(boundaryCoeff);
			
			IloRange constraint = cplex.eq(expr, upperBoundConst, "MarginalBound" + marginalBound);
			
			constraints.add(constraint);
			
			cplex.add(constraint);
		
			marginalBound++;
		}
	}
	
	public void addMarginalBound(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> boundaryCoeff, double upperBoundConst){
		marginalBounds.put(boundaryCoeff, upperBoundConst);
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
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getDefenderScreeningStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : currentTimeWindows){
			defenderScreeningStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : model.getFlights(t)){
				defenderScreeningStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : f.getPassengerDistribution().keySet()){
					defenderScreeningStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						defenderScreeningStrategy.get(t).get(f).get(c).put(o, cplex.getValue(sMap.get(t).get(f).get(c).get(o)));
					}
				}
			}
		}
		
		return defenderScreeningStrategy;
	}
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getDefenderMarginalScreeningStrategy(){
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderMarginalScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : allTimeWindows){
			defenderMarginalScreeningStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : model.getFlights(t)){
				Map<Integer, Map<RiskCategory, Integer>> distribution = f.getTemporalPassengerDistribution();
				
				defenderMarginalScreeningStrategy.get(t).put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
				
				for(RiskCategory c : model.getAdversaryDistribution().keySet()){
					defenderMarginalScreeningStrategy.get(t).get(f).put(c, new HashMap<ScreeningOperation, Double>());
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						defenderMarginalScreeningStrategy.get(t).get(f).get(c).put(o, distribution.get(t).get(c) * defenderScreeningStrategy.get(t).get(f).get(c).get(o));
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
		
		for(int t : allTimeWindows){
			for(Flight f : model.getFlights(t)){
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
	
	public Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> getAdversaryStrategies() throws IloException{
		Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryActionsMap = new HashMap<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			double bestUtility = Double.NEGATIVE_INFINITY;
			
			for(int t : currentTimeWindows){
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
	
	public Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> getOverflowPassengers() throws IloException{
		Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> overflowPassengersMap = new HashMap<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>>();
		
		for(PassengerDistribution passengerDistribution : oMap.keySet()){
			overflowPassengersMap.put(passengerDistribution, new HashMap<Integer, Map<ScreeningResource, Double>>());
			
			for(int t : currentTimeWindows){
				overflowPassengersMap.get(passengerDistribution).put(t, new HashMap<ScreeningResource, Double>());
				
				for(ScreeningResource r : model.getScreeningResources().keySet()){
					overflowPassengersMap.get(passengerDistribution).get(t).put(r, cplex.getValue(oMap.get(passengerDistribution).get(t).get(r)));
				}
			}
		}
		
		return overflowPassengersMap;
	}
	
	public Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> writeOverflowPassengers(String filename) throws Exception{
		Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> overflowPassengersMap = getOverflowPassengers();
		
		List<ScreeningResource> screeningResources = new ArrayList<ScreeningResource>(model.getScreeningResources().keySet());
		
		FileWriter fw = new FileWriter(new File(filename));
		
		fw.write("PassengerDistribution, TimeWindow, ScreeningResource, Overflow");
		
		for(PassengerDistribution passengerDistribution : passengerDistributionList){
			for(int t : currentTimeWindows){
				for(ScreeningResource r : screeningResources){
					String line = "\n" + passengerDistribution + ", " + t + ", " + r + ", " + overflowPassengersMap.get(passengerDistribution).get(t).get(r);
					
					fw.write(line);
				}
			}
		}
		
		fw.close();
		
		return overflowPassengersMap;
	}
	
	public Map<PassengerDistribution, Double> getNumOverflowPassengers() throws IloException{
		Map<PassengerDistribution, Double> numOverflowPassengersMap = new HashMap<PassengerDistribution, Double>();
		
		Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> overflowPassengersMap = getOverflowPassengers();
		
		for(PassengerDistribution passengerDistribution : overflowPassengersMap.keySet()){
			double numOverflowPassengers = 0;
			
			for(int t : currentTimeWindows){
				for(ScreeningResource r : model.getScreeningResources().keySet()){
					numOverflowPassengers += overflowPassengersMap.get(passengerDistribution).get(t).get(r);
				}
			}
			
			numOverflowPassengersMap.put(passengerDistribution, numOverflowPassengers);
		}
		
		return numOverflowPassengersMap;
	}
	
	public Map<PassengerDistribution, Double> getDistributionPayoffs() throws IloException{
		Map<PassengerDistribution, Map<Integer, Map<ScreeningResource, Double>>> overflowPassengers = getOverflowPassengers();
		
		Map<PassengerDistribution, Double> distributionPayoffMap = new HashMap<PassengerDistribution, Double>();

		for(PassengerDistribution passengerDistribution : oMap.keySet()){
			double p = getDefenderPayoff();
			
			for(int t : overflowPassengers.get(passengerDistribution).keySet()){
				for(ScreeningResource r : overflowPassengers.get(passengerDistribution).get(t).keySet()){
					p += overflowPenalties.get(r) * overflowPassengers.get(passengerDistribution).get(t).get(r);
				}
			}
			
			distributionPayoffMap.put(passengerDistribution, p);
		}
		
		return distributionPayoffMap;
	}
	
	public double getMaxRegret() throws IloException{
		return cplex.getObjValue();
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
	
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> getRiskCategoryCoverage() throws IloException{
		Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverageMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>>();
	
		for(int t : currentTimeWindows){
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
	
	public Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> calculateRiskCategoryCoverage() throws IloException{
		Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverageMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>>();
	
		for(int t : currentTimeWindows){
			riskCategoryCoverageMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				riskCategoryCoverageMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, Double>>());
				
				for(Flight f : model.getFlights(t)){
					riskCategoryCoverageMap.get(t).get(c).put(f, new HashMap<AttackMethod, Double>());
					
					for(AttackMethod m : model.getAttackMethods()){
						double probability = 0.0;
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							probability += cplex.getValue(sMap.get(t).get(f).get(c).get(o)) * o.effectiveness(c, m);
						}
						
						if(probability > 1.0){
							probability = 1.0;
						}
						
						riskCategoryCoverageMap.get(t).get(c).get(f).put(m, probability);
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
			for(int t : allTimeWindows){
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
		
		for(int t : allTimeWindows){
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