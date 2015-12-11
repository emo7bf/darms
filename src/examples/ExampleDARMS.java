package examples;

import models.DARMSModel;
import models.DARMSOutput;
import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

public class ExampleDARMS{
	public static void main(String[] args){
		try{
			int intervals = 50;
//			double min = 0;
//			double max = 30;
				
			// double[] fr = new double[intervals + 1];
	
			for( int i = 0; i < intervals + 1; ++i ){
				// fr[i] = min + (max - min)*i / intervals;
	
				String cplexFile = "CplexConfig"; // args[0];
				String inputFile = "InputDARMS.30.6.true.txt"; // args[1];
				String outputFile = "OutputDARMS.txt";
				boolean zeroSum = true;
				boolean decomposed = false;
				boolean verbose = true;
				
				long start = System.currentTimeMillis();
				
				DARMSHelper.loadLibrariesCplex(cplexFile);
				
				if(verbose){
					System.out.println("Building DARMS model... Started");
				}
				
				DARMSModel model = DARMSModelBuilder.buildModel(inputFile, verbose, i);
				
				if(verbose){
					System.out.println("Building DARMS model... Completed");
				}
				
				DARMSOutput output = new DARMSOutput(outputFile);
				
				DARMSMarginalSolver solver = new DARMSMarginalSolver(model, zeroSum, decomposed, model.flightByFlight(), false);
				
				if(verbose){
					System.out.println("Solving DARMS model... Started");
				}
				
				solver.solve();
				
				solver.writeProblem("DARMS.lp");
				solver.writeSolution("DARMS.sol");
				
				if(verbose){
					System.out.println("Solving DARMS model... Completed");
					
					String [] aa = output.defenderScreeningStrategyFile().split(".csv");
					String fname = aa[0] + i + ".csv";
					
					System.out.println("Saving output file: " + fname);
					solver.writeDefenderScreeningStrategy(fname);
					
					aa = output.adversaryStrategiesFile().split(".csv");
					fname = aa[0] + i + ".csv";
					
					System.out.println("Saving output file: " + fname);
					solver.writeAdversaryStrategies(fname);
					
					aa = output.adversaryPayoffsFile().split(".csv");
					fname = aa[0] + i + ".csv";
				
					System.out.println("Saving output file: " + fname);
					solver.writeAdversaryPayoffs(fname);
					
					aa = output.defenderPayoffsFile().split(".csv");
					fname = aa[0] + i + ".csv";
					
					System.out.println("Saving output file: " + fname);
					solver.writeDefenderPayoffs(fname);
					
					aa = output.flightRiskCategoryCoverageFile().split(".csv");
					fname = aa[0] + i + ".csv";				
					
					System.out.println("Saving output file: " + fname);
					solver.writeRiskCategoryCoverage(fname);
					
					aa = output.passengerDistributionFile().split(".csv");
					fname = aa[0] + i + ".csv";					
					
					System.out.println("Saving output file: " + fname);
					solver.writeTemporalPassengerDistribution(fname);
					
					aa = output.resourceFinesFile().split(".csv");
					fname = aa[0] + i + ".csv";
					
				}
				
				boolean flightByFlight = model.flightByFlight();
				int numFlights = model.getFlights().size();
				int numCategories = model.getAdversaryDistribution().keySet().size();
				int numTimeWindows = model.getTimeWindows().size();
				double defenderPayoff = solver.getDefenderPayoff();
				double runtime = (System.currentTimeMillis() - start) / 1000.0;
							
				System.out.println(inputFile + " " + flightByFlight + " " + zeroSum + " " + decomposed + " " + numFlights + " " +  numCategories+ " " + numTimeWindows + " " + defenderPayoff + " " + runtime);
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}