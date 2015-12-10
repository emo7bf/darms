package solvers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.AttackMethod;
import models.DARMSModel;
import models.Flight;
import models.PostScreeningResource;
import models.RiskCategory;
import models.ScreeningOperation;
import models.ScreeningResource;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;

public class DARMSTemporalSolver {
	private DARMSModel model;
	
	private IloCplex cplex;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>> sMap;
	private Map<Integer, Map<Flight, Map<PostScreeningResource, IloNumVar>>> pMap;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>> aMap;
	private Map<RiskCategory, IloNumVar> dMap;
	private Map<RiskCategory, IloNumVar> kMap;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>> xMap;
	
	private Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderScreeningStrategy;
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverage;
	private Map<RiskCategory, Double> defenderPayoffs;
	private Map<RiskCategory, Double> adversaryPayoffs;
	private Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryStrategies; 
	
	private List<IloRange> constraints;
	
	private static final int MM = 100000;
	
	List<Integer> allTimeWindows;
	List<Integer> currentTimeWindows;
	
	Map<Integer, List<Flight>> flights;
	
	public DARMSTemporalSolver(DARMSModel m) throws IloException{
		model = m;
		
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
	}
	
	private void loadProblem(List<Integer> timeWindows) throws IloException{
		sMap = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>>();
		pMap = new HashMap<Integer, Map<Flight, Map<PostScreeningResource, IloNumVar>>>();
		aMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>>();
		dMap = new HashMap<RiskCategory, IloNumVar>();
		kMap = new HashMap<RiskCategory, IloNumVar>();
		xMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>>();
		
		cplex = new IloCplex();
		cplex.setName("DARMS");
		cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Barrier);
		cplex.setParam(IloCplex.IntParam.BarCrossAlg, IloCplex.Algorithm.None);
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
			
