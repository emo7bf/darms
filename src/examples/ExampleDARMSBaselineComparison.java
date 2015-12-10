package examples;

import java.util.Map;

import models.DARMSModel;
import models.PureStrategy;

import solvers.DARMSOptimalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

public class ExampleDARMSBaselineComparison{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			int iterationCutoff = Integer.parseInt(args[2]);
			boolean betterResponse = Boolean.parseBoolean(args[3]);
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
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
			
			for(PureStrategy p : mixedStrategy.keySet()){
				System.out.println("PureStrategy" + p.id() + ": " + mixedStrategy.get(p));
			}
			
			System.out.print(inputFile + " " + numFlights + " " + numCategories+ " " + numTimeWindows + " ");
			System.out.print(iterationCutoff + " " + betterResponse + " ");
			System.out.print(marginalPayoff + " " + oneNormPayoff + " " + oneNormDistance + " ");
			System.out.println(masterIterations + " " + slaveIterations + " " + optimalRuntime); 
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}