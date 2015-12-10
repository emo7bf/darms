package solvers;

import java.util.Map;

import models.PureStrategy;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;
import ilog.concert.IloException;

public interface DARMSSlave{
	public void solve() throws Exception;
	
	public void writeProblem(String filename) throws IloException;
	
	public void writeSolution(String filename) throws IloException;
	
	public PureStrategy getPureStrategy() throws IloException;
	
	public Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Integer>>>> getDefenderScreeningStrategy() throws IloException;
	
	public void writeDefenderScreeningStrategy(String filename) throws Exception;
	
	public void setReducedCosts(Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> reducedCosts)  throws IloException;
	
	public void initializeReducedCosts() throws IloException;
	
	public double getReducedCost() throws IloException;
}