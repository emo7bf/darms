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

public class DARMSSolver {
	private DARMSModel model;
	
	private IloCplex cplex;
	
	private Map<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>> sMap;
	private Map<Flight, Map<PostScreeningResource, IloNumVar>> pMap;
	private Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>> aMap;
	private Map<RiskCategory, IloNumVar> dMap;
	private Map<RiskCategory, IloNumVar> kMap;
	private Map<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>> xMap;
	
	private List<IloRange> constraints;
	
	private static final int MM = 100000;
	
	public DARMSSolver(DARMSModel model) throws IloException{
		this.model = model;
		
		sMap = new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, IloNumVar>>>();
		pMap = new HashMap<Flight, Map<PostScreeningResource, IloNumVar>>();
		aMap = new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>();
		dMap = new HashMap<RiskCategory, IloNumVar>();
		kMap = new HashMap<RiskCategory, IloNumVar>();
		xMap = new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, IloNumVar>>>();
		
		cplex = new IloCplex();
		cplex.setName("DARMS");
		cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Barrier);
		cplex.setParam(IloCplex.IntParam.BarCrossAlg, IloCplex.Algorithm.None);
		cplex.setOut(null);
		
		initVars();
		initConstraints();
		initObjective();
	}
	
	private void initVars() throws IloException{
		List<IloNumVar> varList = new ArrayList<IloNumVar>();
		
		for(Flight f : model.getFlights()){
			sMap.put(f, new HashMap<RiskCategory, Map<ScreeningOperation, IloNumVar>>());
			
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				sMap.get(f).put(c, new HashMap<ScreeningOperation, IloNumVar>());
				
				for(ScreeningOperation o : model.getScreeningOperations()){
					IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "s_f" +  f.id() + "_c" + c.id() + "_o" + o.getID());
				
					sMap.get(f).get(c).put(o, var);
					varList.add(var);
				}
			}
		}
		
		for(Flight f : model.getFlights()){
			pMap.put(f, new HashMap<PostScreeningResource, IloNumVar>());
			
			for(PostScreeningResource r : model.getPostScreeningResources().keySet()){
				IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "p_f" +  f.id() + "_r" + r.id());
				
				pMap.get(f).put(r, var);
				varList.add(var);
			}
		}
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			xMap.put(c, new HashMap<Flight, Map<AttackMethod, IloNumVar>>());
			
			for(Flight f : model.getFlights()){
				xMap.get(c).put(f, new HashMap<AttackMethod, IloNumVar>());
				
				for(AttackMethod m : model.getAttackMethods()){
					IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Float, "x_c" + c.id() + "_f" + f.id() + "_m" + m.id());
					
					xMap.get(c).get(f).put(m, var);
					varList.add(var);
				}
			}
		}
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			aMap.put(c, new HashMap<Flight, Map<AttackMethod, IloNumVar>>());
			
			for(Flight f : model.getFlights()){
				aMap.get(c).put(f, new HashMap<AttackMethod, IloNumVar> ());
				
				for(AttackMethod m : model.getAttackMethods()){
					IloNumVar var = cplex.numVar(0.0, 1.0, IloNumVarType.Int, "a_c" + c.id() + "_f" + f.id() + "_m" + m.id());
					
					aMap.get(c).get(f).put(m, var);
					varList.add(var);
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
	
	public void solve() throws IloException{
		cplex.solve();
	}
	
	private void sumDefenderCoverageRow() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					IloNumExpr expr = xMap.get(c).get(f).get(m);
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						expr = cplex.sum(expr, cplex.prod(sMap.get(f).get(c).get(o), -o.effectiveness(c, m)));
					}
					
					for(PostScreeningResource p : model.getPostScreeningResources().keySet()){
						expr = cplex.sum(expr, cplex.prod(pMap.get(f).get(p), -p.effectiveness(m)));
					}
					
					//constraints.add(cplex.le(expr, 0, "X" + c.id() + "F" + f.getID() + "M" + m.id() + "SUM"));
					constraints.add(cplex.eq(expr, 0, "X" + c.id() + "F" + f.id() + "M" + m.id() + "SUM"));
				}
			}
		}
	}
	
	private void setDefenderPayoffRow() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					// -c_t(delta-U_{mogg.id}^d(t)
					//expr = cplex.sum(expr, cplex.prod(variableList.get(variableMap.get("x" + t.id)), t.defUncovPayoff - t.defCovPayoff));
					IloNumExpr expr = cplex.sum(dMap.get(c), cplex.prod(xMap.get(c).get(f).get(m), f.getDefUncovPayoff() - f.getDefCovPayoff()));
					
					// M. a[j]
					//expr = cplex.sum(expr, cplex.prod(variableList.get(variableMap.get("aT" + sg.id + "A" + (t.id))), Configuration.MM));
					expr = cplex.sum(expr, cplex.prod(aMap.get(c).get(f).get(m), MM));
					
					constraints.add(cplex.le(expr, MM + f.getDefUncovPayoff(), "DC" + c.id() + "F" + f.id() + "M" + m.id()));
				}
			}
		}
	}
	
	private void setAdversaryPayoffRow() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					// -\sum_i [C[ty,i,j]] x[i]
					//expr = cplex.sum(expr, cplex.prod(variableList.get(variableMap.get("x" + t.id)), -1.0 * (t.attCovPayoff - t.attUncovPayoff)));
					IloNumExpr expr = cplex.sum(kMap.get(c), cplex.prod(xMap.get(c).get(f).get(m), -1.0 * (f.getAttCovPayoff() - f.getAttUncovPayoff())));
						
					constraints.add(cplex.ge(expr, f.getAttUncovPayoff(), "AC" + c.id() + "F" + f.id() + "M" + m.id() + "Lo"));
				}
			}
		}

		// row for follower payoff: upper bound
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					// -\sum_i [C[ty,i,j]] x[i]
					//expr = cplex.sum(expr, cplex.prod(variableList.get(variableMap.get("x" + t.id)), -1.0 * (t.attCovPayoff - t.attUncovPayoff)));
					//expr = cplex.sum(expr, cplex.prod(varMap.get("x" + t.id), -1.0 * (f.attCovPayoff - f.attUncovPayoff)));
					IloNumExpr expr = cplex.sum(kMap.get(c), cplex.prod(xMap.get(c).get(f).get(m), -1.0 * (f.getAttCovPayoff() - f.getAttUncovPayoff())));
					
					//expr = cplex.sum(expr, cplex.prod(variableList.get(variableMap.get("aT" + sg.id + "A" + t.id)), Configuration.MM));
					expr = cplex.sum(expr, cplex.prod(aMap.get(c).get(f).get(m), MM));
					
					constraints.add(cplex.le(expr, MM + f.getAttUncovPayoff(), "AC" + c.id() + "F" + f.id() + "M" + m.id() + "Up"));
				}
			}
		}
	}
	
	private void setStaticScreening() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			for(ScreeningOperation o : model.getScreeningOperations()){
				for(Flight f1 : model.getFlights()){
					IloNumExpr expr1 = sMap.get(f1).get(c).get(o);
					
					for(Flight f2 : model.getFlights()){
						if(f2.id() - f1.id() == 1){
							IloNumExpr expr2 = cplex.prod(-1.0, sMap.get(f2).get(c).get(o));
							
							IloNumExpr expr = cplex.sum(expr1, expr2);
							
							constraints.add(cplex.eq(expr, 0.0, "C" + c.id() + "O" + o.getID() + "F" + f1.id() + "F" + f2.id()));
						}
					}
				}
			}
		}
	}
	
	private void sumDefenderScreeningActionRow() throws IloException{
		for(Flight f : model.getFlights()){
			for(RiskCategory c : model.getAdversaryDistribution().keySet()){
				IloNumExpr expr = cplex.constant(0);
				
				for(ScreeningOperation o : model.getScreeningOperations()){
					expr = cplex.sum(expr, sMap.get(f).get(c).get(o));
				}
				
				constraints.add(cplex.eq(expr, 1.0, "SF" + f.id() + "C" + c.id() + "SUM"));
			}
		}
	}
	
	private void sumDefenderScreeningThroughputRow() throws IloException{
		Map<ScreeningResource, Integer> screeningResources = model.getScreeningResources();
		
		for(ScreeningResource r : screeningResources.keySet()){
			IloNumExpr expr = cplex.constant(0);
			
			for(Flight f : model.getFlights()){
				Map<RiskCategory, Integer> categoryDistribution = f.getPassengerDistribution();
				
				for(RiskCategory c : categoryDistribution.keySet()){
					int numPassengers = categoryDistribution.get(c);
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						if(o.getResources().contains(r)){
							expr = cplex.sum(expr, cplex.prod(sMap.get(f).get(c).get(o), numPassengers));
						}
					}
				}
			}
			
			double totalCapacity = r.capacity() * screeningResources.get(r);
			
			constraints.add(cplex.le(expr, totalCapacity, "SR" + r.id() + "THROUGHPUT"));
		}
	}
	
	private void sumDefenderPostScreeningActionRow() throws IloException{
		Map<PostScreeningResource, Integer> postScreeningResources = model.getPostScreeningResources();
		
		for(PostScreeningResource r : postScreeningResources.keySet()){
			IloNumExpr expr = cplex.constant(0);
			
			for(Flight f : model.getFlights()){
				expr = cplex.sum(expr, pMap.get(f).get(r));
			}
			
			constraints.add(cplex.eq(expr, postScreeningResources.get(r), "R" + r.id() + "SUM"));
		}
	}
	
	private void sumAdversaryActionRow() throws IloException{
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			IloNumExpr expr = cplex.constant(0);
			
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					expr = cplex.sum(expr, aMap.get(c).get(f).get(m));
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
	
	public double getDefenderPayoff() throws IloException{
		return cplex.getObjValue();
	}
	
	public Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>> getDefenderScreeningStrategy() throws IloException{
		Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>> defenderStrategy = new HashMap<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>();
		
		for(Flight f : model.getFlights()){
			defenderStrategy.put(f, new HashMap<RiskCategory, Map<ScreeningOperation, Double>>());
			
			for(RiskCategory c : f.getPassengerDistribution().keySet()){
				defenderStrategy.get(f).put(c, new HashMap<ScreeningOperation, Double>());
				
				for(ScreeningOperation o : model.getScreeningOperations()){
					defenderStrategy.get(f).get(c).put(o, cplex.getValue(sMap.get(f).get(c).get(o)));
				}
			}
		}
		
		return defenderStrategy;
	}
	
	public void writeDefenderScreeningStrategy(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>> screeningStrategy = getDefenderScreeningStrategy();
	
		List<Flight> flights = model.getFlights();
		List<ScreeningOperation> screeningOperations = model.getScreeningOperations();
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "Flight, RiskCategory";
		
		for(ScreeningOperation o : screeningOperations){
			line += ", " + o;
		}
		
		fw.write(line);
		
		for(Flight f : flights){
			for(RiskCategory c : riskCategories){
				line = "\n" + f + ", " + c;
				
				for(ScreeningOperation o : screeningOperations){
					line += ", " + screeningStrategy.get(f).get(c).get(o);
				}
				
				fw.write(line);
			}
		}
		
		fw.close();
	}
	
	public Map<PostScreeningResource, Map<Flight, Double>> getDefenderPostScreeningStrategy() throws IloException{
		Map<PostScreeningResource, Map<Flight, Double>> defenderStrategy = new HashMap<PostScreeningResource, Map<Flight, Double>>();
		
		for(PostScreeningResource r : model.getPostScreeningResources().keySet()){
			defenderStrategy.put(r, new HashMap<Flight, Double>());
			
			for(Flight f : model.getFlights()){
				defenderStrategy.get(r).put(f, cplex.getValue(pMap.get(f).get(r)));
			}
		}
		
		return defenderStrategy;
	}
	
	public void writeDefenderPostScreeningStrategy(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<PostScreeningResource, Map<Flight, Double>> postScreeningStrategy = getDefenderPostScreeningStrategy();
	
		String line = "Flight";
		
		List<PostScreeningResource> postScreeningResources = new ArrayList<PostScreeningResource>(postScreeningStrategy.keySet());
		
		for(PostScreeningResource r : postScreeningResources){
			line += ", " + r;
		}
		
		fw.write(line);
		
		for(Flight f : model.getFlights()){
			line = "\n" + f;
			
			for(PostScreeningResource r : postScreeningResources){
				line += ", " + postScreeningStrategy.get(r).get(f);
			}
				
			fw.write(line);
		}
		
		fw.close();
	}
	
	public Map<RiskCategory, Map<Flight, AttackMethod>> getAdversaryStrategies() throws IloException{
		Map<RiskCategory, Map<Flight, AttackMethod>> adversaryActionsMap = new HashMap<RiskCategory, Map<Flight, AttackMethod>>();
		
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			adversaryActionsMap.put(c, new HashMap<Flight, AttackMethod>());
			
			for(Flight f : model.getFlights()){
				for(AttackMethod m : model.getAttackMethods()){
					if(cplex.getValue(aMap.get(c).get(f).get(m)) > DARMSModel.EPSILON){
						adversaryActionsMap.get(c).put(f, m);
					}
				}
			}
		}
		
		return adversaryActionsMap;
	}
	
	public void writeAdversaryStrategies(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<RiskCategory, Map<Flight, AttackMethod>> adversaryStrategies = getAdversaryStrategies();
	
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "RiskCategory, Flight, AttackMethod";
		
		fw.write(line);
		
		for(RiskCategory c : riskCategories){
			List<Flight> flight = new ArrayList<Flight>(adversaryStrategies.get(c).keySet());
			
			Flight f = flight.get(0);
			
			line = "\n" + c + ", " + f + ", " + adversaryStrategies.get(c).get(f);
			
			fw.write(line);
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
		
		Map<RiskCategory, Double> adversaryPayoffs = getAdversaryPayoffs();
	
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
		
		Map<RiskCategory, Double> defenderPayoffs = getDefenderPayoffs();
		
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
	
	public Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>> getRiskCategoryCoverage() throws IloException{
		Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>> riskCategoryCoverageMap = new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>();
	
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			riskCategoryCoverageMap.put(c, new HashMap<Flight, Map<AttackMethod, Double>>());
			
			for(Flight f : model.getFlights()){
				riskCategoryCoverageMap.get(c).put(f, new HashMap<AttackMethod, Double>());
				
				for(AttackMethod m : model.getAttackMethods()){
					riskCategoryCoverageMap.get(c).get(f).put(m, cplex.getValue(xMap.get(c).get(f).get(m)));
				}
			}
		}
		
		return riskCategoryCoverageMap;
	}
	
	public Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>> calculateRiskCategoryCoverage() throws IloException{
		Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>> riskCategoryCoverageMap = new HashMap<RiskCategory, Map<Flight, Map<AttackMethod, Double>>>();
	
		for(RiskCategory c : model.getAdversaryDistribution().keySet()){
			riskCategoryCoverageMap.put(c, new HashMap<Flight, Map<AttackMethod, Double>>());
			
			for(Flight f : model.getFlights()){
				riskCategoryCoverageMap.get(c).put(f, new HashMap<AttackMethod, Double>());
				
				for(AttackMethod m : model.getAttackMethods()){
					double probability = 0.0;
					
					for(ScreeningOperation o : model.getScreeningOperations()){
						probability += cplex.getValue(sMap.get(f).get(c).get(o)) * o.effectiveness(c, m);
					}
					
					for(PostScreeningResource p : model.getPostScreeningResources().keySet()){
						probability += cplex.getValue(pMap.get(f).get(p)) * p.effectiveness(m);
					}
					
					if(probability > 1.0){
						probability = 1.0;
					}
					
					riskCategoryCoverageMap.get(c).get(f).put(m, probability);
				}
			}
		}
		
		return riskCategoryCoverageMap;
	}
	
	public void writeRiskCategoryCoverage(String filename) throws Exception{
		FileWriter fw = new FileWriter(new File(filename));
		
		Map<RiskCategory, Map<Flight, Map<AttackMethod, Double>>> riskCategoryCoverage = calculateRiskCategoryCoverage();
	
		List<Flight> flights = model.getFlights();
		List<AttackMethod> attackMethods = model.getAttackMethods();
		
		List<RiskCategory> riskCategories = new ArrayList<RiskCategory>(model.getAdversaryDistribution().keySet());
		Collections.sort(riskCategories);
		
		String line = "RiskCategory, Flight";
		
		for(AttackMethod m : attackMethods){
			line += ", " + m + "_coverage, " + m + "_payoff, " + m + "_utility";
		}
		
		fw.write(line);
		
		for(RiskCategory c : riskCategories){
			for(Flight f : flights){
				line = "\n" + c + ", " + f;
				
				for(AttackMethod m : attackMethods){
					double coverage = riskCategoryCoverage.get(c).get(f).get(m);
					double payoff = coverage * f.getAttCovPayoff() + ((1.0 - coverage) * f.getAttUncovPayoff()); 
					double utility = payoff * model.getAdversaryDistribution().get(c);
					
					line += ", " + coverage + ", " + payoff + ", " + utility;
				}
				
				fw.write(line);
			}
		}
		
		fw.close();
	}
}