package examples;
import models.DARMSModel;
import solvers.DARMSMarginalSolver;
import utilities.DARMSHelper;
import utilities.DARMSModelBuilder;

public class ExampleFlightByFlightComparison{
	public static void main(String[] args){
		try{
			String cplexFile = args[0];
			String inputFile = args[1];
			
			DARMSHelper.loadLibrariesCplex(cplexFile);
			
			DARMSModel model = DARMSModelBuilder.buildModel(inputFile, false);
			
			long naiveStartTime = System.currentTimeMillis();
			
			DARMSMarginalSolver naiveSolver = new DARMSMarginalSolver(model, true, false, false, true);
			
			naiveSolver.solve();
			
			double naiveRuntime = (System.currentTimeMillis() - naiveStartTime) / 1000.0;
			double naiveDefenderPayoff = naiveSolver.calculateDefenderPayoff();
			
			naiveSolver.writeRiskCategoryCoverage("NaiveCoverage.csv");
			
			long staticStartTime = System.currentTimeMillis();
			
			DARMSMarginalSolver staticSolver = new DARMSMarginalSolver(model, true, false, false, false);
			
			staticSolver.solve();
			
			double staticRuntime = (System.currentTimeMillis() - staticStartTime) / 1000.0;
			double staticDefenderPayoff = staticSolver.getDefenderPayoff();
			
			staticSolver.writeRiskCategoryCoverage("StaticCoverage.csv");
			
			long dynamicStartTime = System.currentTimeMillis();
			
			DARMSMarginalSolver dynamicSolver = new DARMSMarginalSolver(model, true, false, true, false);
			
			dynamicSolver.solve();
			
			double dynamicRuntime = (System.currentTimeMillis() - dynamicStartTime) / 1000.0;
			double dynamicDefenderPayoff = dynamicSolver.getDefenderPayoff();
			
			dynamicSolver.writeRiskCategoryCoverage("DynamicCoverage.csv");
			
			int numFlights = model.getFlights().size();
			int numCategories = model.getAdversaryDistribution().keySet().size();
			int numTimeWindows = model.getTimeWindows().size();
			
			System.out.print(inputFile + " " + numFlights + " " + numCategories + " " + numTimeWindows + " ");
			System.out.print(naiveRuntime + " " + naiveDefenderPayoff + " ");
			System.out.print(staticRuntime + " " + staticDefenderPayoff + " ");
			System.out.println(dynamicRuntime + " " + dynamicDefenderPayoff);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}