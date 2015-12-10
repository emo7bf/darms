package examples;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.DARMSModel;
import models.PassengerDistribution;

import solvers.DARMSMarginalSolver;
import solvers.DARMSRegretMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

public class ExampleDARMSRegret{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			boolean decomposed = Boolean.parseBoolean(args[3]);
			
			long start = System.currentTimeMillis();
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			System.out.println("Building DARMS model... Started");
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			System.out.println("Building DARMS model... Completed");

			List<PassengerDistribution> passengerDistributionList = model.getRandomizedPassengerDistributions(5);
			
			Map<PassengerDistribution, Double> utopiaPoint = new HashMap<PassengerDistribution, Double>();
			
			for(PassengerDistribution passengerDistribution : passengerDistributionList){
				DARMSMarginalSolver solver = new DARMSMarginalSolver(model, passengerDistribution, true, decomposed, true, false);
				
				solver.solve();
			
				utopiaPoint.put(passengerDistribution, solver.getDefenderPayoff());
				
				solver.writeTemporalPassengerDistribution("PassengerDistribution" + passengerDistribution.id() + ".csv");
				
				System.out.println("Defender Payoff: " + solver.getDefenderPayoff());
			}
			
			DARMSRegretMarginalSolver regretSolver = new DARMSRegretMarginalSolver(model, utopiaPoint, decomposed);
			
			regretSolver.solve();
			
			regretSolver.writeProblem("DARMS.Regret.lp");
			regretSolver.writeSolution("DARMS.Regret.Solution.txt");
			
			regretSolver.writeOverflowPassengers("overflow.csv");
			
			System.out.println(regretSolver.getDistributionPayoffs().toString());
			
			boolean flightByFlight = model.flightByFlight();
			int numFlights = model.getFlights().size();
			int numCategories = model.getAdversaryDistribution().keySet().size();
			int numTimeWindows = model.getTimeWindows().size();
			double regret = regretSolver.getMaxRegret();
			double runtime = (System.currentTimeMillis() - start) / 1000.0;
						
			System.out.println(inputFile + " " + flightByFlight + " " + decomposed + " " + numFlights + " " +  numCategories+ " " + numTimeWindows + " " + regret + " " + runtime);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}