			for(Flight f : flights.get(t)){
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
			pMap.put(t, new HashMap<Flight, Map<PostScreeningResource, IloNumVar>>());
			
			for(Flight f : flights.get(t)){
				pMap.get(t).put(f, new HashMap<PostScreeningResource, IloNumVar>());
				
				for(PostScreeningResource r : model.getPostScreeningResources().keySet()){
					IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "p_t" + t + "_f" +  f.id() + "_r" + r.id());
					
					pMap.get(t).get(f).put(r, var);
					varList.add(var);
				}
			}
		}
		
		for(int t : currentTimeWindows){
			xMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				xMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, IloNumVar>>());
				
				for(Flight f : flights.get(t)){
					xMap.get(t).get(c).put(f, new HashMap<AttackMethod, IloNumVar>());
					
					for(AttackMethod m : model.getAttackMethods()){
						IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_t" + t + "_c" + c.id() + "_f" + f.id() + "_m" + m.id());
						
						xMap.get(t).get(c).get(f).put(m, var);
						varList.add(var);
					}
				}
			}
		}
		
		for(int t : currentTimeWindows){
			aMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				aMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, IloNumVar>>());
				
				for(Flight f : flights.get(t)){
					aMap.get(t).get(c).put(f, new HashMap<AttackMethod, IloNumVar> ());
					
					for(AttackMethod m : model.getAttackMethods()){
						IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Int, "a_t" + t +"_c" + c.id() + "_f" + f.id() + "_m" + m.id());
						
						aMap.get(t).get(c).get(f).put(m, var);
						varList.add(var);
					}
				}
			}
		}
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			IloNumVar var1 = cplex.numVar(-MM, MM, IloNumVarType.Float, "d_c" + c.id());
			IloNumVar var2 = cplex.numVar(-MM, MM, IloNumVarType.Float, "k_c" + c.id());
				
			dMap.put(c, var1);
			kMap.put(c, var2);
			
			varList.add(var1);
			varList.add(var2);
		}
		
		IloNumVar[] v = new IloNumVar[varList.size()];

		cplex.add(varList.toArray(v));
	}
	
	private void initConstraints() throws IloException{
		constraints = new ArrayList<IloRange>();
		
		sumDefenderScreeningActionRow();
		sumDefenderPostScreeningActionRow();
		sumDefenderScreeningThroughputRow();
		sumDefenderCoverageRow();
		sumAdversaryActionRow();
		setDefenderPayoffRow();
		setAdversaryPayoffRow();
		
		if(!model.flightByFlight()){
			setStaticScreening();
		}
		
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
	
	public void solve(boolean decomposed) throws IloException{
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
				
				defenderScreeningStrategy.put(t, getDefenderScreeningStrategy().get(t));
				riskCategoryCoverage.put(t, calculateRiskCategoryCoverage().get(t));
				
				Map<RiskCategory, Double> dPayoffs = getDefenderPayoffs();
				Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> aStrategies = getAdversaryStrategies();
				Map<RiskCategory, Double> aPayoffs = getAdversaryPayoffs();
				
				for(RiskCategory c : riskCategories){
					if(!defenderPayoffs.containsKey(c) || dPayoffs.get(c) < defenderPayoffs.get(c)){
						defenderPayoffs.put(c, dPayoffs.get(c));
					}
					
					if(!adversaryPayoffs.containsKey(c) || aPayoffs.get(c) > adversaryPayoffs.get(c)){
						adversaryPayoffs.put(c, aPayoffs.get(c));
						adversaryStrategies.put(c, aStrategies.get(c));
					}
				}
			}
		}
		else{
			loadProblem(allTimeWindows);
			
			cplex.solve();
			
			defenderScreeningStrategy = getDefenderScreeningStrategy();
			riskCategoryCoverage = calculateRiskCategoryCoverage();
			defenderPayoffs = getDefenderPayoffs();
			adversaryPayoffs = getAdversaryPayoffs();
			adversaryStrategies = getAdversaryStrategies();
		}
	}
	
	private void sumDefenderCoverageRow() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = xMap.get(t).get(c).get(f).get(m);
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							expr = cplex.sum(expr, cplex.prod(sMap.get(t).get(f).get(c).get(o), -o.effectiveness(c, m)));
						}
						
						for(PostScreeningResource p : model.getPostScreeningResources().keySet()){
							expr = cplex.sum(expr, cplex.prod(pMap.get(t).get(f).get(p), -p.effectiveness(m)));
						}
						
						//constraints.add(cplex.le(expr, 0, "X" + t + "C" + c.id() + "F" + f.getID() + "M" + m.id() + "SUM"));
						constraints.add(cplex.eq(expr, 0, "X" + t + "C" + c.id() + "F" + f.id() + "M" + m.id() + "SUM"));
					}
				}
			}
		}
	}
	
	private void setDefenderPayoffRow() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = cplex.sum(dMap.get(c), cplex.prod(xMap.get(t).get(c).get(f).get(m), f.getDefUncovPayoff() - f.getDefCovPayoff()));
						
						expr = cplex.sum(expr, cplex.prod(aMap.get(t).get(c).get(f).get(m), MM));
						
						constraints.add(cplex.le(expr, MM + f.getDefUncovPayoff(), "DC" + t + "C" + c.id() + "F" + f.id() + "M" + m.id()));
					}
				}
			}
		}
	}
	
	private void setAdversaryPayoffRow() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = cplex.sum(kMap.get(c), cplex.prod(xMap.get(t).get(c).get(f).get(m), -1.0 * (f.getAttCovPayoff() - f.getAttUncovPayoff())));
							
						constraints.add(cplex.ge(expr, f.getAttUncovPayoff(), "AC" + t + "C" + c.id() + "F" + f.id() + "M" + m.id() + "Lo"));
					}
				}
			}
		}

		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						IloNumExpr expr = cplex.sum(kMap.get(c), cplex.prod(xMap.get(t).get(c).get(f).get(m), -1.0 * (f.getAttCovPayoff() - f.getAttUncovPayoff())));
						
						expr = cplex.sum(expr, cplex.prod(aMap.get(t).get(c).get(f).get(m), MM));
						
						constraints.add(cplex.le(expr, MM + f.getAttUncovPayoff(), "AC" + t + "C" + c.id() + "F" + f.id() + "M" + m.id() + "Up"));
					}
				}
			}
		}
	}

	//TODO: Sort out how static screening works (i.e., the same across all time windows and flights?)
	private void setStaticScreening() throws IloException{
		for(int t : currentTimeWindows){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				for(ScreeningOperation o : model.getScreeningOperations()){
					for(Flight f1 : flights.get(t)){
						IloNumExpr expr1 = sMap.get(t).get(f1).get(c).get(o);
						
						for(Flight f2 : flights.get(t)){
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
			for(Flight f : flights.get(t)){
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
		
		for(int t : currentTimeWindows){
			for(ScreeningResource r : screeningResources.keySet()){
				IloNumExpr expr = cplex.constant(0);
				
				for(Flight f : flights.get(t)){
					Map<Integer, Map<RiskCategory, Integer>> categoryDistribution = f.getTemporalPassengerDistribution();
					
					for(RiskCategory c : categoryDistribution.get(t).keySet()){
						int numPassengers = categoryDistribution.get(t).get(c);
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							if(o.getResources().contains(r)){
								expr = cplex.sum(expr, cplex.prod(sMap.get(t).get(f).get(c).get(o), numPassengers));
							}
						}
					}
				}
				
				double capacity = r.capacity() * screeningResources.get(r); //TODO: Adjust capacity according to time granularity
				
				constraints.add(cplex.le(expr, capacity, "ST" + t + "R" + r.id() + "THROUGHPUT"));
			}
		}
	}
	
	private void sumDefenderPostScreeningActionRow() throws IloException{
		Map<PostScreeningResource, Integer> postScreeningResources = model.getPostScreeningResources();
		
		for(int t : currentTimeWindows){
			for(PostScreeningResource r : postScreeningResources.keySet()){
				IloNumExpr expr = cplex.constant(0);
				
				for(Flight f : flights.get(t)){
					expr = cplex.sum(expr, pMap.get(t).get(f).get(r));
				}
				
				constraints.add(cplex.eq(expr, postScreeningResources.get(r), "PT" + t + "R" + r.id() + "SUM"));
			}
		}
	}

	private void sumAdversaryActionRow() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			IloNumExpr expr = cplex.constant(0);
			
			for(int t : currentTimeWindows){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						expr = cplex.sum(expr, aMap.get(t).get(c).get(f).get(m));
					}
				}
			}
			
			constraints.add(cplex.eq(expr, 1.0, "C" + c.id() + "SUM"));
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
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> getDefenderScreeningStrategy() throws IloException{
		Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> defenderScreeningStrategy = new HashMap<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>>();
		
		for(int t : currentTimeWindows){
			defenderScreeningStrategy.put(t, new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>());
			
			for(Flight f : flights.get(t)){
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
			for(Flight f : flights.get(t)){
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
	
	public Map<Integer, Map<PostScreeningResource, Map<Flight, Double>>> getDefenderPostScreeningStrategy() throws IloException{
		Map<Integer, Map<PostScreeningResource, Map<Flight, Double>>> defenderPostScreeningStrategy = new HashMap<Integer, Map<PostScreeningResource, Map<Flight, Double>>>();
		
		for(int t : currentTimeWindows){
			defenderPostScreeningStrategy.put(t, new HashMap<PostScreeningResource, Map<Flight, Double>>());
			
			for(PostScreeningResource r : model.getPostScreeningResources().keySet()){
				defenderPostScreeningStrategy.get(t).put(r, new HashMap<Flight, Double>());
				
				for(Flight f : flights.get(t)){
					defenderPostScreeningStrategy.get(t).get(r).put(f, cplex.getValue(pMap.get(t).get(f).get(r)));
				}
			}
		}
		
		return defenderPostScreeningStrategy;
	}
	
	public void writeDefenderPostScreeningStrategy(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<Integer, Map<PostScreeningResource, Map<Flight, Double>>> postScreeningStrategy = getDefenderPostScreeningStrategy();
	
		String line = "TimeWindow, Flight";
		
		Set<PostScreeningResource> postScreeningResources = model.getPostScreeningResources().keySet();
		
		for(PostScreeningResource r : postScreeningResources){
			line += ", " + r;
		}
		
		fw.write(line);
		
		for(int t : currentTimeWindows){
			for(Flight f : flights.get(t)){
				line = "\n" + f;
				
				for(PostScreeningResource r : postScreeningResources){
					line += ", " + postScreeningStrategy.get(t).get(r).get(f);
				}
					
				fw.write(line);
			}
		}
		
		fw.close();
	}
	
	public Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> getAdversaryStrategies() throws IloException{
		Map<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>> adversaryActionsMap = new HashMap<RiskCategory, Map<Integer, Map<Flight, AttackMethod>>>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(int t : currentTimeWindows){
				for(Flight f : flights.get(t)){
					for(AttackMethod m : model.getAttackMethods()){
						if(cplex.getValue(aMap.get(t).get(c).get(f).get(m)) > DARMSModel.EPSILON){
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
			adversaryPayoffsMap.put(c, cplex.getValue(kMap.get(c)));
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
	
	private Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> getRiskCategoryCoverage() throws IloException{
		Map<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>> riskCategoryCoverageMap = new HashMap<Integer, Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>>();
	
		for(int t : currentTimeWindows){
			riskCategoryCoverageMap.put(t, new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				riskCategoryCoverageMap.get(t).put(c, new HashMap<Flight, Map<AttackMethod, Double>>());
				
				for(Flight f : flights.get(t)){
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
				
				for(Flight f : flights.get(t)){
					riskCategoryCoverageMap.get(t).get(c).put(f, new HashMap<AttackMethod, Double>());
					
					for(AttackMethod m : model.getAttackMethods()){
						double probability = 0.0;
						
						for(ScreeningOperation o : model.getScreeningOperations()){
							probability += cplex.getValue(sMap.get(t).get(f).get(c).get(o)) * o.effectiveness(c, m);
						}
						
						for(PostScreeningResource p : model.getPostScreeningResources().keySet()){
							probability += cplex.getValue(pMap.get(t).get(f).get(p)) * p.effectiveness(m);
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
				for(Flight f : flights.get(t)){
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
			for(Flight f : flights.get(t)){
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