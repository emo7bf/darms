package examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.DARMSModel;
import models.PureStrategy;

import solvers.DARMSOptimalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

public class ExampleDARMSOptimal{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			//int warmStartIterations = Integer.parseInt(args[2]);
			int iterationCutoff = Integer.parseInt(args[2]);
			boolean betterResponse = Boolean.parseBoolean(args[3]);
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			//long baselineStart = System.currentTimeMillis();
			
			//DARMSBaselineSolver baselineSolver = new DARMSBaselineSolver(model);
			//baselineSolver.solve();
				
			//double baselineRuntime = (System.currentTimeMillis() - baselineStart) / 1000.0;
			//double baselineDefenderPayoff = baselineSolver.getDefenderPayoff();
			
			long optimalStart = System.currentTimeMillis();
			
			DARMSOptimalSolver optimalSolver = new DARMSOptimalSolver(model, 0, iterationCutoff, false, betterResponse);
			
			optimalSolver.solve();
			
			double optimalRuntime = (System.currentTimeMillis() - optimalStart) / 1000.0;
			
			int numFlights = model.getFlights().size();
			int numCategories = model.getAdversaryDistribution().keySet().size();
			int numTimeWindows = model.getTimeWindows().size();
			
			double marginalPayoff = optimalSolver.getMarginalDefenderPayoff();
			double oneNormPayoff = optimalSolver.getOneNormDefenderPayoff();
			
			int masterIterations = optimalSolver.getMasterIterations();
			int slaveIterations = optimalSolver.getSlaveIterations();
			
			double oneNormDistance = optimalSolver.getOneNormDistance();
			
			Map<PureStrategy, Double> mixedStrategy = optimalSolver.getMixedStrategy();
			List<PureStrategy> warmStartPureStrategies = optimalSolver.getWarmStartPureStrategies();
			
			//System.out.println("Warm Start Strategies");
			
			for(PureStrategy p : warmStartPureStrategies){
				//System.out.println("Pure Strategy " + p.getID() + " : " + mixedStrategy.get(p));
			}
			
			//System.out.println("Mixed Strategy");
			
			List<PureStrategy> support = new ArrayList<PureStrategy>();
			
			for(PureStrategy p : warmStartPureStrategies){
				//System.out.println("Pure Strategy " + p.getID() + " : " + mixedStrategy.get(p));
				
				if(mixedStrategy.get(p) > 0.0){
					support.add(p);
				}
			}
			
			//DARMSOptimalSolver optimalSolver1 = new DARMSOptimalSolver(model, support, true, betterResponse);
			//optimalSolver1.solve();
						
			System.out.print(inputFile + " " + numFlights + " " + numCategories+ " " + numTimeWindows + " ");
			System.out.print(iterationCutoff + " " + betterResponse + " ");
			System.out.print(marginalPayoff + " " + oneNormPayoff + " " + oneNormDistance + " ");
			System.out.println(masterIterations + " " + slaveIterations + " " + optimalRuntime); 
			//System.out.println(baselineDefenderPayoff + " " + baselineRuntime);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}