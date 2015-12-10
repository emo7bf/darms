package examples;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

import models.DARMSModel;
import models.PassengerDistribution;
import models.Flight;
import models.RiskCategory;
import models.ScreeningOperation;

public class ExampleDARMSRegretComparison{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			
			long start = System.currentTimeMillis();
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			System.out.println("Building DARMS model... Started");
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			System.out.println("Building DARMS model... Completed");

			List<PassengerDistribution> passengerDistributionList = model.getRandomizedPassengerDistributions(50);
			
			Map<PassengerDistribution, DARMSMarginalSolver> distributionSolverMap = new HashMap<PassengerDistribution, DARMSMarginalSolver>();
			Map<PassengerDistribution, Double> regretMap = new HashMap<PassengerDistribution, Double>();
			Map<PassengerDistribution, Double> overflowMap = new HashMap<PassengerDistribution, Double>();
			
			for(PassengerDistribution pd : passengerDistributionList){
				DARMSMarginalSolver solver = new DARMSMarginalSolver(model, pd, true, false, true, false);
				
				solver.solve();
			
				distributionSolverMap.put(pd, solver);
				
				solver.writeTemporalPassengerDistribution("PassengerDistribution" + pd.id() + ".csv");
				
				System.out.println("Defender Payoff: " + pd + " " + solver.getDefenderPayoff());
			}
			
			for(PassengerDistribution pd1 : passengerDistributionList){
				double totalRegret = 0;
				double totalOverflow = 0;
				
				for(PassengerDistribution pd2 : passengerDistributionList){
					double defenderPayoff1 = distributionSolverMap.get(pd1).getDefenderPayoff();
					
					Map<Integer, Map<Flight, Map<RiskCategory, Map<ScreeningOperation, Double>>>> screeningStrategy = distributionSolverMap.get(pd1).getDefenderScreeningStrategy();  
					
					if(pd1.id() != pd2.id()){
						double defenderPayoff2 = distributionSolverMap.get(pd2).getDefenderPayoff();
					
						double regret = defenderPayoff2 - defenderPayoff1;
						
						if(regret > 0){
							totalRegret += regret;
						}
						
						double overflow = distributionSolverMap.get(pd2).calculateOverflowPassengers(screeningStrategy);
						double overflowPercentage = overflow * 100.0 / (double)pd1.getTotalPassengers();
						
						totalOverflow += overflowPercentage;
						
						//System.out.println(pd1 + " " + pd2 + " " + regret + " " + overflow + " "  + overflowPercentage + " " + pd1.getTotalPassengers());
					}
				}
				
				double averageRegret = totalRegret / (double)(passengerDistributionList.size() - 1);
				double averageOverflow = totalOverflow / (double)(passengerDistributionList.size() - 1);
				
				regretMap.put(pd1, averageRegret);
				overflowMap.put(pd1, averageOverflow);
				
				System.out.println("Evaluation: " + pd1 + " " + averageRegret + " " + averageOverflow);
			}
			
			for(PassengerDistribution pd1 : passengerDistributionList){
				boolean paretoOptimal = true;
				
				for(PassengerDistribution pd2 : passengerDistributionList){
					if(pd1.id() != pd2.id()){
						if(regretMap.get(pd2) <= regretMap.get(pd1) && overflowMap.get(pd2) <= overflowMap.get(pd1)){
							paretoOptimal = false;
							break;
						}
					}
				}
				
				if(paretoOptimal){
					System.out.println("Pareto Optimal: " + pd1 + " " + regretMap.get(pd1) + " " + overflowMap.get(pd1));
				}
			}
			
			boolean flightByFlight = model.flightByFlight();
			int numFlights = model.getFlights().size();
			int numCategories = model.getAdversaryDistribution().keySet().size();
			int numTimeWindows = model.getTimeWindows().size();
			double runtime = (System.currentTimeMillis() - start) / 1000.0;
						
			System.out.println(inputFile + " " + flightByFlight + " " + numFlights + " " +  numCategories + " " + numTimeWindows + " " + runtime);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}