package examples;

import models.DARMSModel;
import models.DARMSOutput;
import models.PureStrategy;
import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;
import utilities.DARMSPureStrategySampler;

public class ExampleDARMSSampler{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			String outputFile = args[2];
			boolean zeroSum = Boolean.parseBoolean(args[3]);
			boolean decomposed = Boolean.parseBoolean(args[4]);
			boolean verbose = Boolean.parseBoolean(args[5]);
			
			long start = System.currentTimeMillis();
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			if(verbose){
				System.out.println("Building DARMS model... Started");
			}
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, verbose);
			
			if(verbose){
				System.out.println("Building DARMS model... Completed");
			}
			
			DARMSOutput output = new DARMSOutput(outputFile);
			
			DARMSMarginalSolver solver = new DARMSMarginalSolver(model, zeroSum, decomposed, model.flightByFlight(), false);
			
			if(verbose){
				System.out.println("Solving DARMS model... Started");
			}
			
			solver.solve();
			
			DARMSPureStrategySampler sampler = new DARMSPureStrategySampler(model, solver);
			
			PureStrategy p = sampler.samplePureStrategy();
			
			System.out.println(p);
			
			if(verbose){
				System.out.println("Solving DARMS model... Completed");
				
				System.out.println("Saving output file: " + output.defenderScreeningStrategyFile());
				solver.writeDefenderScreeningStrategy(output.defenderScreeningStrategyFile());
				
				System.out.println("Saving output file: " + output.adversaryStrategiesFile());
				solver.writeAdversaryStrategies(output.adversaryStrategiesFile());
			
				System.out.println("Saving output file: " + output.adversaryPayoffsFile());
				solver.writeAdversaryPayoffs(output.adversaryPayoffsFile());
			
				System.out.println("Saving output file: " + output.defenderPayoffsFile());
				solver.writeDefenderPayoffs(output.defenderPayoffsFile());
			
				System.out.println("Saving output file: " + output.flightRiskCategoryCoverageFile());
				solver.writeRiskCategoryCoverage(output.flightRiskCategoryCoverageFile());
				
				System.out.println("Saving output file: " + output.passengerDistributionFile());
				solver.writeTemporalPassengerDistribution(output.passengerDistributionFile());
			}
			
			boolean flightByFlight = model.flightByFlight();
			int numFlights = model.getFlights().size();
			int numCategories = model.getAdversaryDistribution().keySet().size();
			int numTimeWindows = model.getTimeWindows().size();
			double defenderPayoff = solver.getDefenderPayoff();
			double runtime = (System.currentTimeMillis() - start) / 1000.0;
						
			System.out.println(inputFile + " " + flightByFlight + " " + zeroSum + " " + decomposed + " " + numFlights + " " +  numCategories+ " " + numTimeWindows + " " + defenderPayoff + " " + runtime);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